package com.flowtap.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object AutomationsRoute

@Serializable
data object SettingsRoute

@Serializable
data object PermissionsRoute

/** [flowId] of [NEW_FLOW] opens the editor on a blank flow. */
@Serializable
data class EditorRoute(val flowId: Long = NEW_FLOW) {
    companion object {
        const val NEW_FLOW = 0L
    }
}
