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
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeCode
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.NetScope
import io.netscope.NotificationStyle
import io.netscope.ui.preferences.NetScopePreferences
import io.netscope.ui.preferences.ThemeMode

// ── Picker options ────────────────────────────────────────────────────────────

private val MAX_TX_OPTIONS = listOf(
    100   to "100",
    250   to "250",
    500   to "500",
    1_000 to "1 000",
    2_000 to "2 000",
    5_000 to "5 000",
)

private val BODY_SIZE_OPTIONS = listOf(
    50_000L    to "50 KB",
    100_000L   to "100 KB",
    250_000L   to "250 KB",
    500_000L   to "500 KB",
    1_048_576L to "1 MB",
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val prefs   = remember { NetScopePreferences(context) }

    var maxTransactions    by rememberSaveable { mutableStateOf(prefs.maxTransactions) }
    var maxBodySize        by rememberSaveable { mutableStateOf(prefs.maxBodySizeBytes) }
    var alwaysReadBody     by rememberSaveable { mutableStateOf(prefs.alwaysReadResponseBody) }
    var redactedHeaders    by rememberSaveable { mutableStateOf(prefs.redactedHeaders) }
    var shakeToOpen        by rememberSaveable { mutableStateOf(prefs.shakeToOpen) }
    var notifStyle         by rememberSaveable { mutableStateOf(prefs.notificationStyle) }

    var showClearDialog    by remember { mutableStateOf(false) }
    var showAddHeaderField by remember { mutableStateOf(false) }
    var addHeaderInput     by remember { mutableStateOf("") }
    val focusRequester     = remember { FocusRequester() }
    val focusManager       = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = NetScopeOnSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NetScopeOnSurface,
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

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                SettingsRow(
                    title    = "Theme",
                    subtitle = "Choose how NetScope looks on your device.",
                ) {
                    ThemeModePicker(
                        selected = currentTheme,
                        onSelect = { mode ->
                            prefs.themeMode = mode
                            onThemeChange(mode)
                        },
                    )
                }
            }

            // ── Capture ───────────────────────────────────────────────────────
            SettingsSection(title = "Capture") {

                SettingsRow(
                    title    = "Max transaction count",
                    subtitle = "Oldest entries are pruned once this limit is reached.",
                ) {
                    OptionChips(
                        options  = MAX_TX_OPTIONS,
                        selected = maxTransactions,
                        onSelect = { v ->
                            maxTransactions = v
                            prefs.maxTransactions = v
                            NetScope.updateConfig { copy(maxTransactions = v) }
                        },
                    )
                }

                SettingsDivider()

                SettingsRow(
                    title    = "Body size limit",
                    subtitle = "Request and response bodies are truncated beyond this size.",
                ) {
                    OptionChips(
                        options  = BODY_SIZE_OPTIONS,
                        selected = maxBodySize,
                        onSelect = { v ->
                            maxBodySize = v
                            prefs.maxBodySizeBytes = v
                            NetScope.updateConfig { copy(maxBodySize = v) }
                        },
                    )
                }

                SettingsDivider()

                SettingsRow(
                    title    = "Always read response body",
                    subtitle = "Force-read bodies even for non-text content types.",
                    trailing = {
                        NetScopeSwitch(
                            checked         = alwaysReadBody,
                            onCheckedChange = { v ->
                                alwaysReadBody = v
                                prefs.alwaysReadResponseBody = v
                                NetScope.updateConfig { copy(alwaysReadResponseBody = v) }
                            },
                        )
                    },
                )
            }

            // ── Privacy ───────────────────────────────────────────────────────
            SettingsSection(title = "Privacy") {
                SettingsRow(
                    title    = "Header redaction rules",
                    subtitle = "Matched header values are replaced with ██████ in the UI and all exports.",
                ) {
                    RedactedHeadersEditor(
                        headers        = redactedHeaders,
                        showAddField   = showAddHeaderField,
                        addInput       = addHeaderInput,
                        focusRequester = focusRequester,
                        onAddInputChange = { addHeaderInput = it },
                        onAddToggle    = {
                            showAddHeaderField = !showAddHeaderField
                            addHeaderInput = ""
                            if (!showAddHeaderField) focusManager.clearFocus()
                        },
                        onConfirmAdd   = {
                            val trimmed = addHeaderInput.trim()
                            if (trimmed.isNotEmpty()) {
                                val updated = redactedHeaders + trimmed.lowercase()
                                redactedHeaders = updated
                                prefs.redactedHeaders = updated
                                NetScope.updateConfig { copy(redactedHeaders = updated.map { it.lowercase() }.toSet()) }
                            }
                            addHeaderInput = ""
                            showAddHeaderField = false
                            focusManager.clearFocus()
                        },
                        onRemove       = { header ->
                            val updated = redactedHeaders - header
                            redactedHeaders = updated
                            prefs.redactedHeaders = updated
                            NetScope.updateConfig { copy(redactedHeaders = updated.map { it.lowercase() }.toSet()) }
                        },
                    )
                }
            }

            // ── Interface ─────────────────────────────────────────────────────
            SettingsSection(title = "Interface") {

                SettingsRow(
                    title    = "Shake-to-open gesture",
                    subtitle = "Shake the device to launch the NetScope transaction list.",
                    trailing = {
                        NetScopeSwitch(
                            checked         = shakeToOpen,
                            onCheckedChange = { v ->
                                shakeToOpen = v
                                prefs.shakeToOpen = v
                                NetScope.setShakeEnabled(v)
                            },
                        )
                    },
                )

                SettingsDivider()

                SettingsRow(
                    title    = "Notification style",
                    subtitle = "Controls how much information appears in the status-bar notification.",
                ) {
                    NotificationStylePicker(
                        selected = notifStyle,
                        onSelect = { v ->
                            notifStyle = v
                            prefs.notificationStyle = v
                            NetScope.notificationStyle = v
                        },
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "NetScope",
                            color = NetScopeOnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(text = "Network inspector for Android", color = NetScopeMuted, fontSize = 12.sp)
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
                    .background(ColorError.copy(alpha = 0.08f))
                    .border(0.5.dp, ColorError.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable { showClearDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ColorError, modifier = Modifier.size(18.dp))
                    Text(text = "Clear all transactions", color = ColorError, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = NetScopeCard,
            title  = { Text("Clear all transactions?", color = NetScopeOnSurface, fontWeight = FontWeight.SemiBold) },
            text   = {
                Text(
                    "This will permanently delete all captured requests. This action cannot be undone.",
                    color = NetScopeMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearAll(); onBack() }) {
                    Text("Clear all", color = ColorError, fontWeight = FontWeight.SemiBold)
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
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
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
        ) { content() }
    }
}

// ── Row layout ────────────────────────────────────────────────────────────────

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
                Text(text = title, color = NetScopeOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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

// ── Theme mode picker ─────────────────────────────────────────────────────────

@Composable
private fun ThemeModePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemeMode.entries.forEach { mode ->
            val active = mode == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) NetScopeAccent.copy(alpha = 0.10f) else NetScopeCardAlt)
                    .border(
                        0.5.dp,
                        if (active) NetScopeAccent.copy(alpha = 0.40f) else NetScopeBorder,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.label,
                        color = if (active) NetScopeAccent else NetScopeOnSurface,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(text = mode.description, color = NetScopeMuted, fontSize = 11.sp)
                }
                if (active) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NetScopeAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Notification style picker ─────────────────────────────────────────────────

@Composable
private fun NotificationStylePicker(
    selected: NotificationStyle,
    onSelect: (NotificationStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NotificationStyle.entries.forEach { style ->
            val active = style == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) NetScopeAccent.copy(alpha = 0.10f) else NetScopeCardAlt)
                    .border(
                        0.5.dp,
                        if (active) NetScopeAccent.copy(alpha = 0.40f) else NetScopeBorder,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(style) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.label,
                        color = if (active) NetScopeAccent else NetScopeOnSurface,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(text = style.description, color = NetScopeMuted, fontSize = 11.sp)
                }
                if (active) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NetScopeAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Generic chip row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) NetScopeAccent.copy(0.15f) else NetScopeCardAlt)
                    .border(
                        0.5.dp,
                        if (active) NetScopeAccent.copy(0.55f) else NetScopeBorder,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) NetScopeAccent else NetScopeMuted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
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
        verticalArrangement   = Arrangement.spacedBy(6.dp),
    ) {
        headers.sorted().forEach { header ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NetScopeAccent.copy(alpha = 0.10f))
                    .border(0.5.dp, NetScopeAccent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = header, color = NetScopeAccent.copy(0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                IconButton(onClick = { onRemove(header) }, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove $header", tint = NetScopeMuted, modifier = Modifier.size(11.dp))
                }
            }
        }

        if (!showAddField) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NetScopeCardAlt)
                    .border(0.5.dp, NetScopeBorder, RoundedCornerShape(20.dp))
                    .clickable { onAddToggle() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(12.dp))
                    Text(text = "Add", color = NetScopeMuted, fontSize = 11.sp)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showAddField,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit  = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value         = addInput,
                onValueChange = onAddInputChange,
                textStyle     = TextStyle(color = NetScopeOnSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush   = SolidColor(NetScopeAccent),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirmAdd() }),
                modifier      = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetScopeCode)
                    .border(0.5.dp, NetScopeBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (addInput.isEmpty()) Text(text = "e.g. X-Api-Key", color = NetScopeMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        inner()
                    }
                },
            )
            IconButton(
                onClick  = onConfirmAdd,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (addInput.isNotBlank()) NetScopeAccent.copy(0.15f) else Color.Transparent),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm", tint = if (addInput.isNotBlank()) NetScopeAccent else NetScopeMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onAddToggle, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = NetScopeMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Switch ────────────────────────────────────────────────────────────────────

@Composable
private fun NetScopeSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked         = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor   = Color.White,
            checkedTrackColor   = NetScopeAccent,
            checkedBorderColor  = Color.Transparent,
            uncheckedThumbColor = NetScopeMuted,
            uncheckedTrackColor = NetScopeCardAlt,
            uncheckedBorderColor= NetScopeBorder,
        ),
    )
}

// ── Private helpers ───────────────────────────────────────────────────────────

private val ColorError = io.netscope.ui.component.ColorError
