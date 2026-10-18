package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.heat.runtime.adapter.AdaptiveGridPolicy
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.adapter.PerUuidEquipmentBridge
import com.greenhands.app.heat.runtime.adapter.SimulationScenarioAdapter
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.FanPadFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.RuntimeClock
import com.greenhands.app.heat.runtime.contracts.SystemRuntimeClock
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.toSnapshot
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.NumericalSettings
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.model.PreviewMode

data class PhysicsRuntimeRequest(
    val previewMode: PreviewMode,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cropRowCount: Int,
    val canopyHeightM: Double?,
    val inside: InsideClimateReading,
    val weather: WeatherBoundary,
    val instances: List<EquipmentInstance>,
    val runtimeById: Map<String, EquipmentRuntimeState>,
    val thresholds: EquipmentRuntimeThresholds,
    val manufacturerSpecsById: Map<String, EquipmentPhysicsSpecification> = emptyMap(),
    val gridConfig: AdaptiveGridPolicy.Config = AdaptiveGridPolicy.Config(),
    val numerical: NumericalSettings = NumericalSettings(),
    val clock: RuntimeClock = SystemRuntimeClock,
    val freshness: FreshnessPolicy = FreshnessPolicy(),
    val fanPad: FanPadBoundaryInput? = null
)

/**
 * Lifecycle-safe deterministic wrapper around [GreenhouseSimulationEngine].
 * Time advances only through [advance]. No background work is started here.
 *
 * Demo T/RH changes use [PhysicsRuntimeCopy.DEMO_ASSIMILATION_RULE]: uniform reinitialization.
 */
class PhysicsFieldRuntime {
    private var engine: GreenhouseSimulationEngine? = null
    private var request: PhysicsRuntimeRequest? = null
    private var elapsedS: Double = 0.0
    private var lastSnapshot: PhysicsFieldSnapshot? = null
    private var lastActiveUuids: Set<String> = emptySet()
    private var lastBoundaryFingerprint: String = ""
    private var lastFanPad: FanPadFieldSnapshot = FanPadFieldSnapshot.NONE
    private var running: Boolean = false
    private var lastFrameWellMixed: Boolean = false

    fun isRunning(): Boolean = running

    fun elapsedTimeS(): Double = elapsedS

    fun snapshot(): PhysicsFieldSnapshot? = lastSnapshot

    fun start(request: PhysicsRuntimeRequest): PhysicsFieldSnapshot {
        stop()
        rejectInvalidNumbers(request)
        this.request = request
        this.elapsedS = 0.0
        this.lastFrameWellMixed = false
        val built = createEngine(request, request.runtimeById)
        engine = built.first
        lastActiveUuids = built.second
        running = true
        lastSnapshot = capture(wellMixed = false)
        return lastSnapshot!!
    }

    fun stop() {
        engine = null
        request = null
        elapsedS = 0.0
        lastSnapshot = null
        lastActiveUuids = emptySet()
        lastBoundaryFingerprint = ""
        lastFanPad = FanPadFieldSnapshot.NONE
        running = false
        lastFrameWellMixed = false
    }

    fun advance(dtS: Double): PhysicsFieldSnapshot {
        val current = engine ?: throw SimulationException("Physics runtime is not running")
        val req = request ?: throw SimulationException("Physics runtime is not running")
        if (!dtS.isFinite() || dtS <= 0.0) {
            throw SimulationException("Elapsed time step must be finite and > 0 s, got $dtS")
        }
        val frame = try {
            current.step(dtS)
        } catch (error: SimulationException) {
            stop()
            throw error
        }
        lastFrameWellMixed = frame.diagnostics.wellMixedVentilation
        elapsedS += dtS
        if (!current.gridState().hasFiniteFields()) {
            stop()
            throw SimulationException("Non-finite field after advance at t=$elapsedS s")
        }
        val predictedT = frame.diagnostics.controlTemperatureC
        val predictedRh = frame.diagnostics.controlRhPercent
            ?: frame.diagnostics.wholeAirMeanRhPercent
        val previous = lastSnapshot?.runtimeById ?: req.runtimeById
        val nextStates = EquipmentRuntimeEvaluator.evaluateAll(
            instances = req.instances,
            previousById = previous,
            thresholds = req.thresholds,
            temperatureC = predictedT,
            humidityPercent = predictedRh
        )
        request = req.copy(runtimeById = nextStates)
        maybeRebuildSources()
        lastSnapshot = capture(wellMixed = lastFrameWellMixed)
        return lastSnapshot!!
    }

    /**
     * Explicit Demo T/RH update. Replaces the entire field; does not mix old and new cells.
     */
    fun assimilateUniform(
        inside: InsideClimateReading,
        runtimeById: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val req = request ?: throw SimulationException("Physics runtime is not running")
        if (!inside.isUsableForControl()) {
            throw SimulationException("Assimilated Demo climate is not valid")
        }
        request = req.copy(inside = inside, runtimeById = runtimeById)
        val built = createEngine(request!!, runtimeById)
        engine = built.first
        lastActiveUuids = built.second
        lastSnapshot = capture(wellMixed = false)
        return lastSnapshot!!
    }

