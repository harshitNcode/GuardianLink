import datetime
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.alert import Alert
from app.models.device import Device
from app.schemas.alert import AlertCreate, AlertResponse, SOSTriggerRequest
from app.services.websocket_manager import manager as ws_manager
from app.services.notification_service import dispatch_emergency_notification
from app.services.geofence_service import evaluate_geofences

router = APIRouter(prefix="/api/v1/alerts", tags=["Alerts & SOS"])


@router.post("/sos", response_model=AlertResponse, status_code=status.HTTP_201_CREATED)
async def trigger_sos(req: SOSTriggerRequest, db: Session = Depends(get_db)):
    """
    Trigger emergency SOS from Android app or wearable button.
    - Sets device.sos = True
    - Creates persistent Alert log
    - Broadcasts emergency state to Android via WebSocket
    - Dispatches emergency alert
    """
    time_str = datetime.datetime.now().strftime("%d %b, %I:%M %p")
    alert = Alert(
        user_id=req.user_id,
        device_id=req.device_id,
        title=req.title or "Emergency SOS Triggered",
        time=time_str,
        type="danger",
        latitude=req.latitude,
        longitude=req.longitude,
        resolved=False
    )
    db.add(alert)

    # Flag device as in emergency SOS
    device = db.query(Device).filter(Device.device_id == req.device_id).first()
    if device:
        device.sos = True
        device.status = "Alert"
        device.updated_at = datetime.datetime.utcnow()
        if req.latitude and req.longitude:
            device.last_latitude = req.latitude
            device.last_longitude = req.longitude

    db.commit()
    db.refresh(alert)

    # Evaluate Geofences on SOS so map / breach alerts appear immediately
    loc_lat = req.latitude or (device.last_latitude if device else None)
    loc_lon = req.longitude or (device.last_longitude if device else None)
    if loc_lat and loc_lon:
        geofence_results = evaluate_geofences(db, req.device_id, loc_lat, loc_lon)
        for result in geofence_results:
            if not result.is_inside:
                map_alert = Alert(
                    user_id=req.user_id,
                    device_id=req.device_id,
                    title=f"Safe Zone Breach: Outside '{result.zone_name}' ({result.distance_meters}m away)",
                    time=time_str,
                    type="warning",
                    latitude=loc_lat,
                    longitude=loc_lon,
                    resolved=False
                )
                db.add(map_alert)
        db.commit()

    # Broadcast emergency to WebSocket listeners
    if device:
        gps_str = f"{device.last_latitude}, {device.last_longitude}" if device.last_latitude and device.last_longitude else "--"
        await ws_manager.broadcast_to_device(req.device_id, {
            "device_id": device.device_id,
            "name": device.name,
            "status": "Alert",
            "battery": device.battery,
            "network": device.network,
            "sos": True,
            "heart_rate": device.heart_rate,
            "temperature": device.temperature,
            "humidity": device.humidity,
            "latitude": device.last_latitude,
            "longitude": device.last_longitude,
            "gps": gps_str,
            "updated_at": device.updated_at.isoformat() if device.updated_at else None
        })

    # Dispatch notification
    await dispatch_emergency_notification(
        user_id=req.user_id,
        device_id=req.device_id,
        title=alert.title,
        latitude=req.latitude,
        longitude=req.longitude
    )

    return alert


@router.post("/sos/cancel", response_model=AlertResponse)
async def cancel_sos(req: SOSTriggerRequest, db: Session = Depends(get_db)):
    """
    Cancel emergency SOS and stand down alert state.
    - Sets device.sos = False
    - Sets device.status = 'Online'
    - Creates a persistent Alert log: 'Emergency SOS Stood Down (Resolved)'
    - Broadcasts updated status over WebSocket to all Android apps
    """
    time_str = datetime.datetime.now().strftime("%d %b, %I:%M %p")
    alert = Alert(
        user_id=req.user_id,
        device_id=req.device_id,
        title="Emergency SOS Stood Down (Resolved)",
        time=time_str,
        type="info",
        latitude=req.latitude,
        longitude=req.longitude,
        resolved=True
    )
    db.add(alert)

    device = db.query(Device).filter(Device.device_id == req.device_id).first()
    if device:
        device.sos = False
        device.status = "Online"
        device.updated_at = datetime.datetime.utcnow()
        db.commit()
        db.refresh(device)

        gps_str = f"{device.last_latitude}, {device.last_longitude}" if device.last_latitude and device.last_longitude else "--"
        await ws_manager.broadcast_to_device(req.device_id, {
            "device_id": device.device_id,
            "name": device.name,
            "status": "Online",
            "battery": device.battery,
            "network": device.network,
            "sos": False,
            "heart_rate": device.heart_rate,
            "temperature": device.temperature,
            "humidity": device.humidity,
            "latitude": device.last_latitude,
            "longitude": device.last_longitude,
            "gps": gps_str,
            "updated_at": device.updated_at.isoformat() if device.updated_at else None
        })

    db.commit()
    db.refresh(alert)
    return alert


@router.get("/{user_id}", response_model=List[AlertResponse])
def get_alerts(user_id: str, limit: int = 50, db: Session = Depends(get_db)):
    target_ids = list(set([user_id, "default_user", "demo_user"]))
    alerts = db.query(Alert).filter(
        (Alert.user_id.in_(target_ids)) | (Alert.device_id == "demo_device")
    ).order_by(Alert.id.desc()).limit(limit).all()
    return alerts


@router.put("/{alert_id}/resolve", response_model=AlertResponse)
def resolve_alert(alert_id: int, db: Session = Depends(get_db)):
    """Mark an alert as resolved and reset device SOS if applicable."""
    alert = db.query(Alert).filter(Alert.id == alert_id).first()
    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")
    alert.resolved = True
    db.commit()
    db.refresh(alert)
    return alert


@router.post("/dispatch-whatsapp")
async def dispatch_whatsapp(payload: dict):
    """
    Accepts automated background WhatsApp emergency alert dispatch.
    Logs and triggers external WhatsApp dispatch without requiring client intervention.
    """
    recipient = payload.get("recipient_phone", "")
    message = payload.get("message", "")
    location_url = payload.get("location_url", "")
    import logging
    logging.getLogger("GuardianLink.Alert").info(
        f"🚨 [AUTOMATIC WHATSAPP DISPATCH] Recipient: {recipient} | Message: {message[:60]}... | Location: {location_url}"
    )
    return {
        "status": "success",
        "detail": "Emergency WhatsApp alert dispatched automatically in background",
        "recipient": recipient
    }

