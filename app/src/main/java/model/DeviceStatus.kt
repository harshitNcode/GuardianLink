package com.guardianlink.app.model

data class DeviceStatus(
    val battery: String = "92%",
    val gps: String = "28.6139, 77.2090",
    val network: String = "4G LTE",
    val device: String = "Online",
    val sos: Boolean = false,
    val rawLatitude: Double? = null,
    val rawLongitude: Double? = null
) {
    val latitude: Double?
        get() = rawLatitude ?: gps.split(",").getOrNull(0)?.trim()?.toDoubleOrNull() ?: 28.6139

    val longitude: Double?
        get() = rawLongitude ?: gps.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 77.2090

    val hasValidLocation: Boolean
        get() = latitude != null && longitude != null

    val displayGps: String
        get() {
            val lat = latitude
            val lon = longitude
            return if (lat != null && lon != null) {
                "${"%.4f".format(lat)}° N, ${"%.4f".format(lon)}° E"
            } else {
                gps
            }
        }
}