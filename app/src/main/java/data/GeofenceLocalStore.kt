package com.guardianlink.app.data

import android.content.Context
import android.util.Log
import com.guardianlink.app.model.GeofenceZone
import org.json.JSONArray
import org.json.JSONObject

object GeofenceLocalStore {

    private const val PREFS_NAME = "guardianlink_geofences_prefs"
    private const val KEY_PREFIX = "geofences_"

    fun saveZones(context: Context, deviceId: String, zones: List<GeofenceZone>) {
        try {
            val jsonArray = JSONArray()
            for (zone in zones) {
                val obj = JSONObject().apply {
                    put("id", zone.id)
                    put("device_id", zone.deviceId)
                    put("user_id", zone.userId)
                    put("name", zone.name)
                    put("center_latitude", zone.centerLatitude)
                    put("center_longitude", zone.centerLongitude)
                    put("radius_meters", zone.radiusMeters)
                    if (zone.polygonWkt != null) {
                        put("polygon_wkt", zone.polygonWkt)
                    } else {
                        put("polygon_wkt", JSONObject.NULL)
                    }
                    put("is_active", zone.isActive)
                }
                jsonArray.put(obj)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PREFIX + deviceId, jsonArray.toString()).apply()
            Log.d("GuardianLink.GeofenceStore", "Saved ${zones.size} geofences locally for $deviceId")
        } catch (e: Exception) {
            Log.e("GuardianLink.GeofenceStore", "Error saving geofences locally: ${e.message}")
        }
    }

    fun loadZones(context: Context, deviceId: String): List<GeofenceZone> {
        val list = mutableListOf<GeofenceZone>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PREFIX + deviceId, null) ?: return list
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val zoneName = item.optString("name", "Safe Zone")
                val centerLat = item.optDouble("center_latitude", 0.0)

                // Purge any legacy dummy Delhi safe zones
                if (zoneName.contains("India Gate", ignoreCase = true) ||
                    zoneName.contains("Rajpath", ignoreCase = true) ||
                    zoneName.contains("Purana", ignoreCase = true) ||
                    zoneName.contains("Test Zone", ignoreCase = true) ||
                    (Math.abs(centerLat - 28.6) < 0.5 && Math.abs(item.optDouble("center_longitude", 0.0) - 77.2) < 0.5)
                ) {
                    continue
                }

                list.add(
                    GeofenceZone(
                        id = item.optInt("id", 0),
                        deviceId = item.optString("device_id", deviceId),
                        userId = item.optString("user_id", "demo_user"),
                        name = zoneName,
                        centerLatitude = centerLat,
                        centerLongitude = item.optDouble("center_longitude", 0.0),
                        radiusMeters = item.optDouble("radius_meters", 500.0),
                        polygonWkt = if (item.isNull("polygon_wkt")) null else item.optString("polygon_wkt"),
                        isActive = item.optBoolean("is_active", true)
                    )
                )
            }
            Log.d("GuardianLink.GeofenceStore", "Loaded ${list.size} geofences locally for $deviceId")
        } catch (e: Exception) {
            Log.e("GuardianLink.GeofenceStore", "Error loading local geofences: ${e.message}")
        }
        return list
    }
}
