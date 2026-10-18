package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.FanPadFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ResolvedFanPadBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.physics.AirProperties
import com.greenhands.app.heat.simulation.physics.EvaporativePadOutlet
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Resolves the fan-and-pad inlet boundary for the runtime adapter.
 * Demo values are never substituted into My Greenhouse.
 */
object FanPadBoundaryResolver {
    fun resolve(
        mode: PreviewMode,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cellEdgeM: Double,
        instances: List<EquipmentInstance>,
        weather: WeatherBoundary,
        input: FanPadBoundaryInput?,
        air: AirProperties = AirProperties.chen2014Preview()
    ): ResolvedFanPadBoundary {
        if (input == null || !input.present || !input.enabled) {
            return ResolvedFanPadBoundary.inactive(
                if (input == null || !input.present) FanPadCopy.PAD_NOT_PRESENT else FanPadCopy.PAD_DISABLED
            )
        }
        val missing = ArrayList<String>()
        val layout = FanPadLayoutResolver.resolve(
            instances,
            lengthM,
            widthM,
            input.wallSelection
        )
        val padWall = layout.padWall
        if (layout.conflict || padWall == null) {
            val explanation = layout.explanation ?: FanPadCopy.CONFLICTING_EXHAUST_WALLS
            return unavailable(
                input = input,
                weather = weather,
                missing = if (layout.explanation != null) listOf(layout.explanation) else emptyList(),
                layoutExplanation = explanation,
                wall = padWall
            )
        }
        val width = input.widthM
        val height = input.heightM
        if (width == null || !width.isFinite() || width <= 0.0) {
            missing += if (height == null || !height.isFinite() || height <= 0.0) {
                FanPadCopy.MISSING_DIMENSIONS
            } else {
                FanPadCopy.MISSING_WIDTH
            }
        }
        if (height == null || !height.isFinite() || height <= 0.0) {
            if (width != null && width.isFinite() && width > 0.0) {
                missing += FanPadCopy.MISSING_HEIGHT
            }
        }
        if (input.wetPadActive) {
            val eta = input.efficiencyFraction
            when {
                eta == null -> missing += FanPadCopy.MISSING_EFFICIENCY
                !eta.isFinite() || eta <= 0.0 || eta > 1.0 -> missing += FanPadCopy.INVALID_EFFICIENCY
            }
        }
        if (!weather.permitsQuantitativeExhaust()) {
            missing += FanPadCopy.MISSING_WEATHER
        }
        if (mode == PreviewMode.MY_GREENHOUSE && input.sourceLabel == FanPadCopy.DEMO_OUTSIDE_WEATHER) {
            missing += FanPadCopy.MISSING_WEATHER
        }
        if (missing.isNotEmpty()) {
            return unavailable(
                input = input,
                weather = weather,
                missing = missing.distinct(),
                layoutExplanation = layout.explanation,
                wall = padWall
            )
        }
        val padWidth = width!!
        val padHeight = height!!
        val outlet = try {
            EvaporativePadOutlet.evaluate(
                outsideTemperatureC = weather.outsideTemperatureC!!,
                outsideRhPercent = weather.outsideHumidityPercent!!,
                pressurePa = air.pressurePa,
                specificHeatJkgK = air.specificHeatJkgK,
                efficiencyFraction = input.efficiencyFraction,
                wetPadActive = input.wetPadActive
            )
        } catch (_: SimulationException) {
            return unavailable(
                input = input,
                weather = weather,
                missing = listOf(FanPadCopy.MISSING_WEATHER),
                layoutExplanation = layout.explanation,
                wall = padWall
            )
        }
        val openings = FanPadIntakeFactory.openings(
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cellEdgeM = cellEdgeM,
            padWall = padWall,
            padWidthM = padWidth,
            padHeightM = padHeight
        )
        if (openings.isEmpty()) {
            return unavailable(
                input = input,
                weather = weather,
                missing = listOf(FanPadCopy.MISSING_DIMENSIONS),
                layoutExplanation = layout.explanation,
                wall = padWall
            )
        }
        return ResolvedFanPadBoundary(
            present = true,
            enabled = true,
            wetPadActive = input.wetPadActive,
            quantitativeAvailable = true,
            wall = padWall,
            widthM = padWidth,
            heightM = padHeight,
            areaM2 = padWidth * padHeight,
            efficiencyFraction = if (input.wetPadActive) input.efficiencyFraction else 0.0,
            outsideTemperatureC = weather.outsideTemperatureC,
            outsideRelativeHumidityPercent = weather.outsideHumidityPercent,
            outsideReadingTimestampMillis = weather.weatherDataTimeMillis ?: weather.fetchedAtMillis,
            outsideStatus = weather.status,
            outsideProvenance = weather.provenance,
            sourceLabel = input.sourceLabel,
            efficiencySourceLabel = input.efficiencySourceLabel,
            missingInputs = emptyList(),
            layoutExplanation = null,
            intakes = openings,
            intakeAirTemperatureC = outlet.temperatureC,
            intakeAirHumidityRatioKgKg = outlet.humidityRatioKgKg,
            padOutlet = outlet,
            measuredPadOutletTemperatureC = input.measuredPadOutletTemperatureC,
            measuredPadOutletRhPercent = input.measuredPadOutletRhPercent,
            airflowSpanM = FanPadLayoutResolver.airflowSpanM(padWall, lengthM, widthM)
        )
    }

