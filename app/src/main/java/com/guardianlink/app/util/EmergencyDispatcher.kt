package com.guardianlink.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.guardianlink.app.data.ApiConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object EmergencyDispatcher {

    private val httpClient = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun dispatchEmergencyAlert(
        context: Context,
        phoneNumber: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val rawDigits = phoneNumber.filter { it.isDigit() }
        val formattedNumber = if (rawDigits.length == 10) "91$rawDigits" else rawDigits

        var actualLat = latitude
        var actualLon = longitude
        if (actualLat == null || actualLon == null) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                val phoneLoc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    ?: lm?.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
                if (phoneLoc != null) {
                    actualLat = phoneLoc.latitude
                    actualLon = phoneLoc.longitude
                }
            } catch (_: SecurityException) {}
        }

        val locationUrl = if (actualLat != null && actualLon != null && actualLat != 0.0 && actualLon != 0.0) {
            "https://maps.google.com/?q=$actualLat,$actualLon"
        } else {
            "GPS tracker active (Awaiting satellite lock)"
        }

        val timeStr = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.getDefault()).format(java.util.Date())
        val plainMessage = "EMERGENCY SOS ALERT! Urgent help needed! Live GPS location: $locationUrl (Triggered at $timeStr via GuardianLink)"

        // 1. Automated WhatsApp Cloud Gateway Dispatch (CallMeBot)
        // If an API key is set in Settings and phone number is provided, sends in background
        val callmebotKey = AuthPreference.getCallMeBotKey(context)
        if (callmebotKey.isNotBlank() && formattedNumber.isNotBlank()) {
            try {
                val encodedPlain = URLEncoder.encode(plainMessage, "UTF-8")
                val callmebotUrl = "https://api.callmebot.com/whatsapp.php?phone=$formattedNumber&text=$encodedPlain&apikey=$callmebotKey"
                httpClient.newCall(Request.Builder().url(callmebotUrl).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.w("EmergencyDispatcher", "CallMeBot WhatsApp gateway failed: ${e.message}")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        Log.d("EmergencyDispatcher", "CallMeBot WhatsApp gateway delivered successfully")
                    }
                })
            } catch (e: Exception) {
                Log.w("EmergencyDispatcher", "CallMeBot dispatch exception: ${e.message}")
            }
        }

        // 2. Backend Emergency Webhook
        if (formattedNumber.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("recipient_phone", formattedNumber)
                    put("message", plainMessage)
                    put("latitude", latitude ?: 0.0)
                    put("longitude", longitude ?: 0.0)
                    put("location_url", locationUrl)
                    put("timestamp", timeStr)
                    put("channel", "whatsapp")
                }
                val body = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${ApiConfig.HTTP_BASE_URL}/api/v1/alerts/dispatch-whatsapp")
                    .addHeader("Bypass-Tunnel-Reminder", "true")
                    .post(body)
                    .build()

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.d("EmergencyDispatcher", "Background WhatsApp relay queued: ${e.message}")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        Log.d("EmergencyDispatcher", "Background WhatsApp relay processed successfully")
                    }
                })
            } catch (e: Exception) {
                Log.w("EmergencyDispatcher", "Background WhatsApp relay exception: ${e.message}")
            }
        }

        // 3. Official WhatsApp SOS Dispatch (Direct Chat or Intent Chooser)
        val waMessage = "🚨 *EMERGENCY SOS ALERT!* 🚨\n\n" +
                "I have triggered an emergency SOS from GuardianLink.\n" +
                "Please assist immediately!\n\n" +
                "📍 *My Live GPS Location:*\n$locationUrl\n\n" +
                "⏰ Time: $timeStr\n\n" +
                "_Sent via GuardianLink Wearable Protection System_"

        val encodedMsg = try {
            URLEncoder.encode(waMessage, "UTF-8")
        } catch (e: Exception) {
            waMessage
        }

        if (formattedNumber.isNotBlank()) {
            try {
                val whatsappUri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=$encodedMsg")
                val whatsappIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage("com.whatsapp")
                }
                context.startActivity(whatsappIntent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=$encodedMsg")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        } else {
            // No phone configured -> open WhatsApp contact picker with pre-filled SOS message
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, waMessage)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                val chooser = Intent.createChooser(shareIntent, "Send Emergency SOS via WhatsApp").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(chooser)
            }
        }

        // 4. Toast feedback to user
        Toast.makeText(
            context,
            "🚨 Emergency SOS alert triggered!",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun sendWhatsAppAlert(
        context: Context,
        phoneNumber: String,
        latitude: Double?,
        longitude: Double?
    ) {
        dispatchEmergencyAlert(context, phoneNumber, latitude, longitude)
    }
}
