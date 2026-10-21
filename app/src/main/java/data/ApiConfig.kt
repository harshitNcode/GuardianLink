package com.guardianlink.app.data

object ApiConfig {
    /**
     * Remote Localtunnel public host URL.
     * Accessible over the internet from anywhere (mobile data, separate Wi-Fi, etc.)
     */
    var HOST = "guardianlink-harshit.loca.lt"
    var USE_SSL = true

    val HTTP_BASE_URL: String
        get() = (if (USE_SSL) "https://" else "http://") + HOST

    val WS_BASE_URL: String
        get() = (if (USE_SSL) "wss://" else "ws://") + HOST
}
