package io.netscope.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.detail.TransactionDetailScreen
import io.netscope.ui.detail.TransactionDetailUiIntent
import io.netscope.ui.detail.TransactionDetailViewModel
import io.netscope.ui.mockeditor.MockRuleEditorScreen
import io.netscope.ui.mockeditor.MockRuleEditorUiIntent
import io.netscope.ui.mockeditor.MockRuleEditorViewModel
import io.netscope.ui.mockrules.MockRulesScreen
import io.netscope.ui.mockrules.MockRulesViewModel
import io.netscope.ui.preferences.ThemeMode
import io.netscope.ui.settings.SettingsScreen
import io.netscope.ui.settings.SettingsViewModel
import io.netscope.ui.transactions.TransactionsViewModel
import io.netscope.ui.transactions.list.TransactionListScreen
import io.netscope.ui.transactions.stats.StatsScreen

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
internal fun NetScopeNavGraph(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onExit: () -> Unit,
) {
    // Scoped to the graph: the list and the Stats screen are one flow, and the Stats screen shows
    // the figures of the filtered list.
    val transactionsViewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory())

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in PRIMARY_ROUTES

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
                    uiState           = transactionsViewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent          = transactionsViewModel::onIntent,
                    onOpenTransaction = { id -> navController.openTransaction(id = id) },
                    onExit            = onExit,
                )
            }

            composable(ROUTE_MOCK_RULES) {
                val viewModel: MockRulesViewModel = viewModel(factory = MockRulesViewModel.Factory())
                MockRulesScreen(
                    uiState    = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent   = viewModel::onIntent,
                    onAddRule  = { navController.navigate(ROUTE_MOCK_ADD) },
                    onEditRule = { id -> navController.navigate("mock_rules/edit/$id") },
                    onExit     = onExit,
                )
            }

            composable(ROUTE_STATS) {
                StatsScreen(
                    uiState           = transactionsViewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent          = transactionsViewModel::onIntent,
                    onOpenTransaction = { id -> navController.openTransaction(id = id) },
                    onExit            = onExit,
                )
            }

            composable(ROUTE_SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory())
                SettingsScreen(
                    uiState       = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent      = viewModel::onIntent,
                    onBack        = { navController.popBackStack() },
                    currentTheme  = themeMode,
                    onThemeChange = onThemeChange,
                    onExit        = onExit,
                )
            }

            // ── Overlay destinations (no bottom nav) ──────────────────────────

            composable(
                route     = ROUTE_DETAIL,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { backStack ->
                val viewModel: TransactionDetailViewModel = viewModel(factory = TransactionDetailViewModel.Factory())
                // Load from the route's argument: when Android recreates the screen, only it survives
                val transactionId = backStack.arguments?.getLong("transactionId")
                LaunchedEffect(Unit) {
                    transactionId?.let { viewModel.onIntent(TransactionDetailUiIntent.Started(transactionId = it)) }
                }
                TransactionDetailScreen(
                    uiState  = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = viewModel::onIntent,
                    onBack   = { navController.popBackStack() },
                )
            }

            composable(ROUTE_MOCK_ADD) {
                val viewModel: MockRuleEditorViewModel = viewModel(factory = MockRuleEditorViewModel.Factory())
                LaunchedEffect(Unit) {
                    viewModel.onIntent(MockRuleEditorUiIntent.Started(ruleId = null))
                }
                MockRuleEditorScreen(
                    uiState  = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = viewModel::onIntent,
                    onBack   = { navController.popBackStack() },
                )
            }

            composable(
                route     = ROUTE_MOCK_EDIT,
                arguments = listOf(navArgument("ruleId") { type = NavType.StringType }),
            ) { backStack ->
                val viewModel: MockRuleEditorViewModel = viewModel(factory = MockRuleEditorViewModel.Factory())
                val ruleId = backStack.arguments?.getString("ruleId")
                LaunchedEffect(Unit) {
                    viewModel.onIntent(MockRuleEditorUiIntent.Started(ruleId = ruleId))
                }
                MockRuleEditorScreen(
                    uiState  = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = viewModel::onIntent,
                    onBack   = { navController.popBackStack() },
                )
            }
        }
    }
}

// Single top: during a tab crossfade the list and the Stats screen can both react to the same state
private fun NavHostController.openTransaction(id: Long) {
    navigate(route = "detail/$id") { launchSingleTop = true }
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
                            fontSize   = 10.sp,
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

