package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.Contact
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ContactRepository {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Fetch emergency contacts list from FastAPI backend
    fun listenToContacts(userId: String, onUpdate: (List<Contact>) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/contacts/$userId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Contact", "Failed to fetch contacts: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return
                        try {
                            val jsonArray = JSONArray(body)
                            val list = mutableListOf<Contact>()
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                list.add(
                                    Contact(
                                        name = item.optString("name", ""),
                                        phone = item.optString("phone", "")
                                    )
                                )
                            }
                            mainHandler.post { onUpdate(list) }
                        } catch (e: Exception) {
                            Log.e("GuardianLink.Contact", "Error parsing contacts: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    // Save contact to FastAPI backend
    fun saveContact(userId: String, contact: Contact) {
        val payload = JSONObject().apply {
            put("name", contact.name)
            put("phone", contact.phone)
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/contacts/$userId")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Contact", "Failed to add contact: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.Contact", "Contact saved successfully on backend!")
                    }
                }
            }
        })
    }

    // Delete contact from FastAPI backend
    fun deleteContact(userId: String, contact: Contact) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/contacts/$userId/by-phone/${contact.phone}")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.Contact", "Failed to delete contact: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.Contact", "Contact deleted successfully from backend!")
                    }
                }
            }
        })
    }
}