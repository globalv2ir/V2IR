package com.smartxray.client.data.remote

import com.smartxray.client.data.cloudflare.CloudflareIpDatabase
import com.smartxray.client.data.model.ConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import kotlinx.coroutines.runBlocking

class ConfigUriParserTest {

    private lateinit var ipLocationService: IpLocationService
    private lateinit var parser: ConfigUriParser

    @Before
    fun setup() {
        ipLocationService = mock()
        parser = ConfigUriParser(CloudflareIpDatabase(), ipLocationService)
    }

    @Test
    fun testParseVlessRobustness() = runBlocking {
        val uri = "vless://00000000-0000-0000-0000-000000000000@1.2.3.4:443?encryption=none&security=reality&sni=example.com&fp=chrome&pbk=pubkey&sid=shortid#MyConfig"
        val config = parser.parseSingle(uri)
        assertNotNull(config)
        assertEquals("MyConfig", config?.name)
        assertEquals("1.2.3.4", config?.address)
        assertEquals(443, config?.port)
        assertEquals(ConfigType.VLESS, config?.type)
        assertEquals("example.com", config?.sni)
    }

    @Test
    fun testParseVlessInvalidUuid() = runBlocking {
        val uri = "vless://invalid-uuid@1.2.3.4:443#BadConfig"
        val config = parser.parseSingle(uri)
        assertNull(config)
    }

    @Test
    fun testParseHysteria2() = runBlocking {
        val uri = "hysteria2://auth-pass@1.2.3.4:1234?sni=hy2.com&obfs=none#Hysteria2Test"
        val config = parser.parseSingle(uri)
        assertNotNull(config)
        assertEquals("Hysteria2Test", config?.name)
        assertEquals(ConfigType.HYSTERIA2, config?.type)
        assertEquals("auth-pass", config?.userId)
        assertEquals("hy2.com", config?.sni)
        assertEquals("none", config?.extraParams?.get("obfs"))
    }

    @Test
    fun testParseTuic() = runBlocking {
        val uri = "tuic://00000000-0000-0000-0000-000000000000:pass123@1.2.3.4:5678?congestion_control=bbr#TuicTest"
        val config = parser.parseSingle(uri)
        assertNotNull(config)
        assertEquals("TuicTest", config?.name)
        assertEquals(ConfigType.TUIC, config?.type)
        assertEquals("00000000-0000-0000-0000-000000000000", config?.userId)
        assertEquals("pass123", config?.extraParams?.get("pass"))
        assertEquals("bbr", config?.extraParams?.get("congestion_control"))
    }
}


