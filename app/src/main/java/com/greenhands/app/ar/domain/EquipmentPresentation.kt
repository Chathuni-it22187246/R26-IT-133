package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Transient equipment presentation. This is not persisted [EquipmentRuntimeState].
 *
 * Actual relay telemetry, when present, is independent of Visualization start/stop.
 */
data class EquipmentPresentationContext(
    val mode: PreviewMode,
    val visualizationRunning: Boolean,
    val actualRelayById: Map<String, EquipmentRuntimeState> = emptyMap()
)

object EquipmentPresentationCopy {
    const val NOT_EVALUATED = "Not evaluated"
    const val NOT_EVALUATED_EXPLANATION = "Live equipment state has not been evaluated."
}

object EquipmentPresentation {
    fun label(
        instanceId: String,
        predicted: EquipmentRuntimeState,
        context: EquipmentPresentationContext
    ): String {
        val actual = context.actualRelayById[instanceId]
        if (actual == EquipmentRuntimeState.ON || actual == EquipmentRuntimeState.OFF) {
            return EquipmentRuntimeEvaluator.label(actual, EquipmentStateProvenance.ACTUAL)
        }
        return when (context.mode) {
            PreviewMode.DEMO -> if (context.visualizationRunning) {
                EquipmentRuntimeEvaluator.label(predicted, EquipmentStateProvenance.SIMULATED)
            } else {
                ArRuntimeCopy.NOT_RUNNING
            }
            PreviewMode.MY_GREENHOUSE -> if (context.visualizationRunning) {
                EquipmentRuntimeEvaluator.label(predicted, EquipmentStateProvenance.PREDICTED)
            } else {
                EquipmentPresentationCopy.NOT_EVALUATED
            }
        }
    }

    fun aggregate(
        instanceIds: List<String>,
        predictedById: Map<String, EquipmentRuntimeState>,
        context: EquipmentPresentationContext
    ): String {
        if (instanceIds.isEmpty()) return ArHudContent.NONE_PLACED
        val labels = instanceIds.map { id ->
            label(id, predictedById[id] ?: EquipmentRuntimeState.NOT_RUNNING, context)
        }
        if (labels.all { it == EquipmentPresentationCopy.NOT_EVALUATED }) {
            return EquipmentPresentationCopy.NOT_EVALUATED
        }
        if (labels.all { it == ArRuntimeCopy.NOT_RUNNING }) {
            return ArRuntimeCopy.NOT_RUNNING
        }
        val counts = LinkedHashMap<String, Int>()
        labels.forEach { state ->
            counts[state] = (counts[state] ?: 0) + 1
        }
        return counts.entries.joinToString(" · ") { (state, n) -> "$n $state" }
    }

    fun showsNotEvaluatedExplanation(context: EquipmentPresentationContext): Boolean {
        if (context.mode != PreviewMode.MY_GREENHOUSE || context.visualizationRunning) return false
        return context.actualRelayById.values.none {
            it == EquipmentRuntimeState.ON || it == EquipmentRuntimeState.OFF
        }
    }

    fun tone(label: String, predicted: EquipmentRuntimeState): ArRuntimeTone {
        return when {
            label == EquipmentPresentationCopy.NOT_EVALUATED -> ArRuntimeTone.NOT_RUNNING
            label.startsWith(ArRuntimeCopy.ACTUAL) ||
                label.startsWith(ArRuntimeCopy.PREDICTED) ||
                label.startsWith(ArRuntimeCopy.SIMULATED) -> EquipmentRuntimeEvaluator.tone(predicted)
            else -> EquipmentRuntimeEvaluator.tone(predicted)
        }
    }
}
