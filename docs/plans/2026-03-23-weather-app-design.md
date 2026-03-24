# Simple Weather App — Design Document
_2026-03-23_

## Overview

A minimal Android weather app whose primary value is a clean home screen widget
displaying the current temperature. A settings screen lets the user configure
location, units, and update frequency.

---

## Architecture

**Stack:**
- Kotlin + Jetpack Compose + Material Design 3
- Min SDK 26 / Target SDK 35
- Navigation Compose (2 destinations: home entry point → settings)
- Jetpack Glance (`GlanceAppWidget`) for the AppWidget
- WorkManager for periodic background weather fetches
- DataStore (Preferences) for settings + last-known weather cache
- Retrofit + kotlinx.serialization for Open-Meteo API calls
- FusedLocationProviderClient for GPS location

**Data flow:**
```
WorkManager (periodic) → WeatherRepository → Open-Meteo API
                                           ↓
                                      DataStore (cache last reading)
                                           ↓
                              ViewModel (settings screen)
                              Glance Widget (reads DataStore directly)
```

No DI framework (Hilt adds unnecessary complexity at this scale).
No Room (DataStore is sufficient; we only need the latest reading).

---

## Widget

Modeled after the jrpie/launcher clock widget — minimal, text-forward, no chrome.

- Displays a single large temperature value (e.g., `72°`) centered in the widget
- Implemented with Jetpack Glance
- Reads cached temperature from DataStore via `GlanceStateDefinition`
- Resizable by the launcher (single size class to start)
- Tapping opens the app settings screen
- Shows `--°` when no data is available (first install / never fetched)
- Background: transparent, blends with any wallpaper
- WorkManager triggers `GlanceAppWidget.update()` after each successful fetch

---

## Settings Screen

Single screen with a Material Design 3 top app bar ("Simple Weather").

| Setting | UI | Detail |
|-|-|-|
| Location | Toggle + text field | "Use device location" toggle; when off, user enters a city name, zip code, or lat/lon |
| Temperature unit | Segmented button | F / C |
| Update interval | Dropdown | 15 min, 30 min, 1 hr, 3 hr, 6 hr |

**Location behavior:**
- Toggle ON → requests `ACCESS_COARSE_LOCATION` at runtime, uses FusedLocationProviderClient
- Toggle OFF → user input (city name, zip code, or `lat,lon`); resolved once via
  Open-Meteo geocoding API (free, no key); resolved lat/lon stored in DataStore

**Persistence:** All settings written to DataStore immediately on change.
WorkManager interval updated on interval change (cancel + re-enqueue).

---

## Data & Storage

**DataStore keys:**

| Key | Type | Description |
|-|-|-|
| `use_device_location` | Boolean | GPS vs static |
| `location_lat` | Float | Resolved latitude |
| `location_lon` | Float | Resolved longitude |
| `location_display_name` | String | Human-readable label shown in settings |
| `temp_unit` | String | `"F"` or `"C"` |
| `update_interval_minutes` | Int | 15 / 30 / 60 / 180 / 360 |
| `last_temp_celsius` | Float | Raw value; converted to F at display time |
| `last_updated_epoch` | Long | Timestamp of last successful fetch |

Temperature always stored as Celsius internally; converted only at display time.

**Open-Meteo API — weather:**
```
GET https://api.open-meteo.com/v1/forecast
  ?latitude=X&longitude=Y
  &current=temperature_2m
  &temperature_unit=celsius
```

**Open-Meteo API — geocoding:**
```
GET https://geocoding-api.open-meteo.com/v1/search
  ?name=<city|zip>&count=1
```

---

## Wishlist (Future Work)

These are confirmed desirables, deferred to keep the initial build focused.

### Widget enhancements
- **Condition icon** — show a weather condition icon (sun, clouds, rain, etc.)
  alongside the temperature
- **High / low** — show the day's forecast high and low below the temperature
- **Font picker** — let the user choose from a selection of fonts for the widget
  display (ties into the minimalist/aesthetic angle)

### Architecture upgrade (v2)
- **Room + Hilt** — once weather history is needed, migrate to Room for
  persistence and Hilt for DI
- **Temperature trend graph** — a separate widget (or widget mode) showing a
  minimalist sparkline of the last 12 or 24 hours of temperature readings at
  the user's location, displayed on the home screen
