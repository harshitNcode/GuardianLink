package com.guardianlink.app.data

import android.os.Build

object ApiConfig {
    /**
     * Automatically detects whether the app is running in an Android Studio Emulator
     * or on a Physical Device.
     * - Android Emulator: Uses "10.0.2.2:8000" (special alias to access host PC's localhost:8000)
     * - Physical Device: Uses "127.0.0.1:8000" (works via USB with `adb reverse tcp:8000 tcp:8000`)
     */
    val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
    }

    var HOST = if (isEmulator) "10.0.2.2:8000" else "127.0.0.1:8000"
    var USE_SSL = false

    val HTTP_BASE_URL: String
        get() = (if (USE_SSL) "https://" else "http://") + HOST

    val WS_BASE_URL: String
        get() = (if (USE_SSL) "wss://" else "ws://") + HOST
}
