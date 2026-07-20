package com.smartxray.client.data.scanner

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScannerScoringTest {

    private fun calculateScore(
        stability: Int, // 0-100
        jitter: Long,
        latencyMs: Long,
        handshakeTimeMs: Long,
        downloadSpeed: Float // KB/s
    ): Int {
        // Stability Weight: 40%
        val stabilityScore = stability * 0.4f

        // Jitter Penalty: Up to 20%
        val jitterPenalty = when {
            jitter < 20 -> 0f
            jitter > 200 -> 20f
            else -> (jitter - 20f) / 180f * 20f
        }

        // Latency Weight: 30% (Normalized)
        val effectiveLatency = (latencyMs + handshakeTimeMs) / 2
        val latencyPoints = when {
            effectiveLatency < 150 -> 100f
            effectiveLatency > 800 -> 0f
            else -> (800f - effectiveLatency) / 650f * 100f
        }
        val latencyWeight = latencyPoints * 0.3f

        // Speed Weight: 30% (Normalized)
        // > 2MB/s = 100 points
        val speedPoints = (downloadSpeed / 20.48f).coerceAtMost(100f)
        val speedWeight = speedPoints * 0.3f

        return (stabilityScore - jitterPenalty + latencyWeight + speedWeight).toInt().coerceIn(0, 100)
    }

    @Test
    fun testPerfectIp() {
        val score = calculateScore(100, 5, 50, 50, 5000f)
        assertTrue("Perfect IP should have high score ($score)", score >= 90)
    }

    @Test
    fun testLowStabilityIp() {
        val goodScore = calculateScore(100, 5, 100, 100, 1000f)
        val lowStabilityScore = calculateScore(60, 5, 100, 100, 1000f)
        assertTrue("Low stability should significantly reduce score (Good: $goodScore, Low: $lowStabilityScore)", lowStabilityScore < goodScore - 15)
    }

    @Test
    fun testHighJitterIp() {
        val goodScore = calculateScore(100, 5, 100, 100, 1000f)
        val highJitterScore = calculateScore(100, 250, 100, 100, 1000f)
        assertTrue("High jitter should reduce score (Good: $goodScore, High Jitter: $highJitterScore)", highJitterScore < goodScore - 15)
    }

    @Test
    fun testDeadIp() {
        val score = calculateScore(0, 500, 5000, 5000, 0f)
        assertTrue("Dead IP should have 0 score ($score)", score == 0)
    }
}


