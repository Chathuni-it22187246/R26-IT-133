package com.greenhands.app

import com.greenhands.app.sensor.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorTypeAbbreviationTest {

    @Test
    fun markerAbbreviationsMatchSupportedSensorTypes() {
        assertEquals("T", SensorType.TEMPERATURE.markerAbbreviation)
        assertEquals("H", SensorType.HUMIDITY.markerAbbreviation)
        assertEquals("SM", SensorType.SOIL_MOISTURE.markerAbbreviation)
        assertEquals("L", SensorType.LIGHT_INTENSITY.markerAbbreviation)
    }

    @Test
    fun everySensorTypeHasANonBlankMarkerAbbreviation() {
        SensorType.entries.forEach { type ->
            assertEquals(false, type.markerAbbreviation.isBlank())
        }
    }
}
