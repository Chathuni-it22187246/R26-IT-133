package com.greenhands.app

import com.greenhands.app.heat.data.HeatConfigCodec
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.PROFILE_VERSION
import com.greenhands.app.heat.profile.CropProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatConfigCodecTest {

    @Test
    fun roundTripPreservesPerCropConfiguration() {
        val stage = CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage
        val original = HeatConfiguration(
            crop = Crop.TOMATO,
            stage = stage,
            dayTemperatureC = 25.5,
            nightTemperatureC = 19.0,
            targetTemperatureC = 25.5,
            targetHumidityPercent = 75.0,
            controlMode = ControlMode.AUTOMATIC,
            circulation = CirculationThresholds(25.5, 23.5, 27.5),
            exhaust = ExhaustThresholds(29.5, 27.5, 31.5),
            fogger = FoggerThresholds(75.0, 73.0, 77.0),
            saved = true,
            valuesAreCustomised = false,
            profileVersion = PROFILE_VERSION,
            lastSuggestedProfileVersion = PROFILE_VERSION
        )
        val decoded = HeatConfigCodec.decode(HeatConfigCodec.encode(original))!!
        assertEquals(original.crop, decoded.crop)
        assertEquals(original.stage?.id, decoded.stage?.id)
        assertEquals(original.targetTemperatureC, decoded.targetTemperatureC)
        assertEquals(original.targetHumidityPercent, decoded.targetHumidityPercent)
        assertEquals(original.circulation, decoded.circulation)
        assertEquals(original.profileVersion, decoded.profileVersion)
    }

    @Test
    fun migrateV1TomatoIntoTomatoNamespace() {
        val migrated = HeatConfigCodec.migrateV1(
            cropId = "tomato",
            stageId = "germination",
            targetTemp = 24.0,
            targetRh = 70.0,
            mode = "AUTOMATIC",
            csp = 24.0,
            cdp = 22.0,
            con = 26.0,
            esp = 28.0,
            edp = 26.0,
            eon = 30.0,
            fsp = 70.0,
            fon = 68.0,
            fdp = 72.0,
            saved = true
        )
        assertTrue(migrated.migratedFromV1)
        assertEquals(Crop.TOMATO, migrated.selectedCrop)
        assertEquals("germination", migrated.selectedStageByCrop["tomato"])
        val config = migrated.configurations.getValue("tomato_germination")
        assertEquals(24.0, config.targetTemperatureC)
        assertEquals(70.0, config.targetHumidityPercent)
        assertTrue(config.valuesAreCustomised)
        assertEquals("2.0.0", config.lastSuggestedProfileVersion)
    }

    @Test
    fun workspaceIsolationKeysDifferByCrop() {
        assertEquals("tomato_germination", HeatConfigCodec.key("tomato", "germination"))
        assertEquals("chilli_germination", HeatConfigCodec.key("chilli", "germination"))
        assertTrue(HeatConfigCodec.key("tomato", "germination") != HeatConfigCodec.key("chilli", "germination"))
    }

    @Test
    fun legacySelectedTargetDifferingFromDayBecomesCustomDay() {
        val raw = "crop=tomato|stage=vegetative|dayT=25.5|nightT=19.0|topt=28.0|rh=75.0|mode=AUTOMATIC|saved=true|custom=true|profile=2.1.1|lastProfile=2.1.1"
        val decoded = HeatConfigCodec.decode(raw)!!
        assertEquals(28.0, decoded.dayTemperatureC)
        assertEquals(19.0, decoded.nightTemperatureC)
        assertEquals(28.0, decoded.targetTemperatureC)
        assertTrue(decoded.valuesAreCustomised)
        assertTrue(!HeatConfigCodec.encode(decoded).contains("topt="))
    }

    @Test
    fun legacySelectedTargetMatchingDayKeepsStoredDayAndNight() {
        val raw = "crop=tomato|stage=vegetative|dayT=25.5|nightT=19.0|topt=25.5|rh=75.0|mode=AUTOMATIC|saved=true|custom=false|profile=2.1.1|lastProfile=2.1.1"
        val decoded = HeatConfigCodec.decode(raw)!!
        assertEquals(25.5, decoded.dayTemperatureC)
        assertEquals(19.0, decoded.nightTemperatureC)
        assertEquals(25.5, decoded.targetTemperatureC)
    }

    @Test
    fun newEncodeOmitsIndependentSelectedTarget() {
        val stage = CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage
        val encoded = HeatConfigCodec.encode(
            HeatConfiguration(
                crop = Crop.TOMATO,
                stage = stage,
                dayTemperatureC = 25.5,
                nightTemperatureC = 19.0,
                targetTemperatureC = 99.0,
                targetHumidityPercent = 75.0,
                saved = true
            )
        )
        assertTrue(!encoded.contains("topt="))
        val decoded = HeatConfigCodec.decode(encoded)!!
        assertEquals(25.5, decoded.dayTemperatureC)
        assertEquals(19.0, decoded.nightTemperatureC)
        assertEquals(25.5, decoded.targetTemperatureC)
    }
}
