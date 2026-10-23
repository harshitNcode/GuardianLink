package com.guardianlink.app.util

import android.content.Context

object MapPreference {
    private const val PREFS_NAME = "guardianlink_prefs"
    private const val KEY_MAP_STYLE = "selected_map_style"

    fun getMapStyle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MAP_STYLE, "STANDARD") ?: "STANDARD"
    }

    fun setMapStyle(context: Context, styleName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MAP_STYLE, styleName).apply()
    }
}