    fun syncEquipment(
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val req = request ?: throw SimulationException("Physics runtime is not running")
        val current = engine ?: throw SimulationException("Physics runtime is not running")
        request = req.copy(instances = instances, runtimeById = runtimeById)
        rebuildPreservingField(current.gridState())
        lastSnapshot = capture(wellMixed = lastFrameWellMixed)
        return lastSnapshot!!
    }

    fun syncBoundary(
        weather: WeatherBoundary,
        fanPad: FanPadBoundaryInput?
    ): PhysicsFieldSnapshot {
        val req = request ?: throw SimulationException("Physics runtime is not running")
        val current = engine ?: throw SimulationException("Physics runtime is not running")
        request = req.copy(weather = weather, fanPad = fanPad)
        rebuildPreservingField(current.gridState())
        lastSnapshot = capture(wellMixed = lastFrameWellMixed)
        return lastSnapshot!!
    }

    private fun maybeRebuildSources() {
        val req = request ?: return
        val current = engine ?: return
        val specs = EquipmentSpecificationResolver.resolveAll(
            req.previewMode,
            req.instances,
            req.manufacturerSpecsById
        )
        val built = SimulationScenarioAdapter.build(
            mode = req.previewMode,
            lengthM = req.lengthM,
            widthM = req.widthM,
            heightM = req.heightM,
            cropRowCount = req.cropRowCount,
            canopyHeightM = req.canopyHeightM,
            inside = req.inside,
            weather = req.weather,
            instances = req.instances,
            runtimeById = req.runtimeById,
            thresholds = req.thresholds,
            specsById = specs,
            gridConfig = req.gridConfig,
            numerical = req.numerical,
            fanPad = req.fanPad
        )
        if (boundaryFingerprint(built) == lastBoundaryFingerprint) return
        rebuildPreservingField(current.gridState())
    }

    private fun rebuildPreservingField(from: GridState) {
        val req = request ?: return
        val built = createEngine(req, req.runtimeById)
        copyGridFields(from, built.first.gridState())
        engine = built.first
        lastActiveUuids = built.second
    }

    private fun createEngine(
        req: PhysicsRuntimeRequest,
        runtimeById: Map<String, EquipmentRuntimeState>
    ): Pair<GreenhouseSimulationEngine, Set<String>> {
        val specs = EquipmentSpecificationResolver.resolveAll(
            req.previewMode,
            req.instances,
            req.manufacturerSpecsById
        )
        val built = SimulationScenarioAdapter.build(
            mode = req.previewMode,
            lengthM = req.lengthM,
            widthM = req.widthM,
            heightM = req.heightM,
            cropRowCount = req.cropRowCount,
            canopyHeightM = req.canopyHeightM,
            inside = req.inside,
            weather = req.weather,
            instances = req.instances,
            runtimeById = runtimeById,
            thresholds = req.thresholds,
            specsById = specs,
            gridConfig = req.gridConfig,
            numerical = req.numerical,
            fanPad = req.fanPad
        )
        lastFanPad = FanPadBoundaryResolver.snapshotOf(built.fanPad)
        lastBoundaryFingerprint = boundaryFingerprint(built)
        val created = GreenhouseSimulationEngine.create(built.scenario)
        return created to built.sources.activeUuidSet()
    }

    private fun boundaryFingerprint(built: com.greenhands.app.heat.runtime.adapter.ScenarioBuildResult): String =
        listOf(
            built.sources.activeUuidSet().sorted().joinToString(","),
            built.scenario.intakes.size.toString(),
            built.scenario.intakeAirTemperatureC?.toString().orEmpty(),
            built.scenario.intakeAirHumidityRatioKgKg?.toString().orEmpty(),
            built.fanPad.wetPadActive.toString(),
            built.fanPad.wall?.name.orEmpty(),
            built.fanPad.quantitativeAvailable.toString()
        ).joinToString("|")

