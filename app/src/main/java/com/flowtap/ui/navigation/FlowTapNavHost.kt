package com.flowtap.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.flowtap.ui.automations.AutomationsScreen
import com.flowtap.ui.editor.FlowEditorScreen
import com.flowtap.ui.home.HomeScreen
import com.flowtap.ui.settings.SettingsScreen

@Composable
fun FlowTapNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                contentPadding = contentPadding,
                onCreateFlow = { navController.navigate(EditorRoute()) },
                onEditFlow = { id -> navController.navigate(EditorRoute(flowId = id)) },
            )
        }
        composable<AutomationsRoute> {
            AutomationsScreen(
                contentPadding = contentPadding,
                onOpenFlow = { id -> navController.navigate(EditorRoute(flowId = id)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(contentPadding = contentPadding)
        }
        composable<EditorRoute>(
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            FlowEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
