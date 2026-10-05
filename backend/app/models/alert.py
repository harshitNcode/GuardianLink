import datetime
from sqlalchemy import Column, String, Integer, Float, Boolean, DateTime
from app.database import Base


class Alert(Base):
    __tablename__ = "alerts"

    id = Column(Integer, primary_key=True, autoincrement=True, index=True)
    user_id = Column(String(64), index=True, nullable=False)
    device_id = Column(String(64), index=True, default="")
    title = Column(String(128), nullable=False)
    time = Column(String(32), default="")
    type = Column(String(32), default="danger")  # "danger", "warning", "success"
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    resolved = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, index=True)
