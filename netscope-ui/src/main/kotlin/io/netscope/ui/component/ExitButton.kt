package io.netscope.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/** The back arrow of a main screen: it leaves NetScope for the app it inspects. */
@Composable
internal fun ExitButton(onExit: () -> Unit) {
    IconButton(onClick = onExit) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to app",
            tint = NetScopeOnSurface,
        )
    }
}
