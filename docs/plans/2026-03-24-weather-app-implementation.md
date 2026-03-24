# Simple Weather App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a minimal Android weather app with a home screen widget that displays the current temperature, powered by Open-Meteo (no API key required).

**Architecture:** MVVM with Repository pattern. DataStore for settings + weather cache. WorkManager for periodic background fetches. Jetpack Glance for the AppWidget. No DI framework, no Room.

**Tech Stack:** Kotlin 2.0, Jetpack Compose + Material3, Glance 1.1, WorkManager 2.10, DataStore 1.1, Retrofit 2.11 + kotlinx.serialization, FusedLocationProviderClient, Navigation Compose 2.8

---

## Package & Project Conventions

- Package: `com.example.simpleweather`
- Min SDK: 26 / Target SDK: 35
- All Gradle files use Kotlin DSL (`.kts`)
- Test framework: JUnit4 + Kotlin Coroutines Test + Robolectric (for Android context in unit tests)
- No Hilt/Dagger — manual dependency passing

---

## Task 1: Project Scaffold

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

**Step 1: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
navigationCompose = "2.8.5"
glance = "1.1.1"
workManager = "2.10.0"
dataStore = "1.1.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
retrofitKotlinxConverter = "1.0.0"
playServicesLocation = "21.3.0"
lifecycle = "2.8.7"
coreKtx = "1.15.0"
junit = "4.13.2"
coroutinesTest = "1.9.0"
robolectric = "4.14"
androidxTestCore = "1.6.1"
mockk = "1.13.13"
androidxTestJunit = "1.2.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "dataStore" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
retrofit-kotlinx-converter = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofitKotlinxConverter" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
androidx-test-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

**Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SimpleWeather"
include(":app")
```

**Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.simpleweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.simpleweather"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM — version-less entries below are resolved through the BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Glance (widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // DataStore
    implementation(libs.datastore.preferences)

    // Retrofit + serialization
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.converter)

    // Location
    implementation(libs.play.services.location)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Core
    implementation(libs.core.ktx)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.junit)
}
```

**Step 5: Create `gradle.properties`**

```properties
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 6: Create base `AndroidManifest.xml`** at `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SimpleWeather">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Widget receiver — filled in Task 8 -->

    </application>
</manifest>
```

**Step 7: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Simple Weather</string>
    <string name="widget_description">Displays current temperature</string>
</resources>
```

**Step 8: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SimpleWeather" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

**Step 9: Sync and verify project compiles**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL (may take a few minutes on first run)

**Step 10: Commit**

```bash
git add -A
git commit -m "feat: scaffold Android project with Compose + Glance + WorkManager deps"
```

---

## Task 2: DataStore — Settings & Weather Cache

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/data/WeatherDataStore.kt`
- Create: `app/src/test/java/com/example/simpleweather/data/WeatherDataStoreTest.kt`

**Step 1: Write the failing test** at `app/src/test/.../data/WeatherDataStoreTest.kt`

```kotlin
package com.example.simpleweather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `default temp unit is C`() = runTest {
        val prefs = context.dataStore.data.first()
        assertEquals("C", prefs[WeatherDataStore.TEMP_UNIT] ?: "C")
    }

    @Test
    fun `writing and reading last temp celsius`() = runTest {
        context.dataStore.edit { it[WeatherDataStore.LAST_TEMP_CELSIUS] = 21.5f }
        val prefs = context.dataStore.data.first()
        assertEquals(21.5f, prefs[WeatherDataStore.LAST_TEMP_CELSIUS])
    }

    @Test
    fun `no location stored by default`() = runTest {
        val prefs = context.dataStore.data.first()
        assertNull(prefs[WeatherDataStore.LOCATION_LAT])
        assertNull(prefs[WeatherDataStore.LOCATION_LON])
    }
}
```

**Step 2: Run test to see it fail**

```bash
./gradlew :app:test --tests "*.WeatherDataStoreTest" 2>&1 | tail -20
```
Expected: compilation error — `WeatherDataStore` not found

**Step 3: Create `WeatherDataStore.kt`**

```kotlin
package com.example.simpleweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_settings")

