package io.netscope.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.preferences.NetScopePreferences

// ── Options ───────────────────────────────────────────────────────────────────

private val MAX_TX_OPTIONS = listOf(100, 250, 500, 1_000, 2_000, 5_000)

private val BODY_SIZE_OPTIONS = listOf(
    50_000L        to "50 KB",
    100_000L       to "100 KB",
    250_000L       to "250 KB",
    500_000L       to "500 KB",
    1_048_576L     to "1 MB",
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { NetScopePreferences(context) }

    // Local state — seeded from persisted prefs
    var maxTransactions      by rememberSaveable { mutableStateOf(prefs.maxTransactions) }
    var maxBodySize          by rememberSaveable { mutableStateOf(prefs.maxBodySizeBytes) }
    var alwaysReadBody       by rememberSaveable { mutableStateOf(prefs.alwaysReadResponseBody) }
    var showNotification     by rememberSaveable { mutableStateOf(prefs.showNotification) }
    var redactedHeaders      by rememberSaveable { mutableStateOf(prefs.redactedHeaders) }
    var showClearDialog      by remember { mutableStateOf(false) }
    var showAddHeaderField   by remember { mutableStateOf(false) }
    var addHeaderInput       by remember { mutableStateOf("") }
    val focusRequester       = remember { FocusRequester() }
    val focusManager         = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NetScopeSurface),
            )
        },
        containerColor = NetScopeDarkBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Capture ───────────────────────────────────────────────────────
            SettingsSection(title = "Capture") {
                // Max transactions
                SettingsRow(
                    title = "Max transactions",
                    subtitle = "Oldest entries are pruned once this limit is reached.",
                ) {
                    ChipSelector(
                        options = MAX_TX_OPTIONS.map { it to formatCount(it) },
                        selected = maxTransactions,
                        onSelect = { v ->
                            maxTransactions = v
                            prefs.maxTransactions = v
                        },
                    )
                }

                SettingsDivider()

                // Max body size
                SettingsRow(
                    title = "Max body size",
                    subtitle = "Request and response bodies are truncated beyond this size.",
                ) {
                    ChipSelector(
                        options = BODY_SIZE_OPTIONS,
                        selected = maxBodySize,
                        onSelect = { v ->
                            maxBodySize = v
                            prefs.maxBodySizeBytes = v
                        },
                    )
                }

                SettingsDivider()

                // Always read response body
                SettingsRow(
                    title = "Always read response body",
                    subtitle = "Force-read bodies even for non-text content types.",
                    trailing = {
                        NetScopeSwitch(
                            checked = alwaysReadBody,
                            onCheckedChange = { v ->
                                alwaysReadBody = v
                                prefs.alwaysReadResponseBody = v
                            },
                        )
                    },
                )
            }

            // ── Privacy ───────────────────────────────────────────────────────
            SettingsSection(title = "Privacy") {
                SettingsRow(
                    title = "Redacted headers",
                    subtitle = "Values are replaced with ██████ in the UI and exports.",
                ) {
                    RedactedHeadersEditor(
                        headers = redactedHeaders,
                        showAddField = showAddHeaderField,
                        addInput = addHeaderInput,
                        focusRequester = focusRequester,
                        onAddInputChange = { addHeaderInput = it },
                        onAddToggle = {
                            showAddHeaderField = !showAddHeaderField
                            addHeaderInput = ""
                            if (!showAddHeaderField) focusManager.clearFocus()
                        },
                        onConfirmAdd = {
                            val trimmed = addHeaderInput.trim()
                            if (trimmed.isNotEmpty()) {
                                val updated = redactedHeaders + trimmed.lowercase()
                                redactedHeaders = updated
                                prefs.redactedHeaders = updated
                            }
                            addHeaderInput = ""
                            showAddHeaderField = false
                            focusManager.clearFocus()
                        },
                        onRemove = { header ->
                            val updated = redactedHeaders - header
                            redactedHeaders = updated
                            prefs.redactedHeaders = updated
                        },
                    )
                }
            }

            // ── Interface ─────────────────────────────────────────────────────
            SettingsSection(title = "Interface") {
                SettingsRow(
                    title = "Persistent notification",
                    subtitle = "Show live request stats in the status bar.",
                    trailing = {
                        NetScopeSwitch(
                            checked = showNotification,
                            onCheckedChange = { v ->
                                showNotification = v
                                prefs.showNotification = v
                            },
                        )
                    },
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "NetScope",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Network inspector for Android",
                            color = NetScopeMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NetScopeAccent.copy(alpha = 0.12f))
                            .border(0.5.dp, NetScopeAccent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "v0.3.0",
                            color = NetScopeAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ── Danger zone ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF4D6A).copy(alpha = 0.08f))
                    .border(0.5.dp, Color(0xFFFF4D6A).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable { showClearDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFFF4D6A),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Clear all transactions",
                        color = Color(0xFFFF4D6A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Clear all confirmation dialog ─────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF1A2235),
            title = {
                Text(text = "Clear all transactions?", color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    text = "This will permanently delete all captured requests. This action cannot be undone.",
                    color = NetScopeMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                        onBack()
                    },
                ) {
                    Text("Clear all", color = Color(0xFFFF4D6A), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = NetScopeMuted)
                }
            },
        )
    }
}

