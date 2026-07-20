package com.smartxray.client.domain

import com.smartxray.client.data.model.Config
import com.smartxray.client.data.model.ConfigType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XrayConfigBuilderTest {

    private lateinit var builder: XrayConfigBuilder

    @Before
    fun setup() {
        builder = XrayConfigBuilder()
    }

    @Test
    fun testBuildSingleConfig() {
        val config = Config(
            id = 1,
            name = "Test",
            address = "1.2.3.4",
            port = 443,
            userId = "00000000-0000-0000-0000-000000000000",
            type = ConfigType.VLESS,
            rawUri = "vless://00000000-0000-0000-0000-000000000000@1.2.3.4:443?security=tls#Test"
        )
        val jsonStr = builder.build(listOf(config))
        val root = JSONObject(jsonStr)
        
        // Basic checks
        assertTrue(root.has("outbounds"))
        val outbounds = root.getJSONArray("outbounds")
        assertEquals(4, outbounds.length()) // proxy, direct, block, dns-out
        
        // No observatory for single config
        assertTrue(!root.has("observatory"))
        
        val routing = root.getJSONObject("routing")
        assertTrue(!routing.has("balancers"))
    }

    @Test
    fun testBuildMultiConfigWithBalancer() {
        val configs = listOf(
            Config(id = 1, name = "S1", address = "1.1.1.1", port = 443, userId = "u1", type = ConfigType.VLESS, rawUri = "vless://u1@1.1.1.1:443#S1"),
            Config(id = 2, name = "S2", address = "2.2.2.2", port = 443, userId = "u2", type = ConfigType.VLESS, rawUri = "vless://u2@2.2.2.2:443#S2")
        )
        val jsonStr = builder.build(configs, balancerStrategy = "random")
        val root = JSONObject(jsonStr)
        
        // Observatory check
        assertTrue(root.has("observatory"))
        val observatory = root.getJSONObject("observatory")
        val selectors = observatory.getJSONArray("subjectSelector")
        assertEquals(2, selectors.length())
        assertEquals("proxy-0", selectors.getString(0))
        assertEquals("proxy-1", selectors.getString(1))
        
        // Routing check
        val routing = root.getJSONObject("routing")
        assertTrue(routing.has("balancers"))
        val balancers = routing.getJSONArray("balancers")
        val balancer = balancers.getJSONObject(0)
        assertEquals("balancer", balancer.getString("tag"))
        assertEquals("random", balancer.getJSONObject("strategy").getString("type"))
    }

    @Test
    fun testBuildWithUTLSAndH2() {
        val config = Config(
            id = 1,
            name = "H2-Test",
            address = "my-cdn.com",
            port = 443,
            userId = "u1",
            type = ConfigType.VLESS,
            rawUri = "vless://u1@my-cdn.com:443?security=tls&fp=firefox&type=h2&path=/v2#H2-Test"
        )
        val jsonStr = builder.build(listOf(config))
        val root = JSONObject(jsonStr)
        val stream = root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        
        assertEquals("http", stream.getString("network"))
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("firefox", tls.getString("fingerprint"))
        
        val http = stream.getJSONObject("httpSettings")
        assertEquals("/v2", http.getString("path"))
        assertEquals("my-cdn.com", http.getJSONArray("host").getString(0))
    }

    @Test
    fun testBuildWithHttpUpgrade() {
        val config = Config(
            id = 2,
            name = "UP-Test",
            address = "1.1.1.1",
            port = 443,
            userId = "u1",
            type = ConfigType.VLESS,
            sni = "sni.com",
            rawUri = "vless://u1@1.1.1.1:443?security=tls&type=httpUpgrade&path=/up#UP-Test"
        )
        val jsonStr = builder.build(listOf(config))
        val root = JSONObject(jsonStr)
        val stream = root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        
        assertEquals("httpupgrade", stream.getString("network"))
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("sni.com", tls.getString("serverName"))
        
        val up = stream.getJSONObject("httpUpgradeSettings")
        assertEquals("/up", up.getString("path"))
        assertEquals("sni.com", up.getString("host"))
    }

    @Test
    fun testAdvancedVlessWSWithCustomAlpnAndFingerprint() {
        val config = Config(
            id = 10,
            name = "Adv-Vless",
            address = "1.2.3.4",
            port = 443,
            userId = "00000000-0000-0000-0000-000000000000",
            type = ConfigType.VLESS,
            rawUri = "vless://00000000-0000-0000-0000-000000000000@1.2.3.4:443?security=tls&type=ws&sni=mysni.com&host=myhost.com&path=mypath&alpn=h2&fp=firefox#Adv-Vless"
        )
        val jsonStr = builder.build(listOf(config))
        val root = JSONObject(jsonStr)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val stream = outbound.getJSONObject("streamSettings")
        
        // Check TLS Settings
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("mysni.com", tls.getString("serverName"))
        assertEquals("firefox", tls.getString("fingerprint"))
        
        val alpn = tls.getJSONArray("alpn")
        assertEquals("h2", alpn.getString(0))
        assertEquals("http/1.1", alpn.getString(1)) // Auto-fallback added!
        
        // Check WS Settings
        val ws = stream.getJSONObject("wsSettings")
        assertEquals("/mypath", ws.getString("path")) // Path normalization
        assertEquals("myhost.com", ws.getJSONObject("headers").getString("Host"))
    }
}


