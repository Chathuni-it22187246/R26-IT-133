package com.greenhands.app.equipment

import com.greenhands.app.equipment.ui.OverlayClimateMapper
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.SchedulePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayClimateMapperTest {

    @Test
    fun usesTheSelectedPeriodThresholdsNotTheOtherPeriod() {
        val dayCirc = HeatFormulas.circulation(26.0)
        val nightCirc = HeatFormulas.circulation(18.0)
        val config = HeatConfiguration(
            crop = Crop.TOMATO,
            dayTemperatureC = 26.0,
            nightTemperatureC = 18.0,
            dayHumidityPercent = 70.0,
            nightHumidityPercent = 80.0,
            circulation = dayCirc,
            exhaust = HeatFormulas.exhaustFromCirculation(dayCirc),
            fogger = HeatFormulas.fogger(70.0),
            nightCirculation = nightCirc,
            nightExhaust = HeatFormulas.exhaustFromCirculation(nightCirc),
            nightFogger = HeatFormulas.fogger(80.0)
        )
        val day = OverlayClimateMapper.fromActiveConfiguration(
            config = config,
            period = SchedulePeriod.DAY,
            temperatureC = 26.0,
            humidityPercent = 70.0
        )
        val night = OverlayClimateMapper.fromActiveConfiguration(
            config = config,
            period = SchedulePeriod.NIGHT,
            temperatureC = 18.0,
            humidityPercent = 80.0
        )
        assertEquals(26.0, day.circulation?.csp)
        assertEquals(28.0, day.circulation?.con)
        assertEquals(18.0, night.circulation?.csp)
        assertEquals(20.0, night.circulation?.con)
        assertEquals(70.0, day.fogger?.fsp)
        assertEquals(80.0, night.fogger?.fsp)
        assertNotEquals(day.circulation?.con, night.circulation?.con)
        assertEquals(day.circulation?.con, day.exhaust?.edp)
        assertEquals(night.circulation?.con, night.exhaust?.edp)
    }

    @Test
    fun doesNotInventThresholdsWhenTemperatureIsMissing() {
        val climate = OverlayClimateMapper.fromActiveConfiguration(
            config = HeatConfiguration(),
            period = SchedulePeriod.DAY,
            temperatureC = null,
            humidityPercent = null
        )
        assertNull(climate.circulation)
        assertNull(climate.exhaust)
        assertNull(climate.fogger)
        assertNull(climate.optimalTemperatureC)
        assertNull(climate.optimalHumidityPercent)
    }
}
