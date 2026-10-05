import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base


class User(Base):
    __tablename__ = "users"

    uid = Column(String(64), primary_key=True, index=True)
    name = Column(String(128), default="")
    phone_number = Column(String(32), default="", index=True)
    device_id = Column(String(64), default="")
    emergency_contact = Column(String(32), default="")
    photo_local_path = Column(String(256), default="")
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    contacts = relationship("Contact", back_populates="user", cascade="all, delete-orphan")


class Contact(Base):
    __tablename__ = "contacts"

    id = Column(Integer, primary_key=True, autoincrement=True, index=True)
    user_id = Column(String(64), ForeignKey("users.uid"), index=True, nullable=False)
    name = Column(String(128), nullable=False)
    phone = Column(String(32), nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    user = relationship("User", back_populates="contacts")
