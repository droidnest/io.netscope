package io.netscope.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.preferences.ThemeMode
import io.netscope.ui.screen.MockRuleEditorScreen
import io.netscope.ui.screen.MockRulesScreen
import io.netscope.ui.screen.SettingsScreen
import io.netscope.ui.screen.StatsScreen
import io.netscope.ui.screen.TransactionDetailScreen
import io.netscope.ui.screen.TransactionListScreen
import io.netscope.ui.viewmodel.MockRulesViewModel
import io.netscope.ui.viewmodel.NetScopeViewModel

private const val ROUTE_LIST        = "list"
private const val ROUTE_DETAIL      = "detail/{transactionId}"
private const val ROUTE_SETTINGS    = "settings"
private const val ROUTE_MOCK_RULES  = "mock_rules"
private const val ROUTE_MOCK_ADD    = "mock_rules/add"
private const val ROUTE_MOCK_EDIT   = "mock_rules/edit/{ruleId}"
private const val ROUTE_STATS       = "stats"

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(ROUTE_LIST,       "Requests", Icons.Outlined.List),
    BottomNavItem(ROUTE_MOCK_RULES, "Mock",     Icons.Outlined.GridOn),
    BottomNavItem(ROUTE_STATS,      "Stats",    Icons.Outlined.AutoGraph),
    BottomNavItem(ROUTE_SETTINGS,   "Settings", Icons.Outlined.Settings),
)

private val PRIMARY_ROUTES = setOf(ROUTE_LIST, ROUTE_MOCK_RULES, ROUTE_STATS, ROUTE_SETTINGS)

@Composable
fun NetScopeNavGraph(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val viewModel: NetScopeViewModel = viewModel(factory = NetScopeViewModel.Factory())
    val mockViewModel: MockRulesViewModel = viewModel(factory = MockRulesViewModel.Factory())

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in PRIMARY_ROUTES

    // Select before navigating so the detail's first frame is already Loading for the new id,
    // never the previously selected transaction.
    val openDetail: (Long) -> Unit = { id ->
        viewModel.selectTransaction(id)
        navController.navigate("detail/$id")
    }

    Scaffold(
        // Outer scaffold only owns the bottom nav — status bar insets are handled
        // by each screen's own TopAppBar, so we zero out the outer content insets
        // to prevent the top gap from being counted twice.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
            ) {
                NetScopeBottomBar(
                    currentRoute  = currentRoute,
                    navController = navController,
                )
            }
        },
        containerColor = NetScopeDarkBackground,
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = ROUTE_LIST,
            modifier         = Modifier.padding(innerPadding),
        ) {

            // ── Primary tab destinations ──────────────────────────────────────

            composable(ROUTE_LIST) {
                TransactionListScreen(
                    viewModel          = viewModel,
                    onTransactionClick = { id -> openDetail(id) },
                )
            }

            composable(ROUTE_MOCK_RULES) {
                MockRulesScreen(
                    viewModel  = mockViewModel,
                    onBack     = { navController.popBackStack() },
                    onAddRule  = { navController.navigate(ROUTE_MOCK_ADD) },
                    onEditRule = { id -> navController.navigate("mock_rules/edit/$id") },
                )
            }

            composable(ROUTE_STATS) {
                StatsScreen(
                    viewModel          = viewModel,
                    onTransactionClick = { id -> openDetail(id) },
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack        = { navController.popBackStack() },
                    onClearAll    = { viewModel.clearAllTransactions() },
                    currentTheme  = themeMode,
                    onThemeChange = onThemeChange,
                )
            }

            // ── Overlay destinations (no bottom nav) ──────────────────────────

            composable(
                route     = ROUTE_DETAIL,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { backStack ->
                // Selection normally happens in openDetail; this only restores it after process
                // death (the nav arg survives, the ViewModel does not). No-op when already selected.
                val transactionId = backStack.arguments?.getLong("transactionId")
                LaunchedEffect(transactionId) {
                    transactionId?.let(viewModel::selectTransaction)
                }
                // No clearSelection() on back: it would blank the screen during the pop animation.
                // The row stops being observed 5 s after the screen leaves (see detailState).
                TransactionDetailScreen(
                    viewModel = viewModel,
                    onBack    = { navController.popBackStack() },
                )
            }

            composable(ROUTE_MOCK_ADD) {
                MockRuleEditorScreen(
                    viewModel = mockViewModel,
                    ruleId    = null,
                    onBack    = { navController.popBackStack() },
                )
            }

            composable(
                route     = ROUTE_MOCK_EDIT,
                arguments = listOf(navArgument("ruleId") { type = NavType.StringType }),
            ) { backStack ->
                MockRuleEditorScreen(
                    viewModel = mockViewModel,
                    ruleId    = backStack.arguments?.getString("ruleId"),
                    onBack    = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Bottom navigation bar ─────────────────────────────────────────────────────

@Composable
private fun NetScopeBottomBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    Column {
        androidx.compose.material3.HorizontalDivider(
            color     = NetScopeBorder,
            thickness = 0.5.dp,
        )
        NavigationBar(
            containerColor = NetScopeSurface,
            tonalElevation = 0.dp,
        ) {
            BOTTOM_NAV_ITEMS.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick  = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(ROUTE_LIST) {
                                    saveState    = true
                                    inclusive    = false
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label,
                            modifier           = Modifier.size(20.dp),
                        )
                    },
                    label = {
                        Text(
                            text       = item.label,
                            fontSize   = 9.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = NetScopeAccent,
                        selectedTextColor   = NetScopeAccent,
                        unselectedIconColor = NetScopeMuted,
                        unselectedTextColor = NetScopeMuted,
                        indicatorColor      = Color.Transparent,
                    ),
                )
            }
        }
    }
}

