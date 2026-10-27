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
        var appContext: android.content.Context? = null
    }

    private val repository = DeviceRepository()
    val deviceId: String
        get() = activeDeviceId

    private val _status = MutableStateFlow(
        appContext?.let { com.guardianlink.app.util.AuthPreference.getCachedDeviceStatus(it) } ?: DeviceStatus()
    )
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
            // Purge mock Delhi seed coordinates (28.6139, 77.2090)
            val cleanUpdatedLat = if (updated.rawLatitude != null && Math.abs(updated.rawLatitude - 28.6139) < 0.05) null else updated.rawLatitude
            val cleanUpdatedLon = if (updated.rawLongitude != null && Math.abs(updated.rawLongitude - 77.2090) < 0.05) null else updated.rawLongitude
            val cleanPrevLat = if (prev.rawLatitude != null && Math.abs(prev.rawLatitude - 28.6139) < 0.05) null else prev.rawLatitude
            val cleanPrevLon = if (prev.rawLongitude != null && Math.abs(prev.rawLongitude - 77.2090) < 0.05) null else prev.rawLongitude
            val cleanGps = if (updated.gps.isNotBlank() && updated.gps != "--" && !updated.gps.contains("28.6139")) {
                updated.gps
            } else if (prev.gps.isNotBlank() && prev.gps != "--" && !prev.gps.contains("28.6139")) {
                prev.gps
            } else {
                "--"
            }

            val merged = updated.copy(
                // Preserve previous valid vitals if the new packet is zero or null (e.g. disconnected)
                heartRate = if (updated.heartRate > 0) updated.heartRate else prev.heartRate,
                temperature = if (updated.temperature > 0f) updated.temperature else prev.temperature,
                humidity = if (updated.humidity > 0f) updated.humidity else prev.humidity,
                battery = if (updated.battery.isNotBlank() && updated.battery != "--") updated.battery else prev.battery,
                gps = cleanGps,
                rawLatitude = cleanUpdatedLat ?: cleanPrevLat,
                rawLongitude = cleanUpdatedLon ?: cleanPrevLon,
                lastUpdatedTimestamp = if (updated.lastUpdatedTimestamp > 0L) updated.lastUpdatedTimestamp else prev.lastUpdatedTimestamp
            )
            appContext?.let { ctx ->
                com.guardianlink.app.util.AuthPreference.saveCachedDeviceStatus(ctx, merged)
            }
            merged
        }
    }

    fun broadcastPhoneLocation(latitude: Double, longitude: Double) {
        repository.sendPhoneLocation(deviceId, latitude, longitude)
    }

    fun triggerBuzzerAlarm(onComplete: (() -> Unit)? = null) {
        val currentIp = _status.value.ip
        repository.triggerBuzzerAlarm(deviceId, currentIp, onComplete)
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
