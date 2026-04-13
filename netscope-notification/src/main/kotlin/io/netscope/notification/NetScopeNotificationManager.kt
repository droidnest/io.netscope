package io.netscope.notification

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.netscope.NetScope
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private var initialized = false

    /**
     * Starts observing the NetScope repository and posts/updates a notification
     * whenever the transaction list changes.
     *
     * @param context       Any context; the application context is used internally.
     * @param launchActivity Optional [Activity] class to open when the notification is tapped.
     *                       Pass `NetScopeActivity::class.java` from the `netscope-ui` artifact.
     */
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
     */
    fun init(context: Context, launchActivity: Class<out Activity>? = null) {
        check(NetScope.isInitialized()) {
            "NetScopeNotificationManager.init() must be called after NetScope.interceptor(context). " +
                    "Make sure your OkHttpClient (and its interceptor) is built before initialising the notification."
        }
        if (initialized) return
        initialized = true

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

        scope.launch {
            NetScope.repository.observeAll().collect { transactions ->
                post(appContext, transactions, tapPendingIntent)
            }
        }
    }

    /** Cancels the notification and stops future updates. */
    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        initialized = false
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

    private fun post(
        context: Context,
        transactions: List<NetTransaction>,
        tapIntent: PendingIntent?,
    ) {
        val total = transactions.size
        val errors = transactions.count {
            it.statusClass == NetTransaction.StatusClass.CLIENT_ERROR ||
                    it.statusClass == NetTransaction.StatusClass.SERVER_ERROR ||
                    it.statusClass == NetTransaction.StatusClass.ERROR
        }
        val lastCode = transactions.firstOrNull()?.responseCode

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
