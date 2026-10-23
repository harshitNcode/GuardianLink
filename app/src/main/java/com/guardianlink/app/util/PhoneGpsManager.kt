package com.guardianlink.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.guardianlink.app.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PhoneGpsManager {
    private val repository = DeviceRepository()
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting

    private val _lastBroadcastText = MutableStateFlow("")
    val lastBroadcastText: StateFlow<String> = _lastBroadcastText

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation

    fun startBroadcasting(context: Context, deviceId: String = "demo_device") {
        if (_isBroadcasting.value) return

        val appCtx = context.applicationContext
        val hasFine = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm

        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                _lastLocation.value = loc
                _lastBroadcastText.value = "%.4f° N, %.4f° E".format(loc.latitude, loc.longitude)
                repository.sendPhoneLocation(deviceId, loc.latitude, loc.longitude)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = l

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 2f, l)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 2f, l)
            }
            val lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            lastLoc?.let {
                _lastLocation.value = it
                _lastBroadcastText.value = "%.4f° N, %.4f° E".format(it.latitude, it.longitude)
                repository.sendPhoneLocation(deviceId, it.latitude, it.longitude)
            }
            _isBroadcasting.value = true
        } catch (e: SecurityException) {
            _isBroadcasting.value = false
        }
    }

    fun stopBroadcasting() {
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Exception) {}
        listener = null
        locationManager = null
        _isBroadcasting.value = false
    }

    fun toggleBroadcasting(context: Context, deviceId: String = "demo_device") {
        if (_isBroadcasting.value) {
            stopBroadcasting()
        } else {
            startBroadcasting(context, deviceId)
        }
    }
}
