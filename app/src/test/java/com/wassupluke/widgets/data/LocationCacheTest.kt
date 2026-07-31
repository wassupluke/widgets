package com.wassupluke.widgets.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LocationCacheTest {
    private val maxAge = TimeUnit.HOURS.toMillis(2)

    @Test
    fun `just-saved location is fresh`() {
        assertTrue(LocationCache.isFresh(savedAtMillis = 1_000L, nowMillis = 1_000L, maxAgeMs = maxAge))
    }

    @Test
    fun `location within the window is fresh`() {
        val savedAt = 1_000L
        val now = savedAt + maxAge - 1
        assertTrue(LocationCache.isFresh(savedAt, now, maxAge))
    }

    @Test
    fun `location exactly at the window edge is still fresh`() {
        val savedAt = 1_000L
        val now = savedAt + maxAge
        assertTrue(LocationCache.isFresh(savedAt, now, maxAge))
    }

    @Test
    fun `location past the window is stale`() {
        val savedAt = 1_000L
        val now = savedAt + maxAge + 1
        assertFalse(LocationCache.isFresh(savedAt, now, maxAge))
    }

    @Test
    fun `location timestamped in the future is not treated as fresh`() {
        // A clock change (or a fix carrying satellite time) must not pin the widget to a stale
        // location forever — the caller should fall through to a live fix instead.
        val now = 1_000L
        assertFalse(LocationCache.isFresh(savedAtMillis = now + 1, nowMillis = now, maxAgeMs = maxAge))
        assertFalse(
            LocationCache.isFresh(savedAtMillis = now + maxAge, nowMillis = now, maxAgeMs = maxAge)
        )
    }

    @Test
    fun `coordinates survive the encode-decode round trip`() {
        // Coordinates are stored as raw bits in Long prefs; this is the surface that would
        // silently corrupt a saved location.
        val latitude = 47.6062
        val longitude = -122.3321
        val decoded = LocationCache.decode(
            latBits = LocationCache.encode(latitude),
            lonBits = LocationCache.encode(longitude),
            savedAtMillis = 1_000L,
            nowMillis = 1_000L,
            maxAgeMs = maxAge
        )
        assertEquals(latitude to longitude, decoded)
    }

    @Test
    fun `negative and zero coordinates survive the round trip`() {
        val decoded = LocationCache.decode(
            latBits = LocationCache.encode(-33.8688),
            lonBits = LocationCache.encode(0.0),
            savedAtMillis = 0L,
            nowMillis = maxAge,
            maxAgeMs = maxAge
        )
        assertEquals(-33.8688 to 0.0, decoded)
    }

    @Test
    fun `decode returns null for a stale entry`() {
        assertNull(
            LocationCache.decode(
                latBits = LocationCache.encode(47.6062),
                lonBits = LocationCache.encode(-122.3321),
                savedAtMillis = 0L,
                nowMillis = maxAge + 1,
                maxAgeMs = maxAge
            )
        )
    }
}
