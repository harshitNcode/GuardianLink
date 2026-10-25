import math
import logging
from typing import List, Tuple
from sqlalchemy.orm import Session
from shapely import wkt
from shapely.geometry import Point
from app.models.geofence import GeofenceZone
from app.schemas.geofence import GeofenceCheckResult

logger = logging.getLogger("GuardianLink.Geofence")


def haversine_distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance in meters between two coordinates using the Haversine formula."""
    R = 6371000.0  # Earth radius in meters
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2))
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


def is_point_in_polygon(lat: float, lon: float, polygon_wkt_str: str) -> bool:
    """Check if point is inside a WKT polygon using shapely."""
    try:
        poly = wkt.loads(polygon_wkt_str)
        # Note: WKT longitude is x, latitude is y
        pt = Point(lon, lat)
        return poly.contains(pt) or poly.touches(pt)
    except Exception as e:
        logger.error(f"Error checking polygon WKT: {e}")
        return False


def evaluate_geofences(db: Session, device_id: str, latitude: float, longitude: float) -> List[GeofenceCheckResult]:
    """
    Evaluate all active geofence zones for a given device.
    Returns list of check results (whether inside or breached).
    """
    zones = db.query(GeofenceZone).filter(
        GeofenceZone.device_id == device_id,
        GeofenceZone.is_active == True
    ).all()

    results: List[GeofenceCheckResult] = []

    for zone in zones:
        dist = haversine_distance_meters(
            latitude, longitude,
            zone.center_latitude, zone.center_longitude
        )

        if zone.radius_meters and zone.radius_meters > 0:
            # Circular radius geofence
            is_inside = dist <= zone.radius_meters
            breach_distance = max(0.0, dist - zone.radius_meters)
        elif zone.polygon_wkt:
            # Custom polygon / rectangle / triangle geofence
            is_inside = is_point_in_polygon(latitude, longitude, zone.polygon_wkt)
            breach_distance = dist
        else:
            # Default circular safe zone
            is_inside = dist <= 500.0
            breach_distance = max(0.0, dist - 500.0)

        results.append(GeofenceCheckResult(
            zone_id=zone.id,
            zone_name=zone.name,
            is_inside=is_inside,
            distance_meters=round(breach_distance if not is_inside else dist, 1),
            status="SAFE" if is_inside else "BREACH"
        ))

    return results
