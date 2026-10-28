package com.guardianlink.app.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApiConfig {
    /**
     * Default server host (Configurable at runtime in Settings).
     * Connects directly to the backend over LAN or tunnel.
     */
    var HOST = "guardianlink-api-itkx.onrender.com"

    /**
     * Automatically use HTTPS/WSS for domain names (e.g. ngrok, localtunnel, onrender.com),
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

    /**
     * Smart DNS resolver with automatic fallback to Render/Cloudflare Anycast Ingress IPs
     * if system DNS resolution fails (e.g. on institutional/captive Wi-Fi with blocked DNS TLS).
     */
    val smartDns: Dns = object : Dns {
        private val RENDER_HOST = "guardianlink-api-itkx.onrender.com"
        private val FALLBACK_IPS by lazy {
            listOf(
                InetAddress.getByAddress(RENDER_HOST, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())),
                InetAddress.getByAddress(RENDER_HOST, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 19.toByte())),
                InetAddress.getByAddress(RENDER_HOST, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 3.toByte())),
                InetAddress.getByAddress(RENDER_HOST, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 253.toByte()))
            )
        }

        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                if (hostname.equals(RENDER_HOST, ignoreCase = true) || hostname.endsWith(".onrender.com", ignoreCase = true)) {
                    android.util.Log.i("GuardianLink.Dns", "System DNS failed for $hostname, using fallback Anycast Ingress IPs")
                    FALLBACK_IPS
                } else {
                    throw e
                }
            }
        }
    }

    val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .dns(smartDns)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .build()
            chain.proceed(request)
        }
        .build()

    val webSocketClient: OkHttpClient = OkHttpClient.Builder()
        .dns(smartDns)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Persistent WebSocket
        .pingInterval(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .build()
            chain.proceed(request)
        }
        .build()
}
