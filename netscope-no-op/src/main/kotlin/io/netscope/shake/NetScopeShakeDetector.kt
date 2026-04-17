package io.netscope.shake

import android.content.Context

/** No-op stub — replace with the `netscope-core` artifact in debug builds. */
class NetScopeShakeDetector(
    context: Context,
    threshold: Float = 12f,
    onShake: () -> Unit,
) {
    fun start() = Unit
    fun stop() = Unit
}
