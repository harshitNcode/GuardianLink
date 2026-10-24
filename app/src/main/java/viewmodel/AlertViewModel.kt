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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AlertViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlertRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user"
    private val currentDeviceId = "demo_device"

    private val istFormatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    companion object {
        private val _sharedAlerts = MutableStateFlow<List<Alert>>(emptyList())
        val sharedAlerts: StateFlow<List<Alert>> = _sharedAlerts
        private const val MAX_SAVED_ALERTS = 30
    }

    val alerts: StateFlow<List<Alert>> = _sharedAlerts

    init {
        // 1. Immediately restore cached alerts from local disk on launch/restart
        val cached = AlertLocalStore.loadAlerts(getApplication())
        if (cached.isNotEmpty()) {
            _sharedAlerts.value = cached.sortedByDescending { it.timestamp }
        }

        // 2. Fetch live alerts from backend
        refresh()

        // 3. Periodic polling keeps alerts synchronized
        viewModelScope.launch {
            while (true) {
                delay(3000)
                refresh()
            }
        }
    }

    fun triggerSOS(onComplete: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val timeNow = istFormatter.format(Date(now))
        val instantAlert = Alert(
            id = null,
            title = "Emergency SOS Triggered",
            time = timeNow,
            type = "danger",
            timestamp = now
        )
        // Instantly display recent alert at top and persist
        val updated = (listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.title == instantAlert.title && it.time == instantAlert.time
        }).filterNot { it.title.contains("Safe Zone Breach", ignoreCase = true) }
            .sortedWith(
                compareByDescending<Alert> { it.type == "danger" || it.title.contains("SOS", ignoreCase = true) }
                    .thenByDescending { it.timestamp }
            ).take(MAX_SAVED_ALERTS)

        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        repository.triggerSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun cancelSOS(onComplete: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val timeNow = istFormatter.format(Date(now))
        val instantAlert = Alert(
            id = null,
            title = "Emergency SOS Stood Down (Resolved)",
            time = timeNow,
            type = "info",
            timestamp = now
        )
        // Instantly display resolution alert at top and persist
        val updated = (listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.title == instantAlert.title && it.time == instantAlert.time
        }).sortedByDescending { it.timestamp }.take(MAX_SAVED_ALERTS)

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

        // 1. Mark signatures as permanently deleted locally
        val sig1 = "${alert.title}_${alert.time}"
        val sig2 = "${alert.id}_${alert.title}"
        AlertLocalStore.markAlertDeleted(getApplication(), sig1)
        if (alert.id != null) {
            AlertLocalStore.markAlertDeleted(getApplication(), sig2)
        }

        // 2. Remove from active UI state immediately
        val updated = _sharedAlerts.value.filterNot {
            (alert.id != null && it.id == alert.id) ||
            (it.title == alert.title && it.time == alert.time)
        }
        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        // 3. Delete from remote backend SQLite DB
        repository.deleteAlert(alert)
    }

    fun refresh() {
        val deletedSignatures = AlertLocalStore.getDeletedSignatures(getApplication())
        repository.listenToAlerts(currentUid) { list ->
            // Filter out any locally deleted alerts from backend payload
            val cleanRemote = list.filterNot { alert ->
                val sig1 = "${alert.title}_${alert.time}"
                val sig2 = "${alert.id}_${alert.title}"
                deletedSignatures.contains(sig1) || deletedSignatures.contains(sig2)
            }

            // Merge local and clean remote alerts, dedup, and sort strictly newest first
            val current = _sharedAlerts.value
            val combined = mutableListOf<Alert>()
            combined.addAll(cleanRemote)
            for (item in current) {
                val sig1 = "${item.title}_${item.time}"
                val sig2 = "${item.id}_${item.title}"
                if (!deletedSignatures.contains(sig1) && !deletedSignatures.contains(sig2)) {
                    if (combined.none { (item.id != null && it.id == item.id) || (it.title == item.title && it.time == item.time) }) {
                        combined.add(item)
                    }
                }
            }

            val sorted = combined
                .filterNot { it.title.contains("Safe Zone Breach", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<Alert> { it.type == "danger" || it.title.contains("SOS", ignoreCase = true) }
                        .thenByDescending { it.timestamp }
                )
                .take(MAX_SAVED_ALERTS)

            _sharedAlerts.value = sorted
            AlertLocalStore.saveAlerts(getApplication(), sorted)
        }
    }
}

