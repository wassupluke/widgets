package com.wassupluke.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.wassupluke.widgets.data.LocationCache

/**
 * Receives OS-pushed location updates (registered via [BackgroundLocationUpdates]) and keeps
 * [LocationCache] warm, so background weather refreshes have a location to use. This is delivered
 * even with no live process — the reliable way to have location in the background without Play
 * Services, since a background *pull* (getCurrentLocation/getLastKnownLocation) is denied on many ROMs.
 */
class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val location = extractLocation(intent) ?: return
        LocationCache.save(context, location.latitude, location.longitude)
        Log.i(BackgroundLocationUpdates.TAG, "location update delivered — refreshing")
        // Coordinates are personal data and logcat ends up in bug reports, so they are debug-only.
        if (BuildConfig.DEBUG) {
            Log.d(
                BackgroundLocationUpdates.TAG,
                "location update: ${location.latitude},${location.longitude}"
            )
        }
        // We now have a fresh location — refresh weather (coalesced with any other trigger).
        RefreshWeatherWorker.enqueueOnce(context)
        // This delivery just refreshed the widget, so push the next heartbeat out a full interval.
        // Both timers run at ~30 min; without this they drift apart and fetch twice per interval.
        WeatherWidgetProvider.deferHeartbeat(context)
    }

    private fun extractLocation(intent: Intent): Location? {
        val extras = intent.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(LocationManager.KEY_LOCATION_CHANGED)
        }
    }
}
