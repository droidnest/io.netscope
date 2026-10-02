package io.netscope.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import io.netscope.NetScope
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeTheme
import io.netscope.ui.navigation.NetScopeNavGraph
import io.netscope.ui.preferences.NetScopePreferences
import io.netscope.ui.preferences.ThemeMode

/**
 * Full-screen Activity that hosts the NetScope UI.
 *
 * Launch via [NetScopeActivity.launch] or tap the persistent notification.
 * The theme (light / dark / system) is read from [NetScopePreferences] and
 * reacts immediately when changed inside the Settings screen.
 */
class NetScopeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Process death may restore this Activity before the host app rebuilds its OkHttp client.
        NetScope.ensureInitialized(this)

        setContent {
            val prefs      = remember { NetScopePreferences(this@NetScopeActivity) }
            var themeMode  by remember { mutableStateOf(prefs.themeMode) }
            val systemDark = isSystemInDarkTheme()
            val isDark     = when (themeMode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> systemDark
            }

            // Sync status-bar icon colour with the resolved theme.
            // Light theme  → dark icons (isAppearanceLightStatusBars = true)
            // Dark theme   → light icons (isAppearanceLightStatusBars = false)
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !isDark
            }

            NetScopeTheme(isDark = isDark) {
                val navController = rememberNavController()
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetScopeDarkBackground),
                ) {
                    NetScopeNavGraph(
                        navController  = navController,
                        themeMode      = themeMode,
                        onThemeChange  = { mode ->
                            prefs.themeMode = mode
                            themeMode = mode
                        },
                    )
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) = context.startActivity(launchIntent(context))

        fun launchIntent(context: Context): Intent =
            Intent(context, NetScopeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        fun launchPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context.applicationContext,
                0,
                launchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
