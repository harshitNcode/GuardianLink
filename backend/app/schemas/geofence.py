from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field


class GeofenceCreate(BaseModel):
    device_id: str
    user_id: str
    name: str = Field(..., example="Home Safe Zone")
    center_latitude: float = Field(..., ge=-90.0, le=90.0)
    center_longitude: float = Field(..., ge=-180.0, le=180.0)
    radius_meters: float = Field(500.0, ge=0.0, description="Radius in meters for circle geofence")
    polygon_wkt: Optional[str] = Field(None, description="Optional WKT polygon string: POLYGON((lng lat, ...))")


class GeofenceResponse(BaseModel):
    id: int
    device_id: str
    user_id: str
    name: str
    center_latitude: float
    center_longitude: float
    radius_meters: float
    polygon_wkt: Optional[str] = None
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True


class GeofenceCheckResult(BaseModel):
    zone_id: int
    zone_name: str
    is_inside: bool
    distance_meters: float
    status: str  # "SAFE" or "BREACH"
