package com.flowtap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowtap.ui.navigation.AutomationsRoute
import com.flowtap.ui.navigation.FlowTapNavHost
import com.flowtap.ui.navigation.HomeRoute
import com.flowtap.ui.navigation.SettingsRoute
import com.flowtap.ui.transfer.TransferViewModel
import com.flowtap.ui.transfer.rememberTransferController
import kotlin.reflect.KClass

private enum class TopLevel(
    val label: String,
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Flows("Flows", HomeRoute, HomeRoute::class, Icons.Rounded.Apps, Icons.Outlined.Apps),
    Automations("Automations", AutomationsRoute, AutomationsRoute::class, Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    Settings("Settings", SettingsRoute, SettingsRoute::class, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
fun FlowTapRoot(transferViewModel: TransferViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination == null || TopLevel.entries.any { destination.isIn(it) }
    val snackbarHostState = remember { SnackbarHostState() }
    val transfer = rememberTransferController(transferViewModel, snackbarHostState)

    // Each screen owns its own Scaffold/TopAppBar; this one only hosts the bottom bar and snackbars.
    Scaffold(
        // Insets are handled by the bottom bar and by each screen's own top bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                FlowTapBottomBar(navController, destination)
            }
        },
    ) { padding ->
        FlowTapNavHost(
            navController = navController,
            contentPadding = padding,
            transfer = transfer,
            modifier = Modifier,
        )
    }
}

@Composable
private fun FlowTapBottomBar(navController: NavHostController, destination: NavDestination?) {
    NavigationBar {
        TopLevel.entries.forEach { item ->
            val selected = destination?.isIn(item) == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

private fun NavDestination.isIn(item: TopLevel): Boolean =
    hierarchy.any { it.hasRoute(item.routeClass) }
