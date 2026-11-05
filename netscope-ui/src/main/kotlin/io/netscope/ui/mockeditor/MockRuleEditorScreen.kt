package io.netscope.ui.mockeditor

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.MockRule
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorPurple
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.methodColor
import io.netscope.ui.component.statusColor
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MockRuleEditorScreen(
    uiState: UiState<MockRuleEditorUiState>,
    onIntent: (UiIntent) -> Unit,
    onBack: () -> Unit,
) {
    val state = uiState.dataOrNull() ?: return
    val form = state.form
    val statusCodeInt = form.statusCodeValue
    val isValid = form.isValid

    if (state is MockRuleEditorUiState.Navigating) {
        LaunchedEffect(key1 = state) {
            onBack()
            onIntent(MockRuleEditorUiIntent.NavigationHandled)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (form.ruleId == null) "New Rule" else "Edit Rule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = NetScopeOnSurface,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.3).sp,
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
                actions = {
                    // Save button — Cyan bg, dark text, radius sm
                    val accent = NetScopeAccent
                    val bg = NetScopeDarkBackground
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isValid) accent else NetScopeMuted.copy(alpha = 0.3f))
                            .clickable(enabled = isValid) { onIntent(MockRuleEditorUiIntent.SaveClicked) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓  Save",
                            color = if (isValid) bg else NetScopeMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
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
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Section 1: Match condition ─────────────────────────────────
            SectionCard(
                title = "Match condition",
                accentColor = NetScopeAccent,
                icon = Icons.Outlined.Link,
            ) {
                FieldLabel("RULE NAME")
                EditorTextField(value = form.name, onValueChange = { onIntent(MockRuleEditorUiIntent.NameTyped(name = it)) }, placeholder = "e.g. Mock login endpoint")

                FieldLabel("URL PATTERN")
                EditorTextField(
                    value = form.urlPattern,
                    onValueChange = { onIntent(MockRuleEditorUiIntent.UrlPatternTyped(urlPattern = it)) },
                    placeholder = "/api/login",
                    fontFamily = FontFamily.Monospace,
                )

                FieldLabel("MATCH TYPE")
                val accent = NetScopeAccent
                SegmentedPicker(
                    options = MockRule.MatchType.values().toList(),
                    selected = form.matchType,
                    onSelect = { onIntent(MockRuleEditorUiIntent.MatchTypeSelected(matchType = it)) },
                    label = { it.label },
                    chipColor = { accent },
                )

                FieldLabel("HTTP METHOD")
                val muted = NetScopeMuted
                val methods = listOf(null, "GET", "POST", "PUT", "PATCH", "DELETE")
                SegmentedPicker(
                    options = methods,
                    selected = form.method,
                    onSelect = { onIntent(MockRuleEditorUiIntent.MethodSelected(method = it)) },
                    label = { it ?: "ANY" },
                    chipColor = { opt -> opt?.let { it.methodColor() } ?: muted },
                )
            }

            // ── Section 2: Fixture response ────────────────────────────────
            SectionCard(
                title = "Fixture response",
                accentColor = ColorPurple,
                icon = Icons.Outlined.Code,
            ) {
                FieldLabel("STATUS CODE")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditorTextField(
                        value = form.statusCode,
                        onValueChange = { onIntent(MockRuleEditorUiIntent.StatusCodeTyped(statusCode = it)) },
                        placeholder = "200",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    // Status code dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusCodeInt?.let { statusColor(it) } ?: NetScopeMuted),
                    )
                }

                FieldLabel("CONTENT-TYPE")
                EditorTextField(
                    value = form.contentType,
                    onValueChange = { onIntent(MockRuleEditorUiIntent.ContentTypeTyped(contentType = it)) },
                    placeholder = "application/json",
                    fontFamily = FontFamily.Monospace,
                )

                FieldLabel("RESPONSE BODY")
                EditorTextField(
                    value = form.body,
                    onValueChange = { onIntent(MockRuleEditorUiIntent.BodyTyped(body = it)) },
                    placeholder = "{}",
                    fontFamily = FontFamily.Monospace,
                    singleLine = false,
                    minLines = 5,
                )
            }

            // ── Section 3: Behaviour ───────────────────────────────────────
            SectionCard(
                title = "Behaviour",
                accentColor = ColorAmber,
                icon = Icons.Outlined.Timer,
            ) {
                FieldLabel("DELAY")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorTextField(
                        value = form.delayMs,
                        onValueChange = { onIntent(MockRuleEditorUiIntent.DelayTyped(delayMs = it)) },
                        placeholder = "0",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "ms", color = NetScopeMuted, fontSize = 12.sp)
                }

                FieldLabel("ENABLED")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (form.isEnabled) "Enabled — active immediately" else "Disabled — rule inactive",
                        color = if (form.isEnabled) NetScopeOnSurface else NetScopeMuted,
                        fontSize = 12.sp,
                    )
                    Switch(
                        checked = form.isEnabled,
                        onCheckedChange = { onIntent(MockRuleEditorUiIntent.EnabledToggled(isEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor    = Color.White,
                            checkedTrackColor    = NetScopeAccent,
                            checkedBorderColor   = Color.Transparent,
                            uncheckedThumbColor  = NetScopeMuted,
                            uncheckedTrackColor  = NetScopeCard,
                            uncheckedBorderColor = NetScopeBorder,
                        ),
                    )
                }
            }

            // ── Live rule preview ──────────────────────────────────────────
            RulePreview(method = form.method, matchType = form.matchType, urlPattern = form.urlPattern, statusCode = statusCodeInt)

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    accentColor: Color,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(14.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = NetScopeBorder, thickness = 0.5.dp)
        // Content
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

// ── Live preview ──────────────────────────────────────────────────────────────

@Composable
private fun RulePreview(
    method: String?,
    matchType: MockRule.MatchType,
    urlPattern: String,
    statusCode: Int?,
) {
    val accent = NetScopeAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCardAlt)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "RULE PREVIEW",
            color = NetScopeMuted,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Method chip
            PreviewChip(
                text = method ?: "ANY",
                color = method?.methodColor() ?: NetScopeMuted,
            )
            Text(text = "URL ${matchType.label.lowercase()}", color = NetScopeMuted, fontSize = 10.sp)
            if (urlPattern.isNotBlank()) {
                PreviewChip(text = urlPattern, color = accent)
            }
            Text(text = "→ return", color = NetScopeMuted, fontSize = 10.sp)
            if (statusCode != null) {
                PreviewChip(text = statusCode.toString(), color = statusColor(statusCode))
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Field helpers ─────────────────────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = NetScopeAccent,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyle(
            color = NetScopeOnSurface,
            fontSize = 13.sp,
            fontFamily = fontFamily,
        ),
        cursorBrush = SolidColor(NetScopeAccent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NetScopeCardAlt)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(8.dp))
            .heightIn(min = if (singleLine) 0.dp else 120.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = NetScopeMuted.copy(alpha = 0.5f), fontSize = 13.sp, fontFamily = fontFamily)
                }
                inner()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    chipColor: (T) -> Color,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val color = chipColor(option)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) color.copy(alpha = 0.18f) else NetScopeCardAlt)
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) color.copy(alpha = 0.7f) else NetScopeBorder,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) color else NetScopeMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
