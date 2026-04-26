package io.netscope.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.MockRule
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.methodColor
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.viewmodel.MockRulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockRuleEditorScreen(
    viewModel: MockRulesViewModel,
    ruleId: String?,
    onBack: () -> Unit,
) {
    val existing = ruleId?.let { viewModel.findById(it) }

    var name        by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var urlPattern  by rememberSaveable { mutableStateOf(existing?.urlPattern ?: "") }
    var matchType   by rememberSaveable { mutableStateOf(existing?.matchType ?: MockRule.MatchType.CONTAINS) }
    var method      by rememberSaveable { mutableStateOf(existing?.method) }
    var statusCode  by rememberSaveable { mutableStateOf(existing?.statusCode?.toString() ?: "200") }
    var contentType by rememberSaveable { mutableStateOf(existing?.contentType ?: "application/json") }
    var body        by rememberSaveable { mutableStateOf(existing?.responseBody ?: "{}") }
    var delayMs     by rememberSaveable { mutableStateOf(existing?.delayMs?.toString() ?: "0") }

    val isValid = name.isNotBlank() && urlPattern.isNotBlank() &&
        statusCode.toIntOrNull()?.let { it in 100..599 } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existing == null) "New Rule" else "Edit Rule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = NetScopeOnSurface,
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
                    IconButton(onClick = {
                        if (!isValid) return@IconButton
                        val code = statusCode.toIntOrNull() ?: 200
                        val delay = delayMs.toLongOrNull() ?: 0L
                        val rule = MockRule(
                            id           = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name         = name.trim(),
                            urlPattern   = urlPattern.trim(),
                            matchType    = matchType,
                            method       = method,
                            statusCode   = code,
                            contentType  = contentType.trim(),
                            responseBody = body,
                            delayMs      = delay,
                            isEnabled    = existing?.isEnabled ?: true,
                        )
                        if (existing == null) viewModel.add(rule) else viewModel.update(rule)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isValid) NetScopeAccent else NetScopeMuted,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorSection(label = "Rule Name") {
                EditorTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "e.g. Mock login endpoint",
                )
            }

            EditorSection(label = "URL Pattern") {
                EditorTextField(
                    value = urlPattern,
                    onValueChange = { urlPattern = it },
                    placeholder = "e.g. /api/login",
                    fontFamily = FontFamily.Monospace,
                )
            }

            EditorSection(label = "Match Type") {
                SegmentedPicker(
                    options = MockRule.MatchType.values().toList(),
                    selected = matchType,
                    onSelect = { matchType = it },
                    label = { it.label },
                )
            }

            EditorSection(label = "HTTP Method") {
                val methods = listOf(null, "GET", "POST", "PUT", "PATCH", "DELETE")
                SegmentedPicker(
                    options = methods,
                    selected = method,
                    onSelect = { method = it },
                    label = { it ?: "Any" },
                    chipColor = { opt -> opt?.let { it.methodColor() } ?: NetScopeMuted },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EditorLabel("Status Code")
                    EditorTextField(
                        value = statusCode,
                        onValueChange = { statusCode = it.filter(Char::isDigit).take(3) },
                        placeholder = "200",
                        keyboardType = KeyboardType.Number,
                        isError = statusCode.toIntOrNull()?.let { it !in 100..599 } ?: true,
                    )
                }
                Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EditorLabel("Content-Type")
                    EditorTextField(
                        value = contentType,
                        onValueChange = { contentType = it },
                        placeholder = "application/json",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            EditorSection(label = "Response Body") {
                EditorTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = "{}",
                    fontFamily = FontFamily.Monospace,
                    singleLine = false,
                    minLines = 5,
                )
            }

            EditorSection(label = "Delay (ms)") {
                EditorTextField(
                    value = delayMs,
                    onValueChange = { delayMs = it.filter(Char::isDigit).take(6) },
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Field helpers ─────────────────────────────────────────────────────────────

@Composable
private fun EditorSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorLabel(label)
        content()
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text = text,
        color = NetScopeMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
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
    isError: Boolean = false,
) {
    val borderColor = when {
        isError -> ColorError.copy(alpha = 0.6f)
        else    -> NetScopeBorder
    }
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
            .background(NetScopeCard)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
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
    chipColor: @Composable (T) -> Color = { NetScopeAccent },
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
                    .background(if (isSelected) color.copy(alpha = 0.18f) else NetScopeCard)
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
                )
            }
        }
    }
}
