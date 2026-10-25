package com.guardianlink.app.data

import com.guardianlink.app.model.GeofenceZone
import org.osmdroid.util.GeoPoint
import kotlin.math.*

object GeofenceWktHelper {

    /**
     * Converts a list of GeoPoints into standard WKT format:
     * POLYGON((lon1 lat1, lon2 lat2, ..., lon1 lat1))
     * Note: Standard WKT coordinates are (longitude, latitude).
     */
    fun toPolygonWkt(points: List<GeoPoint>): String {
        if (points.size < 3) return ""
        val closed = if (points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        val coords = closed.joinToString(", ") { "${it.longitude} ${it.latitude}" }
        return "POLYGON(($coords))"
    }

    /**
     * Parses a standard WKT polygon string back into a list of GeoPoints.
     */
    fun fromPolygonWkt(wkt: String?): List<GeoPoint> {
        if (wkt.isNullOrBlank()) return emptyList()
        try {
            val clean = wkt
                .replace("POLYGON", "", ignoreCase = true)
                .replace("(", "")
                .replace(")", "")
                .trim()
            if (clean.isEmpty()) return emptyList()

            val parts = clean.split(",")
            val result = mutableListOf<GeoPoint>()
            for (p in parts) {
                val tokens = p.trim().split("\\s+".toRegex())
                if (tokens.size >= 2) {
                    val lon = tokens[0].toDoubleOrNull()
                    val lat = tokens[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        result.add(GeoPoint(lat, lon))
                    }
                }
            }
            return result
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Generates a 4-corner rectangular boundary from 2 opposite diagonal taps.
     */
    fun createRectanglePoints(p1: GeoPoint, p2: GeoPoint): List<GeoPoint> {
        val minLat = min(p1.latitude, p2.latitude)
        val maxLat = max(p1.latitude, p2.latitude)
        val minLon = min(p1.longitude, p2.longitude)
        val maxLon = max(p1.longitude, p2.longitude)

        return listOf(
            GeoPoint(minLat, minLon),
            GeoPoint(maxLat, minLon),
            GeoPoint(maxLat, maxLon),
            GeoPoint(minLat, maxLon),
            GeoPoint(minLat, minLon) // Closed
        )
    }

    /**
     * Generates a 3-corner triangular boundary from 3 tapped points.
     */
    fun createTrianglePoints(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint): List<GeoPoint> {
        return listOf(p1, p2, p3, p1)
    }

    /**
     * Generates an array of GeoPoints approximating a circle for rendering on the map.
     */
    fun createCirclePoints(center: GeoPoint, radiusMeters: Double, segments: Int = 40): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0
        val latRad = Math.toRadians(center.latitude)

        val dLat = (radiusMeters / earthRadius) * (180.0 / Math.PI)
        val dLon = dLat / cos(latRad)

        for (i in 0 until segments) {
            val angle = 2.0 * Math.PI * i / segments
            val lat = center.latitude + dLat * sin(angle)
            val lon = center.longitude + dLon * cos(angle)
            points.add(GeoPoint(lat, lon))
        }
        if (points.isNotEmpty()) {
            points.add(points.first()) // Close the polygon
        }
        return points
    }

    /**
     * Calculates the center (centroid) of a list of coordinates.
     */
    fun computeCenter(points: List<GeoPoint>): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        var sumLat = 0.0
        var sumLon = 0.0
        val count = points.size
        for (p in points) {
            sumLat += p.latitude
            sumLon += p.longitude
        }
        return GeoPoint(sumLat / count, sumLon / count)
    }

    /**
     * Shifts a list of GeoPoints by a delta (deltaLat, deltaLon), preserving relative geometry.
     */
    fun translatePoints(points: List<GeoPoint>, deltaLat: Double, deltaLon: Double): List<GeoPoint> {
        return points.map { GeoPoint(it.latitude + deltaLat, it.longitude + deltaLon) }
    }

    /**
     * Haversine distance in meters between two GeoPoints.
     */
    fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Standard Ray-Casting algorithm to check if a point is inside a polygon.
     */
    fun isPointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.longitude > point.longitude) != (pj.longitude > point.longitude) &&
                (point.latitude < (pj.latitude - pi.latitude) * (point.longitude - pi.longitude) / (pj.longitude - pi.longitude) + pi.latitude)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Checks if a point is inside a GeofenceZone (either circle or polygon).
     */
    fun isPointInsideZone(point: GeoPoint, zone: GeofenceZone): Boolean {
        if (zone.radiusMeters > 0) {
            val center = GeoPoint(zone.centerLatitude, zone.centerLongitude)
            return distanceBetween(point, center) <= zone.radiusMeters
        }
        if (!zone.polygonWkt.isNullOrBlank()) {
            val polyPoints = fromPolygonWkt(zone.polygonWkt)
            if (polyPoints.isNotEmpty()) {
                return isPointInPolygon(point, polyPoints)
            }
        }
        val center = GeoPoint(zone.centerLatitude, zone.centerLongitude)
        return distanceBetween(point, center) <= 500.0
    }
}
