import datetime
from sqlalchemy import Column, String, Integer, Float, Boolean, DateTime, Text
from app.database import Base


class GeofenceZone(Base):
    __tablename__ = "geofence_zones"

    id = Column(Integer, primary_key=True, autoincrement=True, index=True)
    device_id = Column(String(64), index=True, nullable=False)
    user_id = Column(String(64), index=True, nullable=False)
    name = Column(String(128), nullable=False)  # e.g. "Home Safe Zone", "School"
    center_latitude = Column(Float, nullable=False)
    center_longitude = Column(Float, nullable=False)
    radius_meters = Column(Float, default=500.0)
    polygon_wkt = Column(Text, nullable=True)  # WKT representation for polygon zones: POLYGON((...))
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
