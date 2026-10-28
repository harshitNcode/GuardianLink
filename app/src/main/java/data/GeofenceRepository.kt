package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.GeofenceZone
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GeofenceRepository {

    private val client get() = ApiConfig.defaultClient
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Fetch all active geofence zones for a device from FastAPI
    fun fetchGeofences(deviceId: String, onResult: (List<GeofenceZone>) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/geofence/$deviceId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Geofence", "Failed to fetch geofences: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return
                        try {
                            val jsonArray = JSONArray(body)
                            val list = mutableListOf<GeofenceZone>()
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                list.add(
                                    GeofenceZone(
                                        id = item.optInt("id", 0),
                                        deviceId = item.optString("device_id", deviceId),
                                        userId = item.optString("user_id", "demo_user"),
                                        name = item.optString("name", "Safe Zone"),
                                        centerLatitude = item.optDouble("center_latitude", 0.0),
                                        centerLongitude = item.optDouble("center_longitude", 0.0),
                                        radiusMeters = item.optDouble("radius_meters", 500.0),
                                        polygonWkt = if (item.isNull("polygon_wkt")) null else item.optString("polygon_wkt"),
                                        isActive = item.optBoolean("is_active", true)
                                    )
                                )
                            }
                            mainHandler.post { onResult(list) }
                        } catch (e: Exception) {
                            Log.e("GuardianLink.Geofence", "Error parsing geofences: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    // Create a new geofence zone on FastAPI
    fun createGeofence(zone: GeofenceZone, onComplete: (GeofenceZone?) -> Unit) {
        val payload = JSONObject().apply {
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
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/geofence")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Geofence", "Failed to create geofence: ${e.message}")
                mainHandler.post { onComplete(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val respStr = response.body?.string() ?: ""
                            val item = JSONObject(respStr)
                            val created = GeofenceZone(
                                id = item.optInt("id", zone.id),
                                deviceId = item.optString("device_id", zone.deviceId),
                                userId = item.optString("user_id", zone.userId),
                                name = item.optString("name", zone.name),
                                centerLatitude = item.optDouble("center_latitude", zone.centerLatitude),
                                centerLongitude = item.optDouble("center_longitude", zone.centerLongitude),
                                radiusMeters = item.optDouble("radius_meters", zone.radiusMeters),
                                polygonWkt = if (item.isNull("polygon_wkt")) zone.polygonWkt else item.optString("polygon_wkt"),
                                isActive = item.optBoolean("is_active", zone.isActive)
                            )
                            mainHandler.post { onComplete(created) }
                            return
                        } catch (e: Exception) {
                            Log.e("GuardianLink.Geofence", "Error parsing created geofence: ${e.message}")
                        }
                    }
                    mainHandler.post { onComplete(null) }
                }
            }
        })
    }

    // Update an existing geofence zone on FastAPI
    fun updateGeofence(zone: GeofenceZone, onComplete: (Boolean) -> Unit) {
        val payload = JSONObject().apply {
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
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/geofence/${zone.id}")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Geofence", "Failed to update geofence: ${e.message}")
                mainHandler.post { onComplete(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val success = response.isSuccessful
                    mainHandler.post { onComplete(success) }
                }
            }
        })
    }

    // Delete a geofence zone by ID
    fun deleteGeofence(zoneId: Int, onComplete: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/geofence/$zoneId")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Geofence", "Failed to delete geofence: ${e.message}")
                mainHandler.post { onComplete(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val success = response.isSuccessful
                    mainHandler.post { onComplete(success) }
                }
            }
        })
    }
}
