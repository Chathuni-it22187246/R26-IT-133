package com.greenhands.app

import com.greenhands.app.harvest.data.csv.AssetCsvLoader
import com.greenhands.app.harvest.data.csv.DiseaseReferenceCsvParser
import com.greenhands.app.harvest.domain.DiseaseMatcher
import com.greenhands.app.harvest.domain.LeafVisibleFlags
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarvestPlantHealthTest {

    private val matcher = DiseaseMatcher()
    private val diseases by lazy {
        val assetsDir = listOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        ).first { it.isDirectory }
        val csv = File(assetsDir, AssetCsvLoader.DISEASE_REFERENCE).readText(Charsets.UTF_8)
        DiseaseReferenceCsvParser().parse(csv)
    }

    @Test
    fun bundledTomatoDiseaseRowsArePresent() {
        val names = diseases.map { it.diseaseName }
        assertEquals(8, diseases.size)
        assertTrue(names.any { it.contains("Early Blight") })
        assertTrue(names.any { it.contains("Late Blight") })
        assertTrue(names.any { it.contains("Powdery Mildew") })
        assertTrue(names.any { it.contains("Bacterial Wilt") })
        assertTrue(names.any { it.contains("TYLCV") })
        assertTrue(names.any { it.contains("Curly Top") })
        assertTrue(names.any { it.contains("TSWV") })
        assertTrue(names.any { it.contains("CMV") })
    }

    @Test
    fun mostlyGreenLowDiscolorationIsHealthy() {
        val result = matcher.assess(
            leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f),
            diseases
        )
        assertEquals(PlantHealthStatus.HEALTHY, result.status)
        assertEquals(PlantHealthReasons.NONE_INDICATED, result.possibleDisease)
        assertNull(result.confidencePercent)
        assertTrue(result.reasons.contains(PlantHealthReasons.MOSTLY_GREEN))
        assertFalse(result.scanRequired)
    }

    @Test
    fun yellowPlusCurlIsPossibleTylcv() {
        val result = matcher.assess(
            leaf(green = 68f, yellow = 22f, brown = 5f, white = 3f),
            diseases,
            LeafVisibleFlags(curlDetected = true)
        )
        assertTrue(result.possibleDisease.contains("TYLCV") || result.possibleDisease.contains("Yellow Leaf Curl"))
        assertTrue(result.confidencePercent != null && result.confidencePercent!! >= 45)
        assertTrue(result.confidencePercent!! <= 82)
        assertTrue(result.status == PlantHealthStatus.WARNING || result.status == PlantHealthStatus.UNHEALTHY)
        assertTrue(result.matchedSymptoms.any { it.contains("yellow", ignoreCase = true) || it.contains("curl", ignoreCase = true) })
    }

    @Test
    fun darkBrownPlusYellowIsPossibleEarlyBlight() {
        val result = matcher.assess(
            leaf(green = 52f, yellow = 20f, brown = 18f, white = 4f),
            diseases
        )
        assertTrue(result.possibleDisease.contains("Early Blight"))
        assertNotNull(result.confidencePercent)
        assertTrue(result.matchedSymptoms.any { it.contains("brown", ignoreCase = true) || it.contains("dark", ignoreCase = true) })
        assertTrue(result.matchedSymptoms.any { it.contains("yellow", ignoreCase = true) })
    }

    @Test
    fun yellowPlusWhitePaleIsPossiblePowderyMildew() {
        val result = matcher.assess(
            leaf(green = 50f, yellow = 20f, brown = 4f, white = 18f),
            diseases
        )
        assertTrue(result.possibleDisease.contains("Powdery Mildew"))
        assertNotNull(result.confidencePercent)
    }

    @Test
    fun bronzeDarkCurlAndStreakIsPossibleTswv() {
        val result = matcher.assess(
            leaf(green = 48f, yellow = 16f, brown = 18f, white = 4f),
            diseases,
            LeafVisibleFlags(
                bronzeDetected = true,
                curlDetected = true,
                streakOrNecroticDetected = true
            )
        )
        assertTrue(result.possibleDisease.contains("TSWV") || result.possibleDisease.contains("Spotted Wilt"))
        assertTrue(result.confidencePercent != null && result.confidencePercent!! >= 60)
    }

    @Test
    fun highDiscolorationWithWeakMatchIsUnhealthyUncertainDisease() {
        val result = matcher.assess(
            leaf(green = 48f, yellow = 20f, brown = 11f, white = 9f),
            diseases
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, result.status)
        assertEquals(PlantHealthReasons.UNCERTAIN_DISEASE, result.possibleDisease)
        assertNull(result.confidencePercent)
        assertTrue(result.reasons.contains(PlantHealthReasons.WEAK_DISEASE_MATCH) ||
            result.reasons.contains(PlantHealthReasons.SINGLE_COLOR_NOT_DIAGNOSIS))
    }

    @Test
    fun insufficientMeasurementIsUncertainScanRequired() {
        val missing = matcher.assess(null, diseases)
        assertEquals(PlantHealthStatus.UNCERTAIN, missing.status)
        assertTrue(missing.scanRequired)
        assertEquals("SCAN REQUIRED", missing.statusLabel)
        assertEquals(PlantHealthReasons.UNCERTAIN_DISEASE, missing.possibleDisease)

        val fewPixels = matcher.assess(
            leaf(green = 80f, yellow = 6f, brown = 4f, white = 3f, samples = 40),
            diseases
        )
        assertTrue(fewPixels.scanRequired)
        assertEquals(PlantHealthStatus.UNCERTAIN, fewPixels.status)
    }

    @Test
    fun singleColorAloneDoesNotProduceConfidentDiseaseDiagnosis() {
        val result = matcher.assess(
            leaf(green = 70f, yellow = 22f, brown = 5f, white = 3f),
            diseases
        )
        assertFalse(result.possibleDisease.contains("TYLCV"))
        assertFalse(result.possibleDisease.contains("CMV"))
        assertEquals(PlantHealthReasons.UNCERTAIN_DISEASE, result.possibleDisease)
        assertNull(result.confidencePercent)
        assertTrue(result.reasons.contains(PlantHealthReasons.SINGLE_COLOR_NOT_DIAGNOSIS))
    }

    @Test
    fun bacterialWiltIsNotInferredFromLeafColorOrOoze() {
        val fromColor = matcher.assess(
            leaf(green = 52f, yellow = 20f, brown = 18f, white = 4f),
            diseases
        )
        assertFalse(fromColor.possibleDisease.contains("Bacterial Wilt"))

        val wiltOnly = matcher.assess(
            leaf(green = 70f, yellow = 8f, brown = 6f, white = 4f),
            diseases,
            LeafVisibleFlags(wiltDetected = true, viscousOozeDetected = true)
        )
        assertFalse(
            "Ooze/wilt from a leaf photo must not yield a confident Bacterial Wilt diagnosis",
            wiltOnly.possibleDisease.contains("Bacterial Wilt") && (wiltOnly.confidencePercent ?: 0) >= 45
        )
        val wiltRow = diseases.first { it.diseaseName.contains("Bacterial Wilt") }
        assertTrue(wiltRow.diagnosisNote!!.contains("ooze", ignoreCase = true))
    }

    @Test
    fun matchingUsesCsvUiLabelAndSourceUrl() {
        val result = matcher.assess(
            leaf(green = 52f, yellow = 20f, brown = 18f, white = 4f),
            diseases
        )
        val early = diseases.first { it.diseaseName.contains("Early Blight") }
        assertTrue(result.possibleDisease.contains("Early Blight"))
        assertNotNull(result.sourceReference)
        assertTrue(result.sourceReference!!.contains("03_disease_reference.csv") ||
            result.sourceReference!!.contains(early.sourceUrl.orEmpty()))
        assertTrue((result.confidencePercent ?: 0) <= 82)
    }

    private fun leaf(
        green: Float,
        yellow: Float,
        brown: Float,
        white: Float,
        samples: Int = 1000
    ): LeafColorMeasurement {
        val discolored = yellow + brown + white
        return LeafColorMeasurement(
            sampledPixelCount = samples,
            hueMean = 90f,
            saturationMean = 0.45f,
            valueMean = 0.55f,
            greenPercent = green,
            yellowPercent = yellow,
            brownDarkPercent = brown,
            whitePalePercent = white,
            discoloredPercent = discolored,
            otherPercent = (100f - green - discolored).coerceAtLeast(0f)
        )
    }
}
