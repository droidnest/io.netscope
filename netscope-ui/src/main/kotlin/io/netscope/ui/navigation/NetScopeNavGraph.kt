package io.netscope.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.netscope.ui.preferences.ThemeMode
import io.netscope.ui.screen.SettingsScreen
import io.netscope.ui.screen.TransactionDetailScreen
import io.netscope.ui.screen.TransactionListScreen
import io.netscope.ui.viewmodel.NetScopeViewModel

private const val ROUTE_LIST     = "list"
private const val ROUTE_DETAIL   = "detail/{transactionId}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun NetScopeNavGraph(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val viewModel: NetScopeViewModel = viewModel(factory = NetScopeViewModel.Factory())

    NavHost(navController = navController, startDestination = ROUTE_LIST) {

        composable(ROUTE_LIST) {
            TransactionListScreen(
                viewModel         = viewModel,
                onTransactionClick = { id ->
                    viewModel.selectTransaction(id)
                    navController.navigate("detail/$id")
                },
                onSettingsClick   = { navController.navigate(ROUTE_SETTINGS) },
            )
        }

        composable(
            route     = ROUTE_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
        ) {
            TransactionDetailScreen(
                viewModel = viewModel,
                onBack    = {
                    viewModel.clearSelection()
                    navController.popBackStack()
                },
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
    }
}
