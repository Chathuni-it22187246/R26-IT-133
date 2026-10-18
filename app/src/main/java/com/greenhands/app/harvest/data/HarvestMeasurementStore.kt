package com.greenhands.app.harvest.data

import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthAssessment

/**
 * Last HSV measurements and fruit harvest decision from the current process.
 * Not persisted and not written to CSV.
 */
object HarvestMeasurementStore {
    @Volatile
    var lastFruit: FruitColorMeasurement? = null

    @Volatile
    var lastLeaf: LeafColorMeasurement? = null

    @Volatile
    var lastFruitDecision: HarvestDecisionResult? = null

    @Volatile
    var lastLeafHealth: PlantHealthAssessment? = null

    @Volatile
    var lastLeafScanId: String? = null

    /**
     * HEALTHY/UNHEALTHY hold for the current leaf-scan camera session only.
     * Never read [lastLeafHealth] for this — that is the previous leaf's result.
     * Reset by [beginNewLeafScan] when a new Scan Leaf screen starts.
     */
    @Volatile
    var activeLeafScanHysteresis: PlantHealthStatus? = null

    fun beginNewLeafScan() {
        activeLeafScanHysteresis = null
    }

    /** Temporary PNG of the original hybrid-validated leaf crop. */
    @Volatile
    var lastDiseaseDebugCropPath: String? = null

    /** Temporary PNG of the classifier-only leaf-focused ROI. */
    @Volatile
    var lastDiseaseDebugFocusedPath: String? = null

    /** Temporary PNG of the exact 224x224 tensor input. */
    @Volatile
    var lastDiseaseDebugInputPath: String? = null

    @Volatile
    var lastDiseaseDebugSummary: String? = null

    fun clearSession() {
        lastFruit = null
        lastLeaf = null
        lastFruitDecision = null
        lastLeafHealth = null
        lastLeafScanId = null
        activeLeafScanHysteresis = null
        lastDiseaseDebugCropPath = null
        lastDiseaseDebugFocusedPath = null
        lastDiseaseDebugInputPath = null
        lastDiseaseDebugSummary = null
    }
}
