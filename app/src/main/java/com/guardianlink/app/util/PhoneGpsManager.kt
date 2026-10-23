package com.guardianlink.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
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

        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(appCtx, "Please enable Location/GPS in your Phone Settings", Toast.LENGTH_LONG).show()
        }

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
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLoc: Location? = null
            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 2000L, 0f, l, Looper.getMainLooper())
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
                            bestLoc = loc
                        }
                    }
                } catch (_: Exception) {}
            }

            if (bestLoc != null) {
                _lastLocation.value = bestLoc
                _lastBroadcastText.value = "%.4f° N, %.4f° E".format(bestLoc.latitude, bestLoc.longitude)
                repository.sendPhoneLocation(deviceId, bestLoc.latitude, bestLoc.longitude)
            } else {
                _lastBroadcastText.value = "Acquiring GPS fix..."
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
        _lastBroadcastText.value = ""
    }

    fun toggleBroadcasting(context: Context, deviceId: String = "demo_device") {
        if (_isBroadcasting.value) {
            stopBroadcasting()
        } else {
            startBroadcasting(context, deviceId)
        }
    }
}
