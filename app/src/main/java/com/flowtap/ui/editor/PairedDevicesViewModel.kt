package com.flowtap.ui.editor

import androidx.lifecycle.ViewModel
import com.flowtap.system.PairedDevice
import com.flowtap.system.PairedDevices
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PairedDevicesViewModel @Inject constructor(
    private val pairedDevices: PairedDevices,
) : ViewModel() {
    val permission: String? get() = pairedDevices.permission

    fun devices(): List<PairedDevice>? = pairedDevices.list()
}
