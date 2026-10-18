package com.greenhands.app.harvest.domain

/**
 * Project-calibrated tomato leaf-health thresholds.
 *
 * Scientific labelling:
 * - Documented disease symptoms come from 03_disease_reference.csv
 *   (Sri Lanka DOA / HORDI tomato crop reference). Those rows describe
 *   visible symptoms; they do not publish HSV percentage cut-offs.
 * - Numeric thresholds below are PROJECT CALIBRATION PARAMETERS for this
 *   research build. They are not DOA facts and must be field-validated.
 * - Matching confidence is a project score, not agronomic or diagnostic
 *   certainty. Do not treat a match as a confirmed diagnosis.
 */
data class TomatoLeafHealthCalibration(
    val minSampledPixels: Int,
    val healthyGreenMinPercent: Float,
    val healthyDiscolorationMaxPercent: Float,
    val healthyYellowMaxPercent: Float,
    val healthyBrownDarkMaxPercent: Float,
    val healthyWhiteMaxPercent: Float,
    val warningDiscolorationMinPercent: Float,
    val warningYellowMinPercent: Float,
    val warningBrownDarkMinPercent: Float,
    val warningWhiteMinPercent: Float,
    val severeDiscolorationMinPercent: Float,
    val severeYellowMinPercent: Float,
    val severeBrownDarkMinPercent: Float,
    val yellowEvidenceMinPercent: Float,
    val brownEvidenceMinPercent: Float,
    val whiteEvidenceMinPercent: Float,
    val maxConfidencePercent: Int,
    val maxSingleEvidenceConfidencePercent: Int
) {
    companion object {
        /** PROJECT CALIBRATION THRESHOLDS — not DOA numeric facts. */
        val PROJECT = TomatoLeafHealthCalibration(
            minSampledPixels = 200,
            healthyGreenMinPercent = 70f,
            healthyDiscolorationMaxPercent = 18f,
            healthyYellowMaxPercent = 12f,
            healthyBrownDarkMaxPercent = 8f,
            healthyWhiteMaxPercent = 8f,
            warningDiscolorationMinPercent = 18f,
            warningYellowMinPercent = 14f,
            warningBrownDarkMinPercent = 10f,
            warningWhiteMinPercent = 10f,
            severeDiscolorationMinPercent = 40f,
            severeYellowMinPercent = 28f,
            severeBrownDarkMinPercent = 22f,
            yellowEvidenceMinPercent = 14f,
            brownEvidenceMinPercent = 12f,
            whiteEvidenceMinPercent = 10f,
            maxConfidencePercent = 82,
            maxSingleEvidenceConfidencePercent = 36
        )
    }
}

enum class PlantHealthStatus {
    HEALTHY,
    WARNING,
    UNHEALTHY,
    UNCERTAIN
}

/**
 * Optional visible-feature flags that cannot be derived from HSV bins.
 * Null means unknown / not confirmed from this image.
 * The camera path leaves all flags unknown.
 */
data class LeafVisibleFlags(
    val curlDetected: Boolean? = null,
    val wiltDetected: Boolean? = null,
    val bronzeDetected: Boolean? = null,
    val mottlingDetected: Boolean? = null,
    val streakOrNecroticDetected: Boolean? = null,
    val powderyTextureDetected: Boolean? = null,
    val concentricRingsDetected: Boolean? = null,
    val shoestringDetected: Boolean? = null,
    /** Must never be inferred from an ordinary leaf photo. */
    val viscousOozeDetected: Boolean? = null
) {
    companion object {
        val UNKNOWN = LeafVisibleFlags()
    }
}

data class LeafColorEvidence(
    val yellowChlorosis: Boolean,
    val brownDarkLesions: Boolean,
    val whitePalePatches: Boolean,
    val highDiscoloration: Boolean
)
