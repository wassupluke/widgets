package com.wassupluke.widgets

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Registers/unregisters OS-pushed background location updates. Uses a [PendingIntent] delivered to
 * [LocationUpdateReceiver] so fixes arrive even with no live process — the framework-only equivalent
 * of the fused-provider background-location pattern, needed because this app avoids Play Services and
 * a background *pull* is denied on this device. Registered when a widget is placed / on boot / on app
 * open; the receiver keeps [com.wassupluke.widgets.data.LocationCache] warm for background refreshes.
 */
object BackgroundLocationUpdates {
    private const val MIN_DISTANCE_M = 0f
    private const val REQUEST_CODE = 5

    /**
     * Idempotent — requesting updates again with the same [PendingIntent] replaces the existing
     * registration rather than stacking a second one, so this is safe to call on every heartbeat.
     */
    fun register(context: Context) {
        if (!hasLocationPermission(context)) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Debug.log("network provider disabled — retrying on the next heartbeat")
            return
        }
        try {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                WeatherWidgetProvider.REFRESH_INTERVAL_MS,
                MIN_DISTANCE_M,
                updatePendingIntent(context)
            )
            if (hasBackgroundLocationPermission(context)) {
                Debug.log("registered background location updates")
            } else {
                Debug.warn("registered location updates without ACCESS_BACKGROUND_LOCATION — " +
                    "they will only be delivered while the app is in the foreground")
            }
        } catch (e: SecurityException) {
            Debug.warn("could not register location updates", e)
        }
    }

    fun unregister(context: Context) {
        val pending = existingPendingIntent(context) ?: return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(pending)
        pending.cancel()
        Debug.log("unregistered background location updates")
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Only meaningful on API 29+; below that, foreground permission covers background delivery. */
    private fun hasBackgroundLocationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    /** The PendingIntent the OS delivers fixes to, created if it doesn't exist yet. */
    private fun updatePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, receiverIntent(context), flags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

    /** The already-registered PendingIntent, or null if we have never registered one. */
    private fun existingPendingIntent(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, receiverIntent(context), flags(PendingIntent.FLAG_NO_CREATE)
        )

    private fun receiverIntent(context: Context) =
        Intent(context, LocationUpdateReceiver::class.java)

    // Must be mutable so the OS can fill in the location extra on delivery (required on API 31+).
    private fun flags(base: Int): Int = base or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}
