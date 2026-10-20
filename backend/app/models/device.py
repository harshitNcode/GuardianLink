import datetime
from sqlalchemy import Column, String, Float, Integer, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base


class Device(Base):
    __tablename__ = "devices"

    device_id = Column(String(64), primary_key=True, index=True)
    name = Column(String(128), default="Guardian Wearable")
    status = Column(String(32), default="Online")
    last_latitude = Column(Float, nullable=True)
    last_longitude = Column(Float, nullable=True)
    battery = Column(String(16), default="100%")
    network = Column(String(32), default="Wi-Fi")
    sos = Column(Boolean, default=False)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    telemetry_logs = relationship("TelemetryLog", back_populates="device", cascade="all, delete-orphan")


class TelemetryLog(Base):
    __tablename__ = "telemetry_logs"

    id = Column(Integer, primary_key=True, autoincrement=True, index=True)
    device_id = Column(String(64), ForeignKey("devices.device_id"), index=True, nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    battery = Column(String(16), default="100%")
    network = Column(String(32), default="Wi-Fi")
    sos = Column(Boolean, default=False)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow, index=True)

    device = relationship("Device", back_populates="telemetry_logs")
