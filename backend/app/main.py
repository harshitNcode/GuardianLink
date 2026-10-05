import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.database import engine, Base
from app.routers import telemetry_router, geofence_router, alerts_router, contacts_router, auth_router
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
    db = SessionLocal()
    try:
        dev = db.query(Device).filter(Device.device_id == "demo_device").first()
        if not dev:
            dev = Device(
                device_id="demo_device",
                name="Guardian Wearable",
                status="Online",
                last_latitude=28.6139,
                last_longitude=77.2090,
                battery="92%",
                network="4G LTE",
                sos=False,
                updated_at=datetime.datetime.utcnow()
            )
            db.add(dev)
            db.commit()
            logger.info("Seeded initial demo_device with coordinates 28.6139, 77.2090")
        elif not dev.last_latitude or not dev.last_longitude:
            dev.last_latitude = 28.6139
            dev.last_longitude = 77.2090
            dev.status = "Online"
            db.commit()
            logger.info("Updated demo_device with default coordinates 28.6139, 77.2090")
    except Exception as e:
        logger.error(f"Error seeding demo device: {e}")
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


@app.get("/health")
def health_check():
    return {"status": "healthy"}


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
