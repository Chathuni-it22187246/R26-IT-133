package com.greenhands.app.equipment.future

/**
 * Design-only contracts for later phases. No live networking, ARCore, or heat animation.
 */

enum class EnvironmentDataSourceKind {
    PREVIEW,
    LIVE,
    OFFLINE_DELAYED
}

data class FutureSensorReading(
    val timestampMillis: Long,
    val sensorId: String,
    val temperatureC: Double,
    val relativeHumidityPercent: Double,
    val positionXM: Double,
    val positionYM: Double,
    val positionZM: Double,
    val qualityNote: String,
    val calibrationStatus: String,
    val stale: Boolean,
    val valid: Boolean,
    val sourceKind: EnvironmentDataSourceKind
)

/**
 * One temperature/RH sensor measures only its own location. It cannot validate
 * the entire spatial heat field. UI must distinguish measured vs predicted.
 */
interface EnvironmentDataSource {
    val kind: EnvironmentDataSourceKind
    fun currentReading(): FutureSensorReading?
}

class PreviewEnvironmentDataSource : EnvironmentDataSource {
    override val kind: EnvironmentDataSourceKind = EnvironmentDataSourceKind.PREVIEW
    override fun currentReading(): FutureSensorReading? = null
}

enum class FutureVisualisationField {
    TEMPERATURE,
    HUMIDITY_VPD,
    AIRFLOW
}

/**
 * Future heat/humidity visualisation must sample Phase 3A predicted grid values.
 * Distance-from-equipment colouring and decorative timed rings are not allowed.
 *
 * Circulation redistributes internal air and does not create cooling energy.
 * Exhaust exchanges indoor air with outside air.
 * Foggers add moisture and remove sensible heat only through evaporation.
 */
object FutureHeatVisualisationContract {
    const val DRIVER = "Phase 3A predicted grid (not inverse-distance or timed animation)"
    const val CIRCULATION_PATTERN = "Elongated internal mixing plume/loop"
    const val EXHAUST_PATTERN = "Directional path from make-up inlet through the crop to the exhaust wall"
    const val FOGGER_PATTERN = "Coupled cooling and humidity plume beneath and downstream of nozzles"
}

data class TemperatureLegendBreak(
    val minC: Double,
    val maxC: Double,
    val colourName: String,
    val meaning: String
)

/**
 * Target-relative temperature legend. Blue is not used for "normal".
 * Breakpoints come from crop/stage evidence, Topt, CDP, CON, ESP, EON and sensor uncertainty.
 * Do not globally hard-code 25 °C ± 0.5 °C.
 */
object FutureColourPolicy {
    fun temperatureLegend(
        cropMinC: Double,
        cropMaxC: Double,
        toptC: Double,
        cdpC: Double,
        conC: Double,
        espC: Double,
        eonC: Double,
        sensorUncertaintyC: Double?
    ): List<TemperatureLegendBreak> {
        val pad = sensorUncertaintyC ?: 0.0
        val orangeFrom = minOf(conC, espC)
        return listOf(
            TemperatureLegendBreak(Double.NEGATIVE_INFINITY, cdpC - pad, "Blue", "Critically below target"),
            TemperatureLegendBreak(cdpC - pad, cropMinC, "Cyan", "Below target"),
            TemperatureLegendBreak(cropMinC, cropMaxC, "Green", "Inside crop target range (Topt ${toptC} °C)"),
            TemperatureLegendBreak(cropMaxC, orangeFrom, "Yellow", "Above target / warm"),
            TemperatureLegendBreak(orangeFrom, eonC, "Orange", "Hot; stronger control approaching (ESP ${espC} °C)"),
            TemperatureLegendBreak(eonC, Double.POSITIVE_INFINITY, "Red", "Critical high temperature")
        )
    }

    fun humidityLegend(
        fonPercent: Double,
        fspPercent: Double,
        fdpPercent: Double
    ): List<TemperatureLegendBreak> = listOf(
        TemperatureLegendBreak(Double.NEGATIVE_INFINITY, fonPercent, "Red/Orange", "Dry / high VPD"),
        TemperatureLegendBreak(fonPercent, fspPercent, "Yellow", "Approaching dry threshold"),
        TemperatureLegendBreak(fspPercent, fdpPercent, "Green", "Target RH/VPD"),
        TemperatureLegendBreak(fdpPercent, fdpPercent + 8.0, "Cyan", "Humid"),
        TemperatureLegendBreak(fdpPercent + 8.0, Double.POSITIVE_INFINITY, "Blue", "Excessively humid / low VPD")
    )
}

/**
 * Future simulation interaction (not implemented in Phase 3B.2):
 * explicit Start Simulation / Stop Simulation. Placement palette collapses.
 * Equipment cannot move accidentally. Tapping equipment opens details only.
 * Reset View affects only the camera. Double-tap is not the start action.
 */
object FutureSimulationInteractionContract {
    const val START_LABEL = "Start Simulation"
    const val STOP_LABEL = "Stop Simulation"
}
