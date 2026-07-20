package com.v2ir.domain

import com.v2ir.data.model.Config
import com.v2ir.data.xray.V2irConstants
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayConfigBuilder @Inject constructor() {

    fun build(
        servers: List<Config>,
        bypassIran: Boolean = false,
        bypassLan: Boolean = true,
        dnsServer: String = "1.1.1.1",
        balancerStrategy: String = "leastPing",
        muxEnabled: Boolean = true,
        logLevel: String = "warning"
    ): String {
        val filteredServers = servers.filter { it.address.isNotBlank() && it.userId.isNotBlank() }
        val balancerServers = filteredServers.take(10)
        val isBalancer = balancerServers.size > 1
        
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", logLevel))

        // Stats & API
        root.put("api", JSONObject().put("tag", "api").put("services", JSONArray().put("StatsService")))
        root.put("stats", JSONObject())
        root.put("policy", JSONObject().put("system", JSONObject()
            .put("statsInboundUplink", true)
            .put("statsInboundDownlink", true)
            .put("statsOutboundUplink", true)
            .put("statsOutboundDownlink", true)))

        // FakeDNS
        root.put("fakedns", JSONArray().put(JSONObject().put("ipPool", "198.18.0.0/15").put("poolSize", 65535)))

        // Observatory
        if (isBalancer) {
            val observatory = JSONObject()
            val subjectSelector = JSONArray()
            balancerServers.indices.forEach { i -> subjectSelector.put("proxy-$i") }
            observatory.put("subjectSelector", subjectSelector)
            // Use Cloudflare for better reliability in Iran
            observatory.put("probeURL", "http://cp.cloudflare.com/generate_204")
            observatory.put("probeInterval", V2irConstants.OBSERVATORY_PROBE_INTERVAL)
            root.put("observatory", observatory)
        }

        val inbounds = JSONArray()
        inbounds.put(JSONObject()
            .put("tag", "socks-in")
            .put("port", V2irConstants.SOCKS_PORT)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic").put("fakedns"))
                .put("routeOnly", false))
        )
        inbounds.put(JSONObject()
            .put("tag", "api")
            .put("port", V2irConstants.API_PORT)
            .put("listen", "127.0.0.1")
            .put("protocol", "dokodemo-door")
            .put("settings", JSONObject().put("address", "127.0.0.1"))
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        if (balancerServers.isNotEmpty()) {
            balancerServers.forEachIndexed { index, config ->
                val tag = if (isBalancer) "proxy-$index" else "proxy"
                outbounds.put(buildOutbound(config, tag, muxEnabled))
            }
        } else {
            outbounds.put(JSONObject().put("tag", "proxy").put("protocol", "freedom"))
        }

        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject().put("domainStrategy", "UseIP")))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
        root.put("outbounds", outbounds)

        // DNS
        val dnsObj = JSONObject()
        val dnsServers = JSONArray()
        dnsServers.put("fakedns")
        if (dnsServer.isNotBlank()) dnsServers.put(dnsServer)
        dnsServers.put(JSONObject().put("address", "https://1.1.1.1/dns-query").put("skipFallback", true))
        dnsServers.put("8.8.8.8")
        dnsServers.put("1.1.1.1")
        dnsObj.put("servers", dnsServers)
        dnsObj.put("tag", "dns-in")
        dnsObj.put("queryStrategy", "UseIPv4")
        root.put("dns", dnsObj)

        // Routing
        val routing = JSONObject()
        routing.put("domainStrategy", "IPIfNonMatch") 
        val rules = JSONArray()

        rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("socks-in")).put("port", 53).put("outboundTag", "dns-out"))
        rules.put(JSONObject().put("type", "field").put("outboundTag", "dns-out").put("protocol", JSONArray().put("dns")))

        if (bypassLan) rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:private")).put("outboundTag", "direct"))
        if (bypassIran) {
            rules.put(JSONObject().put("type", "field").put("domain", JSONArray().put("domain:.ir").put("domain:gov.ir")).put("outboundTag", "direct"))
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:ir")).put("outboundTag", "direct"))
        }

        // Fix: Use mainProxyTag (balancer or proxy) for FakeDNS and default traffic
        if (isBalancer) {
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("198.18.0.0/15")).put("balancerTag", "balancer"))
        } else {
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("198.18.0.0/15")).put("outboundTag", "proxy"))
        }

        if (isBalancer) {
            val balancers = JSONArray()
            val selector = JSONArray()
            balancerServers.indices.forEach { i -> selector.put("proxy-$i") }
            balancers.put(JSONObject().put("tag", "balancer").put("selector", selector).put("strategy", JSONObject().put("type", balancerStrategy)))
            routing.put("balancers", balancers)
            rules.put(JSONObject().put("type", "field").put("network", "tcp,udp").put("balancerTag", "balancer"))
        } else {
            rules.put(JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "proxy"))
        }
        
        routing.put("rules", rules)
        root.put("routing", routing)

        return root.toString(2)
    }

    private fun buildOutbound(config: Config, tag: String, muxEnabled: Boolean): JSONObject {
        val outbound = JSONObject().put("tag", tag)
        // Normalize keys to lowercase for robust Case-Insensitive matching (Hiddify/v2rayNG compatibility)
        val params = config.extraParams.mapKeys { it.key.lowercase() }.toMutableMap()
        
        // Merge query params from rawUri if not already present in extraParams
        if (config.rawUri.contains('?')) {
            val query = config.rawUri.substringAfter('?', "").substringBefore('#')
            if (query.isNotBlank()) {
                query.split('&').forEach { part ->
                    val kv = part.split('=', limit = 2)
                    if (kv.size == 2) {
                        val key = kv[0].lowercase()
                        if (!params.containsKey(key)) {
                            params[key] = decodeURIComponent(kv[1])
                        }
                    }
                }
            }
        }
        
        val flow = params["flow"] ?: ""
        val isVision = flow.contains("vision")
        
        if (muxEnabled && !isVision && config.type != com.v2ir.data.model.ConfigType.HYSTERIA2 && config.type != com.v2ir.data.model.ConfigType.TUIC) {
            outbound.put("mux", JSONObject().put("enabled", true).put("concurrency", 8))
        } else {
            outbound.put("mux", JSONObject().put("enabled", false))
        }

        outbound.put("protocol", when (config.type) {
            com.v2ir.data.model.ConfigType.VMESS -> "vmess"
            com.v2ir.data.model.ConfigType.VLESS -> "vless"
            com.v2ir.data.model.ConfigType.TROJAN -> "trojan"
            com.v2ir.data.model.ConfigType.SHADOWSOCKS -> "shadowsocks"
            com.v2ir.data.model.ConfigType.HYSTERIA2 -> "hysteria2"
            com.v2ir.data.model.ConfigType.TUIC -> "tuic"
            else -> "vless"
        })

        val settings = JSONObject()
        when (config.type) {
            com.v2ir.data.model.ConfigType.VLESS, com.v2ir.data.model.ConfigType.VMESS -> {
                val user = JSONObject().put("id", config.userId)
                if (config.type == com.v2ir.data.model.ConfigType.VLESS) {
                    val enc = params["encryption"] ?: "none"
                    user.put("encryption", if (enc == "zero") "none" else enc)
                    if (flow.isNotBlank()) user.put("flow", flow)
                } else {
                    user.put("security", params["scy"] ?: "auto")
                }
                // IP endpoint stays in address
                val server = JSONObject().put("address", config.address).put("port", config.port).put("users", JSONArray().put(user))
                settings.put("vnext", JSONArray().put(server))
            }
            com.v2ir.data.model.ConfigType.TROJAN -> {
                settings.put("servers", JSONArray().put(JSONObject().put("address", config.address).put("port", config.port).put("password", config.userId)))
            }
            com.v2ir.data.model.ConfigType.SHADOWSOCKS -> {
                val server = JSONObject().put("address", config.address).put("port", config.port)
                val method = params["method"] ?: if (config.userId.contains(":")) config.userId.substringBefore(":") else "aes-256-gcm"
                val password = params["password"] ?: if (config.userId.contains(":")) config.userId.substringAfter(":") else config.userId
                server.put("method", method).put("password", password)
                settings.put("servers", JSONArray().put(server))
            }
            com.v2ir.data.model.ConfigType.HYSTERIA2 -> {
                settings.put("server", config.address).put("port", config.port).put("auth", config.userId)
                params["obfs"]?.let { if (it.isNotBlank() && it != "none") settings.put("obfs", JSONObject().put("type", it).put("password", params["obfs-password"] ?: "")) }
            }
            com.v2ir.data.model.ConfigType.TUIC -> {
                val server = JSONObject().put("address", config.address).put("port", config.port).put("uuid", config.userId).put("password", params["pass"] ?: "")
                server.put("congestion_control", params["congestion_control"] ?: "bbr")
                server.put("udp_relay_mode", params["udp_relay_mode"] ?: "native")
                settings.put("servers", JSONArray().put(server))
            }
            else -> {}
        }
        outbound.put("settings", settings)

        val stream = JSONObject()
        val transport = params["type"] ?: params["net"] ?: "tcp"
        
        if (config.type == com.v2ir.data.model.ConfigType.HYSTERIA2 || config.type == com.v2ir.data.model.ConfigType.TUIC) {
            stream.put("network", "udp")
        } else {
            stream.put("network", if (transport == "grpc") "grpc" else if (transport == "h2" || transport == "http") "http" else if (transport == "httpUpgrade") "httpupgrade" else transport)
        }
        
        val security = params["security"] ?: if (config.port == 443) "tls" else "none"
        stream.put("security", security)

        // CDN Fronting Coordination Logic
        val sni = config.sni.ifBlank { params["sni"] ?: params["host"] ?: "" }

        if (security == "tls") {
            val tlsSettings = JSONObject()
            // Robust boolean mapping for allowInsecure (handles 0/1/true/false Case-Insensitive)
            val allowInsecureStr = params["allowinsecure"] ?: params["insecure"] ?: "false"
            tlsSettings.put("allowInsecure", toBooleanCompat(allowInsecureStr))
            
            // Critical: Always send SNI domain for TLS handshake even if address is IP
            if (sni.isNotBlank()) tlsSettings.put("serverName", sni)
            
            val customAlpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            val alpnArray = JSONArray()
            if (!customAlpn.isNullOrEmpty()) {
                customAlpn.forEach { alpnArray.put(it) }
                if (customAlpn.contains("h2") && !customAlpn.contains("http/1.1") && config.type != com.v2ir.data.model.ConfigType.TUIC) {
                    alpnArray.put("http/1.1")
                }
            } else {
                if (config.type == com.v2ir.data.model.ConfigType.TUIC) {
                    alpnArray.put("h3")
                } else {
                    alpnArray.put("h2")
                    alpnArray.put("http/1.1")
                }
            }
            tlsSettings.put("alpn", alpnArray)

            val fp = params["fp"] ?: "chrome"
            if (fp.isNotBlank() && fp != "none") {
                tlsSettings.put("fingerprint", fp)
            }

            stream.put("tlsSettings", tlsSettings)
        } else if (security == "reality") {
            stream.put("realitySettings", JSONObject()
                .put("show", false)
                .put("fingerprint", params["fp"] ?: "chrome")
                .put("serverName", sni)
                .put("publicKey", params["pbk"] ?: "")
                .put("shortId", params["sid"] ?: "")
                .put("spiderX", params["spx"] ?: ""))
        }

        when (transport) {
            "ws" -> {
                val rawPath = params["path"] ?: "/"
                val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
                val ws = JSONObject().put("path", path)
                // CDN Fronting: Sync Host header with SNI for proper CDN routing
                val host = params["host"] ?: params["sni"] ?: sni
                if (host.isNotBlank()) ws.put("headers", JSONObject().put("Host", host))
                stream.put("wsSettings", ws)
            }
            "h2", "http" -> {
                val h2 = JSONObject()
                val host = params["host"] ?: params["sni"] ?: sni
                if (host.isNotBlank()) h2.put("host", JSONArray().put(host))
                val rawPath = params["path"] ?: "/"
                val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
                h2.put("path", path)
                stream.put("httpSettings", h2)
            }
            "httpUpgrade" -> {
                val rawPath = params["path"] ?: "/"
                val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
                val up = JSONObject().put("path", path)
                val host = params["host"] ?: params["sni"] ?: sni
                if (host.isNotBlank()) up.put("host", host)
                stream.put("httpUpgradeSettings", up)
            }
            "grpc" -> {
                stream.put("grpcSettings", JSONObject()
                    .put("serviceName", params["serviceName"] ?: params["path"] ?: "")
                    .put("multiMode", toBooleanCompat(params["mode"] ?: "false")))
            }
        }

        outbound.put("streamSettings", stream)
        return outbound
    }

    private fun decodeURIComponent(value: String): String =
        try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }


    private fun toBooleanCompat(value: String): Boolean {
        return value == "true" || value == "1"
    }

    // FIX (Bug #25): Removed dead parseQueryParams() method.
    // It was defined but never called — query param parsing is done inline in buildOutbound().
    // Keeping unused private methods adds confusion and maintenance burden.
}




