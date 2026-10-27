package com.guardianlink.app.util

import android.content.Context

object AuthPreference {

    private const val PREFS_NAME = "guardianlink_auth_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"
    private const val KEY_EMAIL = "email"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
    private const val KEY_CALLMEBOT_KEY = "callmebot_key"
    private const val KEY_SERVER_HOST = "server_host"

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(
        context: Context,
        isLoggedIn: Boolean,
        uid: String = "demo_user",
        name: String = "YourName",
        phone: String = "",
        email: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getUid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UID, "demo_user") ?: "demo_user"
    }

    fun getName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, "YourName") ?: "YourName"
    }

    fun getPhone(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PHONE, "") ?: ""
    }

    fun getEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EMAIL, "") ?: ""
    }

    fun setName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun setPhone(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, "demo_device") ?: "demo_device"
    }

    fun setDeviceId(context: Context, deviceId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getEmergencyContact(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EMERGENCY_CONTACT, "") ?: ""
    }

    fun setEmergencyContact(context: Context, contact: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply()
    }

    fun getCallMeBotKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CALLMEBOT_KEY, "") ?: ""
    }

    fun setCallMeBotKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CALLMEBOT_KEY, key.trim()).apply()
    }

    fun getServerHost(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_HOST, "172.18.112.25:8000") ?: "172.18.112.25:8000"
    }

    fun setServerHost(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        prefs.edit().putString(KEY_SERVER_HOST, cleanHost).apply()
        com.guardianlink.app.data.ApiConfig.HOST = cleanHost
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private const val KEY_CACHED_HEART_RATE = "cached_heart_rate"
    private const val KEY_CACHED_TEMP = "cached_temp"
    private const val KEY_CACHED_HUMIDITY = "cached_humidity"
    private const val KEY_CACHED_BATTERY = "cached_battery"
    private const val KEY_CACHED_GPS = "cached_gps"
    private const val KEY_CACHED_LAT = "cached_lat"
    private const val KEY_CACHED_LON = "cached_lon"
    private const val KEY_CACHED_TIMESTAMP = "cached_timestamp"

    fun saveCachedDeviceStatus(context: Context, status: com.guardianlink.app.model.DeviceStatus) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (status.heartRate > 0) editor.putInt(KEY_CACHED_HEART_RATE, status.heartRate)
        if (status.temperature > 0f) editor.putFloat(KEY_CACHED_TEMP, status.temperature)
        if (status.humidity > 0f) editor.putFloat(KEY_CACHED_HUMIDITY, status.humidity)
        if (status.battery.isNotBlank() && status.battery != "--") editor.putString(KEY_CACHED_BATTERY, status.battery)
        if (status.gps.isNotBlank() && status.gps != "--") editor.putString(KEY_CACHED_GPS, status.gps)
        if (status.rawLatitude != null) editor.putLong(KEY_CACHED_LAT, java.lang.Double.doubleToRawLongBits(status.rawLatitude))
        if (status.rawLongitude != null) editor.putLong(KEY_CACHED_LON, java.lang.Double.doubleToRawLongBits(status.rawLongitude))
        if (status.lastUpdatedTimestamp > 0L) editor.putLong(KEY_CACHED_TIMESTAMP, status.lastUpdatedTimestamp)
        editor.apply()
    }

    fun getCachedDeviceStatus(context: Context): com.guardianlink.app.model.DeviceStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hr = prefs.getInt(KEY_CACHED_HEART_RATE, 0)
        val temp = prefs.getFloat(KEY_CACHED_TEMP, 0f)
        val hum = prefs.getFloat(KEY_CACHED_HUMIDITY, 0f)
        val bat = prefs.getString(KEY_CACHED_BATTERY, "--") ?: "--"
        val gps = prefs.getString(KEY_CACHED_GPS, "--") ?: "--"
        val latBits = prefs.getLong(KEY_CACHED_LAT, 0L)
        val lonBits = prefs.getLong(KEY_CACHED_LON, 0L)
        val lat = if (latBits != 0L) java.lang.Double.longBitsToDouble(latBits) else null
        val lon = if (lonBits != 0L) java.lang.Double.longBitsToDouble(lonBits) else null
        val ts = prefs.getLong(KEY_CACHED_TIMESTAMP, 0L)

        // Purge legacy mock Delhi coordinates (28.6139, 77.2090) from cached disk storage
        val isMockDelhi = (lat != null && Math.abs(lat - 28.6139) < 0.05) || gps.contains("28.6139")
        val cleanLat = if (isMockDelhi) null else lat
        val cleanLon = if (isMockDelhi) null else lon
        val cleanGps = if (isMockDelhi) "--" else gps
        if (isMockDelhi) {
            prefs.edit().remove(KEY_CACHED_LAT).remove(KEY_CACHED_LON).remove(KEY_CACHED_GPS).apply()
        }

        return com.guardianlink.app.model.DeviceStatus(
            battery = bat,
            gps = cleanGps,
            network = "Wi-Fi",
            device = "Offline",
            sos = false,
            heartRate = hr,
            temperature = temp,
            humidity = hum,
            rawLatitude = cleanLat,
            rawLongitude = cleanLon,
            lastUpdatedTimestamp = ts
        )
    }
}
