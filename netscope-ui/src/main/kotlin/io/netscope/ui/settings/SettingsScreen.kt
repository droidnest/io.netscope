package io.netscope.ui.settings

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeCode
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.ScreenTitle
import io.netscope.ui.component.WrapRow
import io.netscope.NotificationStyle
import io.netscope.model.SettingChange
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
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
internal fun SettingsScreen(
    uiState: UiState<SettingsUiState>,
    onIntent: (UiIntent) -> Unit,
    onBack: () -> Unit,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val state = uiState.dataOrNull() ?: return
    val content = state.content
    val settings = content.settings
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    if (state is SettingsUiState.Navigating) {
        LaunchedEffect(key1 = state) {
            onBack()
            onIntent(SettingsUiIntent.NavigationHandled)
        }
    }

    Scaffold(
        // The bottom bar of the graph already keeps clear of the navigation bar
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { ScreenTitle(text = "Settings") },
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
                        onSelect = onThemeChange,
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
                        selected = settings.maxTransactions,
                        onSelect = { onIntent(SettingsUiIntent.SettingChanged(change = SettingChange.MaxTransactions(count = it))) },
                    )
                }

                SettingsDivider()

                SettingsRow(
                    title    = "Body size limit",
                    subtitle = "Request and response bodies are truncated beyond this size.",
                ) {
                    OptionChips(
                        options  = BODY_SIZE_OPTIONS,
                        selected = settings.maxBodySize,
                        onSelect = { onIntent(SettingsUiIntent.SettingChanged(change = SettingChange.MaxBodySize(bytes = it))) },
                    )
                }

                SettingsDivider()

                SettingsRow(
                    title    = "Always read response body",
                    subtitle = "Force-read bodies even for non-text content types.",
                    trailing = {
                        NetScopeSwitch(
                            checked         = settings.alwaysReadResponseBody,
                            onCheckedChange = { onIntent(SettingsUiIntent.SettingChanged(change = SettingChange.AlwaysReadResponseBody(isEnabled = it))) },
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
                        headers        = settings.redactedHeaders,
                        lockedHeaders  = settings.lockedRedactedHeaders,
                        showAddField   = content.isAddHeaderOpen,
                        addInput       = content.addHeaderInput,
                        focusRequester = focusRequester,
                        onAddInputChange = { onIntent(SettingsUiIntent.HeaderTyped(input = it)) },
                        onAddToggle    = {
                            if (content.isAddHeaderOpen) focusManager.clearFocus()
                            onIntent(SettingsUiIntent.AddHeaderClicked)
                        },
                        onConfirmAdd   = {
                            onIntent(SettingsUiIntent.AddHeaderConfirmed)
                            focusManager.clearFocus()
                        },
                        onRemove       = { onIntent(SettingsUiIntent.HeaderRemoved(header = it)) },
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
                            checked         = settings.shakeToOpen,
                            onCheckedChange = { onIntent(SettingsUiIntent.SettingChanged(change = SettingChange.ShakeToOpen(isEnabled = it))) },
                        )
                    },
                )

                SettingsDivider()

                SettingsRow(
                    title    = "Notification style",
                    subtitle = "Controls how much information appears in the status-bar notification.",
                ) {
                    NotificationStylePicker(
                        selected = settings.notificationStyle,
                        onSelect = { onIntent(SettingsUiIntent.SettingChanged(change = SettingChange.Notifications(style = it))) },
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
                            text = "v0.7.0",
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
                    .clickable { onIntent(SettingsUiIntent.ClearAllClicked) }
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

    if (content.isClearDialogOpen) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsUiIntent.ClearAllCancelled) },
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
                TextButton(onClick = { onIntent(SettingsUiIntent.ClearAllConfirmed) }) {
                    Text("Clear all", color = ColorError, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsUiIntent.ClearAllCancelled) }) {
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

// ── Radio option picker (generic) ─────────────────────────────────────────────

@Composable
private fun <T> RadioOptionPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    description: (T) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active = option == selected
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
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label(option),
                        color = if (active) NetScopeAccent else NetScopeOnSurface,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(text = description(option), color = NetScopeMuted, fontSize = 11.sp)
                }
                if (active) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NetScopeAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    RadioOptionPicker(
        options = ThemeMode.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label },
        description = { it.description },
    )
}

@Composable
private fun NotificationStylePicker(selected: NotificationStyle, onSelect: (NotificationStyle) -> Unit) {
    RadioOptionPicker(
        options = NotificationStyle.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label },
        description = { it.description },
    )
}

// ── Generic chip row ──────────────────────────────────────────────────────────

@Composable
private fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    WrapRow(
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
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

@Composable
private fun RedactedHeadersEditor(
    headers: Set<String>,
    lockedHeaders: Set<String>,
    showAddField: Boolean,
    addInput: String,
    focusRequester: FocusRequester,
    onAddInputChange: (String) -> Unit,
    onAddToggle: () -> Unit,
    onConfirmAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    WrapRow(
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) {
        headers.sorted().forEach { header ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NetScopeAccent.copy(alpha = 0.10f))
                    .border(0.5.dp, NetScopeAccent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(start = 10.dp, end = if (header in lockedHeaders) 10.dp else 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = header, color = NetScopeAccent.copy(0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (header !in lockedHeaders) {
                    IconButton(onClick = { onRemove(header) }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove $header", tint = NetScopeMuted, modifier = Modifier.size(11.dp))
                    }
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

