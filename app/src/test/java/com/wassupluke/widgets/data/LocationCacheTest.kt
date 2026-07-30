package com.wassupluke.widgets.data

import org.junit.Assert.assertFalse
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
}
