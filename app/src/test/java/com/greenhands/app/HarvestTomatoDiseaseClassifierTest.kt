package com.greenhands.app

import com.greenhands.app.harvest.data.DiseaseReferenceRepository
import com.greenhands.app.harvest.detection.TomatoDiseaseClassScore
import com.greenhands.app.harvest.detection.TomatoDiseaseDebug
import com.greenhands.app.harvest.detection.TomatoDiseaseLabels
import com.greenhands.app.harvest.detection.TomatoDiseasePrediction
import com.greenhands.app.harvest.detection.TomatoDiseaseScores
import com.greenhands.app.harvest.domain.LeafHealthClassifierAssessor
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.TomatoDiseaseClassificationCalibration
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarvestTomatoDiseaseClassifierTest {

    private val hsv = LeafColorMeasurement(
        sampledPixelCount = 800,
        hueMean = 95f,
        saturationMean = 0.42f,
        valueMean = 0.55f,
        greenPercent = 74f,
        yellowPercent = 10f,
        brownDarkPercent = 6f,
        whitePalePercent = 4f,
        discoloredPercent = 20f,
        otherPercent = 6f
    )

    private val hsvHealthyGreen = LeafColorMeasurement(
        sampledPixelCount = 800,
        hueMean = 100f,
        saturationMean = 0.48f,
        valueMean = 0.52f,
        greenPercent = 82f,
        yellowPercent = 8f,
        brownDarkPercent = 4f,
        whitePalePercent = 3f,
        discoloredPercent = 15f,
        otherPercent = 3f
    )

    private val hsvAbnormal = LeafColorMeasurement(
        sampledPixelCount = 800,
        hueMean = 55f,
        saturationMean = 0.40f,
        valueMean = 0.45f,
        greenPercent = 48f,
        yellowPercent = 24f,
        brownDarkPercent = 16f,
        whitePalePercent = 6f,
        discoloredPercent = 46f,
        otherPercent = 6f
    )

    @Test
    fun displayNamesMatchRequiredMapping() {
        assertEquals("Bacterial Spot", TomatoDiseaseLabels.displayName("Bacterial_spot"))
        assertEquals("Early Blight", TomatoDiseaseLabels.displayName("Early_blight"))
        assertEquals("Late Blight", TomatoDiseaseLabels.displayName("Late_blight"))
        assertEquals("Leaf Mold", TomatoDiseaseLabels.displayName("Leaf_Mold"))
        assertEquals("Septoria Leaf Spot", TomatoDiseaseLabels.displayName("Septoria_leaf_spot"))
        assertEquals(
            "Spider Mites / Two-Spotted Spider Mite",
            TomatoDiseaseLabels.displayName("Spider_mites Two-spotted_spider_mite")
        )
        assertEquals("Target Spot", TomatoDiseaseLabels.displayName("Target_Spot"))
        assertEquals(
            "Tomato Yellow Leaf Curl Virus",
            TomatoDiseaseLabels.displayName("Tomato_Yellow_Leaf_Curl_Virus")
        )
        assertEquals("Tomato Mosaic Virus", TomatoDiseaseLabels.displayName("Tomato_mosaic_virus"))
        assertEquals("Healthy", TomatoDiseaseLabels.displayName("healthy"))
        assertEquals("Powdery Mildew", TomatoDiseaseLabels.displayName("powdery_mildew"))
    }

    @Test
    fun tomatoMosaicVirusIsNeverCucumberMosaicVirus() {
        val display = TomatoDiseaseLabels.displayName("Tomato_mosaic_virus")
        assertFalse(display.contains("Cucumber", ignoreCase = true))
        assertFalse(display.contains("CMV", ignoreCase = true))
        val repo = DiseaseReferenceRepository()
        val lookup = repo.lookup("Tomato_mosaic_virus")
        assertEquals("Tomato Mosaic Virus", lookup.displayName)
        assertFalse(lookup.recommendation.contains("Cucumber Mosaic", ignoreCase = true))
    }

    @Test
    fun alreadyNormalizedScoresDoNotGetSoftmaxAgain() {
        val probs = floatArrayOf(0.02f, 0.03f, 0.05f, 0.04f, 0.06f, 0.02f, 0.03f, 0.04f, 0.02f, 0.64f, 0.05f)
        assertTrue(TomatoDiseaseScores.looksLikeProbabilities(probs))
        val (out, applied) = TomatoDiseaseScores.asProbabilities(probs)
        assertFalse(applied)
        assertEquals(0.64f, out[9], 1.0e-5f)
    }

    @Test
    fun logitsReceiveSoftmaxOnce() {
        val logits = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 4f, 0f)
        assertFalse(TomatoDiseaseScores.looksLikeProbabilities(logits))
        val (probs, applied) = TomatoDiseaseScores.asProbabilities(logits)
        assertTrue(applied)
        val sum = probs.sum()
        assertEquals(1f, sum, 1.0e-4f)
        assertEquals(9, probs.indices.maxByOrNull { probs[it] })
    }

    @Test
    fun healthyAboveThresholdMapsToHealthyNone() {
        val prediction = prediction(
            index = 9,
            raw = "healthy",
            confidence = 0.88f,
            healthy = true
        )
        val assessment = LeafHealthClassifierAssessor.fromClassifier(
            hsv,
            prediction,
            DiseaseReferenceRepository().lookup("healthy")
        )
        assertEquals(PlantHealthStatus.HEALTHY, assessment.status)
        assertEquals(PlantHealthReasons.NONE, assessment.possibleDisease)
        assertEquals(88, assessment.confidencePercent)
        assertFalse(assessment.scanRequired)
        assertTrue(assessment.reasons.any { it.contains("HSV") })
        assertEquals(hsv, assessment.leafMeasurement)
    }

    @Test
    fun diseaseAboveThresholdMapsToUnhealthyDisplayName() {
        val prediction = prediction(
            index = 1,
            raw = "Early_blight",
            confidence = 0.81f,
            healthy = false,
            secondRaw = "healthy",
            secondConfidence = 0.09f
        )
        val assessment = LeafHealthClassifierAssessor.fromClassifier(
            hsvAbnormal,
            prediction,
            DiseaseReferenceRepository().lookup("Early_blight")
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, assessment.status)
        assertEquals("Early Blight", assessment.possibleDisease)
        assertEquals(81, assessment.confidencePercent)
        assertFalse(assessment.possibleDisease.contains("Likely"))
        assertTrue(assessment.reasons.any { it.contains("fusionAgreement=AGREE_UNHEALTHY") })
    }

    @Test
    fun diseaseHighConfidenceHealthyHsvIsUncertainDisagree() {
        val prediction = prediction(
            index = 4,
            raw = "Septoria_leaf_spot",
            confidence = 0.76f,
            healthy = false,
            secondRaw = "healthy",
            secondConfidence = 0.08f
        )
        val assessment = LeafHealthClassifierAssessor.fromClassifier(
            hsvHealthyGreen,
            prediction,
            DiseaseReferenceRepository().lookup("Septoria_leaf_spot")
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertEquals("Septoria Leaf Spot", assessment.possibleDisease)
        assertEquals(76, assessment.confidencePercent)
        assertTrue(assessment.reasons.contains(PlantHealthReasons.CLASSIFIER_HSV_DISAGREE))
        assertTrue(assessment.reasons.any { it.contains("fusionAgreement=DISAGREE") })
        assertTrue(assessment.matchedSymptoms.isEmpty())
        assertFalse(assessment.possibleDisease.contains("Likely"))
    }

    @Test
    fun diseaseHighConfidenceCloseTop2IsUncertain() {
        val prediction = prediction(
            index = 4,
            raw = "Septoria_leaf_spot",
            confidence = 0.76f,
            healthy = false,
            secondRaw = "Target_Spot",
            secondConfidence = 0.70f
        )
        val assessment = LeafHealthClassifierAssessor.fromClassifier(
            hsvAbnormal,
            prediction,
            DiseaseReferenceRepository().lookup("Septoria_leaf_spot")
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertEquals("Septoria Leaf Spot", assessment.possibleDisease)
        assertEquals(76, assessment.confidencePercent)
        assertTrue(assessment.reasons.contains(PlantHealthReasons.CLASSIFIER_MARGIN_TOO_SMALL))
        assertTrue(assessment.reasons.any { it.contains("fusionAgreement=INSUFFICIENT") })
    }

    @Test
    fun belowThresholdIsUncertain() {
        val prediction = prediction(
            index = 2,
            raw = "Late_blight",
            confidence = 0.42f,
            healthy = false,
            meets = false
        )
        val assessment = LeafHealthClassifierAssessor.fromClassifier(
            hsv,
            prediction,
            DiseaseReferenceRepository().lookup("Late_blight")
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertEquals(PlantHealthReasons.UNCERTAIN_DISEASE, assessment.possibleDisease)
        assertEquals(42, assessment.confidencePercent)
        assertTrue(assessment.reasons.contains(PlantHealthReasons.CLASSIFIER_BELOW_THRESHOLD))
        assertEquals(PlantHealthReasons.RECOMMEND_GENERIC, assessment.recommendation)
    }

    @Test
    fun projectThresholdIsSeventyPercent() {
        assertEquals(0.70f, TomatoDiseaseClassificationCalibration.CONFIDENCE_THRESHOLD)
        assertEquals(0.10f, TomatoDiseaseClassificationCalibration.MIN_TOP1_TOP2_MARGIN)
        assertEquals(11, TomatoDiseaseClassificationCalibration.EXPECTED_CLASS_COUNT)
        assertEquals(224, TomatoDiseaseClassificationCalibration.TARGET_INPUT_SIDE)
    }

    @Test
    fun defaultLabelOrderMatchesExport() {
        assertEquals(11, TomatoDiseaseLabels.DEFAULT_ORDER.size)
        assertEquals("Bacterial_spot", TomatoDiseaseLabels.DEFAULT_ORDER[0])
        assertEquals("Leaf_Mold", TomatoDiseaseLabels.DEFAULT_ORDER[3])
        assertEquals("healthy", TomatoDiseaseLabels.DEFAULT_ORDER[9])
        assertEquals("powdery_mildew", TomatoDiseaseLabels.DEFAULT_ORDER[10])
        assertEquals("Tomato_mosaic_virus", TomatoDiseaseLabels.DEFAULT_ORDER[8])
        assertTrue(TomatoDiseaseLabels.isHealthy(TomatoDiseaseLabels.DEFAULT_ORDER[9]))
        assertFalse(TomatoDiseaseLabels.isHealthy(TomatoDiseaseLabels.DEFAULT_ORDER[3]))
    }

    @Test
    fun packagedLabelsFileMatchesExportOrder() {
        val file = File("src/main/assets/models/tomato_disease_labels.txt")
        assertTrue("labels asset missing at ${file.absolutePath}", file.exists())
        val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(TomatoDiseaseLabels.DEFAULT_ORDER, lines)
        assertEquals("Leaf_Mold", lines[3])
        assertEquals("healthy", lines[9])
        assertTrue(TomatoDiseaseLabels.isHealthy(lines[9]))
    }

    @Test
    fun debugScoreLogKeepsIndex3LeafMoldAndIndex9Healthy() {
        val scores = FloatArray(11) { i -> i / 100f }
        val logged = TomatoDiseaseDebug.formatScores(scores)
        assertTrue(logged.contains("3:Leaf_Mold="))
        assertTrue(logged.contains("9:healthy="))
    }

    private fun prediction(
        index: Int,
        raw: String,
        confidence: Float,
        healthy: Boolean,
        meets: Boolean = confidence >= 0.70f,
        secondRaw: String = "Leaf_Mold",
        secondConfidence: Float = (confidence - 0.20f).coerceAtLeast(0.01f)
    ) = TomatoDiseasePrediction(
        classIndex = index,
        rawClassName = raw,
        displayName = TomatoDiseaseLabels.displayName(raw),
        confidence = confidence,
        meetsThreshold = meets,
        isHealthyClass = healthy,
        appliedSoftmax = false,
        topPredictions = listOf(
            TomatoDiseaseClassScore(
                classIndex = index,
                rawClassName = raw,
                displayName = TomatoDiseaseLabels.displayName(raw),
                confidence = confidence
            ),
            TomatoDiseaseClassScore(
                classIndex = 3,
                rawClassName = secondRaw,
                displayName = TomatoDiseaseLabels.displayName(secondRaw),
                confidence = secondConfidence
            )
        )
    )
}
