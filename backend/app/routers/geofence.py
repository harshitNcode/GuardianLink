from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.geofence import GeofenceZone
from app.schemas.geofence import GeofenceCreate, GeofenceResponse, GeofenceCheckResult
from app.services.geofence_service import evaluate_geofences

router = APIRouter(prefix="/api/v1/geofence", tags=["Geofencing & Safe Zones"])


@router.post("", response_model=GeofenceResponse, status_code=status.HTTP_201_CREATED)
@router.post("/", response_model=GeofenceResponse, status_code=status.HTTP_201_CREATED)
def create_geofence_zone(zone_in: GeofenceCreate, db: Session = Depends(get_db)):
    """Create a new safe zone (circular radius or custom polygon)."""
    zone = GeofenceZone(
        device_id=zone_in.device_id,
        user_id=zone_in.user_id,
        name=zone_in.name,
        center_latitude=zone_in.center_latitude,
        center_longitude=zone_in.center_longitude,
        radius_meters=zone_in.radius_meters,
        polygon_wkt=zone_in.polygon_wkt,
        is_active=True
    )
    db.add(zone)
    db.commit()
    db.refresh(zone)
    return zone


@router.get("/{device_id}", response_model=List[GeofenceResponse])
def list_geofences_for_device(device_id: str, db: Session = Depends(get_db)):
    """List all active geofences configured for a device."""
    zones = db.query(GeofenceZone).filter(
        GeofenceZone.device_id == device_id,
        GeofenceZone.is_active == True
    ).all()
    return zones


@router.put("/{zone_id}", response_model=GeofenceResponse)
def update_geofence(zone_id: int, zone_in: GeofenceCreate, db: Session = Depends(get_db)):
    """Update an existing safe zone."""
    zone = db.query(GeofenceZone).filter(GeofenceZone.id == zone_id).first()
    if not zone:
        raise HTTPException(status_code=404, detail="Geofence zone not found")
    zone.name = zone_in.name
    zone.center_latitude = zone_in.center_latitude
    zone.center_longitude = zone_in.center_longitude
    zone.radius_meters = zone_in.radius_meters
    zone.polygon_wkt = zone_in.polygon_wkt
    db.commit()
    db.refresh(zone)
    return zone


@router.delete("/{zone_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_geofence(zone_id: int, db: Session = Depends(get_db)):
    """Delete a geofence zone."""
    zone = db.query(GeofenceZone).filter(GeofenceZone.id == zone_id).first()
    if not zone:
        raise HTTPException(status_code=404, detail="Geofence zone not found")
    db.delete(zone)
    db.commit()
    return None


@router.get("/{device_id}/check", response_model=List[GeofenceCheckResult])
def check_device_location_geofences(
    device_id: str,
    latitude: float,
    longitude: float,
    db: Session = Depends(get_db)
):
    """Test a coordinate against all active safe zones for a device."""
    return evaluate_geofences(db, device_id, latitude, longitude)
