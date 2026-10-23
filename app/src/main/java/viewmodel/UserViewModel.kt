package com.guardianlink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.guardianlink.app.data.UserRepository
import com.guardianlink.app.model.UserProfile
import com.guardianlink.app.util.AuthPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserRepository()

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: AuthPreference.getUid(getApplication())

    companion object {
        private val _sharedProfile = MutableStateFlow(
            UserProfile(
                uid = "demo_user",
                name = "Harshit",
                phoneNumber = "",
                deviceId = "demo_device",
                emergencyContact = ""
            )
        )
    }

    val profile: StateFlow<UserProfile> = _sharedProfile

    init {
        // Sync local storage on creation
        if (_sharedProfile.value.name.isBlank() || _sharedProfile.value.name == "Harshit") {
            _sharedProfile.value = _sharedProfile.value.copy(
                uid = currentUid,
                name = AuthPreference.getName(application),
                phoneNumber = AuthPreference.getPhone(application),
                deviceId = AuthPreference.getDeviceId(application),
                emergencyContact = AuthPreference.getEmergencyContact(application)
            )
        }
        loadProfile()
    }

    private fun loadProfile() {
        val uid = currentUid
        android.util.Log.d("GuardianLink", "Starting fetch for uid: $uid")
        repository.fetchProfile(uid) { fetched ->
            android.util.Log.d("GuardianLink", "Fetched profile: $fetched")
            val merged = fetched.copy(
                name = fetched.name.ifBlank { AuthPreference.getName(getApplication()) },
                phoneNumber = fetched.phoneNumber.ifBlank { AuthPreference.getPhone(getApplication()) },
                deviceId = fetched.deviceId.ifBlank { AuthPreference.getDeviceId(getApplication()) },
                emergencyContact = fetched.emergencyContact.ifBlank { AuthPreference.getEmergencyContact(getApplication()) }
            )
            _sharedProfile.value = merged
            AuthPreference.setName(getApplication(), merged.name)
            AuthPreference.setPhone(getApplication(), merged.phoneNumber)
            AuthPreference.setDeviceId(getApplication(), merged.deviceId)
            AuthPreference.setEmergencyContact(getApplication(), merged.emergencyContact)
        }
    }

    fun saveProfilePhoto(localPath: String) {
        _sharedProfile.value = _sharedProfile.value.copy(photoLocalPath = localPath)
        repository.updatePhotoPath(currentUid, localPath)
    }

    fun updateProfile(updated: UserProfile) {
        _sharedProfile.value = updated
        AuthPreference.setName(getApplication(), updated.name)
        AuthPreference.setPhone(getApplication(), updated.phoneNumber)
        AuthPreference.setDeviceId(getApplication(), updated.deviceId)
        AuthPreference.setEmergencyContact(getApplication(), updated.emergencyContact)
        repository.saveProfile(updated)
    }
}