object WeatherDataStore {
    val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
    val LOCATION_LAT = floatPreferencesKey("location_lat")
    val LOCATION_LON = floatPreferencesKey("location_lon")
    val LOCATION_DISPLAY_NAME = stringPreferencesKey("location_display_name")
    val TEMP_UNIT = stringPreferencesKey("temp_unit")                   // "C" or "F"
    val UPDATE_INTERVAL_MINUTES = intPreferencesKey("update_interval_minutes")
    val LAST_TEMP_CELSIUS = floatPreferencesKey("last_temp_celsius")
    val LAST_UPDATED_EPOCH = longPreferencesKey("last_updated_epoch")
}
```

**Step 4: Run test to verify it passes**

```bash
./gradlew :app:test --tests "*.WeatherDataStoreTest"
```
Expected: 3 tests pass

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/data/WeatherDataStore.kt \
        app/src/test/java/com/example/simpleweather/data/WeatherDataStoreTest.kt
git commit -m "feat: add DataStore keys for settings and weather cache"
```

---

## Task 3: Open-Meteo API Client

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/data/api/WeatherApiModels.kt`
- Create: `app/src/main/java/com/example/simpleweather/data/api/OpenMeteoService.kt`
- Create: `app/src/main/java/com/example/simpleweather/data/api/NetworkModule.kt`
- Create: `app/src/test/java/com/example/simpleweather/data/api/OpenMeteoServiceTest.kt`

**Step 1: Create `WeatherApiModels.kt`**

```kotlin
package com.example.simpleweather.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val current: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val time: String,
    @SerialName("temperature_2m") val temperatureCelsius: Float
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val state: String? = null
)
```

**Step 2: Create `OpenMeteoService.kt`**

```kotlin
package com.example.simpleweather.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {

    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Float,
        @Query("longitude") longitude: Float,
        @Query("current") current: String = "temperature_2m",
        @Query("temperature_unit") temperatureUnit: String = "celsius"
    ): WeatherResponse

    @GET("v1/search")
    suspend fun searchLocation(
        @Query("name") query: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
```

Note: `searchLocation` needs a separate Retrofit instance (different base URL). See `NetworkModule.kt`.

**Step 3: Create `NetworkModule.kt`**

```kotlin
package com.example.simpleweather.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()

    private val okHttpClient = OkHttpClient.Builder().build()

    val weatherService: OpenMeteoService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(OpenMeteoService::class.java)

    val geocodingService: OpenMeteoService = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(OpenMeteoService::class.java)
}
```

**Step 4: Write API model test** at `app/src/test/.../data/api/OpenMeteoServiceTest.kt`

```kotlin
package com.example.simpleweather.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes weather response correctly`() {
        val raw = """
            {
              "current": {
                "time": "2024-01-01T12:00",
                "temperature_2m": 22.3
              }
            }
        """.trimIndent()
        val response = json.decodeFromString<WeatherResponse>(raw)
        assertEquals(22.3f, response.current.temperatureCelsius, 0.01f)
    }

    @Test
    fun `deserializes geocoding response correctly`() {
        val raw = """
            {
              "results": [{
                "name": "New York",
                "latitude": 40.71427,
                "longitude": -74.00597,
                "country": "United States",
                "admin1": "New York"
              }]
            }
        """.trimIndent()
        val response = json.decodeFromString<GeocodingResponse>(raw)
        assertEquals("New York", response.results?.first()?.name)
        assertEquals(40.71427, response.results?.first()?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun `geocoding response with empty results does not crash`() {
        val raw = """{}"""
        val response = json.decodeFromString<GeocodingResponse>(raw)
        assertEquals(null, response.results)
    }
}
```

**Step 5: Run tests**

```bash
./gradlew :app:test --tests "*.OpenMeteoServiceTest"
```
Expected: 3 tests pass

**Step 6: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/data/api/ \
        app/src/test/java/com/example/simpleweather/data/api/
git commit -m "feat: add Open-Meteo API models, Retrofit service, and NetworkModule"
```

---

## Task 4: WeatherRepository

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/data/WeatherRepository.kt`
- Create: `app/src/test/java/com/example/simpleweather/data/WeatherRepositoryTest.kt`

**Step 1: Write failing test**

