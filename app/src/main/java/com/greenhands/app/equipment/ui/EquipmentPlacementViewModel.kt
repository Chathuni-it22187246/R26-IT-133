package com.greenhands.app.equipment.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.LiveSensorUiState
import com.greenhands.app.ar.domain.EquipmentPresentationContext
import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.EquipmentRuntimeInput
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.ar.domain.LiveEnvironmentSource
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.equipment.data.EquipmentLayoutRepository
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.EquipmentRecommendationEngine
import com.greenhands.app.equipment.domain.InstanceNumbering
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.equipment.future.EnvironmentDataSourceKind
import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.ManufacturerEquipmentRatings
import com.greenhands.app.equipment.model.SavedManufacturerSpecFactory
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRecommendation
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.RecommendationInputs
import com.greenhands.app.equipment.model.RecommendationStatus
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.model.formatMetres2
import com.greenhands.app.heat.domain.TargetParser
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN
import com.greenhands.app.heat.model.TEMP_HARD_MAX
import com.greenhands.app.heat.model.TEMP_HARD_MIN
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.data.normalizedUserId
import com.greenhands.app.heat.runtime.DemoPhysicsScheduler
import com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.PhysicsRuntimeRequest
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.adapter.AdaptiveGridPolicy
import com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile
import com.greenhands.app.heat.runtime.adapter.DemoOutsideBoundary
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.heat.runtime.contracts.InputValidity
import com.greenhands.app.heat.runtime.contracts.ResolvedFanPadBoundary
import com.greenhands.app.heat.visualization.OfficialValidationTransect
import com.greenhands.app.heat.visualization.PadInletEffectFrame
import com.greenhands.app.heat.visualization.PadInletEffectPlanner
import com.greenhands.app.heat.visualization.EnvironmentalFieldBuilder
import com.greenhands.app.heat.visualization.EnvironmentalFieldFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldHeight
import com.greenhands.app.heat.visualization.EnvironmentalFieldLayer
import com.greenhands.app.heat.visualization.EnvironmentalFieldLegendModel
import com.greenhands.app.heat.visualization.EnvironmentalFieldPlanner
import com.greenhands.app.heat.visualization.EnvironmentalFieldSnapshot
import com.greenhands.app.heat.visualization.EnvironmentalFieldThresholds
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.FreshnessEvaluator
import com.greenhands.app.heat.runtime.contracts.FreshnessStatus
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.RuntimeClock
import com.greenhands.app.heat.visualization.CirculationEffectFrame
import com.greenhands.app.heat.visualization.CirculationEffectPlanner
import com.greenhands.app.heat.visualization.CirculationProbeLabelPlanner
import com.greenhands.app.heat.visualization.CirculationProbeMarker
import com.greenhands.app.heat.visualization.DemoDisplayPublication
import com.greenhands.app.heat.visualization.ExhaustEffectFrame
import com.greenhands.app.heat.visualization.ExhaustEffectPlanner
import com.greenhands.app.heat.visualization.ExhaustProbeLabelPlanner
import com.greenhands.app.heat.visualization.FoggerDetailsModel
import com.greenhands.app.heat.visualization.FoggerEffectFrame
import com.greenhands.app.heat.visualization.FoggerEffectPlanner
import com.greenhands.app.heat.advisory.TooColdAdvisory
import com.greenhands.app.heat.advisory.TooColdAdvisoryEvaluator
import com.greenhands.app.heat.runtime.contracts.SystemRuntimeClock
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundarySource
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.telemetry.GreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseWeatherState
import com.greenhands.app.telemetry.NoOpGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.boundary
import com.greenhands.app.telemetry.toLiveReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OverlayClimate(
    val crop: Crop?,
    val stage: GrowthStage?,
    val temperatureLabel: String,
    val humidityLabel: String,
    val vpdLabel: String,
    val circulation: CirculationThresholds?,
    val exhaust: ExhaustThresholds?,
    val fogger: FoggerThresholds?,
    val optimalTemperatureC: Double? = null,
    val optimalHumidityPercent: Double? = null
) {
    fun compactOptimalLabel(): String {
        val t = optimalTemperatureC
        val h = optimalHumidityPercent
        return when {
            t != null && h != null ->
                "Optimal: ${formatOneDecimal(t)}°C · ${ArHudContent.humidityReadingLabel(h)}"
            t != null -> "Optimal temperature: ${formatOneDecimal(t)}°C"
            h != null -> "Optimal humidity: ${ArHudContent.humidityReadingLabel(h)}"
            else -> "Not available"
        }
    }

    fun runtimeThresholds(): EquipmentRuntimeThresholds =
        EquipmentRuntimeThresholds(circulation = circulation, exhaust = exhaust, fogger = fogger)
}

data class PlacementUiState(
    val mode: PreviewInteractionMode = PreviewInteractionMode.VIEW,
    val instances: List<EquipmentInstance> = emptyList(),
    val ghost: EquipmentInstance? = null,
    val selectedId: String? = null,
    val draggingId: String? = null,
    val xInput: String = "",
    val yInput: String = "",
    val zInput: String = "",
    val yawInput: String = "",
    val editError: String? = null,
    val statusMessage: String? = null,
    val showClearConfirm: Boolean = false,
    val showSuggestionPanel: Boolean = false,
    val recommendation: EquipmentRecommendation? = null,
    val circulationRatedCfmInput: String = "",
    val circulationRatedM3sInput: String = "",
    val circulationThrowRadiusMInput: String = "",
    val exhaustRatedM3sInput: String = "",
    val exhaustRatedCfmInput: String = "",
    val foggerFlowKgSInput: String = "",
    val foggerFlowLphInput: String = "",
    val foggerSprayRadiusMInput: String = "",
    val foggerEtaInput: String = "",
    val showManufacturerSpecsSheet: Boolean = false,
    val heatLoadWInput: String = "",
    val outdoorTempInput: String = "",
    val outdoorRhInput: String = "",
    val overrideCircInput: String = "",
    val overrideExhInput: String = "",
    val overrideFogInput: String = "",
    val saved: Boolean = false,
    val environmentKind: EnvironmentDataSourceKind = EnvironmentDataSourceKind.PREVIEW,
    val equipmentCardExpanded: Boolean = false,
    val paletteDraggingType: EquipmentType? = null,
    val orbitAllowed: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val pendingDeleteLabel: String = "",
    val pendingDeleteId: String? = null,
    val pendingDeleteConfirm: String = "",
    val labelsVisible: Boolean = true,
    val airflowFogVisible: Boolean = true,
    val showUndo: Boolean = false,
    val undoLabel: String = "",
    val galleryType: EquipmentType? = null,
    val showWhyPosition: Boolean = false,
    val showThresholdHelp: Boolean = false,
    val selectedTool: SelectedEquipmentTool = SelectedEquipmentTool.MOVE,
    val showExactEditor: Boolean = false,
    val layoutLoadedMessage: String? = null,
    val previewMode: PreviewMode = PreviewMode.MY_GREENHOUSE,
    val cameraResetEpoch: Int = 0,
    val showReplaceLayoutConfirm: Boolean = false,
    val pendingReplaceType: EquipmentType? = null,
    val behaviorSheetOpen: Boolean = false,
    val behaviorRunning: Boolean = false,
    val runtimeById: Map<String, EquipmentRuntimeState> = emptyMap(),
    val simulatedTemperatureC: Double? = null,
    val simulatedHumidityPercent: Double? = null,
    val simulatedTemperatureInput: String = "",
    val simulatedHumidityInput: String = "",
    val behaviorError: String? = null,
    val liveEnvironment: ArLiveEnvironmentReading = ArLiveEnvironmentReading.DISCONNECTED,
    val tooColdAdvisory: TooColdAdvisory? = null,
    val physicsSnapshot: PhysicsFieldSnapshot? = null,
    val displayedTemperatureC: Double? = null,
    val displayedHumidityPercent: Double? = null,
    val displayedFieldSnapshot: PhysicsFieldSnapshot? = null,
    val actualRelayById: Map<String, EquipmentRuntimeState> = emptyMap(),
    val weatherBoundary: WeatherBoundary = WeatherBoundary.missing(),
    val weatherUi: GreenhouseWeatherState = GreenhouseWeatherState.Connecting,
    val demoFanPadEnabled: Boolean = true,
    val demoFanPadWetActive: Boolean = true,
    val demoFanPadEfficiency: Double = com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile.ILLUSTRATIVE_EFFICIENCY,
    val demoFanPadEfficiencyInput: String = "0.80",
    val demoOutsideTemperatureC: Double = com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile.DEFAULT_OUTSIDE_TEMPERATURE_C,
    val demoOutsideHumidityPercent: Double = com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile.DEFAULT_OUTSIDE_RH_PERCENT,
    val demoOutsideTemperatureInput: String = "35.0",
    val demoOutsideHumidityInput: String = "40.0",
    val myPadPresent: Boolean? = null,
    val myPadWetActive: Boolean = false,
    val myPadWidthInput: String = "",
    val myPadHeightInput: String = "",
    val myPadEfficiencyInput: String = "",
    val myPadWall: com.greenhands.app.heat.runtime.contracts.FanPadWallSelection =
        com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.AUTO,
    val environmentalLayer: EnvironmentalFieldLayer = EnvironmentalFieldLayer.TEMPERATURE,
    val fieldHeight: EnvironmentalFieldHeight = EnvironmentalFieldHeight.WHOLE,
    val environmentalFieldGeneration: Int = 0,
    val fieldLegendExpanded: Boolean = false
) {
    val selected: EquipmentInstance?
        get() = ghost?.takeIf { it.id == selectedId }
            ?: instances.firstOrNull { it.id == selectedId }

    val placedEquipment: List<EquipmentInstance>
        get() = instances

    val suggestedPlacements: List<EquipmentInstance>
        get() = emptyList()

    val displayInstances: List<EquipmentInstance>
        get() = instances + listOfNotNull(ghost)

    val isDemo: Boolean
        get() = previewMode == PreviewMode.DEMO

    val showsPersistentSave: Boolean
        get() = MyGreenhousePersistence.allowsPersistentSave(previewMode)

    val showsAutoPlaceSuggestedLayout: Boolean
        get() = SuggestionEvidence.showsAutoPlaceSuggestedLayout(isDemo)

    fun showsApplyLayout(type: EquipmentType): Boolean =
        SuggestionEvidence.showsApplyLayout(isDemo, SuggestionEvidence.countFor(type, recommendation))

    fun showsArApplyLayout(type: EquipmentType): Boolean =
        SuggestionEvidence.showsArApplyLayout(isDemo, SuggestionEvidence.countFor(type, recommendation))

    fun equipmentCount(type: EquipmentType): Int =
        instances.count { it.type == type && !it.isGhost }

    fun placedTypes(): Set<EquipmentType> =
        instances.filter { !it.isGhost }.map { it.type }.toSet()

    fun manufacturerRatings(): ManufacturerEquipmentRatings = ManufacturerEquipmentRatings(
        circulationAirflowCfm = circulationRatedCfmInput.toDoubleOrNull(),
        circulationAirflowM3s = circulationRatedM3sInput.toDoubleOrNull(),
        circulationThrowRadiusM = circulationThrowRadiusMInput.toDoubleOrNull(),
        exhaustAirflowM3s = exhaustRatedM3sInput.toDoubleOrNull(),
        exhaustAirflowCfm = exhaustRatedCfmInput.toDoubleOrNull(),
        foggerWaterFlowKgS = foggerFlowKgSInput.toDoubleOrNull(),
        foggerWaterFlowLph = foggerFlowLphInput.toDoubleOrNull(),
        foggerSprayRadiusM = foggerSprayRadiusMInput.toDoubleOrNull(),
        foggerEvaporationEfficiency = foggerEtaInput.toDoubleOrNull()
    )

    fun missingManufacturerSpecs(): List<String> = manufacturerRatings().missingLabels(placedTypes())

    fun quantitativeVisualizationComplete(): Boolean =
        manufacturerRatings().quantitativeEffectsReady(placedTypes())
}

