package com.example.ytpost

import java.net.InetSocketAddress
import java.net.Socket

object ProxyManager {
    private val PROXY_PORTS = listOf(
        Pair(10808, "socks5h"), // v2rayNG SOCKS
        Pair(1080, "socks5h"),  // Shadowsocks / General
        Pair(7890, "http"),     // Clash / ClashMeta
        Pair(7891, "http"),     // Clash / ClashMeta
        Pair(10809, "http"),    // v2rayNG HTTP
        Pair(2080, "socks5h"),  // Other
        Pair(8080, "http")      // Other
    )

    fun detectProxy(): String? {
        for ((port, type) in PROXY_PORTS) {
            if (isPortOpen("127.0.0.1", port)) {
                val proxyUrl = "$type://127.0.0.1:$port"
                return proxyUrl
            }
        }
        return null
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 400)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun testAllProxies(): String {
        val results = mutableListOf<String>()
        for ((port, type) in PROXY_PORTS) {
            val status = if (isPortOpen("127.0.0.1", port)) "OPEN" else "CLOSED"
            results.add("$type://127.0.0.1:$port -> $status")
        }
        return results.joinToString("\n")
    }
}
