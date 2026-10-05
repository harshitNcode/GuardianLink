package com.guardianlink.app.viewmodel

import androidx.lifecycle.ViewModel
import com.guardianlink.app.data.DeviceRepository
import com.guardianlink.app.model.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceViewModel : ViewModel() {

    private val repository = DeviceRepository()

    // TODO: replace with the actual paired device ID once device pairing (Phase 3) exists
    private val deviceId = "demo_device"

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status

    init {
        repository.listenToStatus(deviceId) { updated ->
            _status.value = updated
        }
    }
}