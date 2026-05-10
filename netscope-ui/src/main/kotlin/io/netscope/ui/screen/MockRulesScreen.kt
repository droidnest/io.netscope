package io.netscope.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.MockRule
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.NetScopeText2
import io.netscope.ui.component.methodColor
import io.netscope.ui.viewmodel.MockRulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockRulesScreen(
    viewModel: MockRulesViewModel,
    onBack: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
) {
    val rules by viewModel.rules.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NetScopeAccent),
                        )
                        Column {
                            Text(
                                text = "Mock Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = NetScopeOnSurface,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = (-0.3).sp,
                            )
                            if (rules.isNotEmpty()) {
                                val activeCount = rules.count { it.isEnabled }
                                Text(
                                    text = "${rules.size} RULES · $activeCount ACTIVE",
                                    color = NetScopeText2,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                        val activeCount = rules.count { it.isEnabled }
                        AnimatedVisibility(
                            visible = activeCount > 0,
                            enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                            exit = fadeOut(tween(100)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(ColorSuccess.copy(alpha = 0.15f))
                                    .border(0.5.dp, ColorSuccess.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "$activeCount active",
                                    color = ColorSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRule,
                containerColor = NetScopeAccent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add rule")
            }
        },
        containerColor = NetScopeDarkBackground,
    ) { innerPadding ->
        if (rules.isEmpty()) {
            MockRulesEmptyState(onAddRule = onAddRule, modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(innerPadding),
            ) {
                items(rules, key = { it.id }) { rule ->
                    MockRuleItem(
                        rule = rule,
                        onToggle = { viewModel.toggle(rule.id) },
                        onEdit = { onEditRule(rule.id) },
                        onDelete = { viewModel.delete(rule.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

// ── Rule row ──────────────────────────────────────────────────────────────────

@Composable
private fun MockRuleItem(
    rule: MockRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripeColor = if (rule.isEnabled) ColorSuccess else NetScopeMuted.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit),
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = stripeColor,
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (rule.isEnabled) 1f else 0.5f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Row 1: name + status code badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = rule.name,
                    color = if (rule.isEnabled) NetScopeOnSurface else NetScopeMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusCodeBadge(rule.statusCode, rule.isEnabled)
            }
            // Row 2: match type + URL pattern
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = rule.matchType.label,
                    color = NetScopeAccent.copy(alpha = if (rule.isEnabled) 0.7f else 0.35f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rule.urlPattern,
                    color = NetScopeMuted.copy(alpha = if (rule.isEnabled) 1f else 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Row 3: method chip + delay (optional)
            if (rule.method != null || rule.delayMs > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rule.method?.let { method ->
                        MethodPill(method, rule.isEnabled)
                    }
                    if (rule.delayMs > 0) {
                        Text(
                            text = "${rule.delayMs}ms delay",
                            color = NetScopeMuted.copy(alpha = if (rule.isEnabled) 0.75f else 0.4f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // Right controls: toggle + delete
        Column(
            modifier = Modifier
                .padding(end = 8.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = NetScopeAccent,
                    checkedBorderColor   = Color.Transparent,
                    uncheckedThumbColor  = NetScopeMuted,
                    uncheckedTrackColor  = NetScopeCard,
                    uncheckedBorderColor = NetScopeBorder,
                ),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(width = 44.dp, height = 26.dp),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete rule",
                    tint = ColorError.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCodeBadge(code: Int, enabled: Boolean) {
    val color = when (code) {
        in 200..299 -> ColorSuccess
        in 300..399 -> Color(0xFF60A5FA)
        in 400..499 -> Color(0xFFFFB547)
        else        -> ColorError
    }.let { if (enabled) it else it.copy(alpha = 0.4f) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = code.toString(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MethodPill(method: String, enabled: Boolean) {
    val color = method.methodColor()
        .let { if (enabled) it else it.copy(alpha = 0.4f) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = method,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun MockRulesEmptyState(onAddRule: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NetScopeCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Science,
                contentDescription = null,
                tint = NetScopeMuted,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No mock rules yet",
            color = NetScopeOnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap + to add a rule — matching requests will return\nyour fixture response instead of hitting the network",
            color = NetScopeMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
        Spacer(Modifier.height(20.dp))
        // CTA button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NetScopeAccent.copy(alpha = 0.12f))
                .border(1.dp, NetScopeAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .clickable(onClick = onAddRule)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = "+ Add your first rule",
                color = NetScopeAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