    private fun capture(wellMixed: Boolean): PhysicsFieldSnapshot {
        val current = engine ?: throw SimulationException("Physics runtime is not running")
        val req = request ?: throw SimulationException("Physics runtime is not running")
        val grid = current.gridState()
        if (!grid.hasFiniteFields()) {
            throw SimulationException("Non-finite field at t=$elapsedS s")
        }
        rejectNegativeHumidity(grid)
        val pressure = current.scenario.airProperties.pressurePa
        val rh = DoubleArray(grid.size)
        grid.forEachActive { idx, _, _, _ ->
            rh[idx] = Psychrometrics.displayedRelativeHumidityPercent(
                grid.temperatureC[idx],
                grid.humidityRatioKgKg[idx],
                pressure
            )
            if (!rh[idx].isFinite()) {
                throw SimulationException("Non-finite derived RH at index $idx")
            }
        }
        val specs = EquipmentSpecificationResolver.resolveAll(
            req.previewMode,
            req.instances,
            req.manufacturerSpecsById
        )
        val sources = PerUuidEquipmentBridge.buildActiveSources(
            instances = req.instances,
            runtimeById = req.runtimeById,
            specsById = specs,
            weather = req.weather
        )
        val meanT = grid.meanTemperatureC()
        val meanW = grid.meanHumidityRatioKgKg()
        val meanRh = Psychrometrics.displayedRelativeHumidityPercent(meanT, meanW, pressure)
        val padBlocksNumeric = lastFanPad.present && lastFanPad.enabled && !lastFanPad.quantitativeAvailable
        val probes = EquipmentDistanceProbes.sampleAll(
            grid = grid,
            instances = req.instances,
            pressurePa = pressure,
            exhaustQuantitativeAvailable = req.weather.permitsQuantitativeExhaust() && !padBlocksNumeric
        )
        val unavailable = sources.readiness.mapNotNull { rec ->
            rec.unavailableReason?.let { rec.equipmentUuid to it }
        }.toMap()
        val warnings = ArrayList<String>()
        if (req.previewMode == PreviewMode.DEMO) {
            warnings += PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER
        }
        warnings += PhysicsRuntimeCopy.UNCALIBRATED_PREDICTION
        warnings += PhysicsRuntimeCopy.VALIDATION_PENDING
        warnings += sources.warnings
        warnings += lastFanPad.missingInputs
        lastFanPad.layoutExplanation?.let { warnings += it }
        if (wellMixed && sources.exhaust.isNotEmpty()) {
            warnings += "Well-mixed ventilation estimate: no spatial exhaust plume."
        }
        return PhysicsFieldSnapshot(
            elapsedTimeS = elapsedS,
            grid = grid.geometry.toSnapshot(grid.activeCellCount),
            temperatureC = grid.temperatureC.copyOf(),
            humidityRatioKgKg = grid.humidityRatioKgKg.copyOf(),
            relativeHumidityPercent = rh,
            meanTemperatureC = meanT,
            meanRelativeHumidityPercent = meanRh,
            referenceTemperatureC = meanT,
            referenceRelativeHumidityPercent = meanRh,
            insideSource = req.inside.source,
            insideSourceLabel = req.inside.sourceLabel,
            weatherStatus = req.weather.status,
            weatherSourceLabel = req.weather.sourceLabel,
            weatherProvenance = req.weather.provenance,
            weatherKind = req.weather.kind,
            validationStatus = FieldValidationStatus.UNCALIBRATED,
            equipment = sources.readiness,
            runtimeById = req.runtimeById,
            warnings = warnings.distinct(),
            unavailableModuleReasons = unavailable,
            probes = probes,
            wellMixedVentilation = wellMixed,
            finiteValues = true,
            airPressurePa = pressure,
            fanPad = lastFanPad
        )
    }

    companion object {
        fun rejectInvalidNumbers(request: PhysicsRuntimeRequest) {
            val dims = listOf(request.lengthM, request.widthM, request.heightM)
            if (dims.any { !it.isFinite() || it <= 0.0 }) {
                throw SimulationException("Greenhouse dimensions must be finite and > 0 m")
            }
            if (request.inside.temperatureC?.isNaN() == true ||
                request.inside.relativeHumidityPercent?.isNaN() == true ||
                request.inside.temperatureC?.isInfinite() == true ||
                request.inside.relativeHumidityPercent?.isInfinite() == true
            ) {
                throw SimulationException("Inside climate must not contain NaN or infinity")
            }
        }

        fun copyGridFields(from: GridState, to: GridState) {
            require(from.size == to.size) { "Grid sizes must match to preserve the field" }
            from.temperatureC.copyInto(to.temperatureC)
            from.humidityRatioKgKg.copyInto(to.humidityRatioKgKg)
            from.velocityXMps.copyInto(to.velocityXMps)
            from.velocityYMps.copyInto(to.velocityYMps)
            from.velocityZMps.copyInto(to.velocityZMps)
            from.heatSourceWm3.copyInto(to.heatSourceWm3)
            from.moistureSourceKgM3s.copyInto(to.moistureSourceKgM3s)
        }

        fun rejectNegativeHumidity(grid: GridState) {
            grid.forEachActive { idx, _, _, _ ->
                val w = grid.humidityRatioKgKg[idx]
                if (!w.isFinite() || w < 0.0) {
                    throw SimulationException("Invalid humidity ratio $w kg/kg")
                }
                val t = grid.temperatureC[idx]
                if (!t.isFinite()) {
                    throw SimulationException("Invalid temperature $t °C")
                }
            }
        }
    }
}
