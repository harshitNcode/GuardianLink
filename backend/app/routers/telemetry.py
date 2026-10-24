import datetime
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


def get_current_ist_time_str() -> str:
    return datetime.datetime.now(IST).strftime("%d %b, %I:%M %p")


def format_device_status(device: Device) -> dict:
    lat = device.last_latitude
    lon = device.last_longitude

    # Discard any dummy Delhi coordinates (~28.6° N, 77.2° E)
    if lat is not None and lon is not None:
        if abs(lat - 28.6) < 0.4 and abs(lon - 77.2) < 0.4:
            lat = None
            lon = None

    gps_str = f"{lat}, {lon}" if lat and lon else "--"
    
    # Calculate online status dynamically: mark Offline if no telemetry received for > 40 seconds
    status_str = device.status
    if device.updated_at:
        elapsed = (datetime.datetime.utcnow() - device.updated_at).total_seconds()
        if elapsed > 40 and not device.sos:
            status_str = "Offline"

    return {
        "device_id": device.device_id,
        "name": device.name,
        "status": status_str,
        "battery": device.battery,
        "network": device.network,
        "sos": device.sos,
        "heart_rate": device.heart_rate,
        "temperature": device.temperature,
        "humidity": device.humidity,
        "latitude": lat,
        "longitude": lon,
        "gps": gps_str,
        "updated_at": device.updated_at.isoformat() if device.updated_at else None
    }


@router.post("", response_model=DeviceStatusResponse, status_code=201)
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
            heart_rate=payload.heart_rate if payload.heart_rate is not None else 72,
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
        if payload.heart_rate is not None:
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
    log = TelemetryLog(
        device_id=target_device_id,
        latitude=current_lat,
        longitude=current_lon,
        battery=payload.battery or "100%",
        network=payload.network or "Wi-Fi",
        sos=payload.sos or False,
        heart_rate=payload.heart_rate,
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
    for result in geofence_results:
        if not result.is_inside:
            # 45-second cooldown per safe zone to allow responsive map alerts without flooding DB
            recent_alert = db.query(Alert).filter(
                Alert.device_id == target_device_id,
                Alert.title.like(f"%{result.zone_name}%"),
                Alert.created_at >= datetime.datetime.utcnow() - datetime.timedelta(seconds=45)
            ).first()

            if not recent_alert:
                breach_alert = Alert(
                    user_id="default_user",
                    device_id=target_device_id,
                    title=f"Safe Zone Breach: Outside '{result.zone_name}' ({result.distance_meters}m away)",
                    time=datetime.datetime.now().strftime("%d %b, %I:%M %p"),
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
    status_dict = format_device_status(device)
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
        for result in geofence_results:
            if not result.is_inside:
                recent_alert = db.query(Alert).filter(
                    Alert.device_id == target_device_id,
                    Alert.title.like(f"%{result.zone_name}%"),
                    Alert.created_at >= datetime.datetime.utcnow() - datetime.timedelta(seconds=45)
                ).first()

                if not recent_alert:
                    breach_alert = Alert(
                        user_id="default_user",
                        device_id=target_device_id,
                        title=f"Safe Zone Breach: Outside '{result.zone_name}' ({result.distance_meters}m away)",
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

    status_dict = format_device_status(device)
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
    return DeviceStatusResponse(**format_device_status(device))


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
