import datetime
import time
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.device import Device, TelemetryLog
from app.models.alert import Alert
from app.schemas.telemetry import TelemetryCreate, TelemetryResponse, DeviceStatusResponse, LocationUpdate
from app.services.websocket_manager import manager as ws_manager
from app.services.geofence_service import evaluate_geofences
from app.services.notification_service import dispatch_emergency_notification

router = APIRouter(prefix="/api/v1/telemetry", tags=["Telemetry & Devices"])

IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))

# Fast in-memory one-shot buzzer triggers. Consumed once delivered to the device so it never loops!
PENDING_BUZZER_TRIGGERS: set = set()
BUZZER_TRIGGER_EXPIRY: dict = {}
DEVICE_LOCAL_IP_MAP: dict = {}


def trigger_device_buzzer(device_id: str):
    now = time.time()
    PENDING_BUZZER_TRIGGERS.add(device_id)
    PENDING_BUZZER_TRIGGERS.add("demo_device")
    # Expires in 15 seconds if unconsumed so stale triggers don't linger
    BUZZER_TRIGGER_EXPIRY[device_id] = now + 15.0
    BUZZER_TRIGGER_EXPIRY["demo_device"] = now + 15.0


def pop_device_buzzer(device_id: str) -> bool:
    """Check if a buzzer trigger is pending for this device, and CONSUME it immediately so it only fires ONCE."""
    now = time.time()
    stale = [k for k, exp in list(BUZZER_TRIGGER_EXPIRY.items()) if now > exp]
    for k in stale:
        BUZZER_TRIGGER_EXPIRY.pop(k, None)
        PENDING_BUZZER_TRIGGERS.discard(k)

    if device_id in PENDING_BUZZER_TRIGGERS or "demo_device" in PENDING_BUZZER_TRIGGERS:
        PENDING_BUZZER_TRIGGERS.discard(device_id)
        PENDING_BUZZER_TRIGGERS.discard("demo_device")
        BUZZER_TRIGGER_EXPIRY.pop(device_id, None)
        BUZZER_TRIGGER_EXPIRY.pop("demo_device", None)
        return True
    return False


def is_device_buzzer_active(device_id: str) -> bool:
    """Read-only check for whether a buzzer is pending without consuming."""
    now = time.time()
    stale = [k for k, exp in list(BUZZER_TRIGGER_EXPIRY.items()) if now > exp]
    for k in stale:
        BUZZER_TRIGGER_EXPIRY.pop(k, None)
        PENDING_BUZZER_TRIGGERS.discard(k)
    return (device_id in PENDING_BUZZER_TRIGGERS or "demo_device" in PENDING_BUZZER_TRIGGERS)


def get_current_ist_time_str() -> str:
    return datetime.datetime.now(IST).strftime("%d %b, %I:%M %p")


def format_device_status(device: Device, db: Optional[Session] = None, consume_buzzer: bool = False) -> dict:
    lat = device.last_latitude
    lon = device.last_longitude

    # Ignore legacy mock Delhi seed coordinates (28.6139, 77.2090)
    if lat is not None and abs(lat - 28.6139) < 0.01:
        lat = None
        lon = None

    gps_str = f"{lat}, {lon}" if lat and lon else "--"
    
    # Calculate online status dynamically: mark Offline if no telemetry received for > 40 seconds
    status_str = device.status
    if device.updated_at:
        elapsed = (datetime.datetime.utcnow() - device.updated_at).total_seconds()
        if elapsed > 40 and not device.sos:
            status_str = "Offline"
        elif not device.sos and status_str != "Alert":
            status_str = "Online"

    # Always preserve previous live data if current field is empty/zero
    hr = device.heart_rate
    temp = device.temperature
    hum = device.humidity

    if db is not None:
        if hr is None or hr <= 0:
            past_hr = db.query(TelemetryLog.heart_rate).filter(
                TelemetryLog.device_id == device.device_id,
                TelemetryLog.heart_rate.isnot(None),
                TelemetryLog.heart_rate > 0
            ).order_by(TelemetryLog.timestamp.desc()).first()
            if past_hr and past_hr[0]:
                hr = past_hr[0]

        if temp is None or temp <= 0:
            past_temp = db.query(TelemetryLog.temperature).filter(
                TelemetryLog.device_id == device.device_id,
                TelemetryLog.temperature.isnot(None),
                TelemetryLog.temperature > 0
            ).order_by(TelemetryLog.timestamp.desc()).first()
            if past_temp and past_temp[0]:
                temp = past_temp[0]

        if hum is None or hum <= 0:
            past_hum = db.query(TelemetryLog.humidity).filter(
                TelemetryLog.device_id == device.device_id,
                TelemetryLog.humidity.isnot(None),
                TelemetryLog.humidity > 0
            ).order_by(TelemetryLog.timestamp.desc()).first()
            if past_hum and past_hum[0]:
                hum = past_hum[0]

        # Check active buzzer state: consume if requested by ESP32 ingestion
        if consume_buzzer:
            is_buzzer = pop_device_buzzer(device.device_id) or device.sos
        else:
            is_buzzer = is_device_buzzer_active(device.device_id) or device.sos
        is_breached = is_buzzer
    else:
        if consume_buzzer:
            is_buzzer = pop_device_buzzer(device.device_id) or device.sos
        else:
            is_buzzer = is_device_buzzer_active(device.device_id) or device.sos
        is_breached = is_buzzer

    dev_ip = DEVICE_LOCAL_IP_MAP.get(device.device_id) or DEVICE_LOCAL_IP_MAP.get("demo_device")

    return {
        "device_id": device.device_id,
        "name": device.name,
        "status": status_str,
        "battery": device.battery,
        "network": device.network,
        "sos": device.sos,
        "heart_rate": hr,
        "temperature": temp,
        "humidity": hum,
        "latitude": lat,
        "longitude": lon,
        "gps": gps_str,
        "ip": dev_ip,
        "buzzer": is_buzzer,
        "breach": is_breached,
        "updated_at": device.updated_at.isoformat() if device.updated_at else None
    }


