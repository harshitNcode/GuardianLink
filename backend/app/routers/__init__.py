from app.routers.telemetry import router as telemetry_router
from app.routers.geofence import router as geofence_router
from app.routers.alerts import router as alerts_router
from app.routers.contacts import router as contacts_router
from app.routers.auth import router as auth_router

__all__ = ["telemetry_router", "geofence_router", "alerts_router", "contacts_router", "auth_router"]
