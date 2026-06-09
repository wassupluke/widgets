package com.wassupluke.widgets

import com.wassupluke.widgets.data.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodeTest {
    @Test fun clearSky() = assertEquals("☀ Clear", WeatherCode.describe(0))
    @Test fun partlyCloudy() = assertEquals("⛅ Partly cloudy", WeatherCode.describe(2))
    @Test fun overcast() = assertEquals("☁ Overcast", WeatherCode.describe(3))
    @Test fun rain() = assertEquals("🌧 Rain", WeatherCode.describe(63))
    @Test fun snow() = assertEquals("🌨 Snow", WeatherCode.describe(73))
    @Test fun thunderstorm() = assertEquals("⛈ Thunderstorm", WeatherCode.describe(95))
    @Test fun unknownCode() = assertEquals("❓ Unknown", WeatherCode.describe(123))
}