@router.post("", response_model=DeviceStatusResponse, status_code=201)
@router.post("/", response_model=DeviceStatusResponse, status_code=201)
async def ingest_telemetry(payload: TelemetryCreate, db: Session = Depends(get_db)):
    """
    Ingest real-time telemetry from ESP32 wearable device.
    - Saves historical log
    - Updates device latest status
    - Evaluates safe zone geofences (triggers alert if breached)
    - Broadcasts live update over WebSocket to Android apps
    """
    # 1. Upsert Device (auto-map "string" placeholder from Swagger to demo_device)
    target_device_id = "demo_device" if (payload.device_id == "string" or not payload.device_id) else payload.device_id
    if hasattr(payload, "ip") and payload.ip:
        DEVICE_LOCAL_IP_MAP[target_device_id] = payload.ip
        DEVICE_LOCAL_IP_MAP["demo_device"] = payload.ip
    device = db.query(Device).filter(Device.device_id == target_device_id).first()
    if not device:
        device = Device(
            device_id=target_device_id,
            name="Guardian Wearable",
            status="Online",
            last_latitude=payload.latitude,
            last_longitude=payload.longitude,
            battery=payload.battery or "100%",
            network=payload.network or "Wi-Fi",
            sos=payload.sos or False,
            heart_rate=payload.heart_rate if payload.heart_rate is not None else 0,
            temperature=payload.temperature if payload.temperature is not None else 36.6,
            humidity=payload.humidity if payload.humidity is not None else 50.0,
            updated_at=datetime.datetime.utcnow()
        )
        db.add(device)
    else:
        if payload.latitude is not None and payload.longitude is not None:
            device.last_latitude = payload.latitude
            device.last_longitude = payload.longitude
        if payload.battery:
            device.battery = payload.battery
        if payload.network:
            device.network = payload.network
        if payload.heart_rate is not None and payload.heart_rate > 0:
            device.heart_rate = payload.heart_rate
        if payload.temperature is not None:
            device.temperature = payload.temperature
        if payload.humidity is not None:
            device.humidity = payload.humidity
        device.sos = payload.sos or False
        device.status = "Alert" if payload.sos else "Online"
        device.updated_at = datetime.datetime.utcnow()

    # 2. Add history log
    current_lat = payload.latitude if payload.latitude is not None else (device.last_latitude or 0.0)
    current_lon = payload.longitude if payload.longitude is not None else (device.last_longitude or 0.0)
    effective_hr = payload.heart_rate if (payload.heart_rate is not None and payload.heart_rate > 0) else device.heart_rate
    log = TelemetryLog(
        device_id=target_device_id,
        latitude=current_lat,
        longitude=current_lon,
        battery=payload.battery or "100%",
        network=payload.network or "Wi-Fi",
        sos=payload.sos or False,
        heart_rate=effective_hr,
        temperature=payload.temperature,
        humidity=payload.humidity,
        timestamp=datetime.datetime.utcnow()
    )
    db.add(log)
    db.commit()
    db.refresh(device)

    # 3. Server-side Geofence Evaluation (evaluated only when valid coordinates exist)
    geofence_results = []
    if current_lat != 0.0 and current_lon != 0.0:
        geofence_results = evaluate_geofences(db, target_device_id, current_lat, current_lon)
    if geofence_results and not any(r.is_inside for r in geofence_results):
        # Monitored device is outside ALL defined safe zones -> trigger breach alert for closest zone
        trigger_device_buzzer(target_device_id)
        closest_breached = min(geofence_results, key=lambda r: r.distance_meters)
        recent_alert = db.query(Alert).filter(
            Alert.device_id == target_device_id,
            Alert.title.like(f"%{closest_breached.zone_name}%"),
            Alert.created_at >= datetime.datetime.utcnow() - datetime.timedelta(seconds=30)
        ).first()

        if not recent_alert:
            breach_alert = Alert(
                user_id="default_user",
                device_id=target_device_id,
                title=f"Safe Zone Breach: Outside '{closest_breached.zone_name}' ({int(closest_breached.distance_meters)}m away)",
                time=get_current_ist_time_str(),
                type="warning",
                latitude=current_lat,
                longitude=current_lon
            )
            db.add(breach_alert)
            db.commit()
            await dispatch_emergency_notification(
                user_id="default_user",
                device_id=target_device_id,
                title=breach_alert.title,
                latitude=current_lat,
                longitude=current_lon
            )

    # 4. Handle Hardware SOS button trigger
    if payload.sos:
        sos_alert = Alert(
            user_id="default_user",
            device_id=target_device_id,
            title="Physical SOS Button Pressed on Wearable",
            time=get_current_ist_time_str(),
            type="danger",
            latitude=payload.latitude,
            longitude=payload.longitude
        )
        db.add(sos_alert)
        db.commit()
        await dispatch_emergency_notification(
            user_id="default_user",
            device_id=target_device_id,
            title=sos_alert.title,
            latitude=payload.latitude,
            longitude=payload.longitude
        )

    # 4b. Health vitals alert (Heart rate or Temperature anomaly)
    if payload.heart_rate and (payload.heart_rate > 135 or payload.heart_rate < 45):
        health_alert = Alert(
            user_id="default_user",
            device_id=target_device_id,
            title=f"Abnormal Heart Rate Detected: {payload.heart_rate} BPM",
            time=get_current_ist_time_str(),
            type="warning",
            latitude=payload.latitude,
            longitude=payload.longitude
        )
        db.add(health_alert)
        db.commit()
    elif payload.temperature and payload.temperature >= 38.8:
        temp_alert = Alert(
            user_id="default_user",
            device_id=target_device_id,
            title=f"High Body Temperature Detected: {payload.temperature:.1f}°C",
            time=get_current_ist_time_str(),
            type="warning",
            latitude=payload.latitude,
            longitude=payload.longitude
        )
        db.add(temp_alert)
        db.commit()

    # 5. Broadcast to connected WebSocket clients (Android app)
    status_dict = format_device_status(device, db, consume_buzzer=True)
    await ws_manager.broadcast_to_device(target_device_id, status_dict)
    if payload.device_id != target_device_id:
        await ws_manager.broadcast_to_device(payload.device_id, status_dict)

    return DeviceStatusResponse(**status_dict)


