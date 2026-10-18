package com.greenhands.app

import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.DerivationMethod
import com.greenhands.app.heat.profile.CropProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropProfileRegistryTest {

    @Test
    fun fiveCropsAreSelectableWithProfiles() {
        assertEquals(5, Crop.selectable().size)
        Crop.selectable().forEach { crop ->
            val profile = CropProfileRegistry.profile(crop)
            assertTrue(profile.stages.isNotEmpty())
            assertTrue(crop.available)
            assertTrue(crop.scientificName.isNotBlank())
        }
    }

    @Test
    fun growthStageListsMatchPhase21() {
        assertEquals(
            listOf("germination", "vegetative", "flowering", "ripening"),
            CropProfileRegistry.stagesFor(Crop.TOMATO).map { it.id }
        )
        assertEquals(
            listOf("germination", "vegetative", "flowering", "harvest"),
            CropProfileRegistry.stagesFor(Crop.SALAD_CUCUMBER).map { it.id }
        )
        assertEquals(
            listOf("germination", "vegetative", "flowering", "fruiting"),
            CropProfileRegistry.stagesFor(Crop.BELL_PEPPER).map { it.id }
        )
        assertEquals(
            listOf("germination", "vegetative", "flowering", "ripening"),
            CropProfileRegistry.stagesFor(Crop.CHILLI).map { it.id }
        )
        assertEquals(
            listOf("germination", "vegetative", "heading", "harvest"),
            CropProfileRegistry.stagesFor(Crop.LETTUCE).map { it.id }
        )
    }

    @Test
    fun everyNumericRecommendationHasValidSourceIds() {
        CropProfileRegistry.profiles.forEach { profile ->
            profile.stages.forEach { item ->
                val climate = item.climate
                assertTrue(climate.sourceIds.isNotEmpty())
                climate.sourceIds.forEach { id ->
                    assertNotNull(CropProfileRegistry.citationOrNull(id))
                }
                assertTrue(climate.selectedTargetTemperatureC > 0)
            }
        }
    }

    @Test
    fun noProfileClaimsUniversalStandard() {
        CropProfileRegistry.citations.forEach { citation ->
            assertFalse(citation.title.contains("universal standard", ignoreCase = true))
            assertFalse(citation.locationInSource.contains("universal standard", ignoreCase = true))
        }
        CropProfileRegistry.profiles.forEach { profile ->
            profile.stages.forEach { item ->
                assertFalse(item.climate.applicability.text.contains("universal standard", ignoreCase = true))
                assertFalse(item.climate.applicability.text.contains("Official Sri Lankan greenhouse standard", ignoreCase = true))
            }
        }
    }

    @Test
    fun cropsDoNotSilentlyShareUnrelatedValues() {
        val tomato = CropProfileRegistry.climateFor(Crop.TOMATO, "germination")
        val cucumber = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, "germination")
        val lettuce = CropProfileRegistry.climateFor(Crop.LETTUCE, "germination")
        val pepper = CropProfileRegistry.climateFor(Crop.BELL_PEPPER, "germination")
        assertNotEquals(tomato.selectedTargetTemperatureC, cucumber.selectedTargetTemperatureC)
        assertNotEquals(tomato.selectedTargetTemperatureC, lettuce.selectedTargetTemperatureC)
        assertNotEquals(cucumber.selectedTargetTemperatureC, lettuce.selectedTargetTemperatureC)
        assertNotEquals(tomato.humidityPercent, pepper.humidityPercent)
        assertNotNull(cucumber.humidityPercent)
        assertEquals(85.0, cucumber.humidityPercent)
    }

    @Test
    fun cucumberRhIsPublishedFromTable2() {
        val nursery = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, "germination")
        val vegetative = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, "vegetative")
        val flowering = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, "flowering")
        val harvest = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, "harvest")
        assertNotNull(nursery.humidityPercent)
        assertEquals(2, nursery.humiditySubPeriods.size)
        assertEquals("nursery_1_4", nursery.humiditySubPeriods[0].id)
        assertEquals("nursery_5_8", nursery.humiditySubPeriods[1].id)
        assertEquals(85.0, nursery.humiditySubPeriods[0].bands[0].target, 0.0)
        assertEquals(50.0, nursery.humiditySubPeriods[0].bands[1].target, 0.0)
        assertEquals(67.5, nursery.humiditySubPeriods[1].bands[0].target, 0.0)
        assertEquals(50.0, nursery.humiditySubPeriods[1].bands[1].target, 0.0)

        assertEquals(70.0, vegetative.humidityPercent)
        assertEquals(65.0, vegetative.humiditySchedule[0].range!!.min, 0.0)
        assertEquals(75.0, vegetative.humiditySchedule[0].range!!.max, 0.0)
        assertEquals(47.5, vegetative.humiditySchedule[1].target, 0.0)

        assertEquals(60.0, flowering.humidityPercent)
        assertEquals(60.0, harvest.humidityPercent)
        assertEquals(DerivationMethod.CROP_LEVEL_INHERITED, flowering.humidityDerivation)
        assertEquals(DerivationMethod.CROP_LEVEL_INHERITED, harvest.humidityDerivation)
        assertEquals(flowering.humiditySchedule.map { it.id }, harvest.humiditySchedule.map { it.id })
    }

    @Test
    fun cucumberHasFourTable3TemperaturePeriods() {
        CropProfileRegistry.stagesFor(Crop.SALAD_CUCUMBER).forEach { stage ->
            val climate = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, stage.id)
            assertEquals(4, climate.temperatureSchedule.size)
            assertEquals(
                listOf("t_21_06", "t_06_09", "t_09_17", "t_17_21"),
                climate.temperatureSchedule.map { it.id }
            )
            assertEquals(15.0, climate.temperatureSchedule[0].target, 0.0)
            assertEquals(20.0, climate.temperatureSchedule[1].target, 0.0)
            assertEquals(25.0, climate.temperatureSchedule[2].target, 0.0)
            assertEquals(20.0, climate.temperatureSchedule[3].target, 0.0)
            assertEquals(25.0, climate.selectedTargetTemperatureC, 0.0)
            assertEquals(25.0, climate.dayTemperatureC)
            assertEquals(15.0, climate.nightTemperatureC)
            assertEquals(20.0, climate.dayTemperatureRange!!.min, 0.0)
            assertEquals(25.0, climate.dayTemperatureRange!!.max, 0.0)
            assertTrue(climate.hasDayNightSplit())
            assertTrue(climate.hasTimeOfDayTemperatureSchedule())
        }
    }

    @Test
    fun chilliUsesOhNotAlberta() {
        val chilli = CropProfileRegistry.climateFor(Crop.CHILLI, "germination")
        val pepper = CropProfileRegistry.climateFor(Crop.BELL_PEPPER, "germination")
        assertTrue(chilli.sourceIds.contains("SRC-OH-2019"))
        assertTrue(chilli.sourceIds.contains("SRC-GUNAWARDENA-2014"))
        assertFalse(chilli.sourceIds.contains("SRC-ALBERTA-PEPPER"))
        assertFalse(chilli.sourceIds.contains("SRC-ALBERTA-ENV"))
        CropProfileRegistry.profile(Crop.CHILLI).stages.forEach { item ->
            assertFalse(item.climate.sourceIds.any { it.startsWith("SRC-ALBERTA") })
            assertNotEquals(pepper.selectedTargetTemperatureC, item.climate.selectedTargetTemperatureC)
            assertEquals(26.5, item.climate.dayTemperatureC)
            assertEquals(20.0, item.climate.nightTemperatureC)
            assertEquals(65.0, item.climate.humidityPercent)
            assertEquals(DerivationMethod.CROP_LEVEL_INHERITED, item.climate.temperatureDerivation)
            assertTrue(item.climate.warningNotes.any { it.contains("15") && it.contains("32") })
        }
    }

    @Test
    fun lettuceUsesCornellDayNight() {
        val veg = CropProfileRegistry.climateFor(Crop.LETTUCE, "vegetative")
        assertEquals(24.0, veg.dayTemperatureC)
        assertEquals(19.0, veg.nightTemperatureC)
        assertEquals(60.0, veg.humidityPercent)
        assertTrue(veg.sourceIds.contains("SRC-CORNELL-LETTUCE"))
    }

    @Test
    fun cucumberUiDayNightAndOriginalScheduleRemain() {
        CropProfileRegistry.stagesFor(Crop.SALAD_CUCUMBER).forEach { stage ->
            val climate = CropProfileRegistry.climateFor(Crop.SALAD_CUCUMBER, stage.id)
            assertEquals(25.0, climate.uiDayTemperatureC(), 0.0)
            assertEquals(15.0, climate.uiNightTemperatureC(), 0.0)
            assertEquals(20.0, climate.uiDayTemperatureRange().min, 0.0)
            assertEquals(25.0, climate.uiDayTemperatureRange().max, 0.0)
            assertEquals(15.0, climate.temperatureSchedule[0].target, 0.0)
            assertEquals(20.0, climate.temperatureSchedule[1].target, 0.0)
            assertEquals(25.0, climate.temperatureSchedule[2].target, 0.0)
            assertEquals(20.0, climate.temperatureSchedule[3].target, 0.0)
        }
    }

    @Test
    fun generalProfilesMapTheSameValueToDayAndNight() {
        val tomatoNursery = CropProfileRegistry.climateFor(Crop.TOMATO, "germination")
        assertEquals(tomatoNursery.generalTemperatureC, tomatoNursery.uiDayTemperatureC())
        assertEquals(tomatoNursery.generalTemperatureC, tomatoNursery.uiNightTemperatureC())
        val lettuceNursery = CropProfileRegistry.climateFor(Crop.LETTUCE, "germination")
        assertEquals(20.0, lettuceNursery.uiDayTemperatureC(), 0.0)
        assertEquals(20.0, lettuceNursery.uiNightTemperatureC(), 0.0)
    }
}
