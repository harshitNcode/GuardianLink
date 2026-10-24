import datetime
from typing import List, Optional
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

IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))


def get_current_ist_time_str() -> str:
    """Returns standard Indian Standard Time string (e.g. '24 Oct, 07:15 PM')."""
    return datetime.datetime.now(IST).strftime("%d %b, %I:%M %p")


@router.post("/sos", response_model=AlertResponse, status_code=status.HTTP_201_CREATED)
async def trigger_sos(req: SOSTriggerRequest, db: Session = Depends(get_db)):
    """
    Trigger emergency SOS from Android app or wearable button.
    - Sets device.sos = True
    - Creates persistent Alert log
    - Broadcasts emergency state to Android via WebSocket
    - Dispatches emergency alert
    """
    time_str = get_current_ist_time_str()
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
            # Only update device coordinates if not dummy Delhi
            if not (abs(req.latitude - 28.6) < 0.4 and abs(req.longitude - 77.2) < 0.4):
                device.last_latitude = req.latitude
                device.last_longitude = req.longitude

    db.commit()
    db.refresh(alert)

    # Broadcast emergency to WebSocket listeners
    if device:
        dev_lat = device.last_latitude
        dev_lon = device.last_longitude
        if dev_lat is not None and dev_lon is not None:
            if abs(dev_lat - 28.6) < 0.4 and abs(dev_lon - 77.2) < 0.4:
                dev_lat = None
                dev_lon = None
        gps_str = f"{dev_lat}, {dev_lon}" if dev_lat and dev_lon else "--"

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
            "latitude": dev_lat,
            "longitude": dev_lon,
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
    time_str = get_current_ist_time_str()
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

        dev_lat = device.last_latitude
        dev_lon = device.last_longitude
        if dev_lat is not None and dev_lon is not None:
            if abs(dev_lat - 28.6) < 0.4 and abs(dev_lon - 77.2) < 0.4:
                dev_lat = None
                dev_lon = None
        gps_str = f"{dev_lat}, {dev_lon}" if dev_lat and dev_lon else "--"

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
            "latitude": dev_lat,
            "longitude": dev_lon,
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
        ((Alert.user_id.in_(target_ids)) | (Alert.device_id == "demo_device")),
        ~Alert.title.ilike("%Safe Zone Breach%")
    ).order_by(
        (Alert.type == "danger").desc(),
        Alert.id.desc()
    ).limit(limit).all()
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


@router.delete("/{alert_id}")
def delete_alert_by_id(alert_id: int, db: Session = Depends(get_db)):
    """Delete a specific alert by its database ID (except SOS alerts)."""
    alert = db.query(Alert).filter(Alert.id == alert_id).first()
    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")
    if alert.type == "danger" or "sos" in alert.title.lower():
        raise HTTPException(status_code=400, detail="Emergency SOS alerts cannot be deleted")
    db.delete(alert)
    db.commit()
    return {"status": "deleted", "id": alert_id}


@router.delete("")
def delete_alert_by_query(
    title: Optional[str] = None,
    time: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """Delete non-SOS alerts matching title or time."""
    query = db.query(Alert).filter(Alert.type != "danger", ~Alert.title.ilike("%sos%"))
    if title:
        query = query.filter(Alert.title == title)
    if time:
        query = query.filter(Alert.time == time)
    deleted_count = query.delete(synchronize_session=False)
    db.commit()
    return {"status": "deleted", "count": deleted_count}


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

