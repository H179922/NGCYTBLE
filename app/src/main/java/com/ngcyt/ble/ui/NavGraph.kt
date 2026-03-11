package com.ngcyt.ble.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String, val label: String) {
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object Devices : Screen("devices", "Devices")
    data object Companion : Screen("companion", "Companion")
    data object Settings : Screen("settings", "Settings")
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
            PlaceholderScreen("Dashboard")
        }
        composable(Screen.Devices.route) {
            PlaceholderScreen("Devices")
        }
        composable(Screen.Companion.route) {
            PlaceholderScreen("Companion")
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen("Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name)
    }
}
