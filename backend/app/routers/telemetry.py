import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.device import Device, TelemetryLog
from app.models.alert import Alert
from app.schemas.telemetry import TelemetryCreate, TelemetryResponse, DeviceStatusResponse
from app.services.websocket_manager import manager as ws_manager
from app.services.geofence_service import evaluate_geofences
from app.services.notification_service import dispatch_emergency_notification

router = APIRouter(prefix="/api/v1/telemetry", tags=["Telemetry & Devices"])


def format_device_status(device: Device) -> dict:
    gps_str = f"{device.last_latitude}, {device.last_longitude}" if device.last_latitude and device.last_longitude else "--"
    return {
        "device_id": device.device_id,
        "name": device.name,
        "status": device.status,
        "battery": device.battery,
        "network": device.network,
        "sos": device.sos,
        "heart_rate": device.heart_rate if device.heart_rate is not None else 72,
        "temperature": device.temperature if device.temperature is not None else 36.6,
        "latitude": device.last_latitude,
        "longitude": device.last_longitude,
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
        timestamp=datetime.datetime.utcnow()
    )
    db.add(log)
    db.commit()
    db.refresh(device)

    # 3. Server-side Geofence Evaluation (evaluated only when valid coordinates exist)
    if current_lat != 0.0 and current_lon != 0.0:
        geofence_results = evaluate_geofences(db, target_device_id, current_lat, current_lon)
    for result in geofence_results:
        if not result.is_inside:
            # Geofence breach detected!
            breach_alert = Alert(
                user_id="default_user",
                device_id=target_device_id,
                title=f"Safe Zone Breach: Outside '{result.zone_name}' ({result.distance_meters}m away)",
                time=datetime.datetime.now().strftime("%I:%M %p"),
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

    # 4. Handle Hardware SOS button trigger
    if payload.sos:
        sos_alert = Alert(
            user_id="default_user",
            device_id=target_device_id,
            title="Physical SOS Button Pressed on Wearable",
            time=datetime.datetime.now().strftime("%I:%M %p"),
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
            time=datetime.datetime.now().strftime("%I:%M %p"),
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
            time=datetime.datetime.now().strftime("%I:%M %p"),
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