// ── Section container ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title.uppercase(),
            color = NetScopeMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetScopeCard),
        ) {
            content()
        }
    }
}

// ── Row variants ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, color = NetScopeMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (content != null) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = NetScopeBorder,
        thickness = 0.5.dp,
    )
}

// ── Chip selector ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) NetScopeAccent.copy(alpha = 0.15f) else Color(0xFF0F1929),
                    )
                    .border(
                        0.5.dp,
                        if (isSelected) NetScopeAccent.copy(alpha = 0.55f) else NetScopeBorder,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) NetScopeAccent else NetScopeMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Redacted headers editor ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedactedHeadersEditor(
    headers: Set<String>,
    showAddField: Boolean,
    addInput: String,
    focusRequester: FocusRequester,
    onAddInputChange: (String) -> Unit,
    onAddToggle: () -> Unit,
    onConfirmAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Existing header chips
        headers.sorted().forEach { header ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NetScopeAccent.copy(alpha = 0.10f))
                    .border(0.5.dp, NetScopeAccent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header,
                    color = NetScopeAccent.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(
                    onClick = { onRemove(header) },
                    modifier = Modifier.size(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove $header",
                        tint = NetScopeMuted,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        // Add button
        if (!showAddField) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F1929))
                    .border(0.5.dp, NetScopeBorder, RoundedCornerShape(20.dp))
                    .clickable { onAddToggle() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add header",
                        tint = NetScopeMuted,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(text = "Add", color = NetScopeMuted, fontSize = 11.sp)
                }
            }
        }
    }

    // Inline add field — animates in below the chips
    AnimatedVisibility(
        visible = showAddField,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = addInput,
                onValueChange = onAddInputChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(NetScopeAccent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirmAdd() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0E1A))
                    .border(0.5.dp, NetScopeBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (addInput.isEmpty()) {
                            Text(
                                text = "e.g. X-Api-Key",
                                color = NetScopeMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            // Confirm
            IconButton(
                onClick = onConfirmAdd,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (addInput.isNotBlank()) NetScopeAccent.copy(alpha = 0.15f)
                        else Color.Transparent,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirm",
                    tint = if (addInput.isNotBlank()) NetScopeAccent else NetScopeMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Cancel
            IconButton(
                onClick = onAddToggle,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = NetScopeMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Switch ────────────────────────────────────────────────────────────────────

@Composable
private fun NetScopeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = NetScopeAccent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = NetScopeMuted,
            uncheckedTrackColor = Color(0xFF0F1929),
            uncheckedBorderColor = NetScopeBorder,
        ),
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatCount(n: Int): String =
    if (n >= 1_000) "${n / 1_000}k" else "$n"
