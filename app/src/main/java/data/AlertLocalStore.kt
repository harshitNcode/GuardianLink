package com.guardianlink.app.data

import android.content.Context
import android.util.Log
import com.guardianlink.app.model.Alert
import org.json.JSONArray
import org.json.JSONObject

object AlertLocalStore {

    private const val PREFS_NAME = "guardianlink_alerts_prefs"
    private const val KEY_ALERTS = "incident_alerts"
    private const val MAX_ALERTS = 25 // Limit to recent 25 alerts to prevent excessive accumulation

    fun saveAlerts(context: Context, alerts: List<Alert>) {
        try {
            val limited = alerts.take(MAX_ALERTS)
            val jsonArray = JSONArray()
            for (alert in limited) {
                val obj = JSONObject().apply {
                    put("title", alert.title)
                    put("time", alert.time)
                    put("type", alert.type)
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ALERTS, jsonArray.toString()).apply()
            Log.d("GuardianLink.AlertStore", "Saved ${limited.size} alerts locally")
        } catch (e: Exception) {
            Log.e("GuardianLink.AlertStore", "Error saving alerts: ${e.message}")
        }
    }

    fun loadAlerts(context: Context): List<Alert> {
        val list = mutableListOf<Alert>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_ALERTS, null) ?: return list
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    Alert(
                        title = item.optString("title", "Emergency Alert"),
                        time = item.optString("time", "Just now"),
                        type = item.optString("type", "danger")
                    )
                )
            }
            Log.d("GuardianLink.AlertStore", "Loaded ${list.size} alerts locally")
        } catch (e: Exception) {
            Log.e("GuardianLink.AlertStore", "Error loading local alerts: ${e.message}")
        }
        return list
    }
}