@router.post("/{device_id}/location", response_model=DeviceStatusResponse)
async def update_device_location(device_id: str, payload: LocationUpdate, db: Session = Depends(get_db)):
    """
    Update live GPS coordinates for the wearable device from a companion phone.
    - Sets device.last_latitude and device.last_longitude
    - Evaluates safe zone geofences (creates breach alerts if outside)
    - Broadcasts live position to all monitoring phones
    """
    target_device_id = "demo_device" if (device_id == "string" or not device_id) else device_id
    device = db.query(Device).filter(Device.device_id == target_device_id).first()
    if not device:
        device = Device(
            device_id=target_device_id,
            name="Guardian Wearable",
            status="Online",
            battery="95%",
            network="Phone GPS",
            sos=False,
            last_latitude=payload.latitude,
            last_longitude=payload.longitude,
            updated_at=datetime.datetime.utcnow()
        )
        db.add(device)
    else:
        device.last_latitude = payload.latitude
        device.last_longitude = payload.longitude
        device.status = "Alert" if device.sos else "Online"
        device.updated_at = datetime.datetime.utcnow()

    # Log to historical trail
    log = TelemetryLog(
        device_id=target_device_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        battery=device.battery or "95%",
        network="Phone GPS",
        sos=device.sos,
        heart_rate=device.heart_rate,
        temperature=device.temperature,
        humidity=device.humidity,
        timestamp=datetime.datetime.utcnow()
    )
    db.add(log)
    db.commit()
    db.refresh(device)

    # Evaluate Geofences immediately with new phone GPS coordinates (suppress if in active SOS)
    if not device.sos:
        geofence_results = evaluate_geofences(db, target_device_id, payload.latitude, payload.longitude)
        if geofence_results and not any(r.is_inside for r in geofence_results):
            # Monitored device is outside ALL defined safe zones -> trigger breach alert for closest zone
            trigger_device_buzzer(target_device_id)
            closest_breached = min(geofence_results, key=lambda r: r.distance_meters)
            recent_alert = db.query(Alert).filter(
                Alert.device_id == target_device_id,
                Alert.title.like(f"%{closest_breached.zone_name}%"),
                Alert.created_at >= datetime.datetime.utcnow() - datetime.timedelta(seconds=30)
            ).first()

            if not recent_alert:
                breach_alert = Alert(
                    user_id="default_user",
                    device_id=target_device_id,
                    title=f"Safe Zone Breach: Outside '{closest_breached.zone_name}' ({int(closest_breached.distance_meters)}m away)",
                    time=get_current_ist_time_str(),
                    type="warning",
                    latitude=payload.latitude,
                    longitude=payload.longitude
                )
                db.add(breach_alert)
                db.commit()
                await dispatch_emergency_notification(
                    user_id="default_user",
                    device_id=target_device_id,
                    title=breach_alert.title,
                    latitude=payload.latitude,
                    longitude=payload.longitude
                )

    status_dict = format_device_status(device, db, consume_buzzer=True)
    await ws_manager.broadcast_to_device(target_device_id, status_dict)
    return DeviceStatusResponse(**status_dict)


