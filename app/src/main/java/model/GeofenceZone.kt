package com.guardianlink.app.model

data class GeofenceZone(
    val id: Int = 0,
    val deviceId: String = "demo_device",
    val userId: String = "demo_user",
    val name: String = "Safe Zone",
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    val radiusMeters: Double = 500.0,
    val polygonWkt: String? = null,
    val isActive: Boolean = true
) {
    // Determine inferred shape type
    val shapeType: String
        get() {
            if (polygonWkt.isNullOrBlank()) return "Circle"
            val clean = polygonWkt.replace("POLYGON((", "").replace("))", "").trim()
            val points = clean.split(",")
            return when (points.size) {
                4 -> "Triangle" // 3 points + closed repeat
                5 -> "Rectangle" // 4 points + closed repeat
                else -> if (points.size > 20) "Circle" else "Custom Polygon"
            }
        }
}
