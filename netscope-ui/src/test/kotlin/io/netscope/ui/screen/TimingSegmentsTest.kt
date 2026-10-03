package io.netscope.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingSegmentsTest {

    @Test
    fun `TLS is not double counted inside connect`() {
        val result = timingBreakdown(dnsMs = 10, connectMs = 120, sslMs = 80, ttfbMs = 50)

        assertEquals(180L, result.totalMs)
        assertEquals(
            listOf(
                PhaseSegment(TimingPhase.DNS, 10),
                PhaseSegment(TimingPhase.TCP, 40),
                PhaseSegment(TimingPhase.TLS, 80),
                PhaseSegment(TimingPhase.TTFB, 50),
            ),
            result.phases,
        )
    }

    @Test
    fun `zero duration phase is listed but excluded from bar segments`() {
        val result = timingBreakdown(dnsMs = 0, connectMs = 30, sslMs = null, ttfbMs = 20)

        assertEquals(listOf(TimingPhase.DNS, TimingPhase.TCP, TimingPhase.TTFB), result.phases.map { it.phase })
        assertEquals(listOf(TimingPhase.TCP, TimingPhase.TTFB), result.barSegments.map { it.phase })
        assertEquals(50L, result.totalMs)
    }

    @Test
    fun `negative durations are coerced to zero`() {
        val result = timingBreakdown(dnsMs = -5, connectMs = 40, sslMs = -3, ttfbMs = -100)

        assertEquals(listOf(0L, 40L, 0L, 0L), result.phases.map { it.durationMs })
        assertEquals(listOf(TimingPhase.TCP), result.barSegments.map { it.phase })
        assertEquals(40L, result.totalMs)
        assertTrue(result.barSegments.all { it.durationMs > 0 })
    }

    @Test
    fun `TLS longer than connect yields zero TCP instead of negative`() {
        val result = timingBreakdown(dnsMs = null, connectMs = 50, sslMs = 70, ttfbMs = null)

        assertEquals(0L, result.phases.first { it.phase == TimingPhase.TCP }.durationMs)
        assertEquals(70L, result.totalMs)
    }

    @Test
    fun `all zero phases produce no bar segments`() {
        val result = timingBreakdown(dnsMs = 0, connectMs = 0, sslMs = 0, ttfbMs = 0)

        assertEquals(4, result.phases.size)
        assertTrue(result.barSegments.isEmpty())
        assertEquals(0L, result.totalMs)
    }

    @Test
    fun `no captured phases produce an empty breakdown`() {
        val result = timingBreakdown(dnsMs = null, connectMs = null, sslMs = null, ttfbMs = null)

        assertTrue(result.phases.isEmpty())
        assertTrue(result.barSegments.isEmpty())
        assertEquals(0L, result.totalMs)
    }
}
