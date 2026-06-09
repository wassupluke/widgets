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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Available(val latitude: Double, val longitude: Double) : LocationResult
    object PermissionMissing : LocationResult
    object Unavailable : LocationResult
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
            return LocationResult.PermissionMissing
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }

        // Prefer a recent last-known fix.
        for (provider in providers) {
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)?.let {
                return LocationResult.Available(it.latitude, it.longitude)
            }
        }

        // Otherwise request a single current fix from the first enabled provider.
        val provider = providers.firstOrNull() ?: return LocationResult.Unavailable
        return withTimeoutOrNull(15_000) { requestSingle(manager, provider) }
            ?: LocationResult.Unavailable
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
                provider, signal, Executors.newSingleThreadExecutor()
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
