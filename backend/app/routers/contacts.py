from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.user import Contact, User
from app.schemas.user import ContactCreate, ContactResponse

router = APIRouter(prefix="/api/v1/contacts", tags=["Emergency Contacts"])


@router.get("/{user_id}", response_model=List[ContactResponse])
def get_contacts(user_id: str, db: Session = Depends(get_db)):
    """Fetch all emergency contacts for a user."""
    contacts = db.query(Contact).filter(Contact.user_id == user_id).all()
    return contacts


@router.post("/{user_id}", response_model=ContactResponse, status_code=status.HTTP_201_CREATED)
def add_contact(user_id: str, contact_in: ContactCreate, db: Session = Depends(get_db)):
    """Add a new emergency contact."""
    # Ensure user exists (or auto-create placeholder if needed)
    user = db.query(User).filter(User.uid == user_id).first()
    if not user:
        user = User(uid=user_id, name="User", phone_number="")
        db.add(user)
        db.commit()

    contact = Contact(
        user_id=user_id,
        name=contact_in.name,
        phone=contact_in.phone
    )
    db.add(contact)
    db.commit()
    db.refresh(contact)
    return contact


@router.delete("/{contact_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_contact(contact_id: int, db: Session = Depends(get_db)):
    """Delete an emergency contact by ID."""
    contact = db.query(Contact).filter(Contact.id == contact_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    db.delete(contact)
    db.commit()
    return None


@router.delete("/{user_id}/by-phone/{phone}", status_code=status.HTTP_204_NO_CONTENT)
def delete_contact_by_phone(user_id: str, phone: str, db: Session = Depends(get_db)):
    """Delete an emergency contact by phone number."""
    contact = db.query(Contact).filter(Contact.user_id == user_id, Contact.phone == phone).first()
    if contact:
        db.delete(contact)
        db.commit()
    return None
