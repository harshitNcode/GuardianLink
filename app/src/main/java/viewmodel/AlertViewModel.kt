package com.guardianlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianlink.app.data.AlertRepository
import com.guardianlink.app.model.Alert
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlertViewModel : ViewModel() {

    private val repository = AlertRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user"
    private val currentDeviceId = "demo_device"

    companion object {
        private val _sharedAlerts = MutableStateFlow<List<Alert>>(emptyList())
        val sharedAlerts: StateFlow<List<Alert>> = _sharedAlerts
    }

    val alerts: StateFlow<List<Alert>> = _sharedAlerts

    init {
        refresh()
        // Automatic periodic polling keeps alerts & recent activity synchronized
        viewModelScope.launch {
            while (true) {
                delay(4000)
                refresh()
            }
        }
    }

    fun triggerSOS(onComplete: (() -> Unit)? = null) {
        val timeNow = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val instantAlert = Alert(
            title = "Emergency SOS Triggered",
            time = timeNow,
            type = "danger"
        )
        // Instantly display recent alert at top of the feed with 0ms latency
        _sharedAlerts.value = listOf(instantAlert) + _sharedAlerts.value.filterNot { it.title == instantAlert.title && it.time == instantAlert.time }

        repository.triggerSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun cancelSOS(onComplete: (() -> Unit)? = null) {
        val timeNow = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val instantAlert = Alert(
            title = "Emergency SOS Stood Down (Resolved)",
            time = timeNow,
            type = "info"
        )
        // Instantly display resolution alert at top of the feed with 0ms latency
        _sharedAlerts.value = listOf(instantAlert) + _sharedAlerts.value.filterNot { it.title == instantAlert.title && it.time == instantAlert.time }

        repository.cancelSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun refresh() {
        repository.listenToAlerts(currentUid) { list ->
            _sharedAlerts.value = list
        }
    }
}
