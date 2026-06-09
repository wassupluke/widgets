package com.wassupluke.widgets

import com.wassupluke.widgets.data.OpenMeteo
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoTest {
    @Test fun buildsUrlWithCoordsInCelsius() {
        val url = OpenMeteo.buildUrl(52.52, 13.41)
        assertEquals(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=52.52&longitude=13.41" +
                "&current=temperature_2m,weather_code" +
                "&temperature_unit=celsius",
            url
        )
    }

    @Test fun parsesCurrentBlock() {
        val json = """
            {"latitude":52.52,"longitude":13.41,
             "current":{"time":"2026-06-07T10:00","interval":900,
                        "temperature_2m":17.3,"weather_code":3}}
        """.trimIndent()
        val data = OpenMeteo.parse(json)
        assertEquals(17.3, data.temperature, 0.001)
        assertEquals(3, data.weatherCode)
    }
}
