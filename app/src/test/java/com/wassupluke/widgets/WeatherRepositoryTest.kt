package com.wassupluke.widgets

import com.wassupluke.widgets.data.HttpClient
import com.wassupluke.widgets.data.LocationProvider
import com.wassupluke.widgets.data.LocationResult
import com.wassupluke.widgets.data.RefreshResult
import com.wassupluke.widgets.data.WeatherRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {
    private val sampleJson =
        """{"current":{"temperature_2m":17.3,"weather_code":3}}"""

    private fun repo(location: LocationResult, http: (String) -> String?) =
        WeatherRepository(
            locationProvider = object : LocationProvider {
                override suspend fun getLocation() = location
            },
            httpClient = object : HttpClient {
                override fun get(url: String) = http(url)
            }
        )

    @Test fun successReturnsParsedData() = runTest {
        val result = repo(LocationResult.Available(52.52, 13.41)) { sampleJson }
            .refresh()
        assertTrue(result is RefreshResult.Success)
        assertEquals(17.3, (result as RefreshResult.Success).data.temperature, 0.001)
    }

    @Test fun missingPermissionPropagates() = runTest {
        val result = repo(LocationResult.PermissionMissing) { sampleJson }
            .refresh()
        assertEquals(RefreshResult.NoPermission, result)
    }

    @Test fun noLocationPropagates() = runTest {
        val result = repo(LocationResult.Unavailable) { sampleJson }
            .refresh()
        assertEquals(RefreshResult.NoLocation, result)
    }

    @Test fun networkFailureReturnsError() = runTest {
        val result = repo(LocationResult.Available(1.0, 2.0)) { null }
            .refresh()
        assertEquals(RefreshResult.NetworkError, result)
    }
}
