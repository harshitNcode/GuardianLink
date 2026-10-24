package com.guardianlink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guardianlink.app.data.AlertLocalStore
import com.guardianlink.app.data.AlertRepository
import com.guardianlink.app.model.Alert
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlertViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlertRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user"
    private val currentDeviceId = "demo_device"

    companion object {
        private val _sharedAlerts = MutableStateFlow<List<Alert>>(emptyList())
        val sharedAlerts: StateFlow<List<Alert>> = _sharedAlerts
        private const val MAX_SAVED_ALERTS = 25
    }

    val alerts: StateFlow<List<Alert>> = _sharedAlerts

    init {
        // 1. Immediately restore cached alerts from local disk on launch/restart
        val cached = AlertLocalStore.loadAlerts(getApplication())
        if (cached.isNotEmpty()) {
            _sharedAlerts.value = cached
        }

        // 2. Fetch live alerts from backend
        refresh()

        // 3. Periodic polling keeps alerts synchronized
        viewModelScope.launch {
            while (true) {
                delay(4000)
                refresh()
            }
        }
    }

    fun triggerSOS(onComplete: (() -> Unit)? = null) {
        val timeNow = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val instantAlert = Alert(
            title = "Emergency SOS Triggered",
            time = timeNow,
            type = "danger"
        )
        // Instantly display recent alert at top and persist
        val updated = (listOf(instantAlert) + _sharedAlerts.value.filterNot { it.title == instantAlert.title && it.time == instantAlert.time })
            .take(MAX_SAVED_ALERTS)
        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        repository.triggerSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun cancelSOS(onComplete: (() -> Unit)? = null) {
        val timeNow = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val instantAlert = Alert(
            title = "Emergency SOS Stood Down (Resolved)",
            time = timeNow,
            type = "info"
        )
        // Instantly display resolution alert at top and persist
        val updated = (listOf(instantAlert) + _sharedAlerts.value.filterNot { it.title == instantAlert.title && it.time == instantAlert.time })
            .take(MAX_SAVED_ALERTS)
        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        repository.cancelSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun deleteAlert(alert: Alert) {
        // SOS alerts are critical emergency records and cannot be deleted
        if (alert.type == "danger" || alert.title.contains("SOS", ignoreCase = true)) {
            return
        }
        val updated = _sharedAlerts.value.filterNot { it.title == alert.title && it.time == alert.time }
        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)
    }

    fun refresh() {
        repository.listenToAlerts(currentUid) { list ->
            if (list.isNotEmpty()) {
                val merged = (list + _sharedAlerts.value)
                    .distinctBy { "${it.title}_${it.time}" }
                    .take(MAX_SAVED_ALERTS)
                _sharedAlerts.value = merged
                AlertLocalStore.saveAlerts(getApplication(), merged)
            }
        }
    }
}
