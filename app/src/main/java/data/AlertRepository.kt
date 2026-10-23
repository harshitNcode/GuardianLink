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

class AlertRepository {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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
                                list.add(
                                    Alert(
                                        title = item.optString("title", "Emergency Alert"),
                                        time = item.optString("time", "Just now"),
                                        type = item.optString("type", "danger")
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
}
