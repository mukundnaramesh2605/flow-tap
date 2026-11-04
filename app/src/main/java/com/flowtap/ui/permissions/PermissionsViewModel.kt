package com.flowtap.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.system.AppPermission
import com.flowtap.system.AppPermissions
import com.flowtap.system.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val permissions: AppPermissions,
) : ViewModel() {

    private val _states = MutableStateFlow<List<PermissionState>?>(null)
    val states: StateFlow<List<PermissionState>?> = _states.asStateFlow()

    /** Call on resume: grants happen in system screens and come back without a callback. */
    fun refresh() {
        viewModelScope.launch { _states.value = permissions.states(repository.getFlows()) }
    }

    fun grantWithShizuku(permission: AppPermission) {
        viewModelScope.launch {
            permissions.grantWithShizuku(permission)
            refresh()
        }
    }
}
