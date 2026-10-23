package com.guardianlink.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import java.net.URLEncoder

object EmergencyDispatcher {

    fun dispatchEmergencyAlert(
        context: Context,
        phoneNumber: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val rawDigits = phoneNumber.filter { it.isDigit() }
        if (rawDigits.isEmpty()) {
            Toast.makeText(context, "No Emergency Contact configured. Please set one in Contacts.", Toast.LENGTH_LONG).show()
            return
        }

        val formattedNumber = if (rawDigits.length == 10) "91$rawDigits" else rawDigits

        val locationUrl = if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "GPS tracker active (Awaiting satellite lock)"
        }

        val timeStr = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.getDefault()).format(java.util.Date())
        val plainMessage = "EMERGENCY SOS ALERT! I need urgent help! My current GPS location: $locationUrl (Triggered at $timeStr)"

        // 1. Attempt Automatic Background SMS dispatch
        try {
            val smsManager = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(plainMessage)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, plainMessage, null, null)
            }
            Toast.makeText(context, "Emergency SOS alert dispatched!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.w("EmergencyDispatcher", "Direct SMS dispatch exception: ${e.message}")
        }

        // 2. Launch WhatsApp with pre-filled message
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

        try {
            val whatsappUri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=$encodedMsg")
            val whatsappIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            try {
                val smsUri = Uri.parse("smsto:$formattedNumber")
                val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                    putExtra("sms_body", plainMessage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
            } catch (e2: Exception) {
                // Ignore
            }
        }
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
