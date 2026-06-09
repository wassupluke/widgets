package com.wassupluke.widgets

import com.wassupluke.widgets.data.TemperatureUnit
import com.wassupluke.widgets.data.formatTemperature
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TemperatureUnitTest {
    @Test fun usLocaleIsFahrenheit() =
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.forLocale(Locale.US))

    @Test fun germanLocaleIsCelsius() =
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(Locale.GERMANY))

    @Test fun temperatureOverrideBeatsCountry() {
        // US country, but explicit Celsius temperature preference (Android 14+).
        val locale = Locale.Builder().setRegion("US")
            .setUnicodeLocaleKeyword("mu", "celsius").build()
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(locale))
    }

    @Test fun measurementSystemMetricGivesCelsius() {
        val locale = Locale.Builder().setRegion("US")
            .setUnicodeLocaleKeyword("ms", "metric").build()
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.forLocale(locale))
    }

    @Test fun temperatureOverrideForcesFahrenheit() {
        // Germany, but explicit Fahrenheit temperature preference.
        val locale = Locale.Builder().setRegion("DE")
            .setUnicodeLocaleKeyword("mu", "fahrenhe").build()
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.forLocale(locale))
    }

    @Test fun formatRoundsAndAppendsDegree() =
        assertEquals("12°", formatTemperature(11.6, TemperatureUnit.CELSIUS))

    @Test fun formatHandlesNegative() =
        assertEquals("-3°", formatTemperature(-2.5, TemperatureUnit.CELSIUS))

    @Test fun formatConvertsToFahrenheit() {
        assertEquals("32°", formatTemperature(0.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("68°", formatTemperature(20.0, TemperatureUnit.FAHRENHEIT))
    }
}
