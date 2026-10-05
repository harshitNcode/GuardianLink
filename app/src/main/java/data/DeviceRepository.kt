package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.DeviceStatus
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeviceRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for persistent WebSocket
        .build()

    private var activeWebSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    fun listenToStatus(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        isListening = true

        // 1. Immediately fetch latest device status via HTTP REST
        fetchLatestStatus(deviceId, onUpdate)

        // 2. Connect to real-time WebSocket stream
        connectWebSocket(deviceId, onUpdate)
    }

    private fun fetchLatestStatus(deviceId: String, onUpdate: (DeviceStatus) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/telemetry/$deviceId/latest")
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
                            val status = parseDeviceStatus(obj)
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
        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("GuardianLink.Device", "Connected to FastAPI WebSocket: $wsUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("GuardianLink.Device", "WebSocket update received: $text")
                try {
                    val obj = JSONObject(text)
                    val status = parseDeviceStatus(obj)
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

    private fun parseDeviceStatus(obj: JSONObject): DeviceStatus {
        val lat = if (obj.has("latitude") && !obj.isNull("latitude")) obj.optDouble("latitude") else null
        val lon = if (obj.has("longitude") && !obj.isNull("longitude")) obj.optDouble("longitude") else null
        val gpsVal = if (lat != null && lon != null) "$lat, $lon" else obj.optString("gps", "28.6139, 77.2090")

        return DeviceStatus(
            battery = obj.optString("battery", "92%"),
            gps = gpsVal,
            network = obj.optString("network", "4G LTE"),
            device = obj.optString("status", "Online"),
            sos = obj.optBoolean("sos", false),
            rawLatitude = lat,
            rawLongitude = lon
        )
    }

    fun stopListening() {
        isListening = false
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }
}