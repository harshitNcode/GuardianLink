from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.user import User
from app.schemas.user import UserCreate, UserUpdate, UserResponse

router = APIRouter(prefix="/api/v1/users", tags=["Users & Authentication"])


@router.get("/{uid}", response_model=UserResponse)
def get_user_profile(uid: str, db: Session = Depends(get_db)):
    """Fetch user profile by UID (maps to Android UserRepository.fetchProfile)."""
    user = db.query(User).filter(User.uid == uid).first()
    if not user:
        # Create default profile if user does not exist yet
        user = User(
            uid=uid,
            name="Harshit",
            phone_number="",
            device_id="demo_device",
            emergency_contact="",
            photo_local_path=""
        )
        db.add(user)
        db.commit()
        db.refresh(user)
    return user


@router.put("/{uid}", response_model=UserResponse)
def update_user_profile(uid: str, update_in: UserUpdate, db: Session = Depends(get_db)):
    """Update profile fields (name, phone, emergencyContact, deviceId, photoLocalPath)."""
    user = db.query(User).filter(User.uid == uid).first()
    if not user:
        user = User(uid=uid)
        db.add(user)

    if update_in.name is not None:
        user.name = update_in.name
    if update_in.phone_number is not None:
        user.phone_number = update_in.phone_number
    if update_in.device_id is not None:
        user.device_id = update_in.device_id
    if update_in.emergency_contact is not None:
        user.emergency_contact = update_in.emergency_contact
    if update_in.photo_local_path is not None:
        user.photo_local_path = update_in.photo_local_path

    db.commit()
    db.refresh(user)
    return user


@router.post("/{uid}/pair-device", response_model=UserResponse)
def pair_device(uid: str, device_id: str, db: Session = Depends(get_db)):
    """Pair a hardware wearable device_id to a user."""
    user = db.query(User).filter(User.uid == uid).first()
    if not user:
        user = User(uid=uid)
        db.add(user)

    user.device_id = device_id.strip()
    db.commit()
    db.refresh(user)
    return user
