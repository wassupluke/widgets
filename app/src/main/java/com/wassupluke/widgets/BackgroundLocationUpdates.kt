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

    /** Shared with [LocationUpdateReceiver] so both halves of the push mechanism log under one tag. */
    internal const val TAG = "WeatherLocation"

    /**
     * Idempotent — requesting updates again with the same [PendingIntent] replaces the existing
     * registration rather than stacking a second one, so this is safe to call on every heartbeat.
     */
    fun register(context: Context) {
        if (!hasLocationPermission(context)) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Nothing to register against yet. The heartbeat retries, so turning location back on
            // recovers without needing the app to be opened.
            Log.i(TAG, "network provider disabled — retrying on the next heartbeat")
            return
        }
        try {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                INTERVAL_MS,
                MIN_DISTANCE_M,
                updatePendingIntent(context)
            )
            if (hasBackgroundLocationPermission(context)) {
                Log.i(TAG, "registered background location updates")
            } else {
                // Foreground-only permission: the OS delivers these updates only while the app is
                // visible, so the cache goes cold between opens and background refresh falls back
                // to stale data. This is the failure mode the push mechanism exists to avoid.
                Log.w(TAG, "registered location updates without ACCESS_BACKGROUND_LOCATION — " +
                    "they will only be delivered while the app is in the foreground")
            }
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call — nothing to do.
            Log.w(TAG, "could not register location updates", e)
        }
    }

    fun unregister(context: Context) {
        // FLAG_NO_CREATE: if we never registered, there is nothing to remove and no reason to
        // mint a PendingIntent just to hand it straight back.
        val pending = existingPendingIntent(context) ?: return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(pending)
        pending.cancel()
        Log.i(TAG, "unregistered background location updates")
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
