package com.wassupluke.widgets.data

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * The last good device location, persisted so a background refresh has a location to use even when
 * the OS won't hand a background app a fresh fix. Written on a live fix by [FrameworkLocationProvider]
 * and on OS-pushed updates by `LocationUpdateReceiver`; read by [FrameworkLocationProvider].
 */
object LocationCache {
    private const val PREFS = "location_cache"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_AT = "saved_at"

    /** A location older than this is treated as stale, so we never show weather for where you were. */
    val MAX_AGE_MS: Long = TimeUnit.HOURS.toMillis(2)

    fun save(
        context: Context,
        latitude: Double,
        longitude: Double,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAT, latitude.toRawBits())
            .putLong(KEY_LON, longitude.toRawBits())
            .putLong(KEY_AT, nowMillis)
            .apply()
    }

    /** The saved coordinates as (latitude, longitude) if still within [maxAgeMs], else null. */
    fun recent(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS
    ): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_AT)) return null
        if (!isFresh(prefs.getLong(KEY_AT, 0), nowMillis, maxAgeMs)) return null
        return Double.fromBits(prefs.getLong(KEY_LAT, 0)) to Double.fromBits(prefs.getLong(KEY_LON, 0))
    }

    /** Pure freshness test, extracted so it is unit-testable without Android. */
    fun isFresh(savedAtMillis: Long, nowMillis: Long, maxAgeMs: Long): Boolean =
        nowMillis - savedAtMillis <= maxAgeMs
}
