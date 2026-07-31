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

        // 1. A recent framework last-known fix. Only if recent — a stale one would pin weather to
        // where you were (e.g. home over a weekend away).
        for (provider in providers) {
            @Suppress("MissingPermission")
            val fix = manager.getLastKnownLocation(provider) ?: continue
            val fresh =
                LocationCache.isFresh(fix.time, System.currentTimeMillis(), LocationCache.MAX_AGE_MS)
            Debug.log("location: $provider last-known fresh=$fresh")
            if (fresh) {
                return remember("last-known/$provider", fix.latitude, fix.longitude)
            }
        }

        // 2. A live single fix. Works in the foreground (keeps weather following you when you
        // travel); in the background it usually returns nothing, and quickly, so we fall through.
        providers.firstOrNull()?.let { provider ->
            val current = withTimeoutOrNull(15_000) { requestSingle(manager, provider) }
            Debug.log(
                "location: live fix from $provider -> " +
                    if (current is LocationResult.Available) "available" else "$current"
            )
            if (current is LocationResult.Available) {
                return remember("live/$provider", current.latitude, current.longitude)
            }
        }

        // 3. Last resort: a recent location the OS pushed to us (kept warm by LocationUpdateReceiver).
        // This is what makes background refresh work — the OS denies a background app a live fix, so
        // reuse the last one it delivered rather than failing.
        LocationCache.recent(context)?.let { (latitude, longitude) ->
            Debug.log("location: using cached push fix")
            return LocationResult.Available(latitude, longitude)
        }

        Debug.log("location: no fix available")
        return LocationResult.Unavailable
    }

    /** Persist a fresh fix so background refreshes can reuse it, then return it. */
    private fun remember(
        source: String,
        latitude: Double,
        longitude: Double
    ): LocationResult.Available {
        Debug.log(String.format(Locale.US, "location: using %s fix %.2f,%.2f", source, latitude, longitude))
        LocationCache.save(context, latitude, longitude)
        return LocationResult.Available(latitude, longitude)
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
}
