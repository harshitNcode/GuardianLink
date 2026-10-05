from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field


class SOSTriggerRequest(BaseModel):
    user_id: str
    device_id: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    title: Optional[str] = "Emergency SOS Triggered"


class AlertCreate(BaseModel):
    user_id: str
    device_id: Optional[str] = ""
    title: str
    time: Optional[str] = ""
    type: Optional[str] = "danger"  # "danger", "warning", "success"
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class AlertResponse(BaseModel):
    id: int
    user_id: str
    device_id: str
    title: str
    time: str
    type: str  # maps to Android Alert.kt: "danger", "warning", "success"
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    resolved: bool
    created_at: datetime

    class Config:
        from_attributes = True
