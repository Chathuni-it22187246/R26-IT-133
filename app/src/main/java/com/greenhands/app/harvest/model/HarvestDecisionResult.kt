package com.greenhands.app.harvest.model

import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.domain.QualityEvidence
import com.greenhands.app.harvest.domain.RipenessEvidence
import com.greenhands.app.harvest.domain.TomatoHarvestReasons

/**
 * Explainable tomato harvest decision. Kept in memory for the current session.
 * Not persisted and not written to CSV.
 */
data class HarvestDecisionResult(
    val decision: HarvestDecision,
    val ripeness: RipenessEvidence,
    val quality: QualityEvidence,
    val maturity: MaturityAssessment,
    val reasons: List<String>,
    val fruitMeasurement: FruitColorMeasurement?,
    val scanRequired: Boolean = false,
    val harvestReason: String = TomatoHarvestReasons.WAITING_DATE
) {
    val displayLabel: String get() = decision.displayLabel(scanRequired)

    val harvestReasonLabel: String get() = harvestReason

    val maturityReasonLabel: String get() = harvestReason
}
