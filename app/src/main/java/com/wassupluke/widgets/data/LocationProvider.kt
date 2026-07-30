package com.wassupluke.widgets.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.wassupluke.widgets.Debug
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Available(val latitude: Double, val longitude: Double) : LocationResult
    data object PermissionMissing : LocationResult
    data object Unavailable : LocationResult
}

interface LocationProvider {
    suspend fun getLocation(): LocationResult
}

/** Uses the framework LocationManager only — no Google Play Services. */
class FrameworkLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun getLocation(): LocationResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Debug.warn("location: COARSE permission missing")
            return LocationResult.PermissionMissing
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        Debug.log("location: enabled providers=$providers")

        // Use a last-known fix only if it is recent; a stale one would pin weather to where you
        // were (e.g. home over a weekend away), so fall through to an active fix instead.
        for (provider in providers) {
            @Suppress("MissingPermission")
            val fix = manager.getLastKnownLocation(provider)
            val ageMs = fix?.let { System.currentTimeMillis() - it.time }
            Debug.log("location: $provider last-known age=${ageMs?.let { "${it / 60_000}min" } ?: "none"}")
            if (fix != null && ageMs!! <= MAX_AGE_MS) {
                return remember("last-known/$provider", fix.latitude, fix.longitude)
            }
        }

        // Otherwise request a single current fix from the first enabled provider. This now works
        // in the background too (the app holds ACCESS_BACKGROUND_LOCATION).
        providers.firstOrNull()?.let { provider ->
            val current = withTimeoutOrNull(15_000) { requestSingle(manager, provider) }
            // Coordinates are logged rounded (see remember) — no exact position in logcat.
            Debug.log(
                "location: live fix from $provider -> " +
                    if (current is LocationResult.Available) "available" else "$current"
            )
            if (current is LocationResult.Available) {
                return remember("live/$provider", current.latitude, current.longitude)
            }
        }

        // Last resort if no live fix is obtainable right now: reuse our last saved fix, but only
        // while it is still recent, so the widget never shows long-stale-location weather.
        val saved = lastGoodLocation()
        Debug.log("location: falling back to saved fix -> ${if (saved != null) "available" else "none/too old"}")
        return saved ?: LocationResult.Unavailable
    }

    /** Persist a fresh fix so background refreshes can reuse it, then return it. */
    private fun remember(
        source: String,
        latitude: Double,
        longitude: Double
    ): LocationResult.Available {
        Debug.log(String.format(Locale.US, "location: using %s fix %.2f,%.2f", source, latitude, longitude))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAT, latitude.toRawBits())
            .putLong(KEY_LON, longitude.toRawBits())
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
        return LocationResult.Available(latitude, longitude)
    }

    private fun lastGoodLocation(): LocationResult.Available? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT)) return null
        if (System.currentTimeMillis() - prefs.getLong(KEY_AT, 0) > MAX_AGE_MS) return null
        return LocationResult.Available(
            Double.fromBits(prefs.getLong(KEY_LAT, 0)),
            Double.fromBits(prefs.getLong(KEY_LON, 0))
        )
    }

    private suspend fun requestSingle(
        manager: LocationManager,
        provider: String
    ): LocationResult = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            @Suppress("MissingPermission")
            manager.getCurrentLocation(
                provider, signal, ContextCompat.getMainExecutor(context)
            ) { location ->
                if (cont.isActive) {
                    cont.resume(
                        if (location != null)
                            LocationResult.Available(location.latitude, location.longitude)
                        else LocationResult.Unavailable
                    )
                }
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) {
                        cont.resume(
                            LocationResult.Available(location.latitude, location.longitude)
                        )
                    }
                }
                // These are abstract on API < 30; must be overridden explicitly.
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
            @Suppress("MissingPermission")
            manager.requestSingleUpdate(provider, listener, context.mainLooper)
        }
    }

    private companion object {
        const val PREFS = "location_cache"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_AT = "saved_at"
        val MAX_AGE_MS = TimeUnit.HOURS.toMillis(2)
    }
}
