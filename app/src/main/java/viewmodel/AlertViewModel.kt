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

    private fun sortAlerts(list: List<Alert>): List<Alert> {
        // Strictly sort by timestamp newest first (latest time and date to the very top)
        return list.sortedByDescending { it.timestamp }.take(MAX_SAVED_ALERTS)
    }

    private fun isAlertDeleted(alert: Alert, deletedSignatures: Set<String>): Boolean {
        if (alert.id != null && deletedSignatures.contains("${alert.id}")) return true
        if (alert.id != null && deletedSignatures.contains("${alert.id}_${alert.title}")) return true
        if (deletedSignatures.contains("${alert.title}_${alert.time}")) return true
        if (deletedSignatures.contains("${alert.title}_${alert.timestamp}")) return true
        return false
    }

    fun triggerSOS(onComplete: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val timeNow = istFormatter.format(Date(now))
        val instantAlert = Alert(
            id = (now % 10000000).toInt(),
            title = "Emergency SOS Triggered",
            time = timeNow,
            type = "danger",
            timestamp = now
        )
        // Instantly display recent alert at top and persist
        val updated = sortAlerts(listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.title == instantAlert.title && it.time == instantAlert.time
        })

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
            id = (now % 10000000).toInt(),
            title = "Emergency SOS Stood Down (Resolved)",
            time = timeNow,
            type = "info",
            timestamp = now
        )
        // Instantly display resolution alert at top and persist
        val updated = sortAlerts(listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.title == instantAlert.title && it.time == instantAlert.time
        })

        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        repository.cancelSOS(currentUid, currentDeviceId) {
            refresh()
            onComplete?.invoke()
        }
    }

    fun deleteAlert(alert: Alert) {
        // 1. Mark all signature representations as permanently deleted locally
        val sig1 = "${alert.title}_${alert.time}"
        val sig2 = "${alert.id}_${alert.title}"
        val sig3 = "${alert.title}_${alert.timestamp}"
        AlertLocalStore.markAlertDeleted(getApplication(), sig1)
        AlertLocalStore.markAlertDeleted(getApplication(), sig3)
        if (alert.id != null) {
            AlertLocalStore.markAlertDeleted(getApplication(), sig2)
            AlertLocalStore.markAlertDeleted(getApplication(), "${alert.id}")
        }

        // 2. Remove from active UI state immediately
        val updated = _sharedAlerts.value.filterNot {
            (alert.id != null && it.id == alert.id) ||
            (it.title == alert.title && it.time == alert.time) ||
            (it.title == alert.title && it.timestamp == alert.timestamp)
        }
        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        // 3. Delete from remote backend SQLite DB
        repository.deleteAlert(alert) {
            refresh()
        }
    }

    fun refresh() {
        val deletedSignatures = AlertLocalStore.getDeletedSignatures(getApplication())
        repository.listenToAlerts(currentUid) { list ->
            // Filter out any locally deleted alerts from backend payload
            val cleanRemote = list.filterNot { isAlertDeleted(it, deletedSignatures) }

            // Current alerts filtered against deleted signatures
            val current = _sharedAlerts.value.filterNot { isAlertDeleted(it, deletedSignatures) }

            val combined = mutableListOf<Alert>()
            combined.addAll(cleanRemote)
            for (item in current) {
                if (combined.none { (item.id != null && it.id == item.id) || (it.title == item.title && it.time == item.time) }) {
                    combined.add(item)
                }
            }

            val sorted = sortAlerts(combined)

            _sharedAlerts.value = sorted
            AlertLocalStore.saveAlerts(getApplication(), sorted)
        }
    }

    /**
     * Instantly records a safe zone breach alert on the client, persists it, and syncs to backend.
     * Throttles identical alerts for the same safe zone to 30s cooldown.
     */
    fun recordSafeZoneBreach(zoneName: String, distanceMeters: Int, latitude: Double? = null, longitude: Double? = null) {
        val now = System.currentTimeMillis()
        val timeNow = istFormatter.format(Date(now))
        val title = "Safe Zone Breach: Outside '$zoneName' (${distanceMeters}m away)"

        // Cooldown: do not post duplicate breach alert if one for the same zone was posted in last 30s
        val recent = _sharedAlerts.value.firstOrNull {
            it.title.contains(zoneName, ignoreCase = true) && (now - it.timestamp) < 30_000L
        }
        if (recent != null) return

        val uniqueId = ((now % 10000000).toInt())
        val instantAlert = Alert(
            id = uniqueId,
            title = title,
            time = timeNow,
            type = "warning",
            timestamp = now
        )

        val updated = sortAlerts(listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.id == uniqueId
        })

        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        // Sync to backend so emergency dispatch notification & database log are recorded
        repository.createAlert(
            userId = currentUid,
            deviceId = currentDeviceId,
            title = title,
            type = "warning",
            latitude = latitude,
            longitude = longitude
        )
    }

    /**
     * Triggers an immediate "Out of safe Zone" alert irrespective of any condition.
     * Instantly updates Alert Page and Recent Activity, persists locally, and syncs to backend.
     */
    fun triggerOutOfSafeZoneAlert(latitude: Double? = null, longitude: Double? = null, onComplete: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val timeNow = istFormatter.format(Date(now))
        val title = "Out of safe Zone"

        val uniqueId = ((now % 10000000).toInt())
        val instantAlert = Alert(
            id = uniqueId,
            title = title,
            time = timeNow,
            type = "warning",
            timestamp = now
        )

        val updated = sortAlerts(listOf(instantAlert) + _sharedAlerts.value.filterNot {
            it.id == uniqueId
        })

        _sharedAlerts.value = updated
        AlertLocalStore.saveAlerts(getApplication(), updated)

        // Sync to backend so database log and incident activity are recorded
        repository.createAlert(
            userId = currentUid,
            deviceId = currentDeviceId,
            title = title,
            type = "warning",
            latitude = latitude,
            longitude = longitude
        )
        refresh()
        onComplete?.invoke()
    }
}

