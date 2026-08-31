package com.greenhands.app.harvest.integration

import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import com.greenhands.app.ui.navigation.Routes

/**
 * Visible colour issue already decided by Harvesting HSV analysis.
 * Decision Making should display/use this label and must not recompute HSV.
 */
enum class HarvestVisibleIssue {
    NO_SIGNIFICANT_DISCOLORATION,
    YELLOWING,
    DARK_BROWN_SPOTS,
    PALE_WHITE_AREAS,
    MIXED_DISCOLORATION;

    val displayLabel: String
        get() = when (this) {
            NO_SIGNIFICANT_DISCOLORATION -> PlantHealthReasons.VISIBLE_NONE
            YELLOWING -> PlantHealthReasons.VISIBLE_YELLOWING
            DARK_BROWN_SPOTS -> PlantHealthReasons.VISIBLE_DARK_BROWN
            PALE_WHITE_AREAS -> PlantHealthReasons.VISIBLE_PALE
            MIXED_DISCOLORATION -> PlantHealthReasons.VISIBLE_MIXED
        }

    companion object {
        fun fromDisplayLabel(label: String): HarvestVisibleIssue = when (label) {
            PlantHealthReasons.VISIBLE_YELLOWING -> YELLOWING
            PlantHealthReasons.VISIBLE_DARK_BROWN -> DARK_BROWN_SPOTS
            PlantHealthReasons.VISIBLE_PALE -> PALE_WHITE_AREAS
            PlantHealthReasons.VISIBLE_MIXED -> MIXED_DISCOLORATION
            else -> NO_SIGNIFICANT_DISCOLORATION
        }
    }
}

/**
 * Structured Harvesting → Decision Making handoff.
 *
 * Harvesting owns detection (HSV health + TFLite possible disease).
 * Decision Making should consume this payload for recommendations/actions.
 * Do not rerun [com.greenhands.app.harvest.detection.TomatoDiseaseClassifier]
 * and do not implement Decision Making business logic in Harvesting.
 *
 * The Decision Making destination is currently Coming Soon
 * (`Routes.comingSoon(Routes.DECISION_MAKING)`). The other team member should:
 * 1. Add their Decision Making composable on [Routes.DECISION_MAKING]
 * 2. Point [HarvestDecisionMakingBridge.destinationRoute] at that route
 * 3. Read [HarvestDecisionMakingBridge.latestHandoff] (or a future saved-state handle)
 */
data class HarvestLeafDecisionHandoff(
    val healthStatus: PlantHealthStatus,
    val detectedVisibleIssue: HarvestVisibleIssue,
    val detectedVisibleIssueLabel: String,
    val possibleDisease: String?,
    val classifierConfidence: Int?,
    val selectedCropType: String?,
    val scanResultId: String?
)

sealed class HarvestDecisionMakingAction {
    data class Open(val handoff: HarvestLeafDecisionHandoff) : HarvestDecisionMakingAction()
    data class RescanRequired(val message: String) : HarvestDecisionMakingAction()
}

/**
 * Integration-ready bridge. Safe when Decision Making is still Coming Soon.
 */
object HarvestDecisionMakingBridge {
    @Volatile
    var latestHandoff: HarvestLeafDecisionHandoff? = null
        private set

    fun destinationRoute(): String = Routes.comingSoon(Routes.DECISION_MAKING)

    fun isDecisionMakingScreenImplemented(): Boolean = false

    fun canOpenFrom(assessment: PlantHealthAssessment): Boolean =
        prepare(assessment, cropType = null, scanResultId = null) is HarvestDecisionMakingAction.Open

    fun prepare(
        assessment: PlantHealthAssessment,
        cropType: String?,
        scanResultId: String?
    ): HarvestDecisionMakingAction {
        if (assessment.scanRequired ||
            assessment.status == PlantHealthStatus.UNCERTAIN ||
            assessment.status == PlantHealthStatus.WARNING
        ) {
            return HarvestDecisionMakingAction.RescanRequired(PlantHealthReasons.UNCERTAIN_SCAN_AGAIN)
        }
        val issue = HarvestVisibleIssue.fromDisplayLabel(
            assessment.visibleIssue.ifBlank { PlantHealthReasons.VISIBLE_NONE }
        )
        val (disease, confidence) = when (assessment.status) {
            PlantHealthStatus.HEALTHY -> PlantHealthReasons.POSSIBLE_NONE to null
            PlantHealthStatus.UNHEALTHY -> {
                if (assessment.showsNamedDisease) {
                    assessment.possibleDisease to assessment.confidencePercent
                } else {
                    val label = assessment.possibleDisease.ifBlank { PlantHealthReasons.POSSIBLE_UNABLE }
                    label to null
                }
            }
            else -> return HarvestDecisionMakingAction.RescanRequired(
                PlantHealthReasons.UNCERTAIN_SCAN_AGAIN
            )
        }
        return HarvestDecisionMakingAction.Open(
            HarvestLeafDecisionHandoff(
                healthStatus = assessment.status,
                detectedVisibleIssue = issue,
                detectedVisibleIssueLabel = issue.displayLabel,
                possibleDisease = disease,
                classifierConfidence = confidence,
                selectedCropType = cropType,
                scanResultId = scanResultId
            )
        )
    }

    fun commit(handoff: HarvestLeafDecisionHandoff) {
        latestHandoff = handoff
    }

    fun commitFromLeaf(
        assessment: PlantHealthAssessment,
        cropType: String?,
        scanResultId: String?
    ): HarvestDecisionMakingAction {
        val action = prepare(assessment, cropType, scanResultId)
        if (action is HarvestDecisionMakingAction.Open) {
            commit(action.handoff)
        } else {
            latestHandoff = null
        }
        return action
    }

    fun clear() {
        latestHandoff = null
    }

    /**
     * Navigate to the Decision Making integration point. Coming Soon is a valid
     * placeholder. Returns false instead of crashing when [navigate] is missing
     * or throws.
     */
    fun navigateSafely(navigate: ((String) -> Unit)?): Boolean {
        if (navigate == null) return false
        return try {
            navigate(destinationRoute())
            true
        } catch (_: Exception) {
            false
        }
    }
}
