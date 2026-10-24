package com.guardianlink.app.model

data class DeviceStatus(
    val battery: String = "--",
    val gps: String = "--",
    val network: String = "Wi-Fi",
    val device: String = "Offline",
    val sos: Boolean = false,
    val heartRate: Int = 0,
    val temperature: Float = 0.0f,
    val humidity: Float = 0.0f,
    val rawLatitude: Double? = null,
    val rawLongitude: Double? = null,
    val lastUpdatedTimestamp: Long = 0L
) {
    // Device is considered disconnected if backend status is Offline or no packet received for > 40s
    val isDisconnected: Boolean
        get() = device.contains("Offline", ignoreCase = true) ||
                (lastUpdatedTimestamp > 0L && (System.currentTimeMillis() - lastUpdatedTimestamp) > 40_000L)

    val isStale: Boolean
        get() = isDisconnected

    val latitude: Double?
        get() {
            val lat = rawLatitude ?: gps.split(",").getOrNull(0)?.trim()?.toDoubleOrNull()
            if (lat == null || lat == 0.0) return null
            if (Math.abs(lat - 28.6) < 0.4) return null
            return lat
        }

    val longitude: Double?
        get() {
            val lon = rawLongitude ?: gps.split(",").getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lon == null || lon == 0.0) return null
            if (Math.abs(lon - 77.2) < 0.4) return null
            return lon
        }

    val hasValidLocation: Boolean
        get() = latitude != null && longitude != null

    val displayGps: String
        get() {
            val lat = latitude
            val lon = longitude
            return if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                "${"%.4f".format(lat)}° N, ${"%.4f".format(lon)}° E"
            } else {
                if (gps.isNotBlank() && gps != "--") gps else "Awaiting GPS Fix"
            }
        }

    // Always preserve and display cached old data even when disconnected!
    val displayHeartRate: String
        get() = if (heartRate > 0) "$heartRate BPM" else "--"

    val displayTemperature: String
        get() = if (temperature > 0f) "${"%.1f".format(temperature)}°C" else "--"

    val displayTemperatureFahrenheit: String
        get() = if (temperature > 0f) "${"%.1f".format((temperature * 9f / 5f) + 32f)}°F" else "--"

    val displayHumidity: String
        get() = if (humidity > 0f) "${humidity.toInt()}%" else "--"

    val heartRateStatus: String
        get() = when {
            heartRate <= 0 -> "Awaiting Sensor"
            isDisconnected -> "Last Known"
            heartRate > 120 -> "Elevated"
            heartRate < 55 -> "Low Pulse"
            else -> "Normal"
        }

    val temperatureStatus: String
        get() = when {
            temperature <= 0f -> "Awaiting Sensor"
            isDisconnected -> "Last Known"
            temperature >= 38.5f -> "Fever"
            temperature >= 37.5f -> "Warm"
            temperature < 35.0f -> "Low"
            else -> "Normal"
        }

    val humidityStatus: String
        get() = when {
            humidity <= 0f -> "Awaiting Sensor"
            isDisconnected -> "Last Known"
            humidity > 70f -> "High"
            humidity < 30f -> "Dry"
            else -> "Comfort"
        }
}
