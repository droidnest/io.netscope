package io.netscope.notification

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.netscope.NetScope
import io.netscope.NotificationStyle
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Shows and maintains a persistent, silent notification that displays live
 * request stats while the app is running.
 *
 * Usage (after [NetScope.interceptor]):
 * ```kotlin
 * NetScopeNotificationManager.init(context, launchActivity = NetScopeActivity::class.java)
 * ```
 *
 * Call from your `Application.onCreate` or wherever you configure OkHttp.
 * On Android 13+ the system silently ignores the notification if the user has
 * not granted POST_NOTIFICATIONS; no crash occurs.
 */
object NetScopeNotificationManager {

    private const val CHANNEL_ID = "netscope_traffic"
    private const val NOTIFICATION_ID = 0x4E53 // 'N','S'

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Active collector; non-null while the notification is live. Guarded by `this`. */
    private var collectJob: Job? = null

    /**
     * Starts observing the NetScope repository and posts/updates a notification
     * whenever the transaction list changes.
     *
     * **Must be called after [NetScope.interceptor]** so that the repository is
     * already initialised. Throws [IllegalStateException] immediately (on the
     * calling thread) if that contract is violated.
     *
     * @param context       Any context; the application context is used internally.
     * @param launchActivity Optional [Activity] class to open when the notification
     *                       is tapped (e.g. `NetScopeActivity::class.java`).
     *
     * Idempotent and thread-safe: repeated calls while active are ignored.
     * After [dismiss], calling it again restarts updates.
     */
    @Synchronized
    fun init(context: Context, launchActivity: Class<out Activity>? = null) {
        check(NetScope.isInitialized()) {
            "NetScopeNotificationManager.init() must be called after NetScope.interceptor(context). " +
                    "Make sure your OkHttpClient (and its interceptor) is built before initialising the notification."
        }
        if (collectJob?.isActive == true) return

        val appContext = context.applicationContext
        createChannel(appContext)

        val tapPendingIntent = launchActivity?.let { cls ->
            val intent = Intent(appContext, cls).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        collectJob = scope.launch {
            NetScope.repository.observeSummaries()
                .map { it.toStats() }
                // The list re-emits on every row write (insert, response, timing) — only
                // re-post when what the notification shows actually changed.
                .distinctUntilChanged()
                .collect { stats -> post(appContext, stats, tapPendingIntent) }
        }
    }

    /** Stops future updates and cancels the notification. */
    @Synchronized
    fun dismiss(context: Context) {
        collectJob?.cancel()
        collectJob = null
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private fun createChannel(context: Context) {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("NetScope")
            .setDescription("Live HTTP traffic monitor")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** The values the notification renders; compared to skip identical re-posts. */
    private data class Stats(val total: Int, val errors: Int, val lastCode: Int?)

    private fun List<NetTransaction>.toStats() = Stats(
        total = size,
        errors = count {
            it.statusClass == NetTransaction.StatusClass.CLIENT_ERROR ||
                    it.statusClass == NetTransaction.StatusClass.SERVER_ERROR ||
                    it.statusClass == NetTransaction.StatusClass.ERROR
        },
        lastCode = firstOrNull()?.responseCode,
    )

    private fun post(context: Context, stats: Stats, tapIntent: PendingIntent?) {
        // Respect the user's notification style preference.
        when (NetScope.notificationStyle) {
            NotificationStyle.NONE -> {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                return
            }
            NotificationStyle.MINIMAL -> postMinimal(context, stats, tapIntent)
            NotificationStyle.DETAILED -> postDetailed(context, stats, tapIntent)
        }
    }

    private fun postMinimal(context: Context, stats: Stats, tapIntent: PendingIntent?) {
        val total = stats.total
        val title = "$total request${if (total != 1) "s" else ""} captured"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_netscope_notif)
            .setContentTitle(title)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply { tapIntent?.let { setContentIntent(it) } }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun postDetailed(context: Context, stats: Stats, tapIntent: PendingIntent?) {
        val (total, errors, lastCode) = stats

        val title = "$total request${if (total != 1) "s" else ""} captured"
        val text = buildString {
            lastCode?.let { append("Last: $it") }
            if (errors > 0) {
                if (lastCode != null) append("  ·  ")
                append("$errors error${if (errors != 1) "s" else ""}")
            }
            if (isEmpty()) append("Waiting for requests…")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_netscope_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply { tapIntent?.let { setContentIntent(it) } }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
