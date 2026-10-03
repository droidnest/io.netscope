package io.netscope.ui.screen

/** Non-overlapping network phases shown on the Timing tab, in chronological order. */
internal enum class TimingPhase(val label: String) {
    DNS("DNS lookup"),
    TCP("TCP connect"),
    TLS("TLS handshake"),
    TTFB("TTFB"),
}

internal data class PhaseSegment(val phase: TimingPhase, val durationMs: Long)

/**
 * @property phases      Every captured phase (0 ms included), for the textual list.
 * @property barSegments Phases with a strictly positive duration — safe to use as `RowScope.weight`.
 * @property totalMs     Sum of the non-overlapping phases.
 */
internal data class TimingBreakdown(
    val phases: List<PhaseSegment>,
    val barSegments: List<PhaseSegment>,
    val totalMs: Long,
)

/**
 * Splits OkHttp event timings into non-overlapping phases.
 *
 * OkHttp emits `secureConnectStart/End` inside `connectStart → connectEnd`, so the TLS handshake is
 * already part of [connectMs]; TCP is reported as `connect - tls` to avoid counting TLS twice.
 * Negative durations (wall-clock steps) are coerced to 0.
 */
internal fun timingBreakdown(
    dnsMs: Long?,
    connectMs: Long?,
    sslMs: Long?,
    ttfbMs: Long?,
): TimingBreakdown {
    val tls = sslMs?.coerceAtLeast(0L)
    val phases = buildList {
        dnsMs?.let { add(PhaseSegment(TimingPhase.DNS, it.coerceAtLeast(0L))) }
        connectMs?.let { add(PhaseSegment(TimingPhase.TCP, (it - (tls ?: 0L)).coerceAtLeast(0L))) }
        tls?.let { add(PhaseSegment(TimingPhase.TLS, it)) }
        ttfbMs?.let { add(PhaseSegment(TimingPhase.TTFB, it.coerceAtLeast(0L))) }
    }
    return TimingBreakdown(
        phases = phases,
        barSegments = phases.filter { it.durationMs > 0L },
        totalMs = phases.sumOf { it.durationMs },
    )
}
