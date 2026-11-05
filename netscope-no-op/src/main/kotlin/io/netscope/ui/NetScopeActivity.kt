package io.netscope.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * No-op stub matching the real [NetScopeActivity] in netscope-ui, so that release builds
 * compile code that passes `NetScopeActivity::class.java` to the notification manager or calls
 * [launch], and keep the `<activity>` entry apps declare for it in their manifest.
 *
 * [launch] does nothing. If the Activity is started anyway, it closes at once.
 */
class NetScopeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }

    companion object {
        fun launch(context: Context) = Unit

        fun launchIntent(context: Context): Intent = Intent(context, NetScopeActivity::class.java)

        fun launchPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context.applicationContext,
                0,
                launchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
