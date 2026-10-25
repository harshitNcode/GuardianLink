package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.Alert
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AlertRepository {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val istFormatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    // Fetch alert history from FastAPI backend
    fun listenToAlerts(userId: String, onUpdate: (List<Alert>) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/alerts/$userId")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Alert", "Failed to fetch alerts: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return
                        try {
                            val jsonArray = JSONArray(body)
                            val list = mutableListOf<Alert>()
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                val alertId = if (item.has("id") && !item.isNull("id")) item.optInt("id") else null
                                val title = item.optString("title", "Emergency Alert")
                                var timeStr = item.optString("time", "Just now")
                                var timestamp = System.currentTimeMillis()

                                // If server provided created_at ISO UTC timestamp, convert to local IST
                                val createdAtStr = item.optString("created_at", "")
                                if (createdAtStr.isNotBlank() && createdAtStr != "null") {
                                    try {
                                        val cleanIso = createdAtStr.substringBefore(".")
                                        val parsedDate = isoParser.parse(cleanIso)
                                        if (parsedDate != null) {
                                            timestamp = parsedDate.time
                                            timeStr = istFormatter.format(parsedDate)
                                        }
                                    } catch (_: Exception) {}
                                }

                                list.add(
                                    Alert(
                                        id = alertId,
                                        title = title,
                                        time = timeStr,
                                        type = item.optString("type", "danger"),
                                        timestamp = timestamp
                                    )
                                )
                            }
                            mainHandler.post { onUpdate(list) }
                        } catch (e: Exception) {
                            Log.e("GuardianLink.Alert", "Error parsing alerts: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    // Post an incident alert (e.g. Safe Zone Breach) to backend database and trigger notification dispatch
    fun createAlert(
        userId: String,
        deviceId: String,
        title: String,
        type: String = "warning",
        latitude: Double? = null,
        longitude: Double? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val payload = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("title", title)
            put("type", type)
            if (latitude != null) put("latitude", latitude)
            if (longitude != null) put("longitude", longitude)
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/alerts")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.Alert", "Failed to sync alert to backend: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.Alert", "Incident alert successfully recorded on backend!")
                    }
                }
                mainHandler.post { onComplete?.invoke() }
            }
        })
    }

    // Trigger an emergency SOS: flags device and logs alert on the FastAPI server
    fun triggerSOS(userId: String, deviceId: String, onComplete: (() -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("title", "Emergency SOS Triggered")
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/alerts/sos")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Alert", "Failed to trigger SOS on backend: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.Alert", "SOS successfully triggered on FastAPI backend!")
                    } else {
                        Log.w("GuardianLink.Alert", "SOS response failed: code ${response.code}")
                    }
                    mainHandler.post { onComplete?.invoke() }
                }
            }
        })
    }

    // Cancel emergency SOS and stand down alert state
    fun cancelSOS(userId: String, deviceId: String, onComplete: (() -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("title", "Emergency SOS Stood Down")
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/alerts/sos/cancel")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Alert", "Failed to cancel SOS on backend: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.Alert", "SOS successfully cancelled on FastAPI backend!")
                    } else {
                        Log.w("GuardianLink.Alert", "Cancel SOS response failed: code ${response.code}")
                    }
                    mainHandler.post { onComplete?.invoke() }
                }
            }
        })
    }

    // Delete non-SOS alert from backend SQLite database
    fun deleteAlert(alert: Alert, onComplete: (() -> Unit)? = null) {
        val url = if (alert.id != null && alert.id > 0) {
            "${ApiConfig.HTTP_BASE_URL}/api/v1/alerts/${alert.id}"
        } else {
            val encodedTitle = java.net.URLEncoder.encode(alert.title, "UTF-8")
            val encodedTime = java.net.URLEncoder.encode(alert.time, "UTF-8")
            "${ApiConfig.HTTP_BASE_URL}/api/v1/alerts?title=$encodedTitle&time=$encodedTime"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.Alert", "Failed to delete alert from backend: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.d("GuardianLink.Alert", "Deleted alert response code: ${response.code}")
                    mainHandler.post { onComplete?.invoke() }
                }
            }
        })
    }
}

