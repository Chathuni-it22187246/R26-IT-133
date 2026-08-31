package com.greenhands.app

import com.greenhands.app.harvest.data.csv.AssetCsvLoader
import com.greenhands.app.harvest.data.csv.CropReferenceCsvParser
import com.greenhands.app.harvest.data.csv.CsvLoadException
import com.greenhands.app.harvest.data.csv.CsvValues
import com.greenhands.app.harvest.data.csv.DiseaseReferenceCsvParser
import com.greenhands.app.harvest.data.csv.HarvestRulesCsvParser
import com.greenhands.app.harvest.data.csv.ObservationCsvParser
import com.greenhands.app.harvest.data.csv.VarietyReferenceCsvParser
import com.greenhands.app.harvest.model.HarvestReferenceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class HarvestCsvParsingTest {

    private val loader = AssetCsvLoader()
    private val assetsDir: File by lazy {
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app/src/main/assets for unit tests")
    }

    private fun readAsset(fileName: String): String {
        val file = File(assetsDir, fileName)
        assertTrue("Expected asset file: ${file.absolutePath}", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun assetFilesExistAndAreReadable() {
        listOf(
            AssetCsvLoader.CROP_REFERENCE,
            AssetCsvLoader.VARIETY_REFERENCE,
            AssetCsvLoader.DISEASE_REFERENCE,
            AssetCsvLoader.HARVEST_RULES,
            AssetCsvLoader.REAL_OBSERVATIONS
        ).forEach { name ->
            val text = readAsset(name)
            assertTrue("$name should not be blank", text.isNotBlank())
            val table = loader.parseTable(text, name)
            assertTrue("$name should have headers", table.headers.isNotEmpty())
        }
    }

    @Test
    fun expectedRowCountsFromBundledAssets() {
        val crops = CropReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.CROP_REFERENCE))
        val varieties = VarietyReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.VARIETY_REFERENCE))
        val diseases = DiseaseReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.DISEASE_REFERENCE))
        val rules = HarvestRulesCsvParser(loader).parse(readAsset(AssetCsvLoader.HARVEST_RULES))
        val observations = ObservationCsvParser(loader).parse(readAsset(AssetCsvLoader.REAL_OBSERVATIONS))

        assertEquals(1, crops.size)
        assertEquals(13, varieties.size)
        assertEquals(8, diseases.size)
        assertEquals(6, rules.size)
        assertEquals(0, observations.size)

        val data = HarvestReferenceData(crops, varieties, diseases, rules, observations)
        assertTrue(data.isLoaded)
    }

    @Test
    fun cropReferenceParsesImportantFieldsWithoutInventingRanges() {
        val crop = CropReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.CROP_REFERENCE)).single()
        assertEquals("Tomato", crop.cropType)
        assertEquals("Lycopersicon esculentum", crop.scientificName)
        assertEquals("21-24", crop.optimumTemperatureC)
        assertEquals("5.5-7.5", crop.soilPhRange)
        assertEquals("14-18", crop.transplantAfterSowingDays)
        assertTrue(crop.harvestIndicator!!.contains("green-yellow"))
        assertNull(CsvValues.optionalDouble(crop.optimumTemperatureC))
    }

    @Test
    fun varietyReferenceKeepsWeightsButLeavesUnsourcedMaturityNull() {
        val varieties = VarietyReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.VARIETY_REFERENCE))
        val thilina = varieties.first { it.variety == "Thilina" }
        assertEquals(85.0, thilina.averageFruitWeightG!!, 0.0)
        assertEquals("40", thilina.yieldTHa)
        assertNull(thilina.expectedMaturityMinDays)
        assertNull(thilina.expectedMaturityMaxDays)
        assertEquals("TO_BE_SOURCED_OR_FIELD_VALIDATED", thilina.maturityStatus)

        val hybrid = varieties.first { it.variety == "HORDI Tomato hybrid 03" }
        assertNull(hybrid.averageFruitWeightG)
        assertEquals("55-60", hybrid.yieldTHa)
        assertNull(CsvValues.optionalDouble(hybrid.yieldTHa))
        assertNull(hybrid.expectedMaturityMinDays)
        assertNull(hybrid.expectedMaturityMaxDays)

        varieties.forEach { variety ->
            assertNull(
                "Maturity min must stay null until sourced: ${variety.variety}",
                variety.expectedMaturityMinDays
            )
            assertNull(
                "Maturity max must stay null until sourced: ${variety.variety}",
                variety.expectedMaturityMaxDays
            )
        }
    }

    @Test
    fun diseaseReferenceParsesQuotedCommaFields() {
        val diseases = DiseaseReferenceCsvParser(loader).parse(readAsset(AssetCsvLoader.DISEASE_REFERENCE))
        val tswv = diseases.first { it.diseaseName.contains("TSWV") || it.diseaseName.contains("Spotted Wilt") }
        assertTrue(tswv.documentedSymptoms!!.contains("Bronzing"))
        assertEquals("Possible TSWV", tswv.recommendedUiLabel)
        assertEquals("Viral", tswv.diseaseGroup)
    }

    @Test
    fun harvestRulesNeverTurnCalibrationPlaceholdersIntoNumbers() {
        val rules = HarvestRulesCsvParser(loader).parse(readAsset(AssetCsvLoader.HARVEST_RULES))
        assertEquals(listOf("HR001", "HR002", "HR003", "HR004", "HR005", "HR006"), rules.map { it.ruleId })

        val hr001 = rules.first { it.ruleId == "HR001" }
        assertNull(hr001.calibratedThresholdOrValue)
        assertEquals("SOURCE_SUPPORTED", hr001.status)

        val hr003 = rules.first { it.ruleId == "HR003" }
        assertNull(hr003.calibratedThresholdOrValue)
        assertEquals("NOT_YET_CALIBRATED", hr003.status)

        val hr004 = rules.first { it.ruleId == "HR004" }
        assertNull(hr004.calibratedThresholdOrValue)
        assertNull(CsvValues.optionalDouble("TO_BE_CALIBRATED_FROM_REAL_IMAGES"))
        assertNull(CsvValues.optionalInt("TO_BE_SOURCED_OR_FIELD_VALIDATED"))
        assertNull(CsvValues.optionalDouble("TO_BE_VALIDATED"))

        rules.forEach { rule ->
            assertNull(
                "Rule ${rule.ruleId} threshold must not invent a number",
                CsvValues.optionalDouble(rule.calibratedThresholdOrValue)
            )
        }
    }

    @Test
    fun observationsFileIsSchemaReadyWithZeroFakeRows() {
        val table = loader.parseTable(readAsset(AssetCsvLoader.REAL_OBSERVATIONS), AssetCsvLoader.REAL_OBSERVATIONS)
        ObservationCsvParser.REQUIRED.forEach { col ->
            assertTrue("Missing observation column $col", col in table.headers)
        }
        val rows = ObservationCsvParser(loader).parse(table)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun missingColumnsAreRejected() {
        val csv = "crop_type,scientific_name\nTomato,Lycopersicon esculentum\n"
        try {
            CropReferenceCsvParser(loader).parse(csv)
            fail("Expected missing-column failure")
        } catch (e: CsvLoadException) {
            assertTrue(e.message.contains("Missing columns"))
        }
    }

    @Test
    fun malformedColumnCountIsRejected() {
        val header = CropReferenceCsvParser.REQUIRED.joinToString(",")
        val csv = "$header\nTomato,only-two-fields\n"
        try {
            CropReferenceCsvParser(loader).parse(csv)
            fail("Expected malformed-row failure")
        } catch (e: CsvLoadException) {
            assertTrue(e.message.contains("Malformed row"))
        }
    }

    @Test
    fun blankOptionalValuesBecomeNull() {
        assertNull(CsvValues.optionalString(""))
        assertNull(CsvValues.optionalString("   "))
        assertNull(CsvValues.optionalDouble(""))
        assertNull(CsvValues.optionalInt(""))
        assertNull(CsvValues.optionalBoolean(""))
        assertFalse(CsvValues.isUnknownOrPending(""))
        assertTrue(CsvValues.isUnknownOrPending("TO_BE_CALIBRATED_FROM_REAL_IMAGES"))
        assertNotNull(CsvValues.optionalString("SOURCE_SUPPORTED"))
    }

    @Test
    fun observationParserAcceptsARealShapedRowWithoutInventingMissingNumerics() {
        val header = ObservationCsvParser.REQUIRED.joinToString(",")
        val cells = ObservationCsvParser.REQUIRED.map { col ->
            when (col) {
                "record_id" -> "OBS-1"
                "plant_id" -> "P1"
                "crop_type" -> "Tomato"
                "variety" -> "Thilina"
                "ripeness_stage" -> "green-yellow"
                "maturity_status" -> "TO_BE_SOURCED_OR_FIELD_VALIDATED"
                "estimated_days_remaining" -> "TO_BE_SOURCED_OR_FIELD_VALIDATED"
                "fruit_hue_mean" -> ""
                "temperature_c" -> "25.5"
                "humidity_percent" -> ""
                else -> ""
            }
        }.joinToString(",")
        val row = ObservationCsvParser(loader).parse("$header\n$cells\n").single()
        assertEquals("OBS-1", row.recordId)
        assertEquals(25.5, row.temperatureC!!, 0.0)
        assertNull(row.humidityPercent)
        assertNull(row.fruitHueMean)
        assertNull(row.estimatedDaysRemaining)
        assertEquals("TO_BE_SOURCED_OR_FIELD_VALIDATED", row.maturityStatus)
        assertEquals("green-yellow", row.ripenessStage)
    }
}
