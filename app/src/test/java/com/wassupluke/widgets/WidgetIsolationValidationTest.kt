package com.wassupluke.widgets

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.wassupluke.widgets.data.WeatherDataStore
import com.wassupluke.widgets.data.WeatherRepository
import com.wassupluke.widgets.data.api.CurrentWeather
import com.wassupluke.widgets.data.api.GeocodingResponse
import com.wassupluke.widgets.data.api.GeocodingResult
import com.wassupluke.widgets.data.api.OpenMeteoService
import com.wassupluke.widgets.data.api.WeatherResponse
import com.wassupluke.widgets.data.dataStore
import com.wassupluke.widgets.ui.settings.SettingsViewModel
import com.wassupluke.widgets.worker.WorkScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validation flow:
 * 1. Device location → 15-min interval configured; weather temp cached; alarm text unaffected
 * 2. Alarm DataStore key update is isolated from LAST_TEMP_CELSIUS (no cross-key bleed)
 * 3. Disable device location (no manual fallback) → location + temp keys cleared → "--°"
 *    Alarm text must survive the toggle
 * 4. Set manual location "85719" → temp updated; alarm text unchanged
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetIsolationValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application
    private val mockWeatherService = mockk<OpenMeteoService>()
    private val mockGeocodingService = mockk<OpenMeteoService>()

    private fun repo() = WeatherRepository(application, mockWeatherService, mockGeocodingService)
    private fun vm() = SettingsViewModel(application, repo(), testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(WorkScheduler)
        every { WorkScheduler.schedule(any(), any()) } just Runs
        every { WorkScheduler.cancel(any()) } just Runs
        runBlocking { context.dataStore.edit { it.clear() } }
    }

    @After
    fun tearDown() {
        unmockkObject(WorkScheduler)
        Dispatchers.resetMain()
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 1: device location active, 15-min interval, temp cached
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `saveDeviceLocation caches temp and does not touch alarm text`() = runTest(testDispatcher) {
        coEvery { mockWeatherService.getCurrentWeather(any(), any(), any(), any()) } returns
            WeatherResponse(CurrentWeather("2026-04-03T12:00", 28.5f))

        runBlocking {
            context.dataStore.edit { it[WeatherDataStore.ALARM_TEXT] = "7:00 AM" }
        }

        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setUseDeviceLocation(true)
        vm.setUpdateInterval(15)
        vm.saveDeviceLocation(32.22f, -110.97f)
        advanceUntilIdle()

        // Wait for DataStore to emit with lat present (background write may lag advanceUntilIdle)
        val locPrefs = context.dataStore.data.filter { it[WeatherDataStore.LOCATION_LAT] != null }.first()
        assertNotNull("Lat should be stored", locPrefs[WeatherDataStore.LOCATION_LAT])
        assertNotNull("Lon should be stored", locPrefs[WeatherDataStore.LOCATION_LON])

        // Wait for temp to be written (fetchAndCacheWeather is a separate edit)
        val tempPrefs = context.dataStore.data.filter { it[WeatherDataStore.LAST_TEMP_CELSIUS] != null }.first()
        assertEquals(28.5f, tempPrefs[WeatherDataStore.LAST_TEMP_CELSIUS] ?: 0f, 0.01f)

        // Alarm key must not have been touched
        val prefs = context.dataStore.data.first()
        assertEquals("7:00 AM", prefs[WeatherDataStore.ALARM_TEXT])
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 2: alarm key is isolated from the temperature key
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `writing ALARM_TEXT does not alter LAST_TEMP_CELSIUS`() = runTest(testDispatcher) {
        runBlocking {
            context.dataStore.edit {
                it[WeatherDataStore.LAST_TEMP_CELSIUS] = 28.5f
                it[WeatherDataStore.ALARM_TEXT] = "No alarm"
            }
        }

        // Simulate what AlarmWidgetReceiver writes
        runBlocking {
            context.dataStore.edit { it[WeatherDataStore.ALARM_TEXT] = "7:00 AM" }
        }

        val prefs = context.dataStore.data.first()
        assertEquals(
            "Temp must be unchanged after alarm text update",
            28.5f, prefs[WeatherDataStore.LAST_TEMP_CELSIUS] ?: 0f, 0.01f
        )
        assertEquals("7:00 AM", prefs[WeatherDataStore.ALARM_TEXT])
    }

    @Test
    fun `writing LAST_TEMP_CELSIUS does not alter ALARM_TEXT`() = runTest(testDispatcher) {
        runBlocking {
            context.dataStore.edit { it[WeatherDataStore.ALARM_TEXT] = "7:00 AM" }
        }

        // Simulate what WeatherFetchWorker writes
        runBlocking {
            context.dataStore.edit { it[WeatherDataStore.LAST_TEMP_CELSIUS] = 28.5f }
        }

        val prefs = context.dataStore.data.first()
        assertEquals("Alarm text must survive a weather update",
            "7:00 AM", prefs[WeatherDataStore.ALARM_TEXT])
        assertEquals(28.5f, prefs[WeatherDataStore.LAST_TEMP_CELSIUS] ?: 0f, 0.01f)
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 3: disabling device location (no manual fallback)
    //   - location + temp keys must be cleared → widget shows "--°"
    //   - alarm text must survive the toggle
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `disabling device location clears location and temp keys but preserves alarm text`() =
        runTest(testDispatcher) {
            runBlocking {
                context.dataStore.edit {
                    it[WeatherDataStore.USE_DEVICE_LOCATION] = true
                    it[WeatherDataStore.LOCATION_LAT] = 32.22f
                    it[WeatherDataStore.LOCATION_LON] = -110.97f
                    it[WeatherDataStore.LAST_TEMP_CELSIUS] = 28.5f
                    it[WeatherDataStore.ALARM_TEXT] = "7:00 AM"
                }
            }

            val vm = vm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.setUseDeviceLocation(false)
            advanceUntilIdle()

            // Wait for the atomic edit to land: USE_DEVICE_LOCATION=false AND lat removed
            val prefs = context.dataStore.data
                .filter { !(it[WeatherDataStore.USE_DEVICE_LOCATION] ?: true) }
                .first()
            assertNull("Lat must be cleared when no location remains",
                prefs[WeatherDataStore.LOCATION_LAT])
            assertNull("Lon must be cleared when no location remains",
                prefs[WeatherDataStore.LOCATION_LON])
            assertNull("Temp must be cleared so weather widget shows '--°'",
                prefs[WeatherDataStore.LAST_TEMP_CELSIUS])
            // Alarm is independent — must survive the location toggle
            assertEquals("Alarm text must survive device-location toggle",
                "7:00 AM", prefs[WeatherDataStore.ALARM_TEXT])
        }

    // ──────────────────────────────────────────────────────────────
    // Scenario 4: manual location "85719" → temp updated; alarm unchanged
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `resolveAndSaveLocation updates location and preserves alarm text`() =
        runTest(testDispatcher) {
            coEvery { mockGeocodingService.searchLocation(any(), any(), any(), any()) } returns
                GeocodingResponse(listOf(GeocodingResult("Tucson", 32.22, -110.97, "United States", "Arizona")))
            coEvery { mockWeatherService.getCurrentWeather(any(), any(), any(), any()) } returns
                WeatherResponse(CurrentWeather("2026-04-03T12:00", 22.0f))

            runBlocking {
                context.dataStore.edit { it[WeatherDataStore.ALARM_TEXT] = "7:00 AM" }
            }

            val vm = vm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.resolveAndSaveLocation("85719")
            advanceUntilIdle()

            // Wait for uiState to reflect the resolved location (confirms geocoding completed)
            val state = vm.uiState.filter { it.locationDisplayName.isNotEmpty() }.first()
            assertEquals("Tucson, Arizona, United States", state.locationDisplayName)

            val prefs = context.dataStore.data.first()
            assertNotNull("Lat should be set after geocoding", prefs[WeatherDataStore.LOCATION_LAT])
            assertEquals("Alarm text must survive location change",
                "7:00 AM", prefs[WeatherDataStore.ALARM_TEXT])
        }

    @Test
    fun `fetchAndCacheWeather for 85719 coordinates writes correct temp`() = runTest(testDispatcher) {
        // Validates that the weather fetch path (used by resolveAndSaveLocation) stores the temp
        coEvery { mockWeatherService.getCurrentWeather(any(), any(), any(), any()) } returns
            WeatherResponse(CurrentWeather("2026-04-03T12:00", 22.0f))

        repo().fetchAndCacheWeather(lat = 32.22f, lon = -110.97f)

        val prefs = context.dataStore.data.first()
        assertEquals(22.0f, prefs[WeatherDataStore.LAST_TEMP_CELSIUS] ?: 0f, 0.01f)
    }
}