class EquipmentPlacementViewModel(
    private val repository: EquipmentLayoutRepository,
    private val physicsScheduler: DemoPhysicsScheduler = DemoPhysicsScheduler(),
    private val displayScheduler: DemoPhysicsScheduler = DemoPhysicsScheduler(
        DemoPhysicsSchedulerConfig(
            publicationIntervalS = DemoDisplayPublication.INTERVAL_S,
            pauseResetThresholdS = DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S
        )
    ),
    private val clock: RuntimeClock = SystemRuntimeClock,
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy(),
    private val telemetryRepository: GreenhouseTelemetryRepository = NoOpGreenhouseTelemetryRepository,
    private val manufacturerSpecsFor: ((List<EquipmentInstance>) -> Map<String, EquipmentPhysicsSpecification>)? =
        null,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val _state = MutableStateFlow(PlacementUiState())
    val state: StateFlow<PlacementUiState> = _state.asStateFlow()

    private var userId: String = ""
    private var configurationId: String = ""
    private var layoutId: String = idFactory()
    private var context: PlacementContext = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
    private var overlay: OverlayClimate = OverlayClimate(null, null, "—", "—", "Not published", null, null, null)
    private var loadedForConfig: String? = null
    private var dragOrigin: EquipmentInstance? = null
    private var dragLockFar: Boolean = true
    private var paletteDragCreatedGhost: Boolean = false
    private var undoDeleted: EquipmentInstance? = null
    private var undoIndex: Int = -1
    private var undoSnapshot: List<EquipmentInstance>? = null
    private var lastSavedOrigin: LayoutOrigin = LayoutOrigin.MANUAL
    private var previewMode: PreviewMode = PreviewMode.MY_GREENHOUSE
    private var physicsRuntime: PhysicsFieldRuntime? = null
    private var publishingFromPhysics: Boolean = false
    private var boundScientificConfig: ScientificRuntimeConfiguration? = null
    private var liveEnvironmentSource: LiveEnvironmentSource? = null
    private var weatherBoundarySource: WeatherBoundarySource? = null
    private var cachedPhysicsForField: PhysicsFieldSnapshot? = null
    private var cachedEnvironmentalField: EnvironmentalFieldSnapshot? = null
    private var telemetryHeld: Boolean = false
    private var telemetryJob: Job? = null
    private var weatherJob: Job? = null
    private var telemetryStopIssued: Boolean = false
    private var pendingReopenBehaviorAfterSpecs: Boolean = false

    fun attach(
        userId: String,
        configurationId: String,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cropRowCount: Int,
        overlay: OverlayClimate,
        previewMode: PreviewMode = PreviewMode.MY_GREENHOUSE
    ) {
        val incoming = ScientificRuntimeConfiguration.from(
            configurationId = configurationId,
            previewMode = previewMode,
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cropRowCount = cropRowCount,
            overlay = overlay
        )
        val previous = boundScientificConfig
        val scientificChanged = previous != null && previous != incoming
        val wasRunning = _state.value.behaviorRunning
        val previousOverlay = this.overlay
        this.userId = userId.normalizedUserId().orEmpty()
        this.overlay = overlay
        this.previewMode = previewMode
        val canopy = heightM * CropRowGeometry.HEIGHT_FILL
        context = PlacementContext(lengthM, widthM, heightM, cropRowCount, canopy)
        _state.update {
            it.copy(
                previewMode = previewMode,
                environmentKind = if (previewMode == PreviewMode.DEMO) {
                    EnvironmentDataSourceKind.PREVIEW
                } else {
                    it.environmentKind
                },
                environmentalLayer = if (previewMode == PreviewMode.DEMO &&
                    (previous == null || previous.previewMode != PreviewMode.DEMO)
                ) {
                    EnvironmentalFieldLayer.TEMPERATURE
                } else {
                    it.environmentalLayer
                }
            )
        }
        syncLiveTelemetryMonitoring()
        if (scientificChanged && wasRunning) {
            stopBehavior(ArRuntimeCopy.CONFIGURATION_CHANGED)
        } else {
            clearStaleConfigurationChangedReason()
        }
        boundScientificConfig = incoming
        val overlayChanged = previousOverlay != overlay
        if (previewMode == PreviewMode.DEMO) {
            applySimulatedDefaults(
                ArHudContent.resolveOverlay(overlay, previewMode),
                overwrite = overlayChanged
            )
        }
        if (previewMode == PreviewMode.DEMO) {
            if (configurationId.isNotBlank() && configurationId != loadedForConfig) {
                this.configurationId = configurationId
                loadedForConfig = configurationId
            }
            refreshRuntime()
            refreshTooColdAdvisory()
            return
        }
        if (configurationId.isNotBlank() && configurationId != loadedForConfig) {
            this.configurationId = configurationId
            viewModelScope.launch {
                val stored = repository.load(this@EquipmentPlacementViewModel.userId, configurationId)
                if (stored != null && stored.shouldRestoreAsPlaced()) {
                    layoutId = stored.layoutId
                    lastSavedOrigin = stored.resolvedOrigin()
                    _state.update { current ->
                        val keepInProgress = current.instances.isNotEmpty() && stored.instances.isEmpty()
                        val restored = if (keepInProgress) {
                            current.instances
                        } else {
                            InstanceNumbering.assignMissing(stored.instances).map { inst -> revalidate(inst) }
                        }
                        current.countInputsFrom(stored).copy(
                            instances = restored,
                            ghost = if (keepInProgress) current.ghost else null,
                            selectedId = if (keepInProgress) current.selectedId else null,
                            saved = !keepInProgress,
                            layoutLoadedMessage = if (keepInProgress) current.layoutLoadedMessage else "Saved equipment layout loaded",
                            statusMessage = if (keepInProgress) current.statusMessage else "Saved equipment layout loaded"
                        )
                    }
                } else {
                    layoutId = idFactory()
                    lastSavedOrigin = LayoutOrigin.MANUAL
                    _state.update { current ->
                        if (current.instances.isNotEmpty() || current.ghost != null) {
                            current
                        } else {
                            current.copy(
                                instances = emptyList(),
                                ghost = null,
                                selectedId = null,
                                layoutLoadedMessage = null,
                                statusMessage = null
                            )
                        }
                    }
                }
                loadedForConfig = configurationId
                refreshRuntime()
                refreshTooColdAdvisory()
            }
        } else {
            refreshRuntime()
            refreshTooColdAdvisory()
        }
    }

    private fun PlacementUiState.countInputsFrom(stored: EquipmentLayout): PlacementUiState = copy(
        overrideCircInput = stored.countOverrideCirculation?.toString().orEmpty(),
        overrideExhInput = stored.countOverrideExhaust?.toString().orEmpty(),
        overrideFogInput = stored.countOverrideFogger?.toString().orEmpty(),
        circulationRatedCfmInput = stored.circulationRatedCfm?.toString().orEmpty(),
        circulationRatedM3sInput = stored.circulationRatedM3s?.toString().orEmpty(),
        circulationThrowRadiusMInput = stored.circulationThrowRadiusM?.toString().orEmpty(),
        exhaustRatedM3sInput = stored.exhaustRatedAirflowM3s?.toString().orEmpty(),
        exhaustRatedCfmInput = stored.exhaustRatedAirflowCfm?.toString().orEmpty(),
        foggerFlowKgSInput = stored.foggerWaterFlowKgS?.toString().orEmpty(),
        foggerFlowLphInput = stored.foggerWaterFlowLph?.toString().orEmpty(),
        foggerSprayRadiusMInput = stored.foggerSprayRadiusM?.toString().orEmpty(),
        foggerEtaInput = stored.foggerEvaporationEfficiency?.toString().orEmpty(),
        myPadPresent = stored.coolingPadPresent,
        myPadWetActive = stored.coolingPadWetActive ?: false,
        myPadWidthInput = stored.coolingPadWidthM?.toString().orEmpty(),
        myPadHeightInput = stored.coolingPadHeightM?.toString().orEmpty(),
        myPadEfficiencyInput = stored.coolingPadEfficiencyFraction?.toString().orEmpty(),
        myPadWall = when (stored.coolingPadWall) {
            "NEAR_Y0" -> com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.NEAR_Y0
            "FAR_Y_LENGTH" -> com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.FAR_Y_LENGTH
            "NEAR_X0" -> com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.NEAR_X0
            "FAR_X_WIDTH" -> com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.FAR_X_WIDTH
            else -> com.greenhands.app.heat.runtime.contracts.FanPadWallSelection.AUTO
        }
    )

    fun overlay(): OverlayClimate = overlay
    fun placementContext(): PlacementContext = context

    fun enterPlacementMode() {
        _state.update {
            it.copy(
                mode = PreviewInteractionMode.PLACEMENT,
                equipmentCardExpanded = false,
                orbitAllowed = false,
                selectedTool = SelectedEquipmentTool.MOVE,
                showExactEditor = false,
                statusMessage = "Place Equipment. Drag a fan or fogger from the palette into the greenhouse, or tap to add."
            )
        }
    }

    fun enterViewMode() {
        cancelGhost()
        dragOrigin = null
        _state.update {
            it.copy(
                mode = PreviewInteractionMode.VIEW,
                selectedId = null,
                draggingId = null,
                paletteDraggingType = null,
                orbitAllowed = false,
                statusMessage = null
            )
        }
    }

    fun setOrbitAllowed(allowed: Boolean) {
        _state.update { it.copy(orbitAllowed = allowed) }
    }

    fun setLabelsVisible(visible: Boolean) {
        _state.update { it.copy(labelsVisible = visible) }
    }

    fun toggleLabelsVisible() {
        _state.update { it.copy(labelsVisible = !it.labelsVisible) }
    }

    fun setAirflowFogVisible(visible: Boolean) {
        _state.update { it.copy(airflowFogVisible = visible) }
    }

    fun toggleAirflowFogVisible() {
        _state.update { it.copy(airflowFogVisible = !it.airflowFogVisible) }
    }

    fun setEnvironmentalLayer(layer: EnvironmentalFieldLayer) {
        _state.update { it.copy(environmentalLayer = layer) }
    }

    fun setFieldHeight(height: EnvironmentalFieldHeight) {
        _state.update { it.copy(fieldHeight = height) }
    }

    fun toggleFieldLegendExpanded() {
        _state.update { it.copy(fieldLegendExpanded = !it.fieldLegendExpanded) }
    }

    fun setFieldLegendExpanded(expanded: Boolean) {
        _state.update { it.copy(fieldLegendExpanded = expanded) }
    }

    fun tapPalette(type: EquipmentType) {
        if (_state.value.mode != PreviewInteractionMode.PLACEMENT) enterPlacementMode()
        val (pos, yaw) = PlacementSuggester.defaultGhostPosition(type, context)
        val number = InstanceNumbering.nextNumber(type, _state.value.instances)
        val candidate = revalidate(
            EquipmentAssetCatalog.newInstance(
                type = type,
                id = idFactory(),
                positionM = pos,
                yawDeg = yaw,
                isGhost = false,
                displayNumber = number
            )
        )
        if (candidate.validity.isValid) {
            _state.update {
                it.copy(
                    instances = it.instances + candidate,
                    ghost = null,
                    selectedId = candidate.id,
                    saved = false,
                    showExactEditor = false,
                    selectedTool = SelectedEquipmentTool.MOVE,
                    statusMessage = "Placed ${candidate.displayLabel()}."
                ).withEditor(candidate)
            }
            refreshRuntime()
            return
        }
        val ghost = candidate.copy(id = "ghost_${candidate.id}", isGhost = true)
        _state.update {
            it.copy(
                ghost = ghost,
                selectedId = ghost.id,
                showExactEditor = false,
                statusMessage = ghost.validity.reasons.firstOrNull()
            )
        }
    }

    fun beginPaletteDrag(type: EquipmentType) {
        if (_state.value.mode != PreviewInteractionMode.PLACEMENT) enterPlacementMode()
        paletteDragCreatedGhost = false
        _state.update { it.copy(paletteDraggingType = type) }
    }

    fun paletteDragTo(type: EquipmentType, hit: DomainPoint) {
        if (_state.value.mode != PreviewInteractionMode.PLACEMENT) return
        val currentGhost = _state.value.ghost?.takeIf { it.isGhost && it.type == type }
        if (currentGhost == null) {
            val (pos, yaw) = PlacementSuggester.defaultGhostPosition(type, context)
            val lockFar = MovementPlanes.farWallFromY(hit.yM, context.lengthM)
            val moved = MovementPlanes.applyHit(type, pos, hit, context, lockFar)
            val number = InstanceNumbering.nextNumber(type, _state.value.instances)
            val ghost = revalidate(
                EquipmentAssetCatalog.newInstance(
                    type = type,
                    id = "ghost_${idFactory()}",
                    positionM = moved,
                    yawDeg = yaw,
                    isGhost = true,
                    displayNumber = number
                )
            )
            paletteDragCreatedGhost = true
            dragLockFar = lockFar
            _state.update {
                it.copy(
                    ghost = ghost,
                    selectedId = ghost.id,
                    paletteDraggingType = type,
                    draggingId = ghost.id,
                    showExactEditor = false
                )
            }
            return
        }
        dragTo(currentGhost.id, hit)
    }

    fun dropPaletteAt(position: DomainPoint) {
        val type = _state.value.paletteDraggingType ?: return
        paletteDragTo(type, position)
        endPaletteDrag(overViewport = true)
    }

    fun endPaletteDrag(overViewport: Boolean) {
        val ghost = _state.value.ghost
        _state.update { it.copy(paletteDraggingType = null, draggingId = null) }
        if (!overViewport) {
            if (paletteDragCreatedGhost) cancelGhost()
            paletteDragCreatedGhost = false
            return
        }
        paletteDragCreatedGhost = false
        if (ghost != null && ghost.validity.isValid) {
            confirmGhost()
        } else if (ghost != null) {
            cancelGhost()
            _state.update { it.copy(statusMessage = ghost.validity.reasons.firstOrNull() ?: "Release on a valid position to place equipment.") }
        }
    }

    fun cancelPaletteDrag() {
        endPaletteDrag(overViewport = false)
    }

    fun confirmGhost() {
        val ghost = _state.value.ghost ?: return
        if (!ghost.validity.isValid) {
            _state.update { it.copy(statusMessage = ghost.validity.reasons.firstOrNull()) }
            return
        }
        val placed = ghost.copy(id = idFactory(), isGhost = false)
        _state.update {
            it.copy(
                instances = it.instances + placed,
                ghost = null,
                selectedId = placed.id,
                saved = false,
                showExactEditor = false,
                selectedTool = SelectedEquipmentTool.MOVE,
                statusMessage = "Placed ${placed.displayLabel()}."
            ).withEditor(placed)
        }
        refreshRuntime()
    }

    fun cancelGhost() {
        _state.update { current ->
            val keep = current.selectedId?.takeIf { id -> current.instances.any { inst -> inst.id == id } }
            current.copy(ghost = null, selectedId = keep)
        }
    }

    fun select(id: String?) {
        val target = _state.value.displayInstances.firstOrNull { it.id == id }
        _state.update {
            it.copy(
                selectedId = id,
                showExactEditor = if (id == null) false else it.showExactEditor,
                selectedTool = if (id == null) SelectedEquipmentTool.MOVE else it.selectedTool,
                showDeleteConfirm = false,
                pendingDeleteId = null,
                pendingDeleteLabel = "",
                pendingDeleteConfirm = ""
            ).let { st -> if (target != null) st.withEditor(target) else st }
        }
    }

    fun resetView() {
        _state.update { it.copy(cameraResetEpoch = it.cameraResetEpoch + 1) }
    }

    fun startDrag(id: String) {
        if (_state.value.mode != PreviewInteractionMode.PLACEMENT) return
        if (_state.value.orbitAllowed) return
        if (_state.value.selectedTool != SelectedEquipmentTool.MOVE) return
        val target = _state.value.displayInstances.firstOrNull { it.id == id } ?: return
        dragOrigin = target
        dragLockFar = MovementPlanes.farWallFromY(target.positionM.yM, context.lengthM)
        _state.update {
            it.copy(
                draggingId = id,
                selectedId = id,
                showExactEditor = false,
                showDeleteConfirm = false,
                pendingDeleteId = null,
                pendingDeleteLabel = "",
                pendingDeleteConfirm = ""
            ).withEditor(target)
        }
    }

    fun dragTo(id: String, hit: DomainPoint) {
        if (_state.value.mode != PreviewInteractionMode.PLACEMENT) return
        val current = _state.value.displayInstances.firstOrNull { it.id == id } ?: return
        val next = MovementPlanes.applyHit(current.type, current.positionM, hit, context, dragLockFar)
        updatePose(id, next, null)
    }

    fun endDrag() {
        val id = _state.value.draggingId
        val origin = dragOrigin
        dragOrigin = null
        val current = id?.let { target -> _state.value.displayInstances.firstOrNull { it.id == target } }
        if (current != null && !current.isGhost && origin != null && origin.id == current.id && !current.validity.isValid) {
            restoreInstance(origin)
        }
        _state.update { it.copy(draggingId = null) }
    }

    fun setElevation(zM: Double) {
        setAxisZ(zM)
    }

    fun setAxisX(xM: Double) {
        val selected = _state.value.selected ?: return
        if (!xM.isFinite()) return
        updatePose(selected.id, selected.positionM.copy(xM = xM), null)
    }

    fun setAxisY(yM: Double) {
        val selected = _state.value.selected ?: return
        if (!yM.isFinite()) return
        if (selected.type == EquipmentType.EXHAUST_FAN) return
        updatePose(selected.id, selected.positionM.copy(yM = yM), null)
    }

    fun setAxisZ(zM: Double) {
        val selected = _state.value.selected ?: return
        if (!zM.isFinite()) return
        updatePose(selected.id, selected.positionM.copy(zM = zM), null)
    }

    fun rotateSelected(deltaDeg: Double = 90.0) {
        val selected = _state.value.selected ?: return
        updatePose(selected.id, selected.positionM, selected.yawDeg + deltaDeg)
    }

    fun rotateTo(yawDeg: Double) {
        val selected = _state.value.selected ?: return
        if (!yawDeg.isFinite()) return
        updatePose(selected.id, selected.positionM, yawDeg)
    }

    fun applyNumericEdit(xRaw: String, yRaw: String, zRaw: String, yawRaw: String) {
        val selected = _state.value.selected ?: return
        val x = xRaw.replace(',', '.').toDoubleOrNull()
        val y = yRaw.replace(',', '.').toDoubleOrNull()
        val z = zRaw.replace(',', '.').toDoubleOrNull()
        val yaw = yawRaw.replace(',', '.').toDoubleOrNull()
        if (x == null || y == null || z == null || yaw == null ||
            !x.isFinite() || !y.isFinite() || !z.isFinite() || !yaw.isFinite()
        ) {
            _state.update { it.copy(editError = "Enter finite numbers in metres and degrees.", xInput = xRaw, yInput = yRaw, zInput = zRaw, yawInput = yawRaw) }
            return
        }
        updatePose(selected.id, DomainPoint(x, y, z), yaw)
    }

    fun duplicateSelected() {
        val selected = _state.value.selected ?: return
        if (selected.isGhost) return
        val offset = selected.copy(
            id = idFactory(),
            displayNumber = InstanceNumbering.nextNumber(selected.type, _state.value.instances),
            positionM = selected.positionM.copy(
                xM = selected.positionM.xM + 0.45,
                yM = selected.positionM.yM + 0.45
            ),
            isGhost = false
        )
        val placed = revalidateAgainst(offset, _state.value.instances + listOfNotNull(_state.value.ghost))
        _state.update {
            it.copy(
                instances = it.instances + placed,
                selectedId = placed.id,
                saved = false,
                statusMessage = if (placed.validity.isValid) "Duplicated." else placed.validity.reasons.firstOrNull()
            ).withEditor(placed)
        }
        refreshRuntime()
    }

    fun requestDelete() {
        val selected = _state.value.selected ?: return
        if (selected.isGhost) {
            cancelGhost()
            return
        }
        _state.update {
            it.copy(
                showDeleteConfirm = true,
                pendingDeleteId = selected.id,
                pendingDeleteLabel = selected.displayLabel(),
                pendingDeleteConfirm = EquipmentLabels.deleteAction(selected.type, selected.displayNumber)
            )
        }
    }

    fun cancelDelete() {
        _state.update {
            it.copy(
                showDeleteConfirm = false,
                pendingDeleteId = null,
                pendingDeleteLabel = "",
                pendingDeleteConfirm = ""
            )
        }
    }

    fun deleteSelected() {
        if (!_state.value.showDeleteConfirm) return
        val targetId = _state.value.pendingDeleteId ?: return
        deleteById(targetId)
    }

    fun deleteById(id: String) {
        if (id.isBlank()) return
        val ghost = _state.value.ghost
        if (ghost != null && ghost.id == id) {
            cancelGhost()
            return
        }
        val current = _state.value.instances
        val index = current.indexOfFirst { inst -> inst.id == id }
        if (index < 0) return
        val removed = current[index]
        undoDeleted = removed
        undoIndex = index
        undoSnapshot = null
        val remaining = current.filter { inst -> inst.id != id }
        val selectedGone = _state.value.selectedId == id
        _state.update {
            it.copy(
                instances = remaining,
                selectedId = if (selectedGone) null else it.selectedId,
                saved = false,
                showDeleteConfirm = false,
                pendingDeleteId = null,
                pendingDeleteLabel = "",
                pendingDeleteConfirm = "",
                showUndo = false,
                undoLabel = "",
                statusMessage = null
            )
        }
        refreshRuntime()
    }

    fun undoDelete() {
        val snapshot = undoSnapshot
        if (snapshot != null) {
            undoSnapshot = null
            undoDeleted = null
            _state.update {
                it.copy(
                    instances = snapshot,
                    showUndo = false,
                    undoLabel = "",
                    statusMessage = "Restored previous layout."
                )
            }
            refreshRuntime()
            return
        }
        val restored = undoDeleted ?: return
        val insertAt = undoIndex.coerceIn(0, _state.value.instances.size)
        undoDeleted = null
        undoIndex = -1
        _state.update { st ->
            val next = st.instances.toMutableList()
            if (next.none { it.id == restored.id }) {
                next.add(insertAt.coerceIn(0, next.size), restored)
            }
            st.copy(
                instances = next,
                selectedId = restored.id,
                saved = false,
                showUndo = false,
                undoLabel = "",
                statusMessage = "Restored ${restored.displayLabel()}."
            ).withEditor(restored)
        }
        refreshRuntime()
    }

    fun dismissUndo() {
        undoDeleted = null
        undoIndex = -1
        undoSnapshot = null
        _state.update { it.copy(showUndo = false, undoLabel = "") }
    }

    fun requestClear() {
        _state.update { it.copy(showClearConfirm = true) }
    }

    fun cancelClear() {
        _state.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClear() {
        undoDeleted = null
        undoIndex = -1
        undoSnapshot = null
        _state.update {
            it.copy(
                instances = emptyList(),
                ghost = null,
                selectedId = null,
                showClearConfirm = false,
                saved = false,
                showUndo = false,
                undoLabel = "",
                galleryType = null,
                showReplaceLayoutConfirm = false,
                pendingReplaceType = null,
                statusMessage = "Layout cleared."
            )
        }
        refreshRuntime()
    }

    fun openSuggestionGallery(type: EquipmentType) {
        refreshRecommendation()
        _state.update {
            it.copy(
                galleryType = type,
                showWhyPosition = false,
                showSuggestionPanel = false,
                showReplaceLayoutConfirm = false,
                pendingReplaceType = null
            )
        }
    }

    fun closeSuggestionGallery() {
        _state.update {
            it.copy(
                galleryType = null,
                showWhyPosition = false
            )
        }
    }

    fun openWhyPosition() {
        refreshRecommendation()
        _state.update { it.copy(showWhyPosition = true) }
    }

    fun closeWhyPosition() {
        _state.update { it.copy(showWhyPosition = false) }
    }

    fun toggleThresholdHelp() {
        _state.update { it.copy(showThresholdHelp = !it.showThresholdHelp) }
    }

    fun setSelectedTool(tool: SelectedEquipmentTool) {
        val resolved = if (tool == SelectedEquipmentTool.HEIGHT) SelectedEquipmentTool.Z else tool
        _state.update {
            it.copy(
                selectedTool = resolved,
                showExactEditor = resolved == SelectedEquipmentTool.EXACT,
                orbitAllowed = false
            )
        }
    }

    fun setShowExactEditor(show: Boolean) {
        _state.update {
            it.copy(
                showExactEditor = show,
                selectedTool = if (show) SelectedEquipmentTool.EXACT else SelectedEquipmentTool.MOVE
            )
        }
    }

    fun toggleSuggestionPanel() {
        val type = _state.value.galleryType ?: EquipmentType.CIRCULATION_FAN
        if (_state.value.galleryType == null) openSuggestionGallery(type) else closeSuggestionGallery()
    }

    fun updateRecommendationInput(
        circCfm: String? = null,
        exhaustM3s: String? = null,
        fogFlow: String? = null,
        fogEta: String? = null,
        heatLoad: String? = null,
        outdoorT: String? = null,
        outdoorRh: String? = null,
        ovC: String? = null,
        ovE: String? = null,
        ovF: String? = null
    ) {
        _state.update {
            it.copy(
                circulationRatedCfmInput = circCfm ?: it.circulationRatedCfmInput,
                exhaustRatedM3sInput = exhaustM3s ?: it.exhaustRatedM3sInput,
                foggerFlowKgSInput = fogFlow ?: it.foggerFlowKgSInput,
                foggerEtaInput = fogEta ?: it.foggerEtaInput,
                heatLoadWInput = heatLoad ?: it.heatLoadWInput,
                outdoorTempInput = outdoorT ?: it.outdoorTempInput,
                outdoorRhInput = outdoorRh ?: it.outdoorRhInput,
                overrideCircInput = ovC ?: it.overrideCircInput,
                overrideExhInput = ovE ?: it.overrideExhInput,
                overrideFogInput = ovF ?: it.overrideFogInput
            )
        }
        refreshRecommendation()
        rebuildPhysicsAfterSpecChange()
    }

    fun updateManufacturerSpecInput(
        circCfm: String? = null,
        circM3s: String? = null,
        circThrowM: String? = null,
        exhaustM3s: String? = null,
        exhaustCfm: String? = null,
        fogFlowKgS: String? = null,
        fogFlowLph: String? = null,
        fogSprayM: String? = null,
        fogEta: String? = null
    ) {
        _state.update {
            it.copy(
                circulationRatedCfmInput = circCfm ?: it.circulationRatedCfmInput,
                circulationRatedM3sInput = circM3s ?: it.circulationRatedM3sInput,
                circulationThrowRadiusMInput = circThrowM ?: it.circulationThrowRadiusMInput,
                exhaustRatedM3sInput = exhaustM3s ?: it.exhaustRatedM3sInput,
                exhaustRatedCfmInput = exhaustCfm ?: it.exhaustRatedCfmInput,
                foggerFlowKgSInput = fogFlowKgS ?: it.foggerFlowKgSInput,
                foggerFlowLphInput = fogFlowLph ?: it.foggerFlowLphInput,
                foggerSprayRadiusMInput = fogSprayM ?: it.foggerSprayRadiusMInput,
                foggerEtaInput = fogEta ?: it.foggerEtaInput,
                saved = false
            )
        }
        refreshRecommendation()
        rebuildPhysicsAfterSpecChange()
    }

    fun openManufacturerSpecsSheet(reopenBehavior: Boolean = false) {
        _state.update {
            it.copy(
                showManufacturerSpecsSheet = true,
                behaviorSheetOpen = if (reopenBehavior) false else it.behaviorSheetOpen
            )
        }
        if (reopenBehavior) {
            pendingReopenBehaviorAfterSpecs = true
        }
    }

    fun closeManufacturerSpecsSheet() {
        val reopen = pendingReopenBehaviorAfterSpecs
        pendingReopenBehaviorAfterSpecs = false
        _state.update {
            it.copy(
                showManufacturerSpecsSheet = false,
                behaviorSheetOpen = it.behaviorSheetOpen || reopen
            )
        }
        persistManufacturerSpecsIfBound()
        rebuildPhysicsAfterSpecChange()
    }

    fun applySuggestions() {
        val type = _state.value.galleryType ?: EquipmentType.CIRCULATION_FAN
        requestApplySuggestionLayout(type)
    }

    fun autoPlaceSuggestedLayout(type: EquipmentType) {
        if (previewMode != PreviewMode.DEMO) return
        requestApplySuggestionLayout(type)
    }

    fun requestApplySuggestionLayout(type: EquipmentType) {
        refreshRecommendation()
        val countResult = SuggestionEvidence.countFor(type, _state.value.recommendation)
        if (!SuggestionEvidence.canApplyCalculatedLayout(countResult)) {
            _state.update {
                it.copy(
                    statusMessage = "Cannot apply layout — required inputs are missing.",
                    showReplaceLayoutConfirm = false,
                    pendingReplaceType = null
                )
            }
            return
        }
        val hasType = _state.value.instances.any { inst -> inst.type == type && !inst.isGhost }
        if (hasType) {
            _state.update {
                it.copy(
                    showReplaceLayoutConfirm = true,
                    pendingReplaceType = type
                )
            }
            return
        }
        applySuggestionLayout(type)
    }

    fun confirmReplaceLayout() {
        val type = _state.value.pendingReplaceType ?: return
        applySuggestionLayout(type)
    }

    fun cancelReplaceLayout() {
        _state.update {
            it.copy(
                showReplaceLayoutConfirm = false,
                pendingReplaceType = null
            )
        }
    }

    fun applySuggestionLayout(type: EquipmentType) {
        refreshRecommendation()
        val rec = _state.value.recommendation
        val countResult = SuggestionEvidence.countFor(type, rec)
        if (!SuggestionEvidence.canApplyCalculatedLayout(countResult)) {
            _state.update {
                it.copy(
                    statusMessage = "Cannot apply layout — required inputs are missing.",
                    showReplaceLayoutConfirm = false,
                    pendingReplaceType = null
                )
            }
            return
        }
        val n = SuggestionEvidence.illustrationCount(type, countResult)
        val generated = when (type) {
            EquipmentType.CIRCULATION_FAN -> PlacementSuggester.suggest(context, n, 0, 0)
            EquipmentType.EXHAUST_FAN -> PlacementSuggester.suggest(context, 0, n, 0)
            EquipmentType.FOGGER -> PlacementSuggester.suggest(context, 0, 0, n)
        }
        if (generated.isEmpty()) {
            _state.update {
                it.copy(
                    statusMessage = "No suggested positions for this equipment type.",
                    showReplaceLayoutConfirm = false,
                    pendingReplaceType = null
                )
            }
            return
        }
        undoSnapshot = _state.value.instances
        undoDeleted = null
        val kept = _state.value.instances.filter { inst -> inst.type != type }
        val acc = kept.toMutableList()
        generated.forEach { inst ->
            val placed = revalidateAgainst(
                inst.copy(
                    id = idFactory(),
                    isGhost = false,
                    fromSuggestion = true,
                    displayNumber = 0
                ),
                acc
            )
            acc += placed
        }
        val merged = InstanceNumbering.assignMissing(acc)
        lastSavedOrigin = LayoutOrigin.SUGGESTION_APPLIED
        _state.update {
            it.copy(
                mode = PreviewInteractionMode.PLACEMENT,
                instances = merged,
                ghost = null,
                selectedId = merged.lastOrNull { item -> item.type == type }?.id,
                saved = false,
                galleryType = null,
                showWhyPosition = false,
                showReplaceLayoutConfirm = false,
                pendingReplaceType = null,
                showUndo = false,
                undoLabel = "",
                orbitAllowed = false,
                selectedTool = SelectedEquipmentTool.MOVE,
                showExactEditor = false,
                statusMessage = "Applied ${EquipmentLabels.typeTitle(type)} layout. Equipment can be moved."
            )
        }
        refreshRuntime()
    }

    fun saveLayout() {
        if (!MyGreenhousePersistence.allowsPersistentSave(previewMode)) {
            _state.update {
                it.copy(
                    saved = false,
                    statusMessage = MyGreenhousePersistence.DEMO_SESSION_ONLY
                )
            }
            return
        }
        val invalid = _state.value.instances.filter { !it.validity.isValid }
        if (invalid.isNotEmpty()) {
            _state.update {
                it.copy(statusMessage = invalid.first().validity.reasons.firstOrNull() ?: "Fix invalid placements before saving.")
            }
            return
        }
        if (configurationId.isBlank() || userId.isBlank()) {
            _state.update { it.copy(statusMessage = "Layout is kept for this preview only until a configuration ID is available.") }
            return
        }
        viewModelScope.launch {
            lastSavedOrigin = if (_state.value.instances.isNotEmpty() &&
                _state.value.instances.all { it.fromSuggestion }
            ) {
                LayoutOrigin.SUGGESTION_APPLIED
            } else {
                LayoutOrigin.MANUAL
            }
            persist()
            _state.update { it.copy(saved = true, statusMessage = MyGreenhousePersistence.SAVED) }
        }
    }

    fun toggleEquipmentCard() {
        _state.update { it.copy(equipmentCardExpanded = !it.equipmentCardExpanded, behaviorSheetOpen = false) }
    }

    fun openBehaviorSheet() {
        if (previewMode == PreviewMode.DEMO) {
            applySimulatedDefaults(resolvedClimate(), overwrite = false)
        }
        _state.update { it.copy(behaviorSheetOpen = true, equipmentCardExpanded = false) }
    }

    fun closeBehaviorSheet() {
        _state.update { ui ->
            ui.copy(
                behaviorSheetOpen = false,
                behaviorError = ui.behaviorError.takeUnless { it == ArRuntimeCopy.CONFIGURATION_CHANGED }
            )
        }
    }

    fun toggleBehaviorSheet() {
        if (_state.value.behaviorSheetOpen) closeBehaviorSheet() else openBehaviorSheet()
    }

    fun startBehavior() {
        val ui = _state.value
        val climate = resolvedClimate()
        val input = runtimeInput(ui)
        val block = EquipmentRuntimeEvaluator.startBlockReason(
            instances = ui.instances,
            thresholds = climate.runtimeThresholds(),
            input = input,
            mode = previewMode,
            liveReadingFresh = liveReadingIsFresh()
        )
        if (block != null) {
            _state.update { it.copy(behaviorError = block, behaviorRunning = false) }
            return
        }
        stopPhysicsRuntime()
        val states = EquipmentRuntimeEvaluator.evaluateAll(
            instances = ui.instances,
            previousById = emptyMap(),
            thresholds = climate.runtimeThresholds(),
            temperatureC = input.temperatureC,
            humidityPercent = input.humidityPercent
        )
        val snapshot = startPhysicsRuntime(states, input)
        displayScheduler.reset()
        cachedPhysicsForField = null
        cachedEnvironmentalField = null
        telemetryStopIssued = false
        _state.update {
            it.copy(
                behaviorRunning = true,
                runtimeById = states,
                behaviorError = null,
                physicsSnapshot = snapshot,
                displayedTemperatureC = snapshot?.referenceTemperatureC
                    ?: it.simulatedTemperatureC
                    ?: input.temperatureC,
                displayedHumidityPercent = snapshot?.referenceRelativeHumidityPercent
                    ?: it.simulatedHumidityPercent
                    ?: input.humidityPercent,
                displayedFieldSnapshot = snapshot,
                weatherBoundary = if (previewMode == PreviewMode.DEMO) {
                    demoWeatherBoundary()
                } else {
                    it.weatherBoundary
                },
                environmentalFieldGeneration = it.environmentalFieldGeneration + 1
            )
        }
    }

    fun stopBehavior(reason: String? = null) {
        val wasRunning = _state.value.behaviorRunning
        val publishedReason =
            if (reason == ArRuntimeCopy.CONFIGURATION_CHANGED && !wasRunning) null else reason
        stopPhysicsRuntime()
        cachedPhysicsForField = null
        cachedEnvironmentalField = null
        _state.update { ui ->
            ui.copy(
                behaviorRunning = false,
                runtimeById = EquipmentRuntimeEvaluator.notRunningById(ui.instances),
                behaviorError = publishedReason,
                physicsSnapshot = null,
                displayedFieldSnapshot = null,
                environmentalFieldGeneration = ui.environmentalFieldGeneration + 1
            )
        }
    }

    fun setSimulatedTemperature(value: Double) {
        if (previewMode != PreviewMode.DEMO) return
        if (!value.isFinite()) return
        val clamped = value.coerceIn(TEMP_HARD_MIN, TEMP_HARD_MAX)
        _state.update {
            it.copy(
                simulatedTemperatureC = clamped,
                simulatedTemperatureInput = formatOneDecimal(clamped)
            )
        }
        onRuntimeInputChanged()
        refreshTooColdAdvisory()
    }

    fun setSimulatedHumidity(value: Double) {
        if (previewMode != PreviewMode.DEMO) return
        if (!value.isFinite()) return
        val clamped = value.coerceIn(RH_HARD_MIN, RH_HARD_MAX)
        _state.update {
            it.copy(
                simulatedHumidityPercent = clamped,
                simulatedHumidityInput = formatOneDecimal(clamped)
            )
        }
        onRuntimeInputChanged()
    }

    fun setSimulatedTemperatureInput(raw: String) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(simulatedTemperatureInput = raw) }
        if (raw.isBlank()) {
            _state.update { it.copy(simulatedTemperatureC = null) }
            onRuntimeInputChanged()
            refreshTooColdAdvisory()
            return
        }
        val parsed = TargetParser.parseDecimal(raw)
        if (parsed != null && parsed.isFinite()) {
            _state.update { it.copy(simulatedTemperatureC = parsed.coerceIn(TEMP_HARD_MIN, TEMP_HARD_MAX)) }
            onRuntimeInputChanged()
            refreshTooColdAdvisory()
        }
    }

    fun setSimulatedHumidityInput(raw: String) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(simulatedHumidityInput = raw) }
        if (raw.isBlank()) {
            _state.update { it.copy(simulatedHumidityPercent = null) }
            onRuntimeInputChanged()
            return
        }
        val parsed = TargetParser.parseDecimal(raw)
        if (parsed != null && parsed.isFinite()) {
            _state.update { it.copy(simulatedHumidityPercent = parsed.coerceIn(RH_HARD_MIN, RH_HARD_MAX)) }
            onRuntimeInputChanged()
        }
    }

    fun onLiveEnvironment(live: ArLiveEnvironmentReading) {
        val kind = if (previewMode == PreviewMode.MY_GREENHOUSE) {
            when (live.uiState) {
                LiveSensorUiState.LIVE -> EnvironmentDataSourceKind.LIVE
                LiveSensorUiState.STALE -> EnvironmentDataSourceKind.OFFLINE_DELAYED
                else -> EnvironmentDataSourceKind.PREVIEW
            }
        } else {
            EnvironmentDataSourceKind.PREVIEW
        }
        _state.update { it.copy(liveEnvironment = live, environmentKind = kind) }
        refreshTooColdAdvisory()
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        onRuntimeInputChanged()
    }

    fun onWeatherBoundary(boundary: WeatherBoundary) {
        applyWeatherBoundary(boundary, _state.value.weatherUi)
    }

    fun onWeatherState(state: GreenhouseWeatherState) {
        applyWeatherBoundary(state.boundary(), state)
    }

    fun onActualRelayState(byId: Map<String, EquipmentRuntimeState>) {
        _state.update { it.copy(actualRelayById = byId) }
    }

    fun bindLiveEnvironmentSource(source: LiveEnvironmentSource?) {
        liveEnvironmentSource = source
    }

    fun bindWeatherBoundarySource(source: WeatherBoundarySource?) {
        weatherBoundarySource = source
        if (source != null) {
            _state.update { it.copy(weatherBoundary = source.latest()) }
        }
    }

    fun equipmentPresentation(): EquipmentPresentationContext =
        EquipmentPresentationContext(
            mode = previewMode,
            visualizationRunning = _state.value.behaviorRunning,
            actualRelayById = _state.value.actualRelayById
        )

    fun onLeaveArScreen() {
        if (_state.value.behaviorRunning) {
            stopBehavior()
        } else {
            clearStaleConfigurationChangedReason()
        }
        stopPhysicsRuntime()
    }

    fun onArReposition() {
        physicsScheduler.reset()
        displayScheduler.reset()
    }

    fun climate(): OverlayClimate = overlay

    fun resolvedClimate(): OverlayClimate = ArHudContent.resolveOverlay(overlay, previewMode)

    fun onViewTooColdSuggestions(navigateToDecisionSupport: () -> Unit) {
        if (_state.value.tooColdAdvisory == null) return
        TooColdAdvisoryEvaluator.onViewSuggestions(navigateToDecisionSupport)
    }

    private fun refreshTooColdAdvisory() {
        _state.update { ui ->
            val next = TooColdAdvisoryEvaluator.evaluate(
                mode = previewMode,
                climate = overlay,
                simulatedTemperatureC = ui.simulatedTemperatureC,
                live = ui.liveEnvironment,
                previouslyVisible = ui.tooColdAdvisory != null
            )
            if (next == ui.tooColdAdvisory) ui else ui.copy(tooColdAdvisory = next)
        }
    }

    fun environmentKind(): EnvironmentDataSourceKind = _state.value.environmentKind

    fun runtimeInput(ui: PlacementUiState = _state.value): EquipmentRuntimeInput =
        EquipmentRuntimeEvaluator.runtimeInput(
            mode = previewMode,
            simulatedTemperatureC = ui.simulatedTemperatureC,
            simulatedHumidityPercent = ui.simulatedHumidityPercent,
            live = ui.liveEnvironment
        )

    fun startBlockReason(): String? = EquipmentRuntimeEvaluator.startBlockReason(
        instances = _state.value.instances,
        thresholds = resolvedClimate().runtimeThresholds(),
        input = runtimeInput(),
        mode = previewMode,
        liveReadingFresh = liveReadingIsFresh()
    )

    fun circulationEffectFrame(ui: PlacementUiState = _state.value): CirculationEffectFrame {
        if (!ui.airflowFogVisible) return CirculationEffectFrame.EMPTY
        return CirculationEffectPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.physicsSnapshot,
            thresholds = resolvedClimate().runtimeThresholds(),
            targetTemperatureC = resolvedClimate().optimalTemperatureC,
            colorSnapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot
        )
    }

    fun circulationProbeMarkers(ui: PlacementUiState = _state.value): List<CirculationProbeMarker> =
        CirculationProbeLabelPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            labelsVisible = ui.labelsVisible,
            selectedId = ui.selectedId,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.displayedFieldSnapshot,
            targetTemperatureC = resolvedClimate().optimalTemperatureC,
            cdpC = resolvedClimate().circulation?.cdp,
            conC = resolvedClimate().circulation?.con,
            eonC = resolvedClimate().exhaust?.eon
        )

    fun exhaustEffectFrame(ui: PlacementUiState = _state.value): ExhaustEffectFrame {
        if (!ui.airflowFogVisible) return ExhaustEffectFrame.EMPTY
        return ExhaustEffectPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.physicsSnapshot,
            thresholds = resolvedClimate().runtimeThresholds(),
            targetTemperatureC = resolvedClimate().optimalTemperatureC,
            colorSnapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot
        )
    }

    fun padInletEffectFrame(ui: PlacementUiState = _state.value): PadInletEffectFrame {
        val exhaustOn = ui.instances.any { inst ->
            !inst.isGhost &&
                inst.type == EquipmentType.EXHAUST_FAN &&
                ui.runtimeById[inst.id] == EquipmentRuntimeState.ON
        }
        return PadInletEffectPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            exhaustOn = exhaustOn,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot,
            lengthM = context.lengthM,
            widthM = context.widthM,
            heightM = context.heightM,
            thresholds = resolvedClimate().runtimeThresholds(),
            targetTemperatureC = resolvedClimate().optimalTemperatureC,
            colorSnapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot,
            resolved = resolvedFanPadBoundary(ui),
            showActiveEffects = ui.airflowFogVisible
        )
    }

    fun foggerEffectFrame(ui: PlacementUiState = _state.value): FoggerEffectFrame {
        if (!ui.airflowFogVisible) return FoggerEffectFrame.EMPTY
        return FoggerEffectPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.physicsSnapshot,
            thresholds = resolvedClimate().runtimeThresholds(),
            colorSnapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot
        )
    }

    fun foggerDetails(ui: PlacementUiState = _state.value): FoggerDetailsModel? =
        FoggerEffectPlanner.details(
            visualizationRunning = ui.behaviorRunning,
            selected = ui.selected,
            runtimeById = ui.runtimeById,
            snapshot = ui.displayedFieldSnapshot ?: ui.physicsSnapshot,
            thresholds = resolvedClimate().runtimeThresholds(),
            presentation = equipmentPresentation()
        )

    fun exhaustProbeMarkers(ui: PlacementUiState = _state.value): List<CirculationProbeMarker> =
        ExhaustProbeLabelPlanner.plan(
            visualizationRunning = ui.behaviorRunning,
            labelsVisible = ui.labelsVisible,
            selectedId = ui.selectedId,
            instances = ui.instances,
            runtimeById = ui.runtimeById,
            snapshot = ui.displayedFieldSnapshot,
            targetTemperatureC = resolvedClimate().optimalTemperatureC,
            cdpC = resolvedClimate().circulation?.cdp,
            conC = resolvedClimate().circulation?.con,
            eonC = resolvedClimate().exhaust?.eon
        )

    fun visualizationProbeMarkers(ui: PlacementUiState = _state.value): List<CirculationProbeMarker> {
        val selected = ui.instances.firstOrNull { it.id == ui.selectedId } ?: return emptyList()
        return if (OfficialValidationTransect.isPublicEquipment(selected.type)) {
            exhaustProbeMarkers(ui)
        } else {
            emptyList()
        }
    }

    fun visualizationProbeControlAvailable(ui: PlacementUiState = _state.value): Boolean =
        ExhaustProbeLabelPlanner.controlAvailable(
            visualizationRunning = ui.behaviorRunning,
            labelsVisible = ui.labelsVisible,
            selectedId = ui.selectedId,
            instances = ui.instances,
            runtimeById = ui.runtimeById
        )

    fun visualizationProbeCaption(ui: PlacementUiState = _state.value): String {
        val selected = ui.instances.firstOrNull { it.id == ui.selectedId }
        if (selected?.type != EquipmentType.EXHAUST_FAN) {
            return com.greenhands.app.heat.visualization.VisualizationCopy.PREDICTED_UNCALIBRATED
        }
        val snap = ui.displayedFieldSnapshot ?: ui.physicsSnapshot
        return com.greenhands.app.heat.visualization.VisualizationCopy.exhaustProbeCaption(
            snap?.weatherSourceLabel,
            snap?.fanPad?.sourceLabel?.takeIf { snap.fanPad.present },
            demoSimulation = previewMode == PreviewMode.DEMO
        )
    }

    fun environmentalFieldSnapshot(ui: PlacementUiState = _state.value): EnvironmentalFieldSnapshot {
        val climate = resolvedClimate()
        val thresholds = EnvironmentalFieldThresholds(
            targetTemperatureC = climate.optimalTemperatureC,
            cdpC = climate.circulation?.cdp,
            conC = climate.circulation?.con,
            eonC = climate.exhaust?.eon,
            fonPercent = climate.fogger?.fon,
            fspPercent = climate.fogger?.fsp,
            fdpPercent = climate.fogger?.fdp
        )
        val cropName = climate.crop?.displayName ?: VisualizationCopy.COLOUR_UNAVAILABLE
        val stageName = climate.stage?.displayName ?: climate.stage?.shortLabel
            ?: VisualizationCopy.COLOUR_UNAVAILABLE
        val physics = ui.physicsSnapshot
        val cached = cachedEnvironmentalField
        if (ui.behaviorRunning &&
            physics != null &&
            cachedPhysicsForField === physics &&
            cached != null &&
            cached.identity.generation == ui.environmentalFieldGeneration &&
            cached.identity.physicsSnapshotIdentity == System.identityHashCode(physics)
        ) {
            return cached
        }
        val built = EnvironmentalFieldBuilder.build(
            physics = physics,
            previewMode = previewMode,
            widthM = context.widthM,
            lengthM = context.lengthM,
            heightM = context.heightM,
            canopyHeightM = context.canopyHeightM,
            thresholds = thresholds,
            cropName = cropName,
            stageName = stageName,
            generation = ui.environmentalFieldGeneration,
            visualizationRunning = ui.behaviorRunning
        )
        if (ui.behaviorRunning && physics != null &&
            built.availability == com.greenhands.app.heat.visualization.EnvironmentalFieldAvailability.AVAILABLE
        ) {
            cachedPhysicsForField = physics
            cachedEnvironmentalField = built
        } else {
            cachedPhysicsForField = null
            cachedEnvironmentalField = null
        }
        return built
    }

    fun environmentalFieldFrame(ui: PlacementUiState = _state.value): EnvironmentalFieldFrame {
        if (ui.environmentalLayer == EnvironmentalFieldLayer.OFF) {
            return EnvironmentalFieldPlanner.off(ui.fieldHeight, legendExpanded = ui.fieldLegendExpanded)
        }
        val snapshot = environmentalFieldSnapshot(ui)
        return EnvironmentalFieldPlanner.plan(
            snapshot = snapshot,
            layer = ui.environmentalLayer,
            height = ui.fieldHeight,
            generation = ui.environmentalFieldGeneration,
            legendExpanded = ui.fieldLegendExpanded
        )
    }

    fun environmentalFieldLegend(ui: PlacementUiState = _state.value): EnvironmentalFieldLegendModel =
        environmentalFieldFrame(ui).legend

    fun demoFanPadProfile(ui: PlacementUiState = _state.value): DemoFanPadProfile = DemoFanPadProfile(
        enabled = ui.demoFanPadEnabled,
        wetPadActive = ui.demoFanPadWetActive,
        wallSelection = FanPadWallSelection.AUTO,
        efficiencyFraction = ui.demoFanPadEfficiency,
        outsideTemperatureC = ui.demoOutsideTemperatureC,
        outsideRelativeHumidityPercent = ui.demoOutsideHumidityPercent
    )

    fun fanPadBoundaryInput(ui: PlacementUiState = _state.value): FanPadBoundaryInput? {
        return if (previewMode == PreviewMode.DEMO) {
            val profile = demoFanPadProfile(ui)
            if (!profile.enabled) null
            else profile.toBoundaryInput(context.widthM, context.heightM)
        } else {
            val present = ui.myPadPresent ?: return null
            FanPadBoundaryInput(
                present = present,
                enabled = present,
                wetPadActive = ui.myPadWetActive,
                wallSelection = ui.myPadWall,
                widthM = ui.myPadWidthInput.toDoubleOrNull(),
                heightM = ui.myPadHeightInput.toDoubleOrNull(),
                efficiencyFraction = ui.myPadEfficiencyInput.toDoubleOrNull(),
                sourceLabel = ui.weatherBoundary.sourceLabel
            )
        }
    }

    fun resolvedFanPadBoundary(ui: PlacementUiState = _state.value): ResolvedFanPadBoundary {
        val weather = if (previewMode == PreviewMode.DEMO) {
            demoWeatherBoundary(ui)
        } else {
            ui.weatherBoundary
        }
        return FanPadBoundaryResolver.resolve(
            mode = previewMode,
            lengthM = context.lengthM,
            widthM = context.widthM,
            heightM = context.heightM,
            cellEdgeM = AdaptiveGridPolicy.select(
                context.lengthM,
                context.widthM,
                context.heightM
            ).cellEdgeM,
            instances = ui.instances,
            weather = weather,
            input = fanPadBoundaryInput(ui)
        )
    }

    fun demoWeatherBoundary(ui: PlacementUiState = _state.value): WeatherBoundary {
        if (previewMode != PreviewMode.DEMO) return WeatherBoundary.missing()
        if (ui.demoFanPadEnabled) {
            return WeatherBoundary.demoSimulated(
                ui.demoOutsideTemperatureC,
                ui.demoOutsideHumidityPercent,
                FanPadCopy.DEMO_OUTSIDE_WEATHER
            )
        }
        return DemoOutsideBoundary.fromActiveCropTargets(
            previewMode = PreviewMode.DEMO,
            optimalTemperatureC = resolvedClimate().optimalTemperatureC,
            optimalHumidityPercent = resolvedClimate().optimalHumidityPercent
        )
    }

    fun setDemoFanPadEnabled(enabled: Boolean) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(demoFanPadEnabled = enabled) }
        onFanPadBoundaryChanged()
    }

    fun setDemoFanPadWetActive(wet: Boolean) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(demoFanPadWetActive = wet) }
        onFanPadBoundaryChanged()
    }

    fun setDemoFanPadEfficiency(value: Double) {
        if (previewMode != PreviewMode.DEMO) return
        if (!value.isFinite()) return
        val clamped = value.coerceIn(0.01, 1.0)
        _state.update {
            it.copy(
                demoFanPadEfficiency = clamped,
                demoFanPadEfficiencyInput = formatOneDecimal(clamped)
            )
        }
        onFanPadBoundaryChanged()
    }

    fun setDemoFanPadEfficiencyInput(raw: String) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(demoFanPadEfficiencyInput = raw) }
        val parsed = TargetParser.parseDecimal(raw) ?: return
        if (parsed.isFinite() && parsed > 0.0 && parsed <= 1.0) {
            _state.update { it.copy(demoFanPadEfficiency = parsed) }
            onFanPadBoundaryChanged()
        }
    }

    fun setDemoOutsideTemperature(value: Double) {
        if (previewMode != PreviewMode.DEMO) return
        if (!value.isFinite()) return
        val clamped = value.coerceIn(TEMP_HARD_MIN, TEMP_HARD_MAX)
        _state.update {
            it.copy(
                demoOutsideTemperatureC = clamped,
                demoOutsideTemperatureInput = formatOneDecimal(clamped)
            )
        }
        onFanPadBoundaryChanged()
    }

    fun setDemoOutsideTemperatureInput(raw: String) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(demoOutsideTemperatureInput = raw) }
        val parsed = TargetParser.parseDecimal(raw) ?: return
        if (parsed.isFinite()) {
            _state.update { it.copy(demoOutsideTemperatureC = parsed.coerceIn(TEMP_HARD_MIN, TEMP_HARD_MAX)) }
            onFanPadBoundaryChanged()
        }
    }

    fun setDemoOutsideHumidity(value: Double) {
        if (previewMode != PreviewMode.DEMO) return
        if (!value.isFinite()) return
        val clamped = value.coerceIn(RH_HARD_MIN, RH_HARD_MAX)
        _state.update {
            it.copy(
                demoOutsideHumidityPercent = clamped,
                demoOutsideHumidityInput = formatOneDecimal(clamped)
            )
        }
        onFanPadBoundaryChanged()
    }

    fun setDemoOutsideHumidityInput(raw: String) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { it.copy(demoOutsideHumidityInput = raw) }
        val parsed = TargetParser.parseDecimal(raw) ?: return
        if (parsed.isFinite()) {
            _state.update { it.copy(demoOutsideHumidityPercent = parsed.coerceIn(RH_HARD_MIN, RH_HARD_MAX)) }
            onFanPadBoundaryChanged()
        }
    }

    fun setMyPadPresent(present: Boolean) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadPresent = present, saved = false) }
    }

    fun setMyPadWetActive(wet: Boolean) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadWetActive = wet, saved = false) }
    }

    fun setMyPadWidthInput(raw: String) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadWidthInput = raw, saved = false) }
    }

    fun setMyPadHeightInput(raw: String) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadHeightInput = raw, saved = false) }
    }

    fun setMyPadEfficiencyInput(raw: String) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadEfficiencyInput = raw, saved = false) }
    }

    fun setMyPadWall(wall: FanPadWallSelection) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(myPadWall = wall, saved = false) }
    }

    private fun applyWeatherBoundary(
        boundary: WeatherBoundary,
        weatherUi: GreenhouseWeatherState
    ) {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        _state.update { it.copy(weatherBoundary = boundary, weatherUi = weatherUi) }
        val ui = _state.value
        val physics = physicsRuntime
        if (!ui.behaviorRunning || physics == null || !physics.isRunning()) return
        try {
            val snap = physics.syncBoundary(boundary, fanPadBoundaryInput(ui))
            _state.update {
                it.copy(
                    physicsSnapshot = snap,
                    weatherBoundary = boundary,
                    weatherUi = weatherUi,
                    behaviorError = null
                )
            }
        } catch (_: SimulationException) {
            _state.update { it.copy(weatherBoundary = boundary, weatherUi = weatherUi) }
        }
    }

    private fun onFanPadBoundaryChanged() {
        val ui = _state.value
        if (!ui.behaviorRunning) return
        val physics = physicsRuntime ?: return
        if (!physics.isRunning()) return
        val weather = if (previewMode == PreviewMode.DEMO) {
            demoWeatherBoundary(ui)
        } else {
            ui.weatherBoundary
        }
        try {
            val snap = physics.syncBoundary(weather, fanPadBoundaryInput(ui))
            _state.update {
                it.copy(
                    physicsSnapshot = snap,
                    weatherBoundary = weather,
                    behaviorError = null
                )
            }
        } catch (_: SimulationException) {
            if (previewMode == PreviewMode.DEMO) {
                stopBehavior(ArRuntimeCopy.INPUT_UNAVAILABLE)
            }
        }
    }

    fun hudTemperatureC(ui: PlacementUiState = _state.value): Double? =
        ui.displayedTemperatureC ?: ui.simulatedTemperatureC

    fun hudHumidityPercent(ui: PlacementUiState = _state.value): Double? =
        ui.displayedHumidityPercent ?: ui.simulatedHumidityPercent

    private fun applySimulatedDefaults(resolved: OverlayClimate, overwrite: Boolean) {
        if (previewMode != PreviewMode.DEMO) return
        _state.update { current ->
            if (current.behaviorRunning) return@update current
            val temperature = when {
                overwrite || current.simulatedTemperatureC == null -> resolved.optimalTemperatureC
                else -> current.simulatedTemperatureC
            }
            val humidity = when {
                overwrite || current.simulatedHumidityPercent == null -> resolved.optimalHumidityPercent
                else -> current.simulatedHumidityPercent
            }
            current.copy(
                simulatedTemperatureC = temperature,
                simulatedHumidityPercent = humidity,
                simulatedTemperatureInput = temperature?.let { formatOneDecimal(it) }.orEmpty(),
                simulatedHumidityInput = humidity?.let { formatOneDecimal(it) }.orEmpty()
            )
        }
    }

    private fun onRuntimeInputChanged() {
        val ui = _state.value
        if (!ui.behaviorRunning) return
        val climate = resolvedClimate()
        val input = runtimeInput(ui)
        val block = EquipmentRuntimeEvaluator.startBlockReason(
            instances = ui.instances,
            thresholds = climate.runtimeThresholds(),
            input = input,
            mode = previewMode,
            liveReadingFresh = liveReadingIsFresh()
        )
        if (block != null) {
            stopForUnavailableTelemetry()
            return
        }
        telemetryStopIssued = false
        val states = EquipmentRuntimeEvaluator.evaluateAll(
            instances = ui.instances,
            previousById = ui.runtimeById,
            thresholds = climate.runtimeThresholds(),
            temperatureC = input.temperatureC,
            humidityPercent = input.humidityPercent
        )
        val physics = physicsRuntime
        if (!publishingFromPhysics && physics != null && physics.isRunning()) {
            val inside = insideClimateForRuntime(input) ?: run {
                stopForUnavailableTelemetry()
                return
            }
            try {
                val snap = physics.assimilateUniform(inside, states)
                displayScheduler.reset()
                _state.update {
                    it.copy(
                        runtimeById = snap.runtimeById,
                        physicsSnapshot = snap,
                        displayedTemperatureC = snap.referenceTemperatureC,
                        displayedHumidityPercent = snap.referenceRelativeHumidityPercent,
                        displayedFieldSnapshot = snap,
                        behaviorError = null
                    )
                }
            } catch (_: SimulationException) {
                stopBehavior(ArRuntimeCopy.INPUT_UNAVAILABLE)
            }
            return
        }
        _state.update { it.copy(runtimeById = states, behaviorError = null) }
    }

    private fun refreshRuntime() {
        val ui = _state.value
        if (!ui.behaviorRunning) {
            val next = EquipmentRuntimeEvaluator.notRunningById(ui.instances)
            if (next != ui.runtimeById) {
                _state.update { it.copy(runtimeById = next) }
            }
            return
        }
        val climate = resolvedClimate()
        val input = runtimeInput(ui)
        val block = EquipmentRuntimeEvaluator.startBlockReason(
            instances = ui.instances,
            thresholds = climate.runtimeThresholds(),
            input = input,
            mode = previewMode,
            liveReadingFresh = liveReadingIsFresh()
        )
        if (block != null) {
            stopForUnavailableTelemetry()
            return
        }
        val states = EquipmentRuntimeEvaluator.evaluateAll(
            instances = ui.instances,
            previousById = ui.runtimeById,
            thresholds = climate.runtimeThresholds(),
            temperatureC = input.temperatureC,
            humidityPercent = input.humidityPercent
        )
        val physics = physicsRuntime
        if (physics != null && physics.isRunning()) {
            try {
                val snap = physics.syncEquipment(ui.instances, states)
                _state.update {
                    it.copy(
                        runtimeById = snap.runtimeById,
                        physicsSnapshot = snap,
                        behaviorError = null
                    )
                }
            } catch (_: SimulationException) {
                stopBehavior(ArRuntimeCopy.INPUT_UNAVAILABLE)
            }
            return
        }
        _state.update { it.copy(runtimeById = states, behaviorError = null) }
    }

    fun advanceVisualization(elapsedDtS: Double) {
        if (previewMode != PreviewMode.DEMO && previewMode != PreviewMode.MY_GREENHOUSE) return
        val physics = physicsRuntime ?: return
        if (!physics.isRunning() || !_state.value.behaviorRunning) return
        val work = physicsScheduler.acceptFrame(elapsedDtS)
        if (work.stepCount > 0) {
            publishingFromPhysics = true
            try {
                var snap: PhysicsFieldSnapshot? = null
                repeat(work.stepCount) {
                    snap = physics.advance(work.stepSizeS)
                }
                val published = snap
                if (published != null) {
                    val generation = _state.value.environmentalFieldGeneration
                    _state.update {
                        if (!it.behaviorRunning || it.environmentalFieldGeneration != generation) {
                            return@update it
                        }
                        it.copy(
                            runtimeById = published.runtimeById,
                            physicsSnapshot = published,
                            behaviorError = null
                        )
                    }
                }
            } catch (_: SimulationException) {
                stopBehavior(ArRuntimeCopy.INPUT_UNAVAILABLE)
            } finally {
                publishingFromPhysics = false
            }
        }
        val displayWork = displayScheduler.acceptFrame(elapsedDtS)
        if (displayWork.stepCount > 0) {
            publishDisplayedClimate()
        }
    }

    private fun publishDisplayedClimate() {
        val ui = _state.value
        val snap = ui.physicsSnapshot ?: return
        val temperature = snap.referenceTemperatureC
        val humidity = snap.referenceRelativeHumidityPercent
        val sameTemperature = ui.displayedTemperatureC != null &&
            ui.displayedTemperatureC == temperature
        val sameHumidity = ui.displayedHumidityPercent != null &&
            ui.displayedHumidityPercent == humidity
        val sameSnapshot = ui.displayedFieldSnapshot === snap
        if (sameTemperature && sameHumidity && sameSnapshot) return
        _state.update {
            it.copy(
                displayedTemperatureC = temperature,
                displayedHumidityPercent = humidity,
                displayedFieldSnapshot = snap
            )
        }
    }

    private fun startPhysicsRuntime(
        states: Map<String, EquipmentRuntimeState>,
        input: EquipmentRuntimeInput
    ): PhysicsFieldSnapshot? {
        val inside = insideClimateForRuntime(input) ?: return null
        val ui = _state.value
        val weather = if (previewMode == PreviewMode.DEMO) {
            demoWeatherBoundary(ui)
        } else {
            ui.weatherBoundary
        }
        return try {
            val runtime = PhysicsFieldRuntime()
            val snap = runtime.start(
                PhysicsRuntimeRequest(
                    previewMode = previewMode,
                    lengthM = context.lengthM,
                    widthM = context.widthM,
                    heightM = context.heightM,
                    cropRowCount = context.cropRowCount,
                    canopyHeightM = context.canopyHeightM,
                    inside = inside,
                    weather = weather,
                    instances = ui.instances,
                    runtimeById = states,
                    thresholds = resolvedClimate().runtimeThresholds(),
                    manufacturerSpecsById = resolvedManufacturerSpecs(ui.instances),
                    fanPad = fanPadBoundaryInput(ui)
                )
            )
            physicsRuntime = runtime
            snap
        } catch (_: SimulationException) {
            stopPhysicsRuntime()
            null
        }
    }

    private fun insideClimateForRuntime(input: EquipmentRuntimeInput): InsideClimateReading? {
        return if (previewMode == PreviewMode.DEMO) {
            val temperature = input.temperatureC ?: return null
            val humidity = input.humidityPercent ?: return null
            InsideClimateReading.simulated(temperature, humidity)
        } else {
            val live = _state.value.liveEnvironment
            val temperature = live.temperatureC ?: input.temperatureC ?: return null
            val humidity = live.relativeHumidityPercent ?: input.humidityPercent ?: return null
            if (!temperature.isFinite() || !humidity.isFinite()) return null
            InsideClimateReading(
                temperatureC = temperature,
                relativeHumidityPercent = humidity,
                timestampMillis = live.timestampMillis,
                sensorPositionM = null,
                source = InsideClimateSource.ACTUAL_IOT,
                provenance = ProvenanceClass.MEASURED_INPUT,
                sourceLabel = com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.LIVE_READING,
                validity = InputValidity.VALID,
                freshness = FreshnessStatus.FRESH
            )
        }
    }

    private fun stopPhysicsRuntime() {
        physicsRuntime?.stop()
        physicsRuntime = null
        physicsScheduler.reset()
        displayScheduler.reset()
        cachedPhysicsForField = null
        cachedEnvironmentalField = null
    }

    private fun liveReadingIsFresh(
        live: ArLiveEnvironmentReading = _state.value.liveEnvironment
    ): Boolean {
        return when (live.uiState) {
            LiveSensorUiState.CONNECTING,
            LiveSensorUiState.STALE,
            LiveSensorUiState.DISCONNECTED -> false
            LiveSensorUiState.LIVE -> live.connected
            LiveSensorUiState.LEGACY -> {
                if (!live.connected) return false
                val timestamp = live.timestampMillis ?: return true
                val freshness = FreshnessEvaluator.insideClimate(
                    InsideClimateSource.ACTUAL_IOT,
                    timestamp,
                    clock.nowMillis(),
                    freshnessPolicy
                )
                freshness == FreshnessStatus.FRESH
            }
        }
    }

    private fun stopForUnavailableTelemetry() {
        if (!_state.value.behaviorRunning || telemetryStopIssued) return
        telemetryStopIssued = true
        stopBehavior(ArRuntimeCopy.INPUT_UNAVAILABLE)
    }

    private fun syncLiveTelemetryMonitoring() {
        telemetryJob?.cancel()
        telemetryJob = null
        weatherJob?.cancel()
        weatherJob = null
        if (previewMode != PreviewMode.MY_GREENHOUSE || !telemetryRepository.monitorsLiveGreenhouse) {
            releaseLiveTelemetry()
            return
        }
        acquireLiveTelemetry()
        telemetryJob = viewModelScope.launch {
            telemetryRepository.states.collect { state ->
                onLiveEnvironment(state.toLiveReading())
            }
        }
        weatherJob = viewModelScope.launch {
            telemetryRepository.weatherStates.collect { state ->
                onWeatherState(state)
            }
        }
    }

    private fun acquireLiveTelemetry() {
        if (telemetryHeld) return
        telemetryHeld = true
        telemetryRepository.startMonitoring()
    }

    private fun releaseLiveTelemetry() {
        if (!telemetryHeld) return
        telemetryHeld = false
        telemetryRepository.stopMonitoring()
    }

    private fun clearStaleConfigurationChangedReason() {
        if (_state.value.behaviorRunning) return
        if (_state.value.behaviorError != ArRuntimeCopy.CONFIGURATION_CHANGED) return
        _state.update { it.copy(behaviorError = null) }
    }

    override fun onCleared() {
        telemetryJob?.cancel()
        telemetryJob = null
        weatherJob?.cancel()
        weatherJob = null
        releaseLiveTelemetry()
        stopPhysicsRuntime()
        super.onCleared()
    }

    private fun refreshRecommendation() {
        val inputs = currentRecommendationInputs()
        _state.update { it.copy(recommendation = EquipmentRecommendationEngine.recommend(inputs)) }
    }

    private fun currentRecommendationInputs(): RecommendationInputs {
        val ui = _state.value
        val indoorFromOverlay = overlay.optimalTemperatureC ?: overlay.circulation?.csp
        val rhFromOverlay = overlay.optimalHumidityPercent ?: overlay.fogger?.fsp
        if (previewMode == PreviewMode.DEMO) {
            return DemoEquipmentProfile.inputs(
                lengthM = context.lengthM,
                widthM = context.widthM,
                heightM = context.heightM,
                cropRowCount = context.cropRowCount,
                canopyHeightM = context.canopyHeightM,
                indoorTemperatureC = indoorFromOverlay,
                indoorRhPercent = rhFromOverlay,
                crop = overlay.crop,
                stage = overlay.stage
            )
        }
        return RecommendationInputs(
            lengthM = context.lengthM,
            widthM = context.widthM,
            heightM = context.heightM,
            cropRowCount = context.cropRowCount,
            crop = overlay.crop,
            stage = overlay.stage,
            canopyHeightM = context.canopyHeightM,
            canopyHeightIsPreviewAssumption = true,
            optimalTemperatureC = overlay.circulation?.csp ?: overlay.optimalTemperatureC,
            optimalRhPercent = overlay.fogger?.fsp ?: overlay.optimalHumidityPercent,
            outdoorTemperatureC = ui.outdoorTempInput.toDoubleOrNull(),
            outdoorRhPercent = ui.outdoorRhInput.toDoubleOrNull(),
            indoorTemperatureC = overlay.circulation?.csp ?: overlay.optimalTemperatureC,
            indoorRhPercent = overlay.fogger?.fsp ?: overlay.optimalHumidityPercent,
            circulationRatedCfm = ui.circulationRatedCfmInput.toDoubleOrNull(),
            circulationRatedM3s = ui.circulationRatedM3sInput.toDoubleOrNull(),
            exhaustRatedAirflowM3sAtStaticPressure = ui.exhaustRatedM3sInput.toDoubleOrNull()
                ?: ui.exhaustRatedCfmInput.toDoubleOrNull()?.let {
                    com.greenhands.app.equipment.domain.AirflowUnits.cubicFeetPerMinuteToM3s(it)
                },
            foggerWaterFlowKgS = ui.foggerFlowKgSInput.toDoubleOrNull()
                ?: ui.foggerFlowLphInput.toDoubleOrNull()?.let {
                    com.greenhands.app.equipment.domain.AirflowUnits.litresPerHourToKgS(it)
                },
            foggerEvaporationEfficiency = ui.foggerEtaInput.toDoubleOrNull(),
            foggerCoverageRadiusM = ui.foggerSprayRadiusMInput.toDoubleOrNull(),
            netSolarAndInternalHeatLoadW = ui.heatLoadWInput.toDoubleOrNull(),
            overrideCirculationCount = ui.overrideCircInput.toIntOrNull(),
            overrideExhaustCount = ui.overrideExhInput.toIntOrNull(),
            overrideFoggerCount = ui.overrideFogInput.toIntOrNull()
        )
    }

    private fun updatePose(id: String, position: DomainPoint, yawDeg: Double?) {
        val ghost = _state.value.ghost
        if (ghost != null && ghost.id == id) {
            var next = ghost.copy(positionM = position, yawDeg = yawDeg ?: ghost.yawDeg)
            if (next.type == EquipmentType.EXHAUST_FAN) next = PlacementValidator.snapExhaust(next, context)
            if (next.type == EquipmentType.FOGGER) next = PlacementValidator.clampOverhead(next, context)
            if (next.type == EquipmentType.CIRCULATION_FAN) next = PlacementValidator.clampElevation(next, context)
            next = revalidateAgainst(next, _state.value.instances)
            _state.update { it.copy(ghost = next, selectedId = next.id, saved = false).withEditor(next) }
            return
        }
        _state.update { st ->
            val updated = st.instances.map { inst ->
                if (inst.id != id) inst
                else {
                    var next = inst.copy(positionM = position, yawDeg = yawDeg ?: inst.yawDeg)
                    if (next.type == EquipmentType.EXHAUST_FAN) next = PlacementValidator.snapExhaust(next, context)
                    if (next.type == EquipmentType.FOGGER) next = PlacementValidator.clampOverhead(next, context)
                    if (next.type == EquipmentType.CIRCULATION_FAN) next = PlacementValidator.clampElevation(next, context)
                    revalidateAgainst(next, st.instances)
                }
            }
            val selected = updated.firstOrNull { it.id == id }
            st.copy(instances = updated, saved = false, selectedId = id, statusMessage = selected?.validity?.reasons?.firstOrNull())
                .let { if (selected != null) it.withEditor(selected) else it }
        }
    }

    private fun revalidate(instance: EquipmentInstance): EquipmentInstance =
        revalidateAgainst(instance, _state.value.instances)

    private fun revalidateAgainst(instance: EquipmentInstance, others: List<EquipmentInstance>): EquipmentInstance {
        var next = instance
        if (next.type == EquipmentType.EXHAUST_FAN) next = PlacementValidator.snapExhaust(next, context)
        if (next.type == EquipmentType.FOGGER) next = PlacementValidator.clampOverhead(next, context)
        if (next.type == EquipmentType.CIRCULATION_FAN) next = PlacementValidator.clampElevation(next, context)
        return next.copy(validity = PlacementValidator.validate(next, others, context), runtimeState = EquipmentRuntimeState.NOT_RUNNING)
    }

    private fun restoreInstance(origin: EquipmentInstance) {
        if (origin.isGhost) {
            val next = revalidateAgainst(origin, _state.value.instances)
            _state.update { it.copy(ghost = next, selectedId = next.id).withEditor(next) }
            return
        }
        _state.update { st ->
            val updated = st.instances.map { inst ->
                if (inst.id != origin.id) inst else revalidateAgainst(origin, st.instances)
            }
            val selected = updated.firstOrNull { it.id == origin.id }
            st.copy(instances = updated, selectedId = origin.id, saved = false)
                .let { if (selected != null) it.withEditor(selected) else it }
        }
    }

    private fun PlacementUiState.withEditor(instance: EquipmentInstance): PlacementUiState {
        val msg = when {
            !instance.validity.isValid -> instance.validity.reasons.firstOrNull()
            instance.validity.warnings.isNotEmpty() -> instance.validity.warnings.first()
            else -> statusMessage
        }
        return copy(
            xInput = formatMetres2(instance.positionM.xM),
            yInput = formatMetres2(instance.positionM.yM),
            zInput = formatMetres2(instance.positionM.zM),
            yawInput = formatMetres2(instance.yawDeg),
            editError = if (instance.validity.isValid) null else instance.validity.reasons.firstOrNull(),
            statusMessage = msg
        )
    }

    private suspend fun persist() {
        if (!MyGreenhousePersistence.allowsPersistentSave(previewMode)) return
        if (userId.isBlank() || configurationId.isBlank()) return
        val ui = _state.value
        repository.save(
            MyGreenhousePersistence.persistableLayout(
                EquipmentLayout(
                    layoutId = layoutId,
                    configurationId = configurationId,
                    userId = userId,
                    lengthM = context.lengthM,
                    widthM = context.widthM,
                    heightM = context.heightM,
                    cropRowCount = context.cropRowCount,
                    instances = ui.instances,
                    countOverrideCirculation = ui.overrideCircInput.toIntOrNull(),
                    countOverrideExhaust = ui.overrideExhInput.toIntOrNull(),
                    countOverrideFogger = ui.overrideFogInput.toIntOrNull(),
                    recommendationAssumptions = ui.recommendation?.disclaimer,
                    origin = lastSavedOrigin,
                    explicitlySaved = true,
                    coolingPadPresent = ui.myPadPresent,
                    coolingPadWidthM = ui.myPadWidthInput.toDoubleOrNull(),
                    coolingPadHeightM = ui.myPadHeightInput.toDoubleOrNull(),
                    coolingPadEfficiencyFraction = ui.myPadEfficiencyInput.toDoubleOrNull(),
                    coolingPadWetActive = ui.myPadPresent?.let { ui.myPadWetActive },
                    coolingPadWall = ui.myPadPresent?.let { ui.myPadWall.name },
                    circulationRatedCfm = ui.circulationRatedCfmInput.toDoubleOrNull(),
                    circulationRatedM3s = ui.circulationRatedM3sInput.toDoubleOrNull(),
                    circulationThrowRadiusM = ui.circulationThrowRadiusMInput.toDoubleOrNull(),
                    exhaustRatedAirflowM3s = ui.exhaustRatedM3sInput.toDoubleOrNull(),
                    exhaustRatedAirflowCfm = ui.exhaustRatedCfmInput.toDoubleOrNull(),
                    foggerWaterFlowKgS = ui.foggerFlowKgSInput.toDoubleOrNull(),
                    foggerWaterFlowLph = ui.foggerFlowLphInput.toDoubleOrNull(),
                    foggerSprayRadiusM = ui.foggerSprayRadiusMInput.toDoubleOrNull(),
                    foggerEvaporationEfficiency = ui.foggerEtaInput.toDoubleOrNull()
                )
            )
        )
    }

    fun resolvedManufacturerSpecs(
        instances: List<EquipmentInstance> = _state.value.instances
    ): Map<String, EquipmentPhysicsSpecification> {
        manufacturerSpecsFor?.let { return it(instances) }
        if (previewMode != PreviewMode.MY_GREENHOUSE) return emptyMap()
        return SavedManufacturerSpecFactory.fromRatings(instances, _state.value.manufacturerRatings())
    }

    fun missingManufacturerSpecs(ui: PlacementUiState = _state.value): List<String> =
        ui.missingManufacturerSpecs()

    fun quantitativeVisualizationComplete(ui: PlacementUiState = _state.value): Boolean =
        ui.quantitativeVisualizationComplete()

    private fun persistManufacturerSpecsIfBound() {
        if (previewMode != PreviewMode.MY_GREENHOUSE) return
        if (userId.isBlank() || configurationId.isBlank()) return
        viewModelScope.launch { persist() }
    }

    private fun rebuildPhysicsAfterSpecChange() {
        val ui = _state.value
        if (!ui.behaviorRunning) return
        val input = runtimeInput(ui)
        stopPhysicsRuntime()
        val snap = startPhysicsRuntime(ui.runtimeById, input)
        _state.update {
            it.copy(
                physicsSnapshot = snap,
                displayedFieldSnapshot = snap,
                environmentalFieldGeneration = it.environmentalFieldGeneration + 1
            )
        }
    }
}

