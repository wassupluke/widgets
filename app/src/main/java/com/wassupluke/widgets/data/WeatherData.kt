package com.wassupluke.widgets.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeatherData(
    /** Temperature in degrees Celsius; converted to the display unit at render time. */
    val temperature: Double,
    val weatherCode: Int
)

object WeatherCache {
    private const val PREFS = "weather_cache"
    private const val KEY_DATA = "data"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, data: WeatherData) {
        prefs(context).edit().putString(KEY_DATA, json.encodeToString(data)).apply()
    }

    fun load(context: Context): WeatherData? =
        prefs(context).getString(KEY_DATA, null)?.let {
            runCatching { json.decodeFromString<WeatherData>(it) }.getOrNull()
        }
}
