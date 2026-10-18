package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.control.CoolingSessionState
import com.greenhands.app.heat.simulation.control.CoolingSessionTracker
import com.greenhands.app.heat.simulation.control.EquipmentCommand
import com.greenhands.app.heat.simulation.control.EquipmentCommandSource
import com.greenhands.app.heat.simulation.control.EquipmentController
import com.greenhands.app.heat.simulation.model.ControlSource
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.SaturationCorrectionReport
import com.greenhands.app.heat.simulation.model.SimulationDiagnostics
import com.greenhands.app.heat.simulation.model.SimulationFrame
import com.greenhands.app.heat.simulation.model.SimulationScenario
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import kotlin.math.abs

/**
 * Synchronous deterministic reduced-order engine (PHYSICS_MODEL.md Phase 3A).
 * No Android, Compose, DataStore, ARCore or Firebase types.
 */
class GreenhouseSimulationEngine private constructor(
    val scenario: SimulationScenario,
    private val grid: GridState,
    private val controller: EquipmentController,
    private val session: CoolingSessionTracker,
    private val solver: ScalarTransportSolver,
    private var simulatedTimeS: Double,
    private var lastCommand: EquipmentCommand
) {
    private val air = scenario.airProperties
    private val pressurePa = air.pressurePa
    private val outdoorHumidityRatioKgKg: Double = Psychrometrics.humidityRatioKgKg(
        scenario.outsideTemperatureC,
        scenario.outsideRhPercent,
        pressurePa
    )

    fun simulatedTimeS(): Double = simulatedTimeS
    fun sessionState(): CoolingSessionState = session.state
    fun gridState(): GridState = grid
    fun lastCommand(): EquipmentCommand = lastCommand

    fun copyForForecast(): GreenhouseSimulationEngine {
        val clonedGrid = grid.deepCopy()
        return GreenhouseSimulationEngine(
            scenario = scenario,
            grid = clonedGrid,
            controller = controller.copy(),
            session = session.copy(),
            solver = ScalarTransportSolver(
                grid = clonedGrid,
                air = air,
                thermalDiffusivityM2s = scenario.effectiveThermalDiffusivityM2s,
                moistureDiffusivityM2s = scenario.effectiveMoistureDiffusivityM2s
            ),
            simulatedTimeS = simulatedTimeS,
            lastCommand = lastCommand
        )
    }

    fun cancel() {
        session.cancel()
    }

    fun step(outerDtS: Double = scenario.numerical.requestedTimeStepS): SimulationFrame {
        if (!outerDtS.isFinite() || outerDtS <= 0.0) {
            throw SimulationException("Outer time step must be finite and > 0 s, got $outerDtS")
        }
        val control = observeControl()
        val command = nextCommand(control.temperatureC, control.rhPercent)
        lastCommand = command
        applyVelocity(command)
        val stableDt = solver.stableTimeStepS(
            scenario.numerical.cflSafetyFactor,
            scenario.numerical.diffusionSafetyFactor
        )
        val substeps = solver.substepCount(
            outerDtS,
            stableDt,
            scenario.numerical.maxSubstepsPerOuterStep
        )
        val dtSub = outerDtS / substeps
        var condensedKg = 0.0
        var satCells = 0
        var foggerLimitedKgS = 0.0
        var exhaustReport = ExhaustFlowReport(0.0, 0.0, scenario.usesWellMixedVentilation())
        var sub = 0
        while (sub < substeps) {
            grid.zeroSources()
            applyEnvelopeAndSolar()
            val fog = FoggerSource.apply(
                grid = grid,
                air = air,
                foggers = scenario.foggers,
                active = command.foggerOn,
                dtS = dtSub,
                pressurePa = pressurePa
            )
            foggerLimitedKgS += fog.limitedBySaturationKgS
            val qEx = ExhaustBoundary.totalExhaustVolumeFlowM3s(scenario.exhaustFans, command.exhaustOn)
            val inletTemperatureC = scenario.intakeAirTemperatureC ?: scenario.outsideTemperatureC
            val inletHumidityRatioKgKg = scenario.intakeAirHumidityRatioKgKg ?: outdoorHumidityRatioKgKg
            exhaustReport = if (scenario.intakes.isNotEmpty()) {
                ExhaustBoundary.applySpatialIntakeSources(
                    grid = grid,
                    air = air,
                    intakes = scenario.intakes,
                    exhaustVolumeFlowM3s = qEx,
                    outsideTemperatureC = inletTemperatureC,
                    outsideHumidityRatioKgKg = inletHumidityRatioKgKg
                )
            } else {
                ExhaustFlowReport(qEx, qEx, wellMixed = true)
            }
            solver.step(dtSub)
            if (scenario.usesWellMixedVentilation() && command.exhaustOn) {
                exhaustReport = ExhaustBoundary.applyWellMixedExact(
                    grid = grid,
                    volumeFlowM3s = qEx,
                    outsideTemperatureC = scenario.outsideTemperatureC,
                    outsideHumidityRatioKgKg = outdoorHumidityRatioKgKg,
                    dtS = dtSub
                )
            }
            val sat = solver.correctSupersaturation(pressurePa)
            condensedKg += sat.condensedVapourKg
            satCells += sat.affectedCellCount
            sub++
        }
        simulatedTimeS += outerDtS
        if (!grid.hasFiniteFields()) {
            throw SimulationException("Non-finite temperature, humidity ratio or velocity after step at t=$simulatedTimeS s")
        }
        val after = observeAll()
        val inBand = inConfiguredBand(after.cropTemperatureC, after.cropRhPercent)
        val completionEvent = session.update(outerDtS, command.anyOn, inBand)
        val probes = selectedCirculationFan()?.let { fan ->
            DistanceProbeSampler.sampleFanAxis(grid, fan, pressurePa)
        } ?: emptyList()
        val notes = diagnosticNotes()
        val diagnostics = SimulationDiagnostics(
            simulatedTimeS = simulatedTimeS,
            outerTimeStepS = outerDtS,
            stableTimeStepS = stableDt,
            substepCount = substeps,
            finiteValues = true,
            wellMixedVentilation = exhaustReport.wellMixed && scenario.usesWellMixedVentilation(),
            intakeVolumeFlowM3s = exhaustReport.intakeVolumeFlowM3s,
            exhaustVolumeFlowM3s = exhaustReport.exhaustVolumeFlowM3s,
            volumeFlowBalanced = exhaustReport.balanced,
            velocityCapCellCount = lastVelocityCapCount,
            maxAbsVelocityMps = VelocityField.maxSpeedMps(grid),
            advectionMeanCorrectionK = solver.lastAdvectionMeanCorrectionK,
            advectionMeanCorrectionKgKg = solver.lastAdvectionMeanCorrectionKgKg,
            saturation = SaturationCorrectionReport(
                condensedVapourKg = condensedKg,
                affectedCellCount = satCells,
                foggerEvaporationLimitedKgS = foggerLimitedKgS
            ),
            wholeAirMeanTemperatureC = after.greenhouseTemperatureC,
            wholeAirMeanRhPercent = after.greenhouseRhPercent,
            cropZoneMeanTemperatureC = after.cropTemperatureC,
            cropZoneMeanRhPercent = after.cropRhPercent,
            controlTemperatureC = after.controlTemperatureC,
            controlRhPercent = after.controlRhPercent,
            controlSource = scenario.controlSource,
            notes = notes,
            parameterRecords = scenario.parameterRecords
        )
        return SimulationFrame(
            simulatedTimeS = simulatedTimeS,
            command = command,
            sessionState = session.state,
            elapsedCoolingS = session.elapsedCoolingS,
            stabilityElapsedS = session.stabilityElapsedS,
            completionEvent = completionEvent,
            probes = probes,
            eta = null,
            diagnostics = diagnostics
        )
    }

    private var lastVelocityCapCount: Int = 0

    private fun nextCommand(temperatureC: Double, rhPercent: Double?): EquipmentCommand {
        return if (scenario.equipmentDriveMode == EquipmentDriveMode.FORCED) {
            val forced = scenario.forcedEquipment
            EquipmentCommand(
                circulationOn = forced.circulationOn,
                exhaustOn = forced.exhaustOn,
                foggerOn = forced.foggerOn,
                circulationSpeedFraction = if (forced.circulationOn) {
                    scenario.circulationFans.firstOrNull()?.speedFraction ?: 1.0
                } else {
                    0.0
                },
                exhaustSpeedFraction = if (forced.exhaustOn) {
                    scenario.exhaustFans.firstOrNull()?.speedFraction ?: 1.0
                } else {
                    0.0
                },
                foggerSpeedFraction = if (forced.foggerOn) 1.0 else 0.0,
                source = EquipmentCommandSource.FORCED
            )
        } else {
            controller.update(temperatureC, rhPercent)
        }
    }

    private fun applyVelocity(command: EquipmentCommand) {
        val override = scenario.prescribedVelocityOverrideMps
        if (override != null) {
            VelocityField.applyUniformOverride(grid, override)
            lastVelocityCapCount = 0
            return
        }
        val fans = if (command.circulationOn) {
            scenario.circulationFans.map { it to command.circulationSpeedFraction }
        } else {
            emptyList()
        }
        lastVelocityCapCount = CirculationFanField.applyToGrid(grid, fans)
    }

    private fun applyEnvelopeAndSolar() {
        val ua = scenario.envelopeUaWPerK
        if (ua != null && ua > 0.0) {
            val tMean = grid.meanTemperatureC()
            val qTotalW = ua * (scenario.outsideTemperatureC - tMean)
            val areas = DoubleArray(grid.size)
            var areaSum = 0.0
            grid.forEachActive { idx, i, j, k ->
                var area = 0.0
                if (!grid.isActive(i - 1, j, k)) area += grid.dyM * grid.dzM
                if (!grid.isActive(i + 1, j, k)) area += grid.dyM * grid.dzM
                if (!grid.isActive(i, j - 1, k)) area += grid.dxM * grid.dzM
                if (!grid.isActive(i, j + 1, k)) area += grid.dxM * grid.dzM
                if (!grid.isActive(i, j, k - 1)) area += grid.dxM * grid.dyM
                if (!grid.isActive(i, j, k + 1)) area += grid.dxM * grid.dyM
                areas[idx] = area
                areaSum += area
            }
            if (areaSum > 0.0) {
                grid.forEachActive { idx, _, _, _ ->
                    val qCellW = qTotalW * areas[idx] / areaSum
                    grid.heatSourceWm3[idx] += qCellW / grid.cellVolumeM3
                }
            }
        }
        val solarW = scenario.netSolarAndInternalHeatLoadW
        if (solarW != null && abs(solarW) > 0.0) {
            val qVol = solarW / grid.totalActiveVolumeM3()
            grid.forEachActive { idx, _, _, _ ->
                grid.heatSourceWm3[idx] += qVol
            }
        }
    }

    private fun inConfiguredBand(temperatureC: Double, rhPercent: Double): Boolean {
        val circ = scenario.circulationThresholds
        val inTemp = temperatureC >= circ.cdp && temperatureC <= circ.con
        val fog = scenario.foggerThresholds
        val inRh = if (fog == null) {
            true
        } else {
            rhPercent >= fog.fon && rhPercent <= fog.fdp
        }
        return inTemp && inRh
    }

    private fun selectedCirculationFan() = scenario.circulationFans.firstOrNull()

    private data class Observation(
        val greenhouseTemperatureC: Double,
        val greenhouseRhPercent: Double,
        val cropTemperatureC: Double,
        val cropRhPercent: Double,
        val controlTemperatureC: Double,
        val controlRhPercent: Double
    )

    private fun observeAll(): Observation {
        val tGh = grid.meanTemperatureC()
        val wGh = grid.meanHumidityRatioKgKg()
        val rhGh = Psychrometrics.displayedRelativeHumidityPercent(tGh, wGh, pressurePa)
        val tCrop = grid.meanTemperatureC(scenario.cropZone)
        val wCrop = grid.meanHumidityRatioKgKg(scenario.cropZone)
        val rhCrop = Psychrometrics.displayedRelativeHumidityPercent(tCrop, wCrop, pressurePa)
        val control = observeControl()
        return Observation(tGh, rhGh, tCrop, rhCrop, control.temperatureC, control.rhPercent)
    }

    private data class ControlObservation(val temperatureC: Double, val rhPercent: Double)

    private fun observeControl(): ControlObservation {
        return when (scenario.controlSource) {
            ControlSource.GREENHOUSE_MEAN -> {
                val t = grid.meanTemperatureC()
                val w = grid.meanHumidityRatioKgKg()
                ControlObservation(t, Psychrometrics.displayedRelativeHumidityPercent(t, w, pressurePa))
            }
            ControlSource.CROP_ZONE_MEAN -> {
                val t = grid.meanTemperatureC(scenario.cropZone)
                val w = grid.meanHumidityRatioKgKg(scenario.cropZone)
                ControlObservation(t, Psychrometrics.displayedRelativeHumidityPercent(t, w, pressurePa))
            }
            ControlSource.CONTROL_SENSOR -> {
                val point = scenario.controlSensorPositionM
                    ?: throw SimulationException("CONTROL_SENSOR requires controlSensorPositionM")
                val t = grid.interpolateScalar(grid.temperatureC, point)
                val w = grid.interpolateScalar(grid.humidityRatioKgKg, point)
                if (t == null || w == null) {
                    val tMean = grid.meanTemperatureC()
                    val wMean = grid.meanHumidityRatioKgKg()
                    ControlObservation(
                        tMean,
                        Psychrometrics.displayedRelativeHumidityPercent(tMean, wMean, pressurePa)
                    )
                } else {
                    ControlObservation(
                        t,
                        Psychrometrics.displayedRelativeHumidityPercent(t, w, pressurePa)
                    )
                }
            }
        }
    }

    private fun diagnosticNotes(): List<String> {
        val notes = ArrayList<String>()
        notes.add("Reduced-order engineering model; not CFD and not validated.")
        if (scenario.usesWellMixedVentilation()) {
            notes.add("Well-mixed ventilation estimate: no spatial exhaust plume.")
        }
        if (scenario.canopyDragCoefficient != null || scenario.leafAreaIndex != null) {
            notes.add("Canopy drag/LAI accepted as scenario input but not applied in Phase 3A.")
        }
        if (scenario.groundTemperatureC != null) {
            notes.add("Ground temperature accepted as scenario input but not applied in Phase 3A.")
        }
        if (scenario.netSolarAndInternalHeatLoadW == null) {
            notes.add("Heat load not calibrated.")
        }
        if (kotlin.math.abs(solver.lastAdvectionMeanCorrectionK) > 1e-12) {
            notes.add(
                "Advection mean-restoring correction ${solver.lastAdvectionMeanCorrectionK} K " +
                    "applied because the parameterized jet is not divergence-free."
            )
        }
        return notes
    }

    companion object {
        fun create(scenario: SimulationScenario): GreenhouseSimulationEngine {
            val omega0 = Psychrometrics.humidityRatioKgKg(
                scenario.initialIndoorTemperatureC,
                scenario.initialIndoorRhPercent,
                scenario.airProperties.pressurePa
            )
            val grid = GridState.build(
                geometry = scenario.geometry,
                initialTemperatureC = scenario.initialIndoorTemperatureC,
                initialHumidityRatioKgKg = omega0
            )
            val controller = EquipmentController(
                circulation = scenario.circulationThresholds,
                exhaust = scenario.exhaustThresholds,
                fogger = scenario.foggerThresholds,
                controlSource = scenario.controlSource,
                circulationSpeedFraction = scenario.circulationFans.firstOrNull()?.speedFraction ?: 1.0,
                exhaustSpeedFraction = scenario.exhaustFans.firstOrNull()?.speedFraction ?: 1.0
            )
            val solver = ScalarTransportSolver(
                grid = grid,
                air = scenario.airProperties,
                thermalDiffusivityM2s = scenario.effectiveThermalDiffusivityM2s,
                moistureDiffusivityM2s = scenario.effectiveMoistureDiffusivityM2s
            )
            return GreenhouseSimulationEngine(
                scenario = scenario,
                grid = grid,
                controller = controller,
                session = CoolingSessionTracker(scenario.numerical.stabilizationDurationS),
                solver = solver,
                simulatedTimeS = 0.0,
                lastCommand = controller.command()
            )
        }
    }
}