```kotlin
package com.example.simpleweather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.simpleweather.data.api.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockWeatherService = mockk<OpenMeteoService>()
    private val mockGeocodingService = mockk<OpenMeteoService>()

    private fun repo() = WeatherRepository(context, mockWeatherService, mockGeocodingService)

    @Test
    fun `fetchAndCacheWeather stores temperature in DataStore`() = runTest {
        coEvery {
            mockWeatherService.getCurrentWeather(any(), any(), any(), any())
        } returns WeatherResponse(CurrentWeather("2024-01-01T12:00", 21.5f))

        repo().fetchAndCacheWeather(lat = 40.71f, lon = -74.01f)

        val prefs = context.dataStore.data.first()
        assertEquals(21.5f, prefs[WeatherDataStore.LAST_TEMP_CELSIUS] ?: 0f, 0.01f)
        assertTrue((prefs[WeatherDataStore.LAST_UPDATED_EPOCH] ?: 0L) > 0L)
    }

    @Test
    fun `geocodeLocation returns lat-lon and display name on success`() = runTest {
        coEvery { mockGeocodingService.searchLocation(any(), any(), any(), any()) } returns
            GeocodingResponse(listOf(GeocodingResult("Portland", 45.52, -122.68, "United States", "Oregon")))

        val result = repo().geocodeLocation("Portland")

        assertNotNull(result)
        assertEquals(45.52f, result!!.lat, 0.01f)
        assertEquals(-122.68f, result.lon, 0.01f)
        assertEquals("Portland, Oregon", result.displayName)
    }

    @Test
    fun `geocodeLocation returns null when no results`() = runTest {
        coEvery { mockGeocodingService.searchLocation(any(), any(), any(), any()) } returns
            GeocodingResponse(emptyList())

        val result = repo().geocodeLocation("XYZNotAPlace")
        assertNull(result)
    }
}
```

**Step 2: Run test — see it fail**

```bash
./gradlew :app:test --tests "*.WeatherRepositoryTest" 2>&1 | tail -10
```
Expected: compilation error

**Step 3: Create `WeatherRepository.kt`**

```kotlin
package com.example.simpleweather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.simpleweather.data.api.OpenMeteoService

class WeatherRepository(
    private val context: Context,
    private val weatherService: OpenMeteoService,
    private val geocodingService: OpenMeteoService
) {
    data class GeocodingResult(val lat: Float, val lon: Float, val displayName: String)

    suspend fun fetchAndCacheWeather(lat: Float, lon: Float) {
        val response = weatherService.getCurrentWeather(
            latitude = lat,
            longitude = lon
        )
        context.dataStore.edit { prefs ->
            prefs[WeatherDataStore.LAST_TEMP_CELSIUS] = response.current.temperatureCelsius
            prefs[WeatherDataStore.LAST_UPDATED_EPOCH] = System.currentTimeMillis()
        }
    }

    suspend fun geocodeLocation(query: String): GeocodingResult? {
        val response = geocodingService.searchLocation(query)
        val first = response.results?.firstOrNull() ?: return null
        val displayName = listOfNotNull(first.name, first.state).joinToString(", ")
        return GeocodingResult(
            lat = first.latitude.toFloat(),
            lon = first.longitude.toFloat(),
            displayName = displayName
        )
    }
}
```

**Step 4: Run tests**

```bash
./gradlew :app:test --tests "*.WeatherRepositoryTest"
```
Expected: 3 tests pass

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/data/WeatherRepository.kt \
        app/src/test/java/com/example/simpleweather/data/WeatherRepositoryTest.kt
git commit -m "feat: add WeatherRepository with fetch-and-cache and geocoding"
```

---

## Task 5: WorkManager Weather Fetch Worker

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/worker/WeatherFetchWorker.kt`

Note: WorkManager workers are hard to unit test without the full Android environment. We skip the unit test here and rely on integration validation in Task 9.

**Step 1: Create `WeatherFetchWorker.kt`**