    fun snapshotOf(resolved: ResolvedFanPadBoundary): FanPadFieldSnapshot = FanPadFieldSnapshot(
        present = resolved.present,
        enabled = resolved.enabled,
        wetPadActive = resolved.wetPadActive,
        quantitativeAvailable = resolved.quantitativeAvailable,
        wall = resolved.wall,
        sourceLabel = resolved.sourceLabel,
        efficiencySourceLabel = resolved.efficiencySourceLabel,
        missingInputs = resolved.missingInputs,
        layoutExplanation = resolved.layoutExplanation,
        padOutletTemperatureC = resolved.padOutlet?.temperatureC,
        padOutletRelativeHumidityPercent = resolved.padOutlet?.relativeHumidityPercent,
        padOutletHumidityRatioKgKg = resolved.padOutlet?.humidityRatioKgKg,
        outsideTemperatureC = resolved.outsideTemperatureC,
        outsideRelativeHumidityPercent = resolved.outsideRelativeHumidityPercent,
        inwardPhysics = resolved.wall?.let { FanPadLayoutResolver.inwardPhysics(it) },
        airflowSpanM = resolved.airflowSpanM
    )

    private fun unavailable(
        input: FanPadBoundaryInput,
        weather: WeatherBoundary,
        missing: List<String>,
        layoutExplanation: String?,
        wall: com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall?
    ): ResolvedFanPadBoundary = ResolvedFanPadBoundary(
        present = input.present,
        enabled = input.enabled,
        wetPadActive = input.wetPadActive,
        quantitativeAvailable = false,
        wall = wall,
        widthM = input.widthM,
        heightM = input.heightM,
        areaM2 = null,
        efficiencyFraction = input.efficiencyFraction,
        outsideTemperatureC = weather.outsideTemperatureC,
        outsideRelativeHumidityPercent = weather.outsideHumidityPercent,
        outsideReadingTimestampMillis = weather.weatherDataTimeMillis ?: weather.fetchedAtMillis,
        outsideStatus = weather.status,
        outsideProvenance = weather.provenance,
        sourceLabel = input.sourceLabel,
        efficiencySourceLabel = input.efficiencySourceLabel,
        missingInputs = missing,
        layoutExplanation = layoutExplanation,
        intakes = emptyList(),
        intakeAirTemperatureC = null,
        intakeAirHumidityRatioKgKg = null,
        padOutlet = null,
        measuredPadOutletTemperatureC = input.measuredPadOutletTemperatureC,
        measuredPadOutletRhPercent = input.measuredPadOutletRhPercent
    )
}
