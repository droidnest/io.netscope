package io.netscope.notification

import android.app.Activity
import android.content.Context

/**
 * Release no-op implementation of [NetScopeNotificationManager].
 *
 * All calls are empty stubs. No notification is ever posted.
 * Signatures match the real `netscope-notification` artifact.
 *
 * Usage in build.gradle:
 * ```
 * debugImplementation("io.github.droidnest:netscope-notification:<version>")
 * releaseImplementation("io.github.droidnest:netscope-no-op:<version>")
 * ```
 */
object NetScopeNotificationManager {
    fun init(context: Context, launchActivity: Class<out Activity>? = null) = Unit
    fun dismiss(context: Context) = Unit
}