@router.get("/{device_id}/latest", response_model=DeviceStatusResponse)
def get_latest_device_status(device_id: str, db: Session = Depends(get_db)):
    """Fetch current status of a device for the Android dashboard."""
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if not device:
        # Return sensible defaults if device has not checked in yet
        return DeviceStatusResponse(
            device_id=device_id,
            name="Guardian Wearable",
            status="Offline",
            battery="--",
            network="--",
            sos=False,
            latitude=None,
            longitude=None,
            gps="--",
            updated_at=None
        )
    return DeviceStatusResponse(**format_device_status(device, db))


@router.post("/{device_id}/buzzer", response_model=DeviceStatusResponse)
async def trigger_buzzer_endpoint(device_id: str, db: Session = Depends(get_db)):
    """Trigger the 5-second buzzer alarm on the ESP32 board and log a safe zone breach alert."""
    target_device_id = "demo_device" if (device_id == "string" or not device_id) else device_id
    trigger_device_buzzer(target_device_id)

    device = db.query(Device).filter(Device.device_id == target_device_id).first()
    if not device:
        device = Device(
            device_id=target_device_id,
            name="Guardian Wearable",
            status="Alert",
            battery="95%",
            network="Wi-Fi",
            sos=False,
            updated_at=datetime.datetime.utcnow()
        )
        db.add(device)
        db.commit()
        db.refresh(device)

    # 1. Log incident breach alert
    alert = Alert(
        user_id="default_user",
        device_id=target_device_id,
        title="Out of safe Zone",
        time=get_current_ist_time_str(),
        type="warning",
        latitude=device.last_latitude,
        longitude=device.last_longitude
    )
    db.add(alert)
    db.commit()

    # 2. Dispatch emergency WhatsApp / notification
    await dispatch_emergency_notification(
        user_id="default_user",
        device_id=target_device_id,
        title=alert.title,
        latitude=device.last_latitude,
        longitude=device.last_longitude
    )

    # 3. Broadcast status to ESP32 and phone app with buzzer=True and breach=True
    status_dict = format_device_status(device, db, consume_buzzer=False)
    status_dict["buzzer"] = True
    status_dict["breach"] = True
    await ws_manager.broadcast_to_device(target_device_id, status_dict)
    return DeviceStatusResponse(**status_dict)


@router.get("/{device_id}/buzzer-check")
def check_device_buzzer_status(device_id: str):
    """Ultra-fast endpoint for ESP32 to poll buzzer/breach alarm state in milliseconds without DB overhead."""
    target = "demo_device" if (device_id == "string" or not device_id) else device_id
    active = pop_device_buzzer(target)
    return {"buzzer": active, "breach": active}


@router.get("/{device_id}/history", response_model=List[TelemetryResponse])
def get_telemetry_history(
    device_id: str,
    limit: int = Query(50, le=500),
    db: Session = Depends(get_db)
):
    """Fetch recent location history for location trail playback on map."""
    logs = db.query(TelemetryLog).filter(
        TelemetryLog.device_id == device_id
    ).order_by(TelemetryLog.timestamp.desc()).limit(limit).all()
    return logs
