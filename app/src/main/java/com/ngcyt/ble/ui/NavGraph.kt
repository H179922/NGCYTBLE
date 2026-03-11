package com.ngcyt.ble.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ngcyt.ble.ui.companion.CompanionScreen
import com.ngcyt.ble.ui.dashboard.ThreatDashboardScreen
import com.ngcyt.ble.ui.detail.DeviceDetailScreen
import com.ngcyt.ble.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String) {
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object Devices : Screen("devices", "Devices")
    data object Companion : Screen("companion", "Companion")
    data object Settings : Screen("settings", "Settings")
    data object DeviceDetail : Screen("device_detail/{mac}", "Device Detail") {
        fun createRoute(mac: String) = "device_detail/$mac"
    }
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Devices,
    Screen.Companion,
    Screen.Settings,
)

@Composable
fun NavGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier.padding(innerPadding),
    ) {
        composable(Screen.Dashboard.route) {
            ThreatDashboardScreen(
                onThreatClick = { mac ->
                    navController.navigate(Screen.DeviceDetail.createRoute(mac))
                },
            )
        }
        composable(Screen.Devices.route) {
            ThreatDashboardScreen(
                onThreatClick = { mac ->
                    navController.navigate(Screen.DeviceDetail.createRoute(mac))
                },
            )
        }
        composable(Screen.Companion.route) {
            CompanionScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("mac") { type = NavType.StringType }),
        ) {
            DeviceDetailScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
