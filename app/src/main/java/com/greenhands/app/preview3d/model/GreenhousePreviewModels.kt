package com.greenhands.app.preview3d.model

import com.greenhands.app.heat.simulation.model.ProvenanceClass
import java.util.Locale

/**
 * Rectangular greenhouse envelope for Phase 3B.1 preview only.
 * Not a Phase 3A [com.greenhands.app.heat.simulation.model.GreenhouseGeometry] input.
 */
data class GreenhouseDimensions(
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val provenance: ProvenanceClass,
    val provenanceNote: String
) {
    fun displaySummary(): String =
        "${formatMetres(lengthM)} m × ${formatMetres(widthM)} m × ${formatMetres(heightM)} m"

    fun diagnosticLine(): String =
        "provenance=${provenance.name} lengthM=$lengthM widthM=$widthM heightM=$heightM"
}

enum class PreviewMode {
    DEMO,
    MY_GREENHOUSE;

    companion object {
        fun fromRoute(raw: String?): PreviewMode? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

object DemoGreenhouseProfile {
    const val LENGTH_M = 10.0
    const val WIDTH_M = 5.0
    const val HEIGHT_M = 3.0
    const val CROP_ROW_COUNT = 3

    /** Spec-required provenance label for the labelled generic demo profile. */
    val PROVENANCE: ProvenanceClass = ProvenanceClass.PREVIEW_ASSUMPTION

    const val PROVENANCE_NOTE =
        "PREVIEW_ASSUMPTION: labelled generic demo greenhouse envelope (10.0 m × 5.0 m × 3.0 m)"

    fun dimensions(): GreenhouseDimensions = GreenhouseDimensions(
        lengthM = LENGTH_M,
        widthM = WIDTH_M,
        heightM = HEIGHT_M,
        provenance = PROVENANCE,
        provenanceNote = PROVENANCE_NOTE
    )

    fun displayLabel(): String = "Demo greenhouse — ${dimensions().displaySummary()}"
}

fun formatMetres(value: Double): String = String.format(Locale.US, "%.1f", value)
