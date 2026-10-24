import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.database import engine, Base
from app.routers import telemetry_router, geofence_router, alerts_router, contacts_router, auth_router
from app.routers.telemetry import ingest_telemetry
from app.schemas.telemetry import TelemetryCreate, DeviceStatusResponse
from app.database import get_db
from sqlalchemy.orm import Session
from fastapi import Depends
from app.services.websocket_manager import manager as ws_manager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("GuardianLink.Main")

# Create database tables automatically
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="GuardianLink API",
    description="High-performance backend for GuardianLink personal safety and IoT tracking system.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount REST Routers
app.include_router(telemetry_router)
app.include_router(geofence_router)
app.include_router(alerts_router)
app.include_router(contacts_router)
app.include_router(auth_router)


@app.on_event("startup")
def startup_seed_demo_device():
    import datetime
    from app.database import SessionLocal
    from app.models.device import Device
    from app.models.geofence import GeofenceZone
    from app.models.alert import Alert
    db = SessionLocal()
    try:
        # 1. Permanently remove any legacy Delhi dummy geofences
        db.query(GeofenceZone).filter(
            GeofenceZone.name.like("%India Gate%") |
            GeofenceZone.name.like("%Rajpath%") |
            GeofenceZone.name.like("%Purana%") |
            GeofenceZone.name.like("%Test Zone%") |
            (GeofenceZone.center_latitude.between(28.0, 29.5) & GeofenceZone.center_longitude.between(76.5, 78.0))
        ).delete(synchronize_session=False)

        # 2. Reset all devices that have legacy Delhi coordinates
        all_devices = db.query(Device).all()
        for dev in all_devices:
            if dev.last_latitude and abs(dev.last_latitude - 28.6) < 0.5 and dev.last_longitude and abs(dev.last_longitude - 77.2) < 0.5:
                dev.last_latitude = None
                dev.last_longitude = None
                dev.status = "Waiting for signal"

        demo_dev = db.query(Device).filter(Device.device_id == "demo_device").first()
        if not demo_dev:
            demo_dev = Device(
                device_id="demo_device",
                name="Guardian Wearable",
                status="Waiting for signal",
                last_latitude=None,
                last_longitude=None,
                battery="--",
                network="--",
                sos=False,
                updated_at=None
            )
            db.add(demo_dev)

        # 3. Purge dummy test alerts referencing Delhi and dummy Safe Zone Breaches
        db.query(Alert).filter(
            Alert.title.like("%India Gate%") |
            Alert.title.like("%Rajpath%") |
            Alert.title.like("%Purana%") |
            Alert.title.like("%Safe Zone Breach%") |
            (Alert.latitude.between(28.0, 29.5) & Alert.longitude.between(76.5, 78.0))
        ).delete(synchronize_session=False)

        db.commit()
        logger.info("Startup initialized: Delhi coordinates, dummy alerts, and dummy safe zones purged")
    except Exception as e:
        logger.error(f"Error initializing demo device: {e}")
    finally:
        db.close()


@app.get("/")
def root():
    return {
        "app": "GuardianLink API",
        "version": "1.0.0",
        "status": "online",
        "docs": "/docs",
        "websocket_endpoint": "/ws/devices/{device_id}"
    }


@app.post("/", response_model=DeviceStatusResponse, status_code=201)
@app.post("/telemetry", response_model=DeviceStatusResponse, status_code=201)
@app.post("/api/telemetry", response_model=DeviceStatusResponse, status_code=201)
async def root_telemetry_fallback(payload: TelemetryCreate, db: Session = Depends(get_db)):
    """Fallback handler so ESP32 never receives 404 even if path is omitted."""
    return await ingest_telemetry(payload, db)


@app.get("/health")
def health_check():
    return {"status": "healthy"}


