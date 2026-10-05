package com.guardianlink.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardianlink.app.model.UserProfile
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class UserRepository {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun fetchProfile(uid: String, onResult: (UserProfile) -> Unit) {
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/users/$uid")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GuardianLink.User", "Failed to fetch profile: ${e.message}")
                mainHandler.post { onResult(UserProfile(uid = uid)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return
                        try {
                            val obj = JSONObject(body)
                            val profile = UserProfile(
                                uid = obj.optString("uid", uid),
                                name = obj.optString("name", "Harshit"),
                                phoneNumber = obj.optString("phone_number", ""),
                                deviceId = obj.optString("device_id", "demo_device"),
                                emergencyContact = obj.optString("emergency_contact", ""),
                                photoLocalPath = obj.optString("photo_local_path", "")
                            )
                            mainHandler.post { onResult(profile) }
                        } catch (e: Exception) {
                            Log.e("GuardianLink.User", "Error parsing profile: ${e.message}")
                            mainHandler.post { onResult(UserProfile(uid = uid)) }
                        }
                    } else {
                        mainHandler.post { onResult(UserProfile(uid = uid)) }
                    }
                }
            }
        })
    }

    fun saveProfile(profile: UserProfile) {
        if (profile.uid.isEmpty()) return

        val payload = JSONObject().apply {
            put("name", profile.name)
            put("phone_number", profile.phoneNumber)
            put("device_id", profile.deviceId)
            put("emergency_contact", profile.emergencyContact)
            put("photo_local_path", profile.photoLocalPath)
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/users/${profile.uid}")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.User", "Failed to save profile: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.User", "Profile saved successfully on backend!")
                    }
                }
            }
        })
    }

    fun updatePhotoPath(uid: String, localPath: String) {
        if (uid.isEmpty()) return

        val payload = JSONObject().apply {
            put("photo_local_path", localPath)
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${ApiConfig.HTTP_BASE_URL}/api/v1/users/$uid")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GuardianLink.User", "Failed to update photo path: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i("GuardianLink.User", "Photo path updated on backend!")
                    }
                }
            }
        })
    }
}