```kotlin
package com.example.simpleweather.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.simpleweather.data.WeatherDataStore
import com.example.simpleweather.data.WeatherRepository
import com.example.simpleweather.data.api.NetworkModule
import com.example.simpleweather.data.dataStore
import com.example.simpleweather.widget.WeatherWidget
import kotlinx.coroutines.flow.first

class WeatherFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val lat = prefs[WeatherDataStore.LOCATION_LAT] ?: return Result.failure()
        val lon = prefs[WeatherDataStore.LOCATION_LON] ?: return Result.failure()

        return try {
            val repo = WeatherRepository(
                context = applicationContext,
                weatherService = NetworkModule.weatherService,
                geocodingService = NetworkModule.geocodingService
            )
            repo.fetchAndCacheWeather(lat = lat, lon = lon)
            WeatherWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

**Step 2: Create WorkManager scheduler helper**

Add this to `WeatherRepository.kt` as a companion object, or create a separate file `app/src/main/java/com/example/simpleweather/worker/WorkScheduler.kt`:

```kotlin
package com.example.simpleweather.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "weather_fetch"

    fun schedule(context: Context, intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<WeatherFetchWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
```

**Step 3: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: no errors

**Step 4: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/worker/
git commit -m "feat: add WorkManager weather fetch worker and scheduler"
```

---

## Task 6: Settings ViewModel

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/ui/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/example/simpleweather/ui/settings/SettingsViewModelTest.kt`

**Step 1: Write failing test**

```kotlin
package com.example.simpleweather.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.simpleweather.data.WeatherDataStore
import com.example.simpleweather.data.WeatherRepository
import com.example.simpleweather.data.dataStore
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockRepository = mockk<WeatherRepository>(relaxed = true)

    @Test
    fun `setTempUnit writes to DataStore`() = runTest {
        val vm = SettingsViewModel(context, mockRepository, testScheduler)
        vm.setTempUnit("F")
        advanceUntilIdle()
        val prefs = context.dataStore.data.first()
        assertEquals("F", prefs[WeatherDataStore.TEMP_UNIT])
    }

    @Test
    fun `setUpdateInterval writes to DataStore`() = runTest {
        val vm = SettingsViewModel(context, mockRepository, testScheduler)
        vm.setUpdateInterval(60)
        advanceUntilIdle()
        val prefs = context.dataStore.data.first()
        assertEquals(60, prefs[WeatherDataStore.UPDATE_INTERVAL_MINUTES])
    }
}
```

**Step 2: Run test — see it fail**

```bash
./gradlew :app:test --tests "*.SettingsViewModelTest" 2>&1 | tail -10
```

**Step 3: Create `SettingsViewModel.kt`**

```kotlin
package com.example.simpleweather.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleweather.data.WeatherDataStore
import com.example.simpleweather.data.WeatherRepository
import com.example.simpleweather.data.dataStore
import com.example.simpleweather.worker.WorkScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val useDeviceLocation: Boolean = false,
    val locationDisplayName: String = "",
    val tempUnit: String = "C",
    val updateIntervalMinutes: Int = 60
)

