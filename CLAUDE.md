# CLAUDE.md

This file orients a new Claude Code session for this repository.

## Project Overview

Android home-screen widget app (Kotlin + Jetpack Compose + Glance) showing current temperature and next alarm. Open-Meteo provides weather and geocoding (no API key required). No Hilt, no Room.

## Architecture

**Data flow:**
```
WorkManager (periodic, CONNECTED) ──→ WeatherRepository ──→ Open-Meteo API
                                                         ↓
                                                    DataStore ("weather_settings")
                                                         ↓
                                       SettingsViewModel (UI)  /  WeatherWidget (reads via .first())
```

**Key design decisions:**

1. `WeatherRepository` receives two separate `OpenMeteoService` instances: `weatherService` (api.open-meteo.com) and `geocodingService` (geocoding-api.open-meteo.com) — both wired in `NetworkModule`.
2. Retrofit does not honour Kotlin default parameter values — all `@Query` params must be passed explicitly at every call site.
3. Temperature is always stored as Celsius (`LAST_TEMP_CELSIUS`); F/C conversion happens at display time only.
4. `WorkScheduler.schedule` is guarded: only called when both `LOCATION_LAT` and `LOCATION_LON` are present in DataStore. Uses `ExistingPeriodicWorkPolicy.UPDATE` with a `CONNECTED` network constraint.
5. `WeatherFetchWorker` calls `WeatherWidget().updateAll(context)` after a successful fetch.
6. `SettingsViewModel` accepts a `CoroutineDispatcher` for testability (default `Dispatchers.IO`; inject `StandardTestDispatcher` in tests).
7. `MainActivity.onCreate` launches WorkManager init with `lifecycleScope.launch(Dispatchers.IO)` — never use a bare `CoroutineScope` (leaks on Activity recreation).
8. Both widgets are wrapped in `GlanceTheme`. When dynamic color is on, use `GlanceTheme.colors.primary` for text; otherwise use a static `ColorProvider`. `Preferences.resolveDynamicColor()` in `WeatherDataStore.kt` always returns `false` on pre-API 31.
9. Widget tap uses a `startActivity` Action. An empty/null `WIDGET_TAP_PACKAGE` (or `ALARM_WIDGET_TAP_PACKAGE`) falls back to launching `MainActivity`.
10. **Glance layout isolation (critical):** Never share a `@Composable` root scaffold across multiple `GlanceAppWidget` subclasses. Glance can assign the same layout resource ID to different widgets, causing content bleed. Each widget's Content composable must own its full `Box(fillMaxSize) > Box(clickable)` scaffold independently.
11. `AlarmWidgetReceiver` calls `goAsync()` for all broadcasts **except** `ACTION_APPWIDGET_UPDATE` (Glance's base class already calls `goAsync` internally for that action).

## Build & Test Commands

Run all commands from the repo root.

| Command | Purpose |
|-|-|
| `./gradlew assembleDebug` | Full debug build |
| `./gradlew :app:compileDebugKotlin` | Compile-check only (faster) |
| `./gradlew :app:testDebugUnitTest` | All unit tests |
| `./gradlew :app:testDebugUnitTest --tests "com.wassupluke.widgets.data.WeatherRepositoryTest"` | Single test class |

`./gradlew :app:test` is not supported. All unit tests use Robolectric — no emulator needed.

## Package Structure

```
com.wassupluke.widgets
├── data/
│   ├── WeatherDataStore.kt        # DataStore keys + Context.dataStore extension; resolveDynamicColor(); parseColorSafe()
│   ├── WeatherRepository.kt       # fetchAndCacheWeather(), reverseGeocodeLocation(), geocodeLocation(); companion .create()
│   └── api/
│       ├── WeatherApiModels.kt    # @Serializable data classes
│       ├── OpenMeteoService.kt    # Retrofit interface (no default param values)
│       └── NetworkModule.kt       # Two Retrofit+OkHttp instances (weather + geocoding)
├── ui/
│   ├── MainActivity.kt            # Manual ViewModelProvider.Factory; WorkManager init on Dispatchers.IO; fetchAndSaveDeviceLocation()
│   ├── theme/SimpleWeatherTheme.kt
│   └── settings/
│       ├── SettingsViewModel.kt   # StateFlow<SettingsUiState> from DataStore; dispatcher injection
│       └── SettingsScreen.kt      # App picker; color/font/tap controls for both widgets
├── widget/
│   ├── WeatherWidget.kt           # GlanceAppWidget; temp display; dynamic color; tap-to-launch
│   ├── WeatherWidgetReceiver.kt   # GlanceAppWidgetReceiver stub
│   ├── AlarmWidget.kt             # GlanceAppWidget; next alarm display; icon + text layout
│   └── AlarmWidgetReceiver.kt     # GlanceAppWidgetReceiver; handles alarm/time/boot broadcasts; goAsync()
└── worker/
    ├── WeatherFetchWorker.kt      # CoroutineWorker; calls WeatherWidget().updateAll() on success
    └── WorkScheduler.kt           # enqueueUniquePeriodicWork UPDATE policy; CONNECTED constraint
```

**DataStore keys (store name: `weather_settings`):** `USE_DEVICE_LOCATION`, `LOCATION_LAT`, `LOCATION_LON`, `LOCATION_DISPLAY_NAME`, `LOCATION_QUERY`, `TEMP_UNIT` (default `"F"`), `UPDATE_INTERVAL_MINUTES` (default `60`), `LAST_TEMP_CELSIUS`, `LAST_UPDATED_EPOCH`, `WIDGET_TEXT_COLOR`, `WIDGET_TAP_PACKAGE`, `WIDGET_DYNAMIC_COLOR`, `FONT_SIZE` (default `48`), `ALARM_WIDGET_TAP_PACKAGE`, `ALARM_TEXT`.

## Testing Conventions

- `@RunWith(RobolectricTestRunner::class)` + `ApplicationProvider.getApplicationContext()` on all test classes.
- `@Before` must call `context.dataStore.edit { it.clear() }` to prevent cross-test DataStore pollution.
- Mock `OpenMeteoService` with MockK; pass separate mocks as `weatherService` and `geocodingService`.
- ViewModel tests: inject `StandardTestDispatcher(testScheduler)` and call `advanceUntilIdle()`.
- `SettingsViewModel.uiState` uses `stateIn(WhileSubscribed(5000))` — activate upstream before asserting: `backgroundScope.launch { vm.uiState.collect {} }`.
- Assert ViewModel state via `vm.uiState.filter { ... }.first()`, not by reading DataStore directly.

## Dependency Versions

| Library | Version |
|-|-|
| Kotlin | 2.3.20 |
| Compose BOM | 2026.03.00 |
| Glance | 1.2.0-rc01 |
| WorkManager | 2.11.1 |
| DataStore | 1.2.1 |
| Retrofit | 3.0.0 |
| Min SDK / Target SDK | 26 / 36 |

All version aliases live in `gradle/libs.versions.toml`.
