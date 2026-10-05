import logging
from typing import Optional

logger = logging.getLogger("GuardianLink.Notification")


async def dispatch_emergency_notification(
    user_id: str,
    device_id: str,
    title: str,
    latitude: Optional[float] = None,
    longitude: Optional[float] = None
):
    """
    Emergency notification dispatch service.
    Can be hooked into Firebase FCM (Push notifications) or Twilio / Fast2SMS (SMS notifications).
    For now, logs and formats the emergency dispatch payload.
    """
    coord_str = f"({latitude}, {longitude})" if latitude and longitude else "Unknown location"
    logger.warning(
        f"🚨 [EMERGENCY DISPATCH] User: {user_id} | Device: {device_id} | Alert: '{title}' at {coord_str}"
    )
    # Hooks for external free FCM / SMS dispatch:
    # 1. Send FCM high-priority message to all tokens associated with user_id
    # 2. Query emergency contacts and dispatch SMS / Webhook
    return {
        "status": "dispatched",
        "user_id": user_id,
        "device_id": device_id,
        "alert": title,
        "location": coord_str
    }
