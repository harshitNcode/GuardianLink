from typing import Optional, List
from datetime import datetime
from pydantic import BaseModel, Field


class ContactCreate(BaseModel):
    name: str = Field(..., min_length=1)
    phone: str = Field(..., min_length=3)


class ContactResponse(BaseModel):
    id: int
    user_id: str
    name: str
    phone: str
    created_at: datetime

    class Config:
        from_attributes = True


class UserCreate(BaseModel):
    uid: str
    name: Optional[str] = ""
    phone_number: Optional[str] = ""
    device_id: Optional[str] = ""
    emergency_contact: Optional[str] = ""
    photo_local_path: Optional[str] = ""


class UserUpdate(BaseModel):
    name: Optional[str] = None
    phone_number: Optional[str] = None
    device_id: Optional[str] = None
    emergency_contact: Optional[str] = None
    photo_local_path: Optional[str] = None


class UserResponse(BaseModel):
    uid: str
    name: str
    phone_number: str
    device_id: str
    emergency_contact: str
    photo_local_path: str
    contacts: List[ContactResponse] = []

    class Config:
        from_attributes = True
