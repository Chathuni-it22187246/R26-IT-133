package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.DiseaseReference
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.LeafHealthResult
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import kotlin.math.min

/**
 * Matches measured leaf colour evidence, plus optional visible-feature flags,
 * against tomato rows from 03_disease_reference.csv.
 *
 * Does not invent disease facts beyond the CSV. Shape/pattern symptoms and
 * laboratory signs (ooze, powdery texture, concentric rings, true curl/wilt)
 * are counted only when explicitly provided as flags — never inferred from
 * HSV bins. A single colour cue cannot produce a confident diagnosis.
 */
class DiseaseMatcher(
    private val calibration: TomatoLeafHealthCalibration = TomatoLeafHealthCalibration.PROJECT
) {
    fun assess(
        measurement: LeafColorMeasurement?,
        diseases: List<DiseaseReference>,
        flags: LeafVisibleFlags = LeafVisibleFlags.UNKNOWN
    ): PlantHealthAssessment {
        if (measurement == null || !measurement.hasSamples ||
            measurement.sampledPixelCount < calibration.minSampledPixels
        ) {
            val reason = if (measurement == null || !measurement.hasSamples) {
                PlantHealthReasons.SCAN_REQUIRED
            } else {
                PlantHealthReasons.INSUFFICIENT_FRAME
            }
            return PlantHealthAssessment.scanRequired(measurement).copy(
                reasons = listOf(reason)
            )
        }

        val color = colorEvidence(measurement)
        val tomatoDiseases = diseases.filter {
            it.cropType.equals("Tomato", ignoreCase = true)
        }
        val ranked = tomatoDiseases.mapNotNull { disease ->
            scoreDisease(disease, color, flags)
        }.sortedWith(
            compareByDescending<DiseaseMatch> { it.score }
                .thenByDescending { it.matchedCount }
                .thenByDescending { it.colorMatchRatio }
        )
        val best = ranked.firstOrNull()
        val assignDisease = best != null && best.matchedCount >= 2
        val health = healthStatus(measurement, color, flags, assignDisease)

        val possibleDisease: String
        val confidence: Int?
        val matchedSymptoms: List<String>
        val diagnosisNote: String?
        val source: String?
        val matchedDisease: DiseaseReference?
        if (assignDisease && best != null) {
            possibleDisease = if (best.matchedCount >= 3 && best.score >= 70) {
                PlantHealthReasons.likelyLabel(best.disease.diseaseName)
            } else {
                PlantHealthReasons.possibleLabel(
                    best.disease.recommendedUiLabel,
                    best.disease.diseaseName
                )
            }
            confidence = best.score
            matchedSymptoms = best.matchedSymptoms
            diagnosisNote = best.disease.diagnosisNote
            source = listOfNotNull(
                best.disease.sourceUrl,
                PlantHealthReasons.SOURCE_CSV
            ).joinToString(" · ")
            matchedDisease = best.disease
        } else {
            possibleDisease = if (health == PlantHealthStatus.HEALTHY) {
                PlantHealthReasons.NONE_INDICATED
            } else {
                PlantHealthReasons.UNCERTAIN_DISEASE
            }
            confidence = null
            matchedSymptoms = emptyList()
            diagnosisNote = null
            source = PlantHealthReasons.SOURCE_CSV
            matchedDisease = null
        }

        val reasons = buildList {
            addAll(healthReasons(health, color, assignDisease, best))
            if (!assignDisease && (color.yellowChlorosis || color.brownDarkLesions || color.whitePalePatches) &&
                health != PlantHealthStatus.HEALTHY
            ) {
                add(PlantHealthReasons.SINGLE_COLOR_NOT_DIAGNOSIS)
                add(PlantHealthReasons.WEAK_DISEASE_MATCH)
            }
            add(PlantHealthReasons.IMAGE_UNSUPPORTED_FEATURES)
            if (best?.disease?.diseaseName?.contains("Bacterial Wilt", ignoreCase = true) == true) {
                add(PlantHealthReasons.OOZE_NOT_FROM_IMAGE)
            }
        }

        return PlantHealthAssessment(
            status = health,
            possibleDisease = possibleDisease,
            confidencePercent = confidence,
            matchedSymptoms = matchedSymptoms,
            reasons = reasons,
            recommendation = recommendation(health, matchedDisease),
            sourceReference = source,
            diagnosisNote = diagnosisNote,
            scanRequired = false,
            leafMeasurement = measurement,
            matchedDisease = matchedDisease
        )
    }

    /**
     * Legacy signature kept for compile compatibility. Leaf scans must use [assess].
     */
    @Deprecated("Use assess(measurement, diseases, flags)")
    fun match(
        leafGreenPercent: Double? = null,
        leafYellowPercent: Double? = null,
        leafBrownPercent: Double? = null,
        leafWhitePercent: Double? = null,
        leafSpotPercent: Double? = null,
        leafCurlDetected: Boolean = false,
        leafWiltingDetected: Boolean = false
    ): LeafHealthResult {
        return LeafHealthResult(
            plantHealthStatus = "Not analyzed",
            notes = "Use DiseaseMatcher.assess with CSV disease rows."
        )
    }

    private fun colorEvidence(measurement: LeafColorMeasurement) = LeafColorEvidence(
        yellowChlorosis = measurement.yellowPercent >= calibration.yellowEvidenceMinPercent,
        brownDarkLesions = measurement.brownDarkPercent >= calibration.brownEvidenceMinPercent,
        whitePalePatches = measurement.whitePalePercent >= calibration.whiteEvidenceMinPercent,
        highDiscoloration = measurement.discoloredPercent >= calibration.severeDiscolorationMinPercent
    )

    private fun healthStatus(
        measurement: LeafColorMeasurement,
        color: LeafColorEvidence,
        flags: LeafVisibleFlags,
        diseaseAssigned: Boolean
    ): PlantHealthStatus {
        val severe =
            measurement.discoloredPercent >= calibration.severeDiscolorationMinPercent ||
                measurement.brownDarkPercent >= calibration.severeBrownDarkMinPercent ||
                measurement.yellowPercent >= calibration.severeYellowMinPercent
        val warning =
            measurement.discoloredPercent >= calibration.warningDiscolorationMinPercent ||
                measurement.yellowPercent >= calibration.warningYellowMinPercent ||
                measurement.brownDarkPercent >= calibration.warningBrownDarkMinPercent ||
                measurement.whitePalePercent >= calibration.warningWhiteMinPercent
        val healthyColor =
            measurement.greenPercent >= calibration.healthyGreenMinPercent &&
                measurement.discoloredPercent <= calibration.healthyDiscolorationMaxPercent &&
                measurement.yellowPercent <= calibration.healthyYellowMaxPercent &&
                measurement.brownDarkPercent <= calibration.healthyBrownDarkMaxPercent &&
                measurement.whitePalePercent <= calibration.healthyWhiteMaxPercent
        val strongFlags = flags.wiltDetected == true || flags.curlDetected == true

        return when {
            severe || (diseaseAssigned && color.highDiscoloration) -> PlantHealthStatus.UNHEALTHY
            healthyColor && !strongFlags && !diseaseAssigned -> PlantHealthStatus.HEALTHY
            warning || diseaseAssigned || strongFlags -> PlantHealthStatus.WARNING
            else -> PlantHealthStatus.WARNING
        }
    }

    private fun healthReasons(
        health: PlantHealthStatus,
        color: LeafColorEvidence,
        diseaseAssigned: Boolean,
        best: DiseaseMatch?
    ): List<String> = buildList {
        when (health) {
            PlantHealthStatus.HEALTHY -> add(PlantHealthReasons.MOSTLY_GREEN)
            PlantHealthStatus.UNHEALTHY -> add(PlantHealthReasons.STRONG_DISCOLORATION)
            PlantHealthStatus.WARNING -> add(PlantHealthReasons.MODERATE_DISCOLORATION)
            PlantHealthStatus.UNCERTAIN -> add(PlantHealthReasons.WEAK_DISEASE_MATCH)
        }
        if (diseaseAssigned && best != null) {
            add("Matched documented features for ${best.disease.diseaseName}.")
        }
        if (color.yellowChlorosis) add("Yellow / chlorotic colour share is elevated.")
        if (color.brownDarkLesions) add("Brown / dark lesion colour share is elevated.")
        if (color.whitePalePatches) add("White / pale colour share is elevated.")
        if (health != PlantHealthStatus.HEALTHY) add(PlantHealthReasons.NEEDS_INSPECTION)
    }

    private fun recommendation(
        health: PlantHealthStatus,
        matched: DiseaseReference?
    ): String {
        val inspect = PlantHealthReasons.RECOMMEND_INSPECT
        return when {
            health == PlantHealthStatus.HEALTHY -> PlantHealthReasons.RECOMMEND_HEALTHY
            matched != null -> listOfNotNull(
                inspect,
                matched.diagnosisNote,
                matched.managementSummary
            ).joinToString(" ")
            else -> "$inspect ${PlantHealthReasons.NEEDS_INSPECTION}"
        }
    }

    private fun scoreDisease(
        disease: DiseaseReference,
        color: LeafColorEvidence,
        flags: LeafVisibleFlags
    ): DiseaseMatch? {
        val colorTokens = splitFeatures(disease.visibleColorFeatures)
            .filterNot { it.contains("no unique color", ignoreCase = true) }
        val shapeTokens = splitFeatures(disease.visibleShapePatternFeatures)
        val matched = mutableListOf<String>()
        var colorMatches = 0
        var colorConsidered = 0
        for (token in colorTokens) {
            when (colorTokenResult(token, color, flags)) {
                TokenResult.MATCH -> {
                    colorMatches++
                    colorConsidered++
                    matched += token
                }
                TokenResult.ABSENT -> colorConsidered++
                TokenResult.UNSUPPORTED -> Unit
            }
        }
        var shapeMatches = 0
        for (token in shapeTokens) {
            when (shapeTokenResult(token, flags)) {
                TokenResult.MATCH -> {
                    shapeMatches++
                    matched += token
                }
                TokenResult.ABSENT, TokenResult.UNSUPPORTED -> Unit
            }
        }
        val matchedCount = colorMatches + shapeMatches
        if (matchedCount == 0) return null
        val score = confidenceScore(colorMatches, shapeMatches)
        val ratio = if (colorConsidered == 0) 0f else colorMatches.toFloat() / colorConsidered.toFloat()
        return DiseaseMatch(
            disease = disease,
            score = score,
            matchedCount = matchedCount,
            colorMatchRatio = ratio,
            matchedSymptoms = matched
        )
    }

    private fun confidenceScore(colorMatches: Int, shapeMatches: Int): Int {
        val n = colorMatches + shapeMatches
        if (n <= 0) return 0
        if (n == 1) {
            return min(
                calibration.maxSingleEvidenceConfidencePercent,
                24 + 8 * colorMatches + 8 * shapeMatches
            )
        }
        return min(
            calibration.maxConfidencePercent,
            38 + 16 * colorMatches + 14 * shapeMatches
        )
    }

    private fun colorTokenResult(
        token: String,
        color: LeafColorEvidence,
        flags: LeafVisibleFlags
    ): TokenResult {
        val t = token.lowercase()
        if (t.contains("ooze")) return TokenResult.UNSUPPORTED
        if (t.contains("purple") || t.contains("dull green") ||
            t.contains("water-soaked") || t.contains("grey-green") || t.contains("gray-green")
        ) {
            return TokenResult.UNSUPPORTED
        }
        if (t.contains("bronze")) {
            return if (flags.bronzeDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("mottl")) {
            return if (flags.mottlingDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("white")) {
            return if (color.whitePalePatches) TokenResult.MATCH else TokenResult.ABSENT
        }
        if (t.contains("yellow") || t.contains("chlorotic")) {
            return if (color.yellowChlorosis) TokenResult.MATCH else TokenResult.ABSENT
        }
        if (t.contains("brown") || t.contains("dark")) {
            return if (color.brownDarkLesions) TokenResult.MATCH else TokenResult.ABSENT
        }
        return TokenResult.UNSUPPORTED
    }

    private fun shapeTokenResult(token: String, flags: LeafVisibleFlags): TokenResult {
        val t = token.lowercase()
        if (t.contains("ooze")) return TokenResult.UNSUPPORTED
        if (t.contains("stunting") || t.contains("smaller leaves") || t.contains("drooping")) {
            return TokenResult.UNSUPPORTED
        }
        if (t.contains("wilt")) {
            return if (flags.wiltDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("curl") || t.contains("roll") || t.contains("inward")) {
            return if (flags.curlDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("powdery")) {
            return if (flags.powderyTextureDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("concentric")) {
            return if (flags.concentricRingsDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("streak") || t.contains("necrotic") || t.contains("irregular lesion")) {
            return if (flags.streakOrNecroticDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("shoestring")) {
            return if (flags.shoestringDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("mottl")) {
            return if (flags.mottlingDetected == true) TokenResult.MATCH else TokenResult.UNSUPPORTED
        }
        if (t.contains("sunken")) return TokenResult.UNSUPPORTED
        return TokenResult.UNSUPPORTED
    }

    private fun splitFeatures(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private enum class TokenResult { MATCH, ABSENT, UNSUPPORTED }

    private data class DiseaseMatch(
        val disease: DiseaseReference,
        val score: Int,
        val matchedCount: Int,
        val colorMatchRatio: Float,
        val matchedSymptoms: List<String>
    )
}
