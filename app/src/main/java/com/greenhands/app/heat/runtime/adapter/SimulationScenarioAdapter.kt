package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.ResolvedFanPadBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.model.AxisAlignedRegion
import com.greenhands.app.heat.simulation.model.ControlSource
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.NumericalSettings
import com.greenhands.app.heat.simulation.model.ParameterRecord
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.heat.simulation.model.SimulationScenario
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.AirProperties
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.model.PreviewMode

data class ScenarioBuildResult(
    val scenario: SimulationScenario,
    val sources: ActivePhysicsSources,
    val grid: AdaptiveGridPolicy.Selection,
    val warnings: List<String>,
    val fanPad: ResolvedFanPadBoundary = ResolvedFanPadBoundary.inactive()
)

/**
 * Thin adapter from product inputs to a frozen Phase 3A [SimulationScenario].
 * Does not invent missing weather or My Greenhouse ratings. Does not change Phase 3A types.
 *
 * Conflict C3: [SimulationScenario] still requires finite outdoor T/RH. When weather is not
 * available, exhaust sources are omitted and the outdoor fields are unused constructor
 * placeholders equal to the indoor state — they are not a weather boundary.
 */
object SimulationScenarioAdapter {
    fun build(
        mode: PreviewMode,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cropRowCount: Int,
        canopyHeightM: Double?,
        inside: InsideClimateReading,
        weather: WeatherBoundary,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        thresholds: EquipmentRuntimeThresholds,
        specsById: Map<String, EquipmentPhysicsSpecification>,
        gridConfig: AdaptiveGridPolicy.Config = AdaptiveGridPolicy.Config(),
        numerical: NumericalSettings = NumericalSettings(),
        fanPad: FanPadBoundaryInput? = null
    ): ScenarioBuildResult {
        val indoorT = inside.temperatureC
        val indoorRh = inside.relativeHumidityPercent
        if (indoorT == null || indoorRh == null || !inside.isUsableForControl()) {
            throw SimulationException("A valid inside temperature and humidity are required to build a scenario")
        }
        val grid = AdaptiveGridPolicy.select(lengthM, widthM, heightM, gridConfig)
        if (!grid.withinCellLimit) {
            throw SimulationException(
                "Selected grid ${grid.cellCount} cells exceeds the configured maximum ${gridConfig.maximumCellCount}"
            )
        }
        val geometry = UserPhysicsCoordinateAdapter.geometry(lengthM, widthM, heightM, grid.cellEdgeM)
        val sources = PerUuidEquipmentBridge.buildActiveSources(
            instances = instances,
            runtimeById = runtimeById,
            specsById = specsById,
            weather = weather
        )
        val exhaustWeatherOk = weather.permitsQuantitativeExhaust()
        val outsideT: Double
        val outsideRh: Double
        val warnings = sources.warnings.toMutableList()
        if (exhaustWeatherOk) {
            outsideT = weather.outsideTemperatureC!!
            outsideRh = weather.outsideHumidityPercent!!
        } else {
            outsideT = indoorT
            outsideRh = indoorRh
            if (instances.any { !it.isGhost && it.type == EquipmentType.EXHAUST_FAN }) {
                warnings += PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
            }
        }
        val circulation = requireNotNull(thresholds.circulation) {
            "Circulation thresholds are required to construct a Phase 3A scenario"
        }
        val exhaust = thresholds.exhaust ?: HeatFormulas.exhaustFromCirculation(circulation)
        val cropZone = canopyHeightM?.takeIf { it > 0.0 && it.isFinite() }?.let { canopy ->
            AxisAlignedRegion(
                minM = Vec3(0.0, 0.0, 0.0),
                maxM = Vec3(lengthM, widthM, canopy.coerceAtMost(heightM))
            )
        }
        val records = ArrayList<ParameterRecord>()
        records += ParameterRecord(
            "effectiveThermalDiffusivityM2s",
            UncalibratedPhase3AProfileSettings.EFFECTIVE_THERMAL_DIFFUSIVITY_M2S.toString(),
            "m²/s",
            UncalibratedPhase3AProfileSettings.PROVENANCE,
            UncalibratedPhase3AProfileSettings.NOTE
        )
        records += ParameterRecord(
            "effectiveMoistureDiffusivityM2s",
            UncalibratedPhase3AProfileSettings.EFFECTIVE_MOISTURE_DIFFUSIVITY_M2S.toString(),
            "m²/s",
            UncalibratedPhase3AProfileSettings.PROVENANCE,
            UncalibratedPhase3AProfileSettings.NOTE
        )
        records += ParameterRecord(
            "insideClimate",
            "$indoorT °C / $indoorRh % RH",
            "",
            inside.provenance,
            inside.sourceLabel
        )
        records += ParameterRecord(
            "weatherBoundary",
            weather.status.name,
            "",
            weather.parameterRecordProvenance(),
            weather.sourceLabel
        )
        records += ParameterRecord(
            "previewMode",
            mode.name,
            "",
            ProvenanceClass.PROJECT_CONTROL_RULE,
            "Demo ratings are never applied in My Greenhouse"
        )
        val resolvedPad = FanPadBoundaryResolver.resolve(
            mode = mode,
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cellEdgeM = grid.cellEdgeM,
            instances = instances,
            weather = weather,
            input = fanPad
        )
        if (resolvedPad.quantitativeAvailable) {
            records += ParameterRecord(
                "fanPadIntake",
                "${resolvedPad.intakeAirTemperatureC} °C / ${resolvedPad.padOutlet?.humidityRatioKgKg} kg/kg",
                "",
                ProvenanceClass.PHYSICAL_RELATION,
                resolvedPad.sourceLabel
            )
        }
        resolvedPad.missingInputs.forEach { reason ->
            warnings += reason
        }
        resolvedPad.layoutExplanation?.let { warnings += it }
        @Suppress("UNUSED_VARIABLE")
        val ignoredCropRows = cropRowCount
        val scenario = SimulationScenario(
            geometry = geometry,
            airProperties = AirProperties.chen2014Preview(),
            initialIndoorTemperatureC = indoorT,
            initialIndoorRhPercent = indoorRh,
            outsideTemperatureC = outsideT,
            outsideRhPercent = outsideRh,
            circulationFans = sources.circulation,
            exhaustFans = sources.exhaust,
            intakes = if (resolvedPad.quantitativeAvailable) resolvedPad.intakes else emptyList(),
            intakeAirTemperatureC = if (resolvedPad.quantitativeAvailable) {
                resolvedPad.intakeAirTemperatureC
            } else {
                null
            },
            intakeAirHumidityRatioKgKg = if (resolvedPad.quantitativeAvailable) {
                resolvedPad.intakeAirHumidityRatioKgKg
            } else {
                null
            },
            foggers = sources.foggers,
            cropZone = cropZone,
            controlSensorPositionM = inside.sensorPositionM?.let {
                UserPhysicsCoordinateAdapter.positionToPhysics(it)
            },
            envelopeUaWPerK = null,
            netSolarAndInternalHeatLoadW = null,
            effectiveThermalDiffusivityM2s = UncalibratedPhase3AProfileSettings.EFFECTIVE_THERMAL_DIFFUSIVITY_M2S,
            effectiveMoistureDiffusivityM2s = UncalibratedPhase3AProfileSettings.EFFECTIVE_MOISTURE_DIFFUSIVITY_M2S,
            circulationThresholds = circulation,
            exhaustThresholds = exhaust,
            foggerThresholds = thresholds.fogger,
            controlSource = if (inside.sensorPositionM != null) {
                ControlSource.CONTROL_SENSOR
            } else {
                ControlSource.GREENHOUSE_MEAN
            },
            equipmentDriveMode = EquipmentDriveMode.FORCED,
            forcedEquipment = ForcedEquipmentState(
                circulationOn = sources.circulation.isNotEmpty(),
                exhaustOn = sources.exhaust.isNotEmpty(),
                foggerOn = sources.foggers.isNotEmpty()
            ),
            numerical = numerical,
            parameterRecords = records
        )
        warnings += grid.note
        return ScenarioBuildResult(scenario, sources, grid, warnings.distinct(), resolvedPad)
    }
}
