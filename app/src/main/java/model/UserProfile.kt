package com.guardianlink.app.model

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val deviceId: String = "",
    val emergencyContact: String = "",
    val photoLocalPath: String = ""
)