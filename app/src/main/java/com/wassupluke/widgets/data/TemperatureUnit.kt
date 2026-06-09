package com.wassupluke.widgets.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The unit a temperature is displayed in. Weather is always fetched in Celsius. */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT;

    companion object {
        // Countries that conventionally use Fahrenheit.
        private val FAHRENHEIT_COUNTRIES =
            setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

        /** Best-effort unit for a locale, used by the "Automatic" setting. */
        fun forLocale(locale: Locale): TemperatureUnit {
            // Android 14+ regional preferences are carried as Unicode locale
            // extensions. An explicit temperature-unit override ("mu") wins.
            when (locale.getUnicodeLocaleType("mu")) {
                "celsius", "kelvin" -> return CELSIUS
                "fahrenhe" -> return FAHRENHEIT
            }
            // Otherwise honor a measurement-system override ("ms"). Only the US
            // system implies Fahrenheit; the UK uses Celsius for temperature.
            when (locale.getUnicodeLocaleType("ms")) {
                "metric", "uksystem" -> return CELSIUS
                "ussystem" -> return FAHRENHEIT
            }
            // Fallback for older Android / no override: country heuristic.
            return if (locale.country.uppercase(Locale.ROOT) in FAHRENHEIT_COUNTRIES)
                FAHRENHEIT else CELSIUS
        }
    }
}

/**
 * Formats a Celsius temperature for display in [unit]: rounded integer + degree
 * sign, no unit letter (e.g. "12°"). Ties round away from zero, and "-0°" is avoided.
 */
fun formatTemperature(celsius: Double, unit: TemperatureUnit): String {
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }
    val rounded = abs(value).roundToInt() * (if (value < 0) -1 else 1)
    return "$rounded°"
}
