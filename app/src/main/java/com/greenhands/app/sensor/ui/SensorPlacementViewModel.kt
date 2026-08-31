package com.greenhands.app.sensor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.domain.GreenhouseConfigResult
import com.greenhands.app.sensor.domain.SensorIdFactory
import com.greenhands.app.sensor.domain.SensorCountOptimizationEvaluator
import com.greenhands.app.sensor.domain.SimulatedGreenhouseScanSession
import com.greenhands.app.sensor.model.CoverageByType
import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.DEFAULT_SCAN_STEP_DELAY_MS
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.OptimizationApplySummary
import com.greenhands.app.sensor.model.OptimizationEvaluation
import com.greenhands.app.sensor.model.OptimizationResult
import com.greenhands.app.sensor.model.ScanUiState
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SensorWorkflowStep {
    SETUP,
    SCAN,
    PLACE,
    COVERAGE,
    OPTIMIZE
}

data class SensorPlacementUiState(
    val greenhouse: Greenhouse = Greenhouse(),
    val physicalConfig: GreenhousePhysicalConfig = GreenhousePhysicalConfig.default(),
    /** True after a valid Create Virtual Greenhouse action in this session. */
    val greenhouseConfigured: Boolean = false,
    val configError: String? = null,
    val sensors: List<Sensor> = emptyList(),
    val selectedSensorId: String? = null,
    /**
     * Aggregate monitoring coverage across all sensor types.
     * Does not treat different types covering the same cell as overlap.
     */
    val coverage: CoverageResult,
    /** Independent coverage for each [SensorType] (same-type overlap only). */
    val coverageByType: CoverageByType,
    val step: SensorWorkflowStep = SensorWorkflowStep.SETUP,
    val placementError: String? = null,
    val scan: ScanUiState = ScanUiState(),
    /** True while the sensor-type picker is visible. Does not create a sensor. */
    val showSensorTypePicker: Boolean = false,
    /** Type highlighted in the picker / used for the next cell tap when awaiting placement. */
    val pendingSensorType: SensorType? = null,
    /** True after "Place Sensor" — next empty-cell tap creates a sensor of [pendingSensorType]. */
    val awaitingCellPlacement: Boolean = false,
    /** Sensor type targeted by the Optimize Placement stage. */
    val optimizationSensorType: SensorType = SensorType.TEMPERATURE,
    /** Multi-count evaluation; populated after Analyze. */
    val optimizationEvaluation: OptimizationEvaluation? = null,
    /** Additional-sensor count currently previewed (system recommendation or user alternative). */
    val selectedOptimizationAlternative: Int? = null,
    val optimizationResult: OptimizationResult? = null,
    /** Selected recommendation cells as "x,y" keys (defaults to all after analyze). */
    val selectedOptimizationPositions: Set<String> = emptySet(),
    val isOptimizing: Boolean = false,
    /** Shown after explicit Apply; cleared when user dismisses or starts a new analyze. */
    val lastOptimizationApply: OptimizationApplySummary? = null
) {
    val selectedSensor: Sensor?
        get() = sensors.find { it.id == selectedSensorId }

    val sensorCount: Int get() = sensors.size
}

fun optimizationPositionKey(x: Double, y: Double): String = "$x,$y"