class SettingsViewModel(
    private val context: Context,
    private val repository: WeatherRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = context.dataStore.data.map { prefs ->
        SettingsUiState(
            useDeviceLocation = prefs[WeatherDataStore.USE_DEVICE_LOCATION] ?: false,
            locationDisplayName = prefs[WeatherDataStore.LOCATION_DISPLAY_NAME] ?: "",
            tempUnit = prefs[WeatherDataStore.TEMP_UNIT] ?: "C",
            updateIntervalMinutes = prefs[WeatherDataStore.UPDATE_INTERVAL_MINUTES] ?: 60
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTempUnit(unit: String) {
        viewModelScope.launch(dispatcher) {
            context.dataStore.edit { it[WeatherDataStore.TEMP_UNIT] = unit }
        }
    }

    fun setUpdateInterval(minutes: Int) {
        viewModelScope.launch(dispatcher) {
            context.dataStore.edit { it[WeatherDataStore.UPDATE_INTERVAL_MINUTES] = minutes }
            WorkScheduler.schedule(context, minutes)
        }
    }

    fun setUseDeviceLocation(use: Boolean) {
        viewModelScope.launch(dispatcher) {
            context.dataStore.edit { it[WeatherDataStore.USE_DEVICE_LOCATION] = use }
        }
    }

    /** Called when user types a static location (city, zip, lat,lon) */
    fun resolveAndSaveLocation(query: String) {
        viewModelScope.launch(dispatcher) {
            val result = repository.geocodeLocation(query) ?: return@launch
            context.dataStore.edit { prefs ->
                prefs[WeatherDataStore.LOCATION_LAT] = result.lat
                prefs[WeatherDataStore.LOCATION_LON] = result.lon
                prefs[WeatherDataStore.LOCATION_DISPLAY_NAME] = result.displayName
            }
        }
    }

    /** Called after GPS location is obtained */
    fun saveDeviceLocation(lat: Float, lon: Float) {
        viewModelScope.launch(dispatcher) {
            context.dataStore.edit { prefs ->
                prefs[WeatherDataStore.LOCATION_LAT] = lat
                prefs[WeatherDataStore.LOCATION_LON] = lon
                prefs[WeatherDataStore.LOCATION_DISPLAY_NAME] = "Current Location"
            }
        }
    }
}
```

**Step 4: Run tests**

```bash
./gradlew :app:test --tests "*.SettingsViewModelTest"
```
Expected: 2 tests pass

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/ui/settings/SettingsViewModel.kt \
        app/src/test/java/com/example/simpleweather/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add SettingsViewModel with DataStore-backed state and WorkManager scheduling"
```

---

## Task 7: Settings Screen UI

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/ui/settings/SettingsScreen.kt`

No unit test for pure Compose UI — this is validated visually.

**Step 1: Create `SettingsScreen.kt`**

```kotlin
package com.example.simpleweather.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationInput by remember { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Location resolved in MainActivity after permission is granted
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Simple Weather") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // --- Location ---
            Text("Location", style = MaterialTheme.typography.titleSmall)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use device location", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.useDeviceLocation,
                    onCheckedChange = { use ->
                        viewModel.setUseDeviceLocation(use)
                        if (use) locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                )
            }

            if (!uiState.useDeviceLocation) {
                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("City, zip code, or lat,lon") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (locationInput.isNotBlank()) {
                            TextButton(onClick = {
                                viewModel.resolveAndSaveLocation(locationInput)
                            }) { Text("Set") }
                        }
                    }
                )
                if (uiState.locationDisplayName.isNotEmpty()) {
                    Text(
                        text = "Current: ${uiState.locationDisplayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Temperature Unit ---
            Text("Temperature Unit", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("C", "F").forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = uiState.tempUnit == unit,
                        onClick = { viewModel.setTempUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text("°$unit") }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Update Interval ---
            Text("Update Interval", style = MaterialTheme.typography.titleSmall)
            val intervals = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hr", 180 to "3 hr", 360 to "6 hr")
            intervals.forEach { (minutes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = uiState.updateIntervalMinutes == minutes,
                        onClick = { viewModel.setUpdateInterval(minutes) }
                    )
                    Text(label)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

**Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/ui/settings/SettingsScreen.kt
git commit -m "feat: add Compose settings screen with location, unit, and interval controls"
```

---

## Task 8: Glance Widget

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/widget/WeatherWidget.kt`
- Create: `app/src/main/java/com/example/simpleweather/widget/WeatherWidgetReceiver.kt`
- Create: `app/src/main/res/xml/weather_widget_info.xml`

**Step 1: Create `app/src/main/res/xml/weather_widget_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="80dp"
    android:minHeight="80dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:description="@string/widget_description"
    android:resizeMode="horizontal|vertical" />
```

Note: `updatePeriodMillis="0"` because WorkManager handles updates — we don't want Android to poll.

**Step 2: Create `WeatherWidget.kt`**

```kotlin
package com.example.simpleweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import com.example.simpleweather.data.WeatherDataStore
import com.example.simpleweather.data.dataStore
import com.example.simpleweather.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val tempCelsius = prefs[WeatherDataStore.LAST_TEMP_CELSIUS]
        val unit = prefs[WeatherDataStore.TEMP_UNIT] ?: "C"

        val displayTemp = if (tempCelsius == null) {
            "--°"
        } else {
            val value = if (unit == "F") celsiusToFahrenheit(tempCelsius) else tempCelsius.roundToInt()
            "$value°"
        }

        provideContent {
            WeatherWidgetContent(displayTemp = displayTemp)
        }
    }

    private fun celsiusToFahrenheit(celsius: Float): Int =
        ((celsius * 9f / 5f) + 32f).roundToInt()
}

@Composable
private fun WeatherWidgetContent(displayTemp: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayTemp,
            style = TextStyle(
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = ColorProvider(
                    day = androidx.compose.ui.graphics.Color.Black,
                    night = androidx.compose.ui.graphics.Color.White
                )
            )
        )
    }
}
```

**Step 3: Create `WeatherWidgetReceiver.kt`**

```kotlin
package com.example.simpleweather.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
```

**Step 4: Register widget in `AndroidManifest.xml`**

Inside `<application>`, replace the `<!-- Widget receiver -->` comment with:

```xml
<receiver
    android:name=".widget.WeatherWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/weather_widget_info" />
</receiver>
```

**Step 5: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/widget/ \
        app/src/main/res/xml/ \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add Glance AppWidget showing current temperature"
```

---

## Task 9: MainActivity and Navigation

**Files:**
- Create: `app/src/main/java/com/example/simpleweather/ui/MainActivity.kt`
- Modify: nothing else — the ViewModel is wired here

**Step 1: Create `MainActivity.kt`**

```kotlin
package com.example.simpleweather.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simpleweather.data.WeatherRepository
import com.example.simpleweather.data.api.NetworkModule
import com.example.simpleweather.ui.settings.SettingsScreen
import com.example.simpleweather.ui.settings.SettingsViewModel
import com.example.simpleweather.ui.theme.SimpleWeatherTheme
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = WeatherRepository(
                    context = applicationContext,
                    weatherService = NetworkModule.weatherService,
                    geocodingService = NetworkModule.geocodingService
                )
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(applicationContext, repo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleWeatherTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }

    /** Called from SettingsScreen when location permission is granted */
    @SuppressLint("MissingPermission")
    fun fetchAndSaveDeviceLocation() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                viewModel.saveDeviceLocation(it.latitude.toFloat(), it.longitude.toFloat())
            }
        }
    }
}
```

**Step 2: Create the theme file** at `app/src/main/java/com/example/simpleweather/ui/theme/SimpleWeatherTheme.kt`

```kotlin
package com.example.simpleweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun SimpleWeatherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialTheme(
        colorScheme = dynamicLightColorScheme(context),
        content = content
    )
}
```

**Step 3: Build and check for errors**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL

**Step 4: Update SettingsScreen to call fetchAndSaveDeviceLocation on permission grant**

In `SettingsScreen.kt`, update the permission launcher result handler:

```kotlin
val activity = LocalContext.current as? MainActivity

