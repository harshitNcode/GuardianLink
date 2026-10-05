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

    private val _profile = MutableStateFlow(
        UserProfile(
            uid = "demo_user",
            name = AuthPreference.getName(application),
            phoneNumber = AuthPreference.getPhone(application)
        )
    )
    val profile: StateFlow<UserProfile> = _profile

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = currentUid
        android.util.Log.d("GuardianLink", "Starting fetch for uid: $uid")
        repository.fetchProfile(uid) { fetched ->
            android.util.Log.d("GuardianLink", "Fetched profile: $fetched")
            _profile.value = fetched
        }
    }

    fun saveProfilePhoto(localPath: String) {
        _profile.value = _profile.value.copy(photoLocalPath = localPath)
        repository.updatePhotoPath(currentUid, localPath)
    }

    fun updateProfile(updated: UserProfile) {
        _profile.value = updated
        repository.saveProfile(updated)
    }
}