class SensorPlacementViewModel(
    initialGreenhouse: Greenhouse = Greenhouse(),
    scanStepDelayMs: Long = DEFAULT_SCAN_STEP_DELAY_MS
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(initialGreenhouse))
    val state: StateFlow<SensorPlacementUiState> = _state.asStateFlow()
    private var nextSequence = 1
    private val scanSession = SimulatedGreenhouseScanSession(viewModelScope, scanStepDelayMs)

    init {
        viewModelScope.launch {
            scanSession.state.collect { scan ->
                _state.update { it.copy(scan = scan) }
            }
        }
    }

    fun startScan() {
        scanSession.start()
    }

    fun resetScan() {
        scanSession.reset()
    }

    /**
     * Validates [config], derives the logical [Greenhouse] grid, and replaces the current greenhouse.
     * Clears sensors and placement selection so coordinates stay inside the new bounds.
     */
    fun createOrUpdateGreenhouse(config: GreenhousePhysicalConfig): Boolean {
        return when (val result = GreenhouseConfigFactory.validate(config)) {
            is GreenhouseConfigResult.Invalid -> {
                _state.update {
                    it.copy(
                        configError = result.message,
                        greenhouseConfigured = false,
                        placementError = null
                    )
                }
                false
            }
            is GreenhouseConfigResult.Success -> {
                nextSequence = 1
                scanSession.reset()
                val byType = CoverageCalculator.calculateByType(result.greenhouse, emptyList())
                _state.update {
                    it.copy(
                        physicalConfig = result.config,
                        greenhouse = result.greenhouse,
                        greenhouseConfigured = true,
                        configError = null,
                        sensors = emptyList(),
                        selectedSensorId = null,
                        coverageByType = byType,
                        coverage = byType.monitoring,
                        placementError = null,
                        showSensorTypePicker = false,
                        pendingSensorType = null,
                        awaitingCellPlacement = false,
                        scan = ScanUiState(),
                        optimizationResult = null,
                        optimizationEvaluation = null,
                        selectedOptimizationAlternative = null,
                        selectedOptimizationPositions = emptySet(),
                        isOptimizing = false,
                        lastOptimizationApply = null
                    )
                }
                true
            }
        }
    }

    fun clearConfigError() {
        _state.update { it.copy(configError = null) }
    }

    /** Opens type selection. Does not create a sensor. */
    fun openSensorTypePicker() {
        _state.update {
            it.copy(
                showSensorTypePicker = true,
                pendingSensorType = SensorType.TEMPERATURE,
                awaitingCellPlacement = false,
                selectedSensorId = null,
                placementError = null
            )
        }
    }

    fun selectPendingSensorType(type: SensorType) {
        if (!_state.value.showSensorTypePicker) return
        _state.update { it.copy(pendingSensorType = type, placementError = null) }
    }

    /** Locks the chosen type and waits for an empty-cell tap. Still does not create a sensor. */
    fun confirmPendingSensorType() {
        val type = _state.value.pendingSensorType ?: return
        _state.update {
            it.copy(
                showSensorTypePicker = false,
                pendingSensorType = type,
                awaitingCellPlacement = true,
                selectedSensorId = null,
                placementError = null
            )
        }
    }

    fun cancelSensorTypePicker() {
        _state.update {
            it.copy(
                showSensorTypePicker = false,
                pendingSensorType = null,
                awaitingCellPlacement = false,
                placementError = null
            )
        }
    }

    fun cancelAwaitingPlacement() {
        _state.update {
            it.copy(
                awaitingCellPlacement = false,
                pendingSensorType = null,
                placementError = null
            )
        }
    }

    fun addSensor(
        x: Double,
        y: Double,
        coverageRadius: Double = DEFAULT_COVERAGE_RADIUS_CELLS,
        type: SensorType? = null
    ): Boolean {
        val greenhouse = _state.value.greenhouse
        if (!CoverageCalculator.isValidPosition(greenhouse, x, y)) {
            setPlacementError(OUT_OF_BOUNDS)
            return false
        }
        val resolvedType = type ?: _state.value.pendingSensorType ?: SensorType.TEMPERATURE
        val sensor = Sensor(
            id = SensorIdFactory.idFor(nextSequence),
            type = resolvedType,
            x = x,
            y = y,
            coverageRadius = coverageRadius,
            status = SensorStatus.ACTIVE
        )
        nextSequence += 1
        _state.update {
            val sensors = it.sensors + sensor
            val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
            it.copy(
                sensors = sensors,
                selectedSensorId = sensor.id,
                coverageByType = byType,
                coverage = byType.monitoring,
                placementError = null,
                showSensorTypePicker = false,
                awaitingCellPlacement = false,
                pendingSensorType = null
            )
        }
        return true
    }

    fun removeSensor(id: String): Boolean {
        val current = _state.value.sensors
        if (current.none { it.id == id }) return false
        val remaining = current.filterNot { it.id == id }
        val selected = _state.value.selectedSensorId?.takeIf { it != id && remaining.any { sensor -> sensor.id == it } }
        replaceSensors(remaining, selectedId = selected)
        return true
    }

    fun moveSensor(id: String, x: Double, y: Double): Boolean {
        val greenhouse = _state.value.greenhouse
        if (!CoverageCalculator.isValidPosition(greenhouse, x, y)) {
            setPlacementError(OUT_OF_BOUNDS)
            return false
        }
        val current = _state.value.sensors
        if (current.none { it.id == id }) return false
        val updated = current.map { sensor ->
            if (sensor.id == id) sensor.copy(x = x, y = y) else sensor
        }
        replaceSensors(updated, selectedId = id)
        return true
    }

    fun selectSensor(id: String): Boolean {
        if (_state.value.sensors.none { it.id == id }) return false
        _state.update {
            it.copy(
                selectedSensorId = id,
                placementError = null,
                showSensorTypePicker = false,
                awaitingCellPlacement = false,
                pendingSensorType = null
            )
        }
        return true
    }

    fun deselectSensor() {
        _state.update {
            it.copy(
                selectedSensorId = null,
                placementError = null
            )
        }
    }

    fun setSensorActive(id: String, active: Boolean): Boolean {
        val current = _state.value.sensors
        if (current.none { it.id == id }) return false
        val status = if (active) SensorStatus.ACTIVE else SensorStatus.INACTIVE
        val updated = current.map { sensor ->
            if (sensor.id == id) sensor.copy(status = status) else sensor
        }
        replaceSensors(updated, selectedId = _state.value.selectedSensorId)
        return true
    }

    fun resetSensors() {
        nextSequence = 1
        _state.update {
            val byType = CoverageCalculator.calculateByType(it.greenhouse, emptyList())
            it.copy(
                sensors = emptyList(),
                selectedSensorId = null,
                coverageByType = byType,
                coverage = byType.monitoring,
                placementError = null,
                showSensorTypePicker = false,
                pendingSensorType = null,
                awaitingCellPlacement = false,
                optimizationResult = null,
                optimizationEvaluation = null,
                selectedOptimizationAlternative = null,
                selectedOptimizationPositions = emptySet(),
                isOptimizing = false,
                lastOptimizationApply = null
            )
        }
    }

    fun goToStep(step: SensorWorkflowStep) {
        _state.update { it.copy(step = step, placementError = null) }
    }

    fun clearPlacementError() {
        _state.update { it.copy(placementError = null) }
    }

    fun selectOptimizationSensorType(type: SensorType) {
        _state.update {
            it.copy(
                optimizationSensorType = type,
                optimizationResult = null,
                optimizationEvaluation = null,
                selectedOptimizationAlternative = null,
                selectedOptimizationPositions = emptySet(),
                isOptimizing = false,
                lastOptimizationApply = null
            )
        }
    }

    fun calculateOptimization() {
        val snapshot = _state.value
        _state.update { it.copy(isOptimizing = true) }
        val evaluation = SensorCountOptimizationEvaluator.evaluate(
            greenhouse = snapshot.greenhouse,
            sensors = snapshot.sensors,
            sensorType = snapshot.optimizationSensorType
        )
        val recommendedCount = evaluation.recommendedAdditionalCount
        _state.update {
            applyEvaluationSelection(
                state = it.copy(isOptimizing = false, placementError = null, lastOptimizationApply = null),
                evaluation = evaluation,
                additionalCount = recommendedCount,
                userSelectedAlternative = false
            )
        }
    }

    fun selectOptimizationAlternative(additionalCount: Int) {
        val evaluation = _state.value.optimizationEvaluation ?: return
        if (evaluation.candidateFor(additionalCount) == null) return
        _state.update {
            applyEvaluationSelection(
                state = it,
                evaluation = evaluation,
                additionalCount = additionalCount,
                userSelectedAlternative = additionalCount != evaluation.recommendedAdditionalCount
            )
        }
    }

    private fun applyEvaluationSelection(
        state: SensorPlacementUiState,
        evaluation: OptimizationEvaluation,
        additionalCount: Int?,
        @Suppress("UNUSED_PARAMETER") userSelectedAlternative: Boolean
    ): SensorPlacementUiState {
        if (additionalCount == null) {
            return state.copy(
                optimizationEvaluation = evaluation,
                optimizationResult = null,
                selectedOptimizationAlternative = null,
                selectedOptimizationPositions = emptySet()
            )
        }
        val candidate = evaluation.candidateFor(additionalCount) ?: return state.copy(
            optimizationEvaluation = evaluation
        )
        val selected = candidate.result.recommendedPositions
            .map { optimizationPositionKey(it.x, it.y) }
            .toSet()
        return state.copy(
            optimizationEvaluation = evaluation,
            optimizationResult = candidate.result,
            selectedOptimizationAlternative = additionalCount,
            selectedOptimizationPositions = selected
        )
    }

    fun toggleOptimizationPosition(x: Double, y: Double) {
        val key = optimizationPositionKey(x, y)
        _state.update { state ->
            val result = state.optimizationResult ?: return@update state
            if (result.recommendedPositions.none { optimizationPositionKey(it.x, it.y) == key }) {
                return@update state
            }
            val next = state.selectedOptimizationPositions.toMutableSet()
            if (!next.add(key)) next.remove(key)
            state.copy(selectedOptimizationPositions = next)
        }
    }

    /**
     * Creates real sensors only for currently selected recommendations.
     * Preserves existing sensors and IDs; assigns new IDs via [SensorIdFactory].
     */
    fun applyOptimization(): Boolean {
        val state = _state.value
        val result = state.optimizationResult ?: return false
        val selected = result.recommendedPositions
            .filter { optimizationPositionKey(it.x, it.y) in state.selectedOptimizationPositions }
            .sortedBy { it.rank }
        if (selected.isEmpty()) return false

        val occupied = state.sensors.map {
            kotlin.math.floor(it.x).toInt() to kotlin.math.floor(it.y).toInt()
        }.toMutableSet()
        val additions = mutableListOf<Sensor>()
        for (pos in selected) {
            val cell = kotlin.math.floor(pos.x).toInt() to kotlin.math.floor(pos.y).toInt()
            if (cell in occupied) continue
            if (!CoverageCalculator.isValidPosition(state.greenhouse, pos.x, pos.y)) continue
            additions += Sensor(
                id = SensorIdFactory.idFor(nextSequence),
                type = result.sensorType,
                x = pos.x,
                y = pos.y,
                coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS,
                status = SensorStatus.ACTIVE
            )
            nextSequence += 1
            occupied += cell
        }
        if (additions.isEmpty()) return false

        val beforeTotal = state.sensors.size
        val beforeType = CoverageCalculator.calculateForType(
            state.greenhouse,
            state.sensors,
            result.sensorType
        )
        val merged = state.sensors + additions
        val byType = CoverageCalculator.calculateByType(state.greenhouse, merged)
        val afterType = byType.forType(result.sensorType)
        val summary = OptimizationApplySummary(
            sensorType = result.sensorType,
            beforeSensorCount = beforeTotal,
            afterSensorCount = merged.size,
            appliedRecommendationCount = additions.size,
            beforeCoveragePercent = beforeType.overallCoveragePercent,
            afterCoveragePercent = afterType.overallCoveragePercent,
            coverageImprovement = afterType.overallCoveragePercent - beforeType.overallCoveragePercent,
            beforeBlindSpotPercent = beforeType.blindSpotPercent,
            afterBlindSpotPercent = afterType.blindSpotPercent,
            beforeBlindSpotCells = beforeType.blindSpotCells,
            afterBlindSpotCells = afterType.blindSpotCells,
            beforeOverlapCells = beforeType.overlapCells,
            afterOverlapCells = afterType.overlapCells
        )
        _state.update {
            it.copy(
                sensors = merged,
                selectedSensorId = additions.last().id,
                coverageByType = byType,
                coverage = byType.monitoring,
                optimizationResult = null,
                optimizationEvaluation = null,
                selectedOptimizationAlternative = null,
                selectedOptimizationPositions = emptySet(),
                isOptimizing = false,
                placementError = null,
                lastOptimizationApply = summary
            )
        }
        return true
    }

    /** Reject recommendations and keep the current sensor configuration unchanged. */
    fun keepCurrentPlacement() {
        clearOptimization()
    }

    fun clearOptimization() {
        _state.update {
            it.copy(
                optimizationResult = null,
                optimizationEvaluation = null,
                selectedOptimizationAlternative = null,
                selectedOptimizationPositions = emptySet(),
                isOptimizing = false
            )
        }
    }

    fun dismissOptimizationApplySummary() {
        _state.update { it.copy(lastOptimizationApply = null) }
    }

    private fun replaceSensors(sensors: List<Sensor>, selectedId: String?) {
        val greenhouse = _state.value.greenhouse
        val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
        _state.update {
            it.copy(
                sensors = sensors,
                selectedSensorId = selectedId,
                coverageByType = byType,
                coverage = byType.monitoring,
                placementError = null
            )
        }
    }

    private fun setPlacementError(message: String) {
        _state.update { it.copy(placementError = message) }
    }

    companion object {
        const val OUT_OF_BOUNDS = "Sensor position must be inside the greenhouse grid."

        fun initialState(greenhouse: Greenhouse = Greenhouse()): SensorPlacementUiState {
            val config = GreenhousePhysicalConfig.default()
            val byType = CoverageCalculator.calculateByType(greenhouse, emptyList())
            return SensorPlacementUiState(
                greenhouse = greenhouse,
                physicalConfig = config,
                greenhouseConfigured = false,
                coverageByType = byType,
                coverage = byType.monitoring,
                step = SensorWorkflowStep.SETUP
            )
        }
    }
}

class SensorPlacementViewModelFactory(
    private val greenhouse: Greenhouse = Greenhouse()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SensorPlacementViewModel(greenhouse) as T
    }
}
