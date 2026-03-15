package io.netscope.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.netscope.NetScope
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.navigation.NetScopeNavGraph

/**
 * Full-screen Activity that hosts the NetScope UI.
 *
 * Launch via [NetScopeActivity.launch] or tap the persistent notification.
 */
class NetScopeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        check(NetScope.isInitialized()) {
            "NetScope not initialized. Call NetScope.interceptor(context) before launching."
        }

        setContent {
            val navController = rememberNavController()
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetScopeDarkBackground),
            ) {
                NetScopeNavGraph(navController = navController)
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, NetScopeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
