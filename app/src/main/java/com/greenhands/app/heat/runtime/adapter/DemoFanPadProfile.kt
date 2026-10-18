package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Named Demo-only fan-and-pad profile. Never copied into My Greenhouse.
 *
 * Pad area covers the Demo envelope end wall opposite the exhaust. Efficiency is a
 * labelled research-guided illustration, not a manufacturer rating (UF/IFAS fan-and-pad
 * literature typically reports roughly 80 % for a clean pad; that is not universal).
 */
data class DemoFanPadProfile(
    val enabled: Boolean = true,
    val wetPadActive: Boolean = true,
    val wallSelection: FanPadWallSelection = FanPadWallSelection.AUTO,
    val efficiencyFraction: Double = ILLUSTRATIVE_EFFICIENCY,
    val outsideTemperatureC: Double = DEFAULT_OUTSIDE_TEMPERATURE_C,
    val outsideRelativeHumidityPercent: Double = DEFAULT_OUTSIDE_RH_PERCENT
) {
    fun toBoundaryInput(widthM: Double, heightM: Double): FanPadBoundaryInput =
        FanPadBoundaryInput(
            present = true,
            enabled = enabled,
            wetPadActive = wetPadActive,
            wallSelection = wallSelection,
            widthM = widthM,
            heightM = heightM,
            efficiencyFraction = efficiencyFraction,
            sourceLabel = FanPadCopy.DEMO_OUTSIDE_WEATHER,
            efficiencySourceLabel = FanPadCopy.DEMO_PAD_EFFICIENCY
        )

    companion object {
        const val ILLUSTRATIVE_EFFICIENCY = 0.80
        const val DEFAULT_OUTSIDE_TEMPERATURE_C = 35.0
        const val DEFAULT_OUTSIDE_RH_PERCENT = 40.0
        const val EFFICIENCY_ASSUMPTION = FanPadCopy.DEMO_EFFICIENCY_ASSUMPTION
        val DEFAULT = DemoFanPadProfile()

        fun neverForMyGreenhouse(mode: PreviewMode): DemoFanPadProfile? =
            if (mode == PreviewMode.DEMO) DEFAULT else null
    }
}
