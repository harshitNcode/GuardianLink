package com.guardianlink.app.data

import android.content.Context
import android.util.Log
import com.guardianlink.app.model.Alert
import org.json.JSONArray
import org.json.JSONObject

object AlertLocalStore {

    private const val PREFS_NAME = "guardianlink_alerts_prefs"
    private const val KEY_ALERTS = "incident_alerts"
    private const val KEY_DELETED_SIGNATURES = "deleted_alert_signatures"
    private const val MAX_ALERTS = 30

    fun getDeletedSignatures(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DELETED_SIGNATURES, emptySet()) ?: emptySet()
    }

    fun markAlertDeleted(context: Context, signature: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_DELETED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(signature)
            prefs.edit().putStringSet(KEY_DELETED_SIGNATURES, current).apply()
            Log.d("GuardianLink.AlertStore", "Added deleted signature: $signature")
        } catch (e: Exception) {
            Log.e("GuardianLink.AlertStore", "Error marking alert deleted: ${e.message}")
        }
    }

    fun saveAlerts(context: Context, alerts: List<Alert>) {
        try {
            val deletedSignatures = getDeletedSignatures(context)
            val filtered = alerts.filterNot { alert ->
                val sig1 = "${alert.title}_${alert.time}"
                val sig2 = "${alert.id}_${alert.title}"
                deletedSignatures.contains(sig1) || deletedSignatures.contains(sig2)
            }.take(MAX_ALERTS)

            val jsonArray = JSONArray()
            for (alert in filtered) {
                val obj = JSONObject().apply {
                    if (alert.id != null) put("id", alert.id)
                    put("title", alert.title)
                    put("time", alert.time)
                    put("type", alert.type)
                    put("timestamp", alert.timestamp)
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ALERTS, jsonArray.toString()).apply()
            Log.d("GuardianLink.AlertStore", "Saved ${filtered.size} alerts locally")
        } catch (e: Exception) {
            Log.e("GuardianLink.AlertStore", "Error saving alerts: ${e.message}")
        }
    }

    fun loadAlerts(context: Context): List<Alert> {
        val list = mutableListOf<Alert>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deletedSignatures = getDeletedSignatures(context)
            val jsonStr = prefs.getString(KEY_ALERTS, null) ?: return list
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val alertId = if (item.has("id") && !item.isNull("id")) item.optInt("id") else null
                val title = item.optString("title", "Emergency Alert")
                val time = item.optString("time", "Just now")
                val type = item.optString("type", "danger")
                val timestamp = item.optLong("timestamp", System.currentTimeMillis())

                val sig1 = "${title}_${time}"
                val sig2 = "${alertId}_${title}"
                if (deletedSignatures.contains(sig1) || deletedSignatures.contains(sig2)) {
                    continue
                }

                list.add(
                    Alert(
                        id = alertId,
                        title = title,
                        time = time,
                        type = type,
                        timestamp = timestamp
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

