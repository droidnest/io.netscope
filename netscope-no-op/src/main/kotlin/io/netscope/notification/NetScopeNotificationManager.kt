package io.netscope.notification

import android.app.Activity
import android.content.Context

/**
 * Release no-op implementation of [NetScopeNotificationManager].
 *
 * All calls are empty stubs. No notification is ever posted.
 * This artifact has no transitive dependencies and adds ~0KB to your APK.
 *
 * Usage in build.gradle:
 * ```
 * debugImplementation("io.netscope:netscope-notification:0.2.0")
 * releaseImplementation("io.netscope:netscope-no-op:0.2.0")
 * ```
 */
object NetScopeNotificationManager {
    fun init(context: Context, launchActivity: Class<out Activity>? = null) = Unit
    fun dismiss(context: Context) = Unit
}