private data class ScientificRuntimeConfiguration(
    val configurationId: String,
    val previewMode: PreviewMode,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cropRowCount: Int,
    val crop: Crop?,
    val stageId: String?,
    val optimalTemperatureC: Double?,
    val optimalHumidityPercent: Double?,
    val circulation: CirculationThresholds?,
    val exhaust: ExhaustThresholds?,
    val fogger: FoggerThresholds?
) {
    companion object {
        fun from(
            configurationId: String,
            previewMode: PreviewMode,
            lengthM: Double,
            widthM: Double,
            heightM: Double,
            cropRowCount: Int,
            overlay: OverlayClimate
        ): ScientificRuntimeConfiguration = ScientificRuntimeConfiguration(
            configurationId = configurationId,
            previewMode = previewMode,
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cropRowCount = cropRowCount,
            crop = overlay.crop,
            stageId = overlay.stage?.id,
            optimalTemperatureC = overlay.optimalTemperatureC,
            optimalHumidityPercent = overlay.optimalHumidityPercent,
            circulation = overlay.circulation,
            exhaust = overlay.exhaust,
            fogger = overlay.fogger
        )
    }
}

class EquipmentPlacementViewModelFactory(
    private val repository: EquipmentLayoutRepository,
    private val telemetryRepository: GreenhouseTelemetryRepository = NoOpGreenhouseTelemetryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EquipmentPlacementViewModel(
            repository = repository,
            telemetryRepository = telemetryRepository
        ) as T
    }
}

fun InMemoryPlacementVm(): EquipmentPlacementViewModel =
    EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
