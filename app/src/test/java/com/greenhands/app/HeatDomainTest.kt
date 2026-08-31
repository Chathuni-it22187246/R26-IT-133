package com.greenhands.app

import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.domain.HeatStageChange
import com.greenhands.app.heat.domain.HeatValidation
import com.greenhands.app.heat.domain.TargetParser
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.DerivationMethod
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN
import com.greenhands.app.heat.model.TEMP_HARD_MAX
import com.greenhands.app.heat.model.TEMP_HARD_MIN
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.profile.DerivedValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatDomainTest {

    private val tomatoNursery = CropDefaults.stage(Crop.TOMATO, "germination")
    private val tomatoVeg = CropDefaults.stage(Crop.TOMATO, "vegetative")
    private val tomatoFlower = CropDefaults.stage(Crop.TOMATO, "flowering")
    private val tomatoRipe = CropDefaults.stage(Crop.TOMATO, "ripening")

    @Test
    fun tomatoSuggestedValuesFollowShamshiriTable4() {
        val germination = CropDefaults.rangeFor(Crop.TOMATO, "germination")
        assertEquals(24.0, germination.tempMinC, 0.0)
        assertEquals(26.1, germination.tempMaxC, 0.0)
        assertEquals(25.05, germination.defaultTempC, 0.0001)
        assertEquals(75.0, germination.rhMinPercent)
        assertEquals(100.0, germination.rhMaxPercent)
        assertEquals(75.0, germination.defaultRhPercent)

        val vegetative = CropDefaults.recommendation(Crop.TOMATO, "vegetative")
        assertEquals(25.5, vegetative.dayTemperatureC)
        assertEquals(19.0, vegetative.nightTemperatureC)
        assertEquals(25.5, vegetative.selectedTargetTemperatureC, 0.0)
        assertEquals(75.0, vegetative.humidityPercent)

        val flowering = CropDefaults.recommendation(Crop.TOMATO, "flowering")
        assertEquals(25.5, flowering.selectedTargetTemperatureC, 0.0)
        assertEquals(70.0, flowering.humidityPercent)

        val ripening = CropDefaults.recommendation(Crop.TOMATO, "ripening")
        assertEquals(DerivationMethod.CROP_LEVEL_INHERITED, ripening.temperatureDerivation)
        assertEquals(25.5, ripening.selectedTargetTemperatureC, 0.0)
        assertEquals(70.0, ripening.humidityPercent)
    }

    @Test
    fun circulationFormulas() {
        val circ = HeatFormulas.circulation(24.0)
        assertEquals(24.0, circ.csp, 0.0)
        assertEquals(22.0, circ.cdp, 0.0)
        assertEquals(26.0, circ.con, 0.0)
        assertNull(HeatValidation.circulationOrder(circ.cdp, circ.csp, circ.con))
    }

    @Test
    fun exactTopt25Example() {
        val circ = HeatFormulas.circulation(25.0)
        val exhaust = HeatFormulas.exhaustFromCirculation(circ)
        assertEquals(25.0, circ.csp, 0.0)
        assertEquals(23.0, circ.cdp, 0.0)
        assertEquals(27.0, circ.con, 0.0)
        assertEquals(29.0, exhaust.esp, 0.0)
        assertEquals(27.0, exhaust.edp, 0.0)
        assertEquals(31.0, exhaust.eon, 0.0)
        assertEquals(circ.con, exhaust.edp, 0.0)
        assertEquals(circ.csp + 4.0, exhaust.esp, 0.0)
        assertFalse(exhaust.esp == 30.0)
    }

    @Test
    fun equipmentFormulasUseCropSpecificTargets() {
        val lettuce = HeatFormulas.configurationFor(Crop.LETTUCE, "vegetative")
        assertEquals(24.0, lettuce.circulation.csp, 0.0)
        assertEquals(28.0, lettuce.exhaust.esp, 0.0)
        val cucumber = HeatFormulas.configurationFor(Crop.SALAD_CUCUMBER, "vegetative")
        assertEquals(25.0, cucumber.circulation.csp, 0.0)
        assertEquals(29.0, cucumber.exhaust.esp, 0.0)
        assertEquals(70.0, cucumber.fogger!!.fsp, 0.0)
        assertNotEquals(lettuce.circulation.csp, cucumber.circulation.csp)
    }

    @Test
    fun exhaustFormulasAndOrdering() {
        val circ = HeatFormulas.circulation(25.0)
        val exhaust = HeatFormulas.exhaustFromCirculation(circ)
        assertNull(HeatValidation.exhaustOrder(exhaust.edp, exhaust.esp, exhaust.eon))
        assertTrue(circ.cdp < circ.csp && circ.csp < circ.con && circ.con == exhaust.edp)
        assertTrue(exhaust.edp < exhaust.esp && exhaust.esp < exhaust.eon)
    }

    @Test
    fun exactRhopt70Example() {
        val fogger = HeatFormulas.fogger(70.0)
        assertEquals(68.0, fogger.fon, 0.0)
        assertEquals(70.0, fogger.fsp, 0.0)
        assertEquals(72.0, fogger.fdp, 0.0)
        assertNull(HeatValidation.foggerOrder(fogger.fon, fogger.fsp, fogger.fdp))
    }

    @Test
    fun decimalTargetValues() {
        val circ = HeatFormulas.circulation(24.5)
        val exhaust = HeatFormulas.exhaustFromCirculation(circ)
        val fogger = HeatFormulas.fogger(67.5)
        assertEquals(24.5, circ.csp, 0.0)
        assertEquals(22.5, circ.cdp, 0.0)
        assertEquals(26.5, circ.con, 0.0)
        assertEquals(28.5, exhaust.esp, 0.0)
        assertEquals(26.5, exhaust.edp, 0.0)
        assertEquals(30.5, exhaust.eon, 0.0)
        assertEquals(65.5, fogger.fon, 0.0)
        assertEquals(67.5, fogger.fsp, 0.0)
        assertEquals(69.5, fogger.fdp, 0.0)
        assertEquals(24.5, TargetParser.parseDecimal("24,5"))
        assertEquals(24.5, TargetParser.parseDecimal("24.5"))
    }

    @Test
    fun hardInputLimits() {
        val range = CropDefaults.rangeFor(Crop.TOMATO, "germination")
        assertNotNull(HeatValidation.temperature("", range).error)
        assertNotNull(HeatValidation.temperature("4.9", range).error)
        assertNotNull(HeatValidation.temperature("50.1", range).error)
        assertNull(HeatValidation.temperature("5.0", range).error)
        assertNull(HeatValidation.temperature("50.0", range).error)
        assertEquals(TEMP_HARD_MIN, 5.0, 0.0)
        assertEquals(TEMP_HARD_MAX, 50.0, 0.0)

        assertNotNull(HeatValidation.humidity("", range).error)
        assertNotNull(HeatValidation.humidity("-0.1", range).error)
        assertNotNull(HeatValidation.humidity("100.1", range).error)
        assertNull(HeatValidation.humidity("0", range).error)
        assertNull(HeatValidation.humidity("100", range).error)
        assertEquals(RH_HARD_MIN, 0.0, 0.0)
        assertEquals(RH_HARD_MAX, 100.0, 0.0)
    }

    @Test
    fun recommendedRangeWarningsStillAllowSave() {
        val range = CropDefaults.rangeFor(Crop.TOMATO, "germination")
        val cool = HeatValidation.temperature("21.0", range)
        assertTrue(cool.isValid)
        assertNotNull(cool.warning)
        val dry = HeatValidation.humidity("50", range)
        assertTrue(dry.isValid)
        assertNotNull(dry.warning)
        val ok = HeatValidation.temperature("25.05", range)
        assertTrue(ok.isValid)
        assertNull(ok.warning)
    }

    @Test
    fun cucumberHumidityUsesTable2RangeWarning() {
        val range = CropDefaults.rangeFor(Crop.SALAD_CUCUMBER, "vegetative")
        assertEquals(70.0, CropDefaults.recommendation(Crop.SALAD_CUCUMBER, "vegetative").humidityPercent)
        val inside = HeatValidation.humidity("70", range)
        assertTrue(inside.isValid)
        assertNull(inside.warning)
        val outside = HeatValidation.humidity("90", range)
        assertTrue(outside.isValid)
        assertNotNull(outside.warning)
    }

    @Test
    fun advancedModeInvalidOrdering() {
        assertNotNull(HeatValidation.circulationOrder(25.0, 25.0, 26.0))
        assertNotNull(HeatValidation.exhaustOrder(27.0, 27.0, 31.0))
        assertNotNull(HeatValidation.foggerOrder(70.0, 70.0, 72.0))
        assertNull(HeatValidation.circulationOrder(23.0, 25.0, 27.0))
        assertNotNull(HeatValidation.cspDiffersFromTarget(26.0, 25.0))
        assertNull(HeatValidation.cspDiffersFromTarget(25.0, 25.0))
        assertNotNull(HeatValidation.fspDiffersFromTarget(72.0, 70.0))
    }

    @Test
    fun stageChangeResetBehavior() {
        val current = HeatStageChange.apply(
            HeatConfiguration(crop = Crop.TOMATO),
            Crop.TOMATO,
            tomatoNursery
        )
        assertEquals(25.05, current.targetTemperatureC!!, 0.0001)
        assertEquals(75.0, current.targetHumidityPercent)
        assertTrue(HeatStageChange.shouldConfirm(current, tomatoRipe))
        assertFalse(HeatStageChange.shouldConfirm(current, tomatoNursery))

        val edited = current.copy(targetTemperatureC = 28.0, controlMode = ControlMode.ADVANCED)
        val reset = HeatStageChange.apply(edited, Crop.TOMATO, tomatoRipe)
        assertEquals("ripening", reset.stage!!.id)
        assertEquals(25.5, reset.targetTemperatureC)
        assertEquals(70.0, reset.targetHumidityPercent)
        assertEquals(ControlMode.AUTOMATIC, reset.controlMode)
        assertEquals(25.5, reset.circulation!!.csp, 0.0)
        assertEquals(23.5, reset.circulation!!.cdp, 0.0)
        assertEquals(27.5, reset.circulation!!.con, 0.0)
        assertEquals(29.5, reset.exhaust!!.esp, 0.0)
        assertEquals(27.5, reset.exhaust!!.edp, 0.0)
        assertEquals(31.5, reset.exhaust!!.eon, 0.0)
        assertEquals(68.0, reset.fogger!!.fon, 0.0)
        assertEquals(70.0, reset.fogger!!.fsp, 0.0)
        assertEquals(72.0, reset.fogger!!.fdp, 0.0)
    }

    @Test
    fun derivedMidpointsAreExact() {
        assertEquals(25.05, DerivedValues.midpoint(24.0, 26.1), 0.0001)
        assertEquals(26.0, DerivedValues.midpoint(22.0, 30.0), 0.0)
        assertEquals(25.5, DerivedValues.midpoint(24.0, 27.0), 0.0)
        assertEquals(19.0, DerivedValues.midpoint(18.0, 20.0), 0.0)
        assertEquals(75.0, DerivedValues.midpoint(70.0, 80.0), 0.0)
        assertEquals(70.0, DerivedValues.midpoint(60.0, 80.0), 0.0)
        assertEquals(77.5, DerivedValues.midpoint(75.0, 80.0), 0.0)
        assertEquals(60.0, DerivedValues.midpoint(50.0, 70.0), 0.0)
        assertEquals(22.0, DerivedValues.midpoint(21.0, 23.0), 0.0)
        assertEquals(17.0, DerivedValues.midpoint(16.0, 18.0), 0.0)
        assertEquals(25.5, DerivedValues.midpoint(25.0, 26.0), 0.0)
        assertEquals(85.0, DerivedValues.midpoint(80.0, 90.0), 0.0)
        assertEquals(67.5, DerivedValues.midpoint(65.0, 70.0), 0.0)
        assertEquals(70.0, DerivedValues.midpoint(65.0, 75.0), 0.0)
        assertEquals(47.5, DerivedValues.midpoint(45.0, 50.0), 0.0)
        assertEquals(60.0, DerivedValues.midpoint(55.0, 65.0), 0.0)
        assertEquals(52.5, DerivedValues.midpoint(50.0, 55.0), 0.0)
        assertEquals(26.5, DerivedValues.midpoint(25.0, 28.0), 0.0)
        assertEquals(20.0, DerivedValues.midpoint(18.0, 22.0), 0.0)
        assertEquals(65.0, DerivedValues.midpoint(60.0, 70.0), 0.0)
    }

    @Test
    fun tomatoVegetativeKeepsDayNightSplit() {
        val rec = CropDefaults.recommendation(Crop.TOMATO, "vegetative")
        assertTrue(rec.hasDayNightSplit())
        assertNotEquals(rec.dayTemperatureC, rec.nightTemperatureC)
        assertEquals(rec.dayTemperatureC, rec.selectedTargetTemperatureC)
    }

    @Test
    fun profile212MigratesLegacySelectedTargetToCustomDay() {
        val stage = CropDefaults.stage(Crop.TOMATO, "vegetative")
        val legacy = HeatConfiguration(
            crop = Crop.TOMATO,
            stage = stage,
            dayTemperatureC = 25.5,
            nightTemperatureC = 19.0,
            targetTemperatureC = 28.0,
            targetHumidityPercent = 75.0,
            saved = true,
            valuesAreCustomised = false
        )
        val migrated = com.greenhands.app.heat.domain.ProfileMigration212.migrate(legacy)
        assertEquals(28.0, migrated.dayTemperatureC)
        assertEquals(19.0, migrated.nightTemperatureC)
        assertTrue(migrated.valuesAreCustomised)
    }
}
