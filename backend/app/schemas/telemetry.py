from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field


class TelemetryCreate(BaseModel):
    device_id: str = Field("demo_device", example="demo_device", description="Unique hardware identifier for the ESP32 wearable")
    latitude: Optional[float] = Field(None, example=12.9716, ge=-90.0, le=90.0)
    longitude: Optional[float] = Field(None, example=77.5946, ge=-180.0, le=180.0)
    battery: Optional[str] = "95%"
    network: Optional[str] = "Wi-Fi"
    sos: Optional[bool] = False
    heart_rate: Optional[int] = Field(None, example=72, description="Heart rate in BPM")
    temperature: Optional[float] = Field(None, example=36.6, description="Body temperature in Celsius")
    humidity: Optional[float] = Field(None, example=50.0, description="Relative humidity percentage")


class TelemetryResponse(BaseModel):
    id: int
    device_id: str
    latitude: float
    longitude: float
    battery: str
    network: str
    sos: bool
    heart_rate: Optional[int] = None
    temperature: Optional[float] = None
    humidity: Optional[float] = None
    timestamp: datetime

    class Config:
        from_attributes = True


class LocationUpdate(BaseModel):
    latitude: float = Field(..., ge=-90.0, le=90.0, example=28.6139)
    longitude: float = Field(..., ge=-180.0, le=180.0, example=77.2090)


class DeviceStatusResponse(BaseModel):
    device_id: str
    name: str
    status: str
    battery: str
    network: str
    sos: bool
    heart_rate: Optional[int] = None
    temperature: Optional[float] = None
    humidity: Optional[float] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    gps: str  # Format: "12.9716, 77.5946" compatible with Android DeviceStatus
    buzzer: bool = False  # Activates 5-second beep on ESP32 board
    breach: bool = False  # True when device is currently outside safe zone
    updated_at: Optional[datetime] = None

    class Config:
        from_attributes = True
