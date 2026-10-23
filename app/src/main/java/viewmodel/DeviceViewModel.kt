package com.guardianlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianlink.app.data.DeviceRepository
import com.guardianlink.app.model.DeviceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceViewModel : ViewModel() {

    companion object {
        var activeDeviceId: String = "demo_device"
    }

    private val repository = DeviceRepository()
    val deviceId: String
        get() = activeDeviceId

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        // Initial setup and listener
        repository.listenToStatus(deviceId) { updated ->
            mergeStatusWithCache(updated)
        }

        // Active 2-second real-time telemetry polling loop
        viewModelScope.launch {
            while (true) {
                delay(2000)
                repository.fetchLatestStatus(deviceId) { updated ->
                    mergeStatusWithCache(updated)
                }
            }
        }
    }

    private fun mergeStatusWithCache(updated: DeviceStatus) {
        _status.update { prev ->
            updated.copy(
                // Preserve previous valid vitals if the new packet is zero or null (e.g. disconnected)
                heartRate = if (updated.heartRate > 0) updated.heartRate else prev.heartRate,
                temperature = if (updated.temperature > 0f) updated.temperature else prev.temperature,
                humidity = if (updated.humidity > 0f) updated.humidity else prev.humidity,
                battery = if (updated.battery.isNotBlank() && updated.battery != "--") updated.battery else prev.battery,
                gps = if (updated.gps.isNotBlank() && updated.gps != "--") updated.gps else prev.gps,
                rawLatitude = updated.rawLatitude ?: prev.rawLatitude,
                rawLongitude = updated.rawLongitude ?: prev.rawLongitude,
                lastUpdatedTimestamp = if (updated.lastUpdatedTimestamp > 0L) updated.lastUpdatedTimestamp else prev.lastUpdatedTimestamp
            )
        }
    }

    fun broadcastPhoneLocation(latitude: Double, longitude: Double) {
        repository.sendPhoneLocation(deviceId, latitude, longitude)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        repository.refresh(deviceId) { updated ->
            mergeStatusWithCache(updated)
        }
        viewModelScope.launch {
            delay(1000)
            _isRefreshing.value = false
        }
    }
}
