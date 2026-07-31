package com.wassupluke.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.wassupluke.widgets.data.LocationCache

/**
 * Receives OS-pushed location updates (registered via [BackgroundLocationUpdates]) and keeps
 * [LocationCache] warm, so background weather refreshes have a location to use. This is delivered
 * even with no live process — the reliable way to have location in the background without Play
 * Services, since a background *pull* (getCurrentLocation/getLastKnownLocation) is denied on many ROMs.
 */
class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val location = extractLocation(intent)
        if (location == null) {
            logFixlessDelivery(intent)
            return
        }
        LocationCache.save(context, location.latitude, location.longitude)
        Debug.log("location update delivered — refreshing")
        if (BuildConfig.DEBUG) {
            Debug.log("location update: ${location.latitude},${location.longitude}")
        }
        RefreshWeatherWorker.enqueueOnce(context)
        // This delivery just refreshed the widget, so push the next heartbeat out a full interval.
        // Both timers run at ~30 min; without this they drift apart and fetch twice per interval.
        WeatherWidgetProvider.deferHeartbeat(context)
    }

    /**
     * A delivery carrying no fix. Turning location off/on is the expected cause and is logged as
     * routine — the registration survives it, and the heartbeat re-registers if it didn't. Anything
     * else means the ROM shapes the extras differently than documented, which would otherwise be an
     * invisible "the widget just stopped updating": exactly the failure this logging exists to catch.
     */
    private fun logFixlessDelivery(intent: Intent) {
        val extras = intent.extras
        if (extras != null && extras.containsKey(LocationManager.KEY_PROVIDER_ENABLED)) {
            val enabled = extras.getBoolean(LocationManager.KEY_PROVIDER_ENABLED)
            Debug.log("network provider ${if (enabled) "enabled" else "disabled"}")
        } else {
            Debug.warn("location update delivered with no fix — ignoring")
        }
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
