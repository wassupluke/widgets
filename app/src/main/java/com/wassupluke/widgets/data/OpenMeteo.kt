package com.wassupluke.widgets.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    // Always request Celsius; conversion to the display unit happens at render time.
    fun buildUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&temperature_unit=celsius"

    fun parse(body: String): WeatherData {
        val response = json.decodeFromString<Response>(body)
        return WeatherData(
            temperature = response.current.temperature,
            weatherCode = response.current.weatherCode
        )
    }

    @Serializable
    private data class Response(val current: Current)

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int
    )
}
