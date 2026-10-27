package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.DeviceStatus
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeviceRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for persistent WebSocket
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .build()
            chain.proceed(request)
        }
        .build()

    private var activeWebSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendPhoneLocation(deviceId: String, latitude: Double, longitude: Double, onComplete: (() -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
        }
        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/telemetry/$deviceId/location")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.Device", "Failed to broadcast phone location: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d("GuardianLink.Device", "Live phone location broadcast successfully!")
                    }
                }
                mainHandler.post { onComplete?.invoke() }
            }
        })
    }

    fun triggerBuzzerAlarm(deviceId: String, deviceIp: String? = null, onComplete: (() -> Unit)? = null) {
        // 1. Direct local IP trigger if device IP is known (fires in ~10ms on local Wi-Fi / hotspot)
        if (!deviceIp.isNullOrBlank()) {
            val cleanIp = deviceIp.trim()
            val localReq = Request.Builder()
                .url("http://$cleanIp/buzzer")
                .get()
                .build()
            client.newCall(localReq).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("GuardianLink.Device", "Direct local buzzer trigger not reachable: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    Log.d("GuardianLink.Device", "Direct local buzzer trigger executed in <15ms!")
                }
            })
        }

        // 2. Cloud backend trigger (dispatches WhatsApp emergency, logs alert, sets cloud buzzer flag)
        val emptyBody = "{}".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/telemetry/$deviceId/buzzer")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .post(emptyBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.Device", "Failed to trigger buzzer alarm: ${e.message}")
                mainHandler.post { onComplete?.invoke() }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d("GuardianLink.Device", "Buzzer alarm successfully triggered on ESP32!")
                    }
                }
                mainHandler.post { onComplete?.invoke() }
            }
        })
    }

    fun listenToStatus(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        isListening = true

        // 1. Immediately fetch latest device status via HTTP REST
        fetchLatestStatus(deviceId, onUpdate)

        // 2. Connect to real-time WebSocket stream
        connectWebSocket(deviceId, onUpdate)
    }

    fun refresh(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        fetchLatestStatus(deviceId, onUpdate)
        if (activeWebSocket == null || !isListening) {
            connectWebSocket(deviceId, onUpdate)
        }
    }

    fun fetchLatestStatus(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/telemetry/$deviceId/latest")
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.Device", "Failed to fetch latest status: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return
                        try {
                            val obj = JSONObject(body)
                            val status = parseDeviceStatus(obj, isLiveWebSocket = false)
                            mainHandler.post { onUpdate(status) }
                        } catch (e: Exception) {
                            Log.e("GuardianLink.Device", "Error parsing latest status: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    private fun connectWebSocket(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        if (!isListening) return

        val wsUrl = "${ApiConfig.WS_BASE_URL}/ws/devices/$deviceId"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Bypass-Tunnel-Reminder", "true")
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("GuardianLink.Device", "Connected to FastAPI WebSocket: $wsUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("GuardianLink.Device", "WebSocket update received: $text")
                try {
                    val obj = JSONObject(text)
                    val status = parseDeviceStatus(obj, isLiveWebSocket = true)
                    mainHandler.post { onUpdate(status) }
                } catch (e: Exception) {
                    Log.e("GuardianLink.Device", "Failed to parse WebSocket JSON: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("GuardianLink.Device", "WebSocket disconnected: ${t.message}. Reconnecting in 3s...")
                // Auto-reconnect after 3 seconds if still listening
                if (isListening) {
                    mainHandler.postDelayed({
                        connectWebSocket(deviceId, onUpdate)
                    }, 3000)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("GuardianLink.Device", "WebSocket closed: $reason")
            }
        })
    }

    private fun parseDeviceStatus(obj: JSONObject, isLiveWebSocket: Boolean = false): DeviceStatus {
        val lat = if (obj.has("latitude") && !obj.isNull("latitude")) obj.optDouble("latitude") else null
        val lon = if (obj.has("longitude") && !obj.isNull("longitude")) obj.optDouble("longitude") else null
        val gpsVal = if (lat != null && lon != null) "$lat, $lon" else obj.optString("gps", "--")
        val bpm = if (obj.has("heart_rate") && !obj.isNull("heart_rate")) obj.optInt("heart_rate", 0) else obj.optInt("heartRate", 0)
        val temp = if (obj.has("temperature") && !obj.isNull("temperature")) obj.optDouble("temperature", 0.0).toFloat() else 0.0f
        val hum = if (obj.has("humidity") && !obj.isNull("humidity")) obj.optDouble("humidity", 0.0).toFloat() else 0.0f

        val now = System.currentTimeMillis()
        val updatedAtStr = obj.optString("updated_at", "")
        val lastUpdate = if (isLiveWebSocket) {
            now
        } else if (updatedAtStr.isNotBlank() && updatedAtStr != "null") {
            try {
                val cleanIso = if (updatedAtStr.contains("Z")) updatedAtStr else updatedAtStr + "Z"
                java.time.Instant.parse(cleanIso).toEpochMilli()
            } catch (e: Exception) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.parse(updatedAtStr.substringBefore("."))?.time ?: now
                } catch (e2: Exception) {
                    now
                }
            }
        } else {
            0L
        }

        val ipStr = obj.optString("ip", "").takeIf { it.isNotBlank() && it != "null" }
        return DeviceStatus(
            battery = obj.optString("battery", "--"),
            gps = gpsVal,
            network = obj.optString("network", "Wi-Fi"),
            device = obj.optString("status", "Online"),
            sos = obj.optBoolean("sos", false),
            heartRate = bpm,
            temperature = temp,
            humidity = hum,
            rawLatitude = lat,
            rawLongitude = lon,
            ip = ipStr,
            lastUpdatedTimestamp = lastUpdate
        )
    }

    fun stopListening() {
        isListening = false
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }
}