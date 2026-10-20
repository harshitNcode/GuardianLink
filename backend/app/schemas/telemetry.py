from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field


class TelemetryCreate(BaseModel):
    device_id: str = Field("demo_device", example="demo_device", description="Unique hardware identifier for the ESP32 wearable")
    latitude: float = Field(12.9716, example=12.9716, ge=-90.0, le=90.0)
    longitude: float = Field(77.5946, example=77.5946, ge=-180.0, le=180.0)
    battery: Optional[str] = "95%"
    network: Optional[str] = "Wi-Fi"
    sos: Optional[bool] = False


class TelemetryResponse(BaseModel):
    id: int
    device_id: str
    latitude: float
    longitude: float
    battery: str
    network: str
    sos: bool
    timestamp: datetime

    class Config:
        from_attributes = True


class DeviceStatusResponse(BaseModel):
    device_id: str
    name: str
    status: str
    battery: str
    network: str
    sos: bool
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    gps: str  # Format: "12.9716, 77.5946" compatible with Android DeviceStatus
    updated_at: Optional[datetime] = None

    class Config:
        from_attributes = True