val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        activity?.fetchAndSaveDeviceLocation()
    }
}
```

**Step 5: Final build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 6: Run all unit tests**

```bash
./gradlew :app:test
```
Expected: all tests pass

**Step 7: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/ui/
git commit -m "feat: add MainActivity, theme, and GPS location integration"
```

---

## Task 10: WorkManager Initialization on First Launch

**Files:**
- Modify: `MainActivity.kt` — trigger initial WorkManager schedule and first fetch

**Step 1: Add first-launch initialization to `MainActivity.onCreate`**

In `MainActivity.kt`, after `setContent { ... }`, add:

```kotlin
// Schedule WorkManager on first launch (if location is already set)
CoroutineScope(Dispatchers.IO).launch {
    val prefs = applicationContext.dataStore.data.first()
    val lat = prefs[WeatherDataStore.LOCATION_LAT]
    val lon = prefs[WeatherDataStore.LOCATION_LON]
    val intervalMinutes = prefs[WeatherDataStore.UPDATE_INTERVAL_MINUTES] ?: 60

    if (lat != null && lon != null) {
        WorkScheduler.schedule(applicationContext, intervalMinutes)
    }
}
```

Add the necessary imports:
```kotlin
import com.example.simpleweather.data.WeatherDataStore
import com.example.simpleweather.data.dataStore
import com.example.simpleweather.worker.WorkScheduler
import kotlinx.coroutines.flow.first
```

**Step 2: Final build and test**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:test
```
Expected: BUILD SUCCESSFUL, all tests pass

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/simpleweather/ui/MainActivity.kt
git commit -m "feat: schedule WorkManager on first launch when location is configured"
```

---

## Manual Smoke Test Checklist

After building and installing on a device/emulator:

1. Launch app — settings screen appears with top bar "Simple Weather"
2. Enable "Use device location" toggle — permission dialog appears
3. Grant permission — device location resolves and is saved
4. Set temperature unit to F
5. Set update interval to 30 min
6. Long-press home screen → Widgets → find "Simple Weather" widget
7. Add widget to home screen — shows `--°` until first fetch
8. Wait for WorkManager fetch OR force-trigger:
   ```bash
   adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS --es "work_id" "weather_fetch"
   ```
   Or use Android Studio's Background Task Inspector to manually trigger the worker
9. Widget updates to show temperature (e.g., `72°` in F mode)
10. Tap widget — Settings screen opens

---

## Wishlist (not in scope)

See `docs/plans/2026-03-23-weather-app-design.md` for the full wishlist including:
- Widget condition icon + high/low
- Widget font picker
- Room + Hilt migration (v2)
- Temperature trend sparkline widget (12/24hr graph)
