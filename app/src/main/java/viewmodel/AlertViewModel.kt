package com.guardianlink.app.viewmodel

import androidx.lifecycle.ViewModel
import com.guardianlink.app.data.AlertRepository
import com.guardianlink.app.model.Alert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AlertViewModel : ViewModel() {

    private val repository = AlertRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user"
    private val currentDeviceId = "demo_device"

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts

    init {
        // Listen to live alerts from Firebase
        repository.listenToAlerts(currentUid) { list ->
            _alerts.value = list
        }
    }

    fun triggerSOS() {
        repository.triggerSOS(currentUid, currentDeviceId)
    }
}