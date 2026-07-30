package com.wassupluke.widgets

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Registers/unregisters OS-pushed background location updates. Uses a [PendingIntent] delivered to
 * [LocationUpdateReceiver] so fixes arrive even with no live process — the framework-only equivalent
 * of the fused-provider background-location pattern, needed because this app avoids Play Services and
 * a background *pull* is denied on this device. Registered when a widget is placed / on boot / on app
 * open; the receiver keeps [com.wassupluke.widgets.data.LocationCache] warm for background refreshes.
 */
object BackgroundLocationUpdates {
    private val INTERVAL_MS = TimeUnit.MINUTES.toMillis(30)
    private const val MIN_DISTANCE_M = 0f
    private const val REQUEST_CODE = 5
    private const val TAG = "WeatherLocation"

    fun register(context: Context) {
        if (!hasLocationPermission(context)) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return
        try {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, pendingIntent(context)
            )
            Log.i(TAG, "registered background location updates")
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call — nothing to do.
            Log.w(TAG, "could not register location updates", e)
        }
    }

    fun unregister(context: Context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(pendingIntent(context))
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationUpdateReceiver::class.java)
        // Must be mutable so the OS can fill in the location extra on delivery (required on API 31+).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
