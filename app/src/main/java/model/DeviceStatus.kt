package com.guardianlink.app.model

data class DeviceStatus(
    val battery: String = "92%",
    val gps: String = "28.6139, 77.2090",
    val network: String = "Wi-Fi",
    val device: String = "Online",
    val sos: Boolean = false,
    val heartRate: Int = 74,
    val temperature: Float = 36.6f,
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

    val displayHeartRate: String
        get() = "$heartRate BPM"

    val displayTemperature: String
        get() = "${"%.1f".format(temperature)}°C"

    val displayTemperatureFahrenheit: String
        get() = "${"%.1f".format((temperature * 9f / 5f) + 32f)}°F"

    val heartRateStatus: String
        get() = when {
            heartRate > 120 -> "Elevated"
            heartRate < 55 -> "Low Pulse"
            else -> "Normal"
        }

    val temperatureStatus: String
        get() = when {
            temperature >= 38.5f -> "Fever"
            temperature >= 37.5f -> "Warm"
            temperature < 35.0f -> "Low"
            else -> "Normal"
        }
}