@app.get("/tracker", response_class=HTMLResponse)
def live_gps_tracker_page():
    return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>GuardianLink Companion GPS Tracker</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0c1222; color: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 20px; }
        .card { background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(20px); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 24px; padding: 28px; width: 100%; max-width: 420px; box-shadow: 0 20px 40px rgba(0,0,0,0.4); text-align: center; }
        .badge { display: inline-block; background: rgba(56, 189, 248, 0.15); color: #38bdf8; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; padding: 6px 14px; border-radius: 20px; margin-bottom: 16px; border: 1px solid rgba(56, 189, 248, 0.3); }
        h1 { font-size: 22px; font-weight: 800; margin-bottom: 6px; }
        p.subtitle { color: #94a3b8; font-size: 13px; margin-bottom: 24px; }
        .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; text-align: left; }
        .stat-box { background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(255, 255, 255, 0.06); border-radius: 16px; padding: 14px; }
        .stat-label { font-size: 11px; color: #94a3b8; margin-bottom: 4px; }
        .stat-value { font-size: 16px; font-weight: 700; color: #f1f5f9; font-variant-numeric: tabular-nums; }
        .btn-toggle { width: 100%; padding: 16px; border: none; border-radius: 16px; font-size: 15px; font-weight: 700; cursor: pointer; transition: all 0.2s; display: flex; justify-content: center; align-items: center; gap: 8px; }
        .btn-start { background: linear-gradient(135deg, #0284c7, #2563eb); color: #fff; box-shadow: 0 8px 24px rgba(37, 99, 235, 0.4); }
        .btn-stop { background: linear-gradient(135deg, #dc2626, #b91c1c); color: #fff; box-shadow: 0 8px 24px rgba(220, 38, 38, 0.4); }
        .pulse-indicator { width: 10px; height: 10px; border-radius: 50%; background: #22c55e; display: inline-block; animation: pulse 1.5s infinite; }
        @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7); } 70% { transform: scale(1.1); box-shadow: 0 0 0 8px rgba(34, 197, 94, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); } }
        .log { margin-top: 20px; font-size: 12px; color: #64748b; line-height: 1.5; }
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">Companion GPS Mode</div>
        <h1>Phone Live Location</h1>
        <p class="subtitle">Broadcast this phone's GPS as wearable location for real-time tracking & geofencing.</p>
        
        <div class="stat-grid">
            <div class="stat-box">
                <div class="stat-label">Latitude</div>
                <div class="stat-value" id="val-lat">--</div>
            </div>
            <div class="stat-box">
                <div class="stat-label">Longitude</div>
                <div class="stat-value" id="val-lon">--</div>
            </div>
            <div class="stat-box">
                <div class="stat-label">Accuracy</div>
                <div class="stat-value" id="val-acc">--</div>
            </div>
            <div class="stat-box">
                <div class="stat-label">Updates Sent</div>
                <div class="stat-value" id="val-count">0</div>
            </div>
        </div>

        <button id="toggle-btn" class="btn-toggle btn-start" onclick="toggleTracking()">
            <span>📍 Start Live GPS Broadcast</span>
        </button>

        <div class="log" id="status-log">Tap above to share your live location. Keep this browser tab open.</div>
    </div>

    <script>
        let watchId = null;
        let updateCount = 0;
        let isTracking = false;
        const deviceId = "demo_device";

        async function sendLocation(lat, lon, acc) {
            try {
                const res = await fetch(`/api/v1/telemetry/${deviceId}/location`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'bypass-tunnel-reminder': 'true' },
                    body: JSON.stringify({ latitude: lat, longitude: lon })
                });
                if (res.ok) {
                    updateCount++;
                    document.getElementById('val-count').innerText = updateCount;
                    document.getElementById('status-log').innerHTML = `<span class="pulse-indicator"></span> Transmitting live GPS (Last: ${new Date().toLocaleTimeString()})`;
                }
            } catch (err) {
                document.getElementById('status-log').innerText = "Transmission error: " + err.message;
            }
        }

        function toggleTracking() {
            if (!isTracking) {
                if (!navigator.geolocation) {
                    alert("Geolocation is not supported by your browser");
                    return;
                }
                const btn = document.getElementById('toggle-btn');
                btn.className = "btn-toggle btn-stop";
                btn.innerHTML = "<span>🛑 Stop Live Broadcast</span>";
                document.getElementById('status-log').innerText = "Acquiring GPS fix...";

                watchId = navigator.geolocation.watchPosition(
                    (pos) => {
                        const lat = pos.coords.latitude;
                        const lon = pos.coords.longitude;
                        const acc = pos.coords.accuracy;
                        document.getElementById('val-lat').innerText = lat.toFixed(5);
                        document.getElementById('val-lon').innerText = lon.toFixed(5);
                        document.getElementById('val-acc').innerText = `±${Math.round(acc)}m`;
                        sendLocation(lat, lon, acc);
                    },
                    (err) => {
                        document.getElementById('status-log').innerText = "GPS Error: " + err.message;
                    },
                    { enableHighAccuracy: true, maximumAge: 2000, timeout: 10000 }
                );
                isTracking = true;
            } else {
                if (watchId !== null) {
                    navigator.geolocation.clearWatch(watchId);
                    watchId = null;
                }
                const btn = document.getElementById('toggle-btn');
                btn.className = "btn-toggle btn-start";
                btn.innerHTML = "<span>📍 Start Live GPS Broadcast</span>";
                document.getElementById('status-log').innerText = "Broadcast paused.";
                isTracking = false;
            }
        }
    </script>
</body>
</html>"""


@app.get("/tracker", response_class=HTMLResponse)
def live_gps_tracker_page():
    """
    Zero-install mobile web page for the wearer's phone.
    Streams high-accuracy phone GPS coordinates to GuardianLink in real time.
    """
    return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>GuardianLink Wearer Beacon</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #090d16; color: #f8fafc; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(16px); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 24px; padding: 28px 24px; width: 100%; max-width: 380px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); text-align: center; }
        .badge { display: inline-block; background: rgba(56, 189, 248, 0.15); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3); padding: 5px 12px; border-radius: 20px; font-size: 11px; font-weight: 800; letter-spacing: 0.5px; text-transform: uppercase; margin-bottom: 12px; }
        h1 { font-size: 22px; font-weight: 800; color: #fff; margin-bottom: 8px; }
        p { color: #94a3b8; font-size: 13px; line-height: 1.5; margin-bottom: 20px; }
        .btn { background: linear-gradient(135deg, #2563eb, #3b82f6); color: white; border: none; border-radius: 14px; padding: 16px; font-size: 15px; font-weight: 700; width: 100%; cursor: pointer; transition: all 0.2s ease; box-shadow: 0 4px 15px rgba(37, 99, 235, 0.4); }
        .btn:active { transform: scale(0.98); }
        .btn.active { background: linear-gradient(135deg, #dc2626, #ef4444); box-shadow: 0 4px 15px rgba(239, 68, 68, 0.4); }
        .stats { margin-top: 24px; text-align: left; display: none; }
        .stat-box { background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255, 255, 255, 0.06); border-radius: 14px; padding: 12px 14px; margin-bottom: 10px; }
        .stat-title { font-size: 11px; color: #64748b; font-weight: 700; text-transform: uppercase; }
        .stat-value { font-size: 15px; font-weight: 700; color: #fff; margin-top: 3px; word-break: break-all; }
        .live-dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: #22c55e; margin-right: 6px; box-shadow: 0 0 10px #22c55e; }
    </style>
</head>
<body>
    <div class="card">
        <span class="badge">Companion GPS Beacon</span>
        <h1>Wearer Live Location</h1>
        <p>Keep this page open on the phone in the wearer's pocket. It streams real-time phone GPS coordinates to GuardianLink for live tracking and geofencing.</p>
        <button id="toggleBtn" class="btn" onclick="toggleTracking()">START SHARING LOCATION</button>
        <div id="stats" class="stats">
            <div class="stat-box">
                <div class="stat-title">Status</div>
                <div class="stat-value" style="color: #22c55e;"><span class="live-dot"></span>BROADCASTING LIVE</div>
            </div>
            <div class="stat-box">
                <div class="stat-title">Live GPS Coordinates</div>
                <div class="stat-value" id="coords">Acquiring satellites...</div>
            </div>
            <div class="stat-box">
                <div class="stat-title">Accuracy</div>
                <div class="stat-value" id="accuracy">Calculating...</div>
            </div>
            <div class="stat-box">
                <div class="stat-title">Last Server Sync</div>
                <div class="stat-value" id="lastSync">--</div>
            </div>
        </div>
    </div>
    <script>
        let watchId = null;
        let isTracking = false;
        let lastPost = 0;

        function toggleTracking() {
            const btn = document.getElementById('toggleBtn');
            const stats = document.getElementById('stats');
            if (!isTracking) {
                if (!navigator.geolocation) {
                    alert("Geolocation is not supported by your mobile browser.");
                    return;
                }
                isTracking = true;
                btn.innerText = "STOP SHARING LOCATION";
                btn.classList.add("active");
                stats.style.display = "block";

                watchId = navigator.geolocation.watchPosition(
                    (pos) => {
                        const lat = pos.coords.latitude;
                        const lon = pos.coords.longitude;
                        const acc = pos.coords.accuracy;
                        document.getElementById('coords').innerText = lat.toFixed(5) + " N, " + lon.toFixed(5) + " E";
                        document.getElementById('accuracy').innerText = "±" + Math.round(acc) + " meters";

                        const now = Date.now();
                        if (now - lastPost >= 3000) {
                            lastPost = now;
                            sendLiveCoords(lat, lon);
                        }
                    },
                    (err) => {
                        console.error(err);
                        document.getElementById('coords').innerText = "Location Error: " + err.message;
                    },
                    { enableHighAccuracy: true, maximumAge: 2000, timeout: 10000 }
                );
            } else {
                isTracking = false;
                if (watchId) navigator.geolocation.clearWatch(watchId);
                btn.innerText = "START SHARING LOCATION";
                btn.classList.remove("active");
                stats.style.display = "none";
            }
        }

        async function sendLiveCoords(lat, lon) {
            try {
                const res = await fetch('/api/v1/telemetry', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Bypass-Tunnel-Reminder': 'true' },
                    body: JSON.stringify({
                        device_id: 'demo_device',
                        latitude: lat,
                        longitude: lon
                    })
                });
                if (res.ok) {
                    const time = new Date().toLocaleTimeString();
                    document.getElementById('lastSync').innerText = time + " (Synced to GuardianLink)";
                }
            } catch (e) {
                console.error("Network error sending coordinates", e);
            }
        }
    </script>
</body>
</html>"""


@app.websocket("/ws/devices/{device_id}")
async def websocket_device_stream(websocket: WebSocket, device_id: str):
    """
    Real-time WebSocket endpoint for the GuardianLink Android app.
    Streams live telemetry, battery, GPS coordinates, and SOS events instantly.
    """
    await ws_manager.connect(websocket, device_id)
    try:
        while True:
            # Keep connection alive; can also receive heartbeats/commands from the Android app
            data = await websocket.receive_text()
            logger.debug(f"Received from client on device {device_id}: {data}")
    except WebSocketDisconnect:
        ws_manager.disconnect(websocket, device_id)
    except Exception as e:
        logger.error(f"WebSocket error on device {device_id}: {e}")
        ws_manager.disconnect(websocket, device_id)
