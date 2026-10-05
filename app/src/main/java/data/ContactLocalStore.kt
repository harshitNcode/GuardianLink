package com.guardianlink.app.data

import android.content.Context
import android.util.Log
import com.guardianlink.app.model.Contact
import org.json.JSONArray
import org.json.JSONObject

object ContactLocalStore {

    private const val PREFS_NAME = "guardianlink_contacts_prefs"
    private const val KEY_CONTACTS = "emergency_contacts"

    fun saveContacts(context: Context, contacts: List<Contact>) {
        try {
            val jsonArray = JSONArray()
            for (contact in contacts) {
                val obj = JSONObject().apply {
                    put("name", contact.name)
                    put("phone", contact.phone)
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
            Log.d("GuardianLink.ContactStore", "Saved ${contacts.size} contacts locally")
        } catch (e: Exception) {
            Log.e("GuardianLink.ContactStore", "Error saving contacts: ${e.message}")
        }
    }

    fun loadContacts(context: Context): List<Contact> {
        val list = mutableListOf<Contact>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CONTACTS, null) ?: return list
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    Contact(
                        name = item.optString("name", ""),
                        phone = item.optString("phone", "")
                    )
                )
            }
            Log.d("GuardianLink.ContactStore", "Loaded ${list.size} contacts locally")
        } catch (e: Exception) {
            Log.e("GuardianLink.ContactStore", "Error loading local contacts: ${e.message}")
        }
        return list
    }
}
