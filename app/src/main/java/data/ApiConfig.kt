package com.guardianlink.app.data

object ApiConfig {
    /**
     * Default server host (Configurable at runtime in Settings).
     * Connects directly to the backend over LAN or tunnel.
     */
    var HOST = "guardianlink-api-itkx.onrender.com"

    /**
     * Automatically use HTTPS/WSS for domain names (e.g. ngrok, localtunnel),
     * and HTTP/WS for local IP addresses and dev ports.
     */
    val USE_SSL: Boolean
        get() {
            val h = HOST.lowercase()
            return !(h.startsWith("192.") || h.startsWith("172.") || h.startsWith("10.") ||
                    h.startsWith("127.") || h.startsWith("localhost") ||
                    h.contains(":8000") || h.contains(":8080") || h.contains(":3000"))
        }

    val HTTP_BASE_URL: String

        get() = (if (USE_SSL) "https://" else "http://") + HOST

    val WS_BASE_URL: String
        get() = (if (USE_SSL) "wss://" else "ws://") + HOST
}
