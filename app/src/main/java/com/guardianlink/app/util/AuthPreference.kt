package com.guardianlink.app.util

import android.content.Context

object AuthPreference {

    private const val PREFS_NAME = "guardianlink_auth_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"
    private const val KEY_EMAIL = "email"

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(
        context: Context,
        isLoggedIn: Boolean,
        uid: String = "demo_user",
        name: String = "Harshit",
        phone: String = "",
        email: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getUid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UID, "demo_user") ?: "demo_user"
    }

    fun getName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, "Harshit") ?: "Harshit"
    }

    fun getPhone(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PHONE, "") ?: ""
    }

    fun getEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EMAIL, "") ?: ""
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
