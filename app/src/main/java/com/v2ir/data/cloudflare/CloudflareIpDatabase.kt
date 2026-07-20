package com.v2ir.data.cloudflare

import java.math.BigInteger
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complete Cloudflare IPv4 and IPv6 CIDR range database.
 * Source: https://www.cloudflare.com/ips/
 */
@Singleton
class CloudflareIpDatabase @Inject constructor() {

    private data class IpRange(
        val start: BigInteger,
        val end: BigInteger,
        val isIpv6: Boolean
    )

    private val ipv4Ranges: List<IpRange> = IPV4_CIDRS.map { parseCidr(it, false) }
    private val ipv6Ranges: List<IpRange> = IPV6_CIDRS.map { parseCidr(it, true) }

    fun isCloudflareIp(address: String): Boolean {
        val clean = address.trim()
        if (clean.isEmpty()) return false
        return try {
            val ip = InetAddress.getByName(clean)
            val numeric = addressToBigInteger(ip.address)
            val ranges = if (ip.address.size == 4) ipv4Ranges else ipv6Ranges
            ranges.any { numeric in it.start..it.end }
        } catch (_: Exception) {
            false
        }
    }

    fun isCloudflareHost(host: String): Boolean {
        val trimmed = host.trim().lowercase()
        if (trimmed.isEmpty()) return false
        if (isCloudflareIp(trimmed)) return true
        return CLOUDFLARE_DOMAINS.any { domain ->
            trimmed == domain || trimmed.endsWith(".$domain")
        }
    }

    fun detectCloudflare(address: String, sni: String): Boolean =
        isCloudflareIp(address) || isCloudflareHost(sni)

    fun getCidrRanges(isIpv6: Boolean): List<String> {
        return if (isIpv6) IPV6_CIDRS else IPV4_CIDRS
    }

    private fun parseCidr(cidr: String, isIpv6: Boolean): IpRange {
        val parts = cidr.split("/")
        val base = InetAddress.getByName(parts[0])
        val prefix = parts.getOrNull(1)?.toInt() ?: if (isIpv6) 128 else 32
        val numeric = addressToBigInteger(base.address)
        val hostBits = base.address.size * 8 - prefix
        val mask = BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE)
        return IpRange(numeric, numeric.or(mask), isIpv6)
    }

    private fun addressToBigInteger(bytes: ByteArray): BigInteger {
        val unsigned = ByteArray(bytes.size + 1)
        System.arraycopy(bytes, 0, unsigned, 1, bytes.size)
        return BigInteger(unsigned)
    }

    companion object {
        private val CLOUDFLARE_DOMAINS = listOf(
            "cloudflare.com",
            "cloudflare.net",
            "cloudflare-dns.com",
            "cdn.cloudflare.net"
        )

        val IPV4_CIDRS = listOf(
            "173.245.48.0/20",
            "103.21.244.0/22",
            "103.22.200.0/22",
            "103.31.4.0/22",
            "141.101.64.0/18",
            "108.162.192.0/18",
            "190.93.240.0/20",
            "188.114.96.0/20",
            "197.234.240.0/22",
            "198.41.128.0/17",
            "162.158.0.0/15",
            "104.16.0.0/13",
            "104.24.0.0/14",
            "172.64.0.0/13",
            "131.0.72.0/22"
        )

        val IPV6_CIDRS = listOf(
            "2400:cb00::/32",
            "2606:4700::/32",
            "2803:f800::/32",
            "2405:b500::/32",
            "2405:8100::/32",
            "2a06:98c0::/29",
            "2c0f:f248::/32"
        )

        /** Popular clean Cloudflare edge IPs for scanning */
        val CLEAN_IP_CANDIDATES = listOf(
            "104.16.131.1",
            "104.16.132.1",
            "104.21.44.1",
            "104.21.45.1",
            "172.67.182.111",
            "172.67.183.111",
            "162.159.135.1",
            "162.159.136.1",
            "188.114.96.1",
            "188.114.97.1",
            "141.101.114.1",
            "141.101.115.1",
            "103.21.244.1",
            "103.22.200.1",
            "198.41.215.1",
            "198.41.216.1"
        )
    }
}




