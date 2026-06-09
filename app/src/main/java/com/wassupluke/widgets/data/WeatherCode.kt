package com.wassupluke.widgets.data

/** Maps Open-Meteo WMO weather codes to an emoji + short label. */
object WeatherCode {
    fun describe(code: Int): String = when (code) {
        0 -> "☀ Clear"
        1 -> "🌤 Mainly clear"
        2 -> "⛅ Partly cloudy"
        3 -> "☁ Overcast"
        45, 48 -> "🌫 Fog"
        51, 53, 55 -> "🌦 Drizzle"
        56, 57 -> "🌧 Freezing drizzle"
        61, 63, 65 -> "🌧 Rain"
        66, 67 -> "🌧 Freezing rain"
        71, 73, 75 -> "🌨 Snow"
        77 -> "🌨 Snow grains"
        80, 81, 82 -> "🌧 Showers"
        85, 86 -> "🌨 Snow showers"
        95, 96, 99 -> "⛈ Thunderstorm"
        else -> "❓ Unknown"
    }
}
