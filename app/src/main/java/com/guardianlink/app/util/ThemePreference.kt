package com.guardianlink.app.util

import android.content.Context

object ThemePreference {

    private const val PREFS_NAME = "guardianlink_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    fun isDarkMode(context: Context, default: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, default)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }
}