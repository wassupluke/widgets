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
            .putLong(KEY_LAT, encode(latitude))
            .putLong(KEY_LON, encode(longitude))
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
        return decode(
            latBits = prefs.getLong(KEY_LAT, 0),
            lonBits = prefs.getLong(KEY_LON, 0),
            savedAtMillis = prefs.getLong(KEY_AT, 0),
            nowMillis = nowMillis,
            maxAgeMs = maxAgeMs
        )
    }

    /** A coordinate as stored in a `Long` pref. Paired with [decode]. */
    fun encode(coordinate: Double): Long = coordinate.toRawBits()

    /**
     * Pure freshness gate + coordinate decode, extracted from [recent] so the storage encoding is
     * unit-testable without Android.
     */
    fun decode(
        latBits: Long,
        lonBits: Long,
        savedAtMillis: Long,
        nowMillis: Long,
        maxAgeMs: Long
    ): Pair<Double, Double>? {
        if (!isFresh(savedAtMillis, nowMillis, maxAgeMs)) return null
        return Double.fromBits(latBits) to Double.fromBits(lonBits)
    }

    /**
     * Pure freshness test, extracted so it is unit-testable without Android. A timestamp in the
     * future means the clock moved or the fix carries its own (satellite) time — treat that as
     * unusable rather than eternally fresh, so the caller falls through to a live fix instead.
     */
    fun isFresh(savedAtMillis: Long, nowMillis: Long, maxAgeMs: Long): Boolean =
        nowMillis - savedAtMillis in 0..maxAgeMs
}
