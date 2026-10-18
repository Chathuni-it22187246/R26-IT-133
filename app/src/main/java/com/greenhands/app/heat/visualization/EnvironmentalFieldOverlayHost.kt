package com.greenhands.app.heat.visualization

/**
 * Stable AR overlay host contract. Visibility is data, not conditional
 * destruction of the Compose slot. The legend composable stays in the tree
 * across Detecting → Placed; [legendModel] only swaps the model.
 */
object EnvironmentalFieldOverlayHost {
    const val COMPOSE_SLOT_KEY = "ar_environmental_field_overlay"

    fun legendModel(
        placed: Boolean,
        model: EnvironmentalFieldLegendModel
    ): EnvironmentalFieldLegendModel = if (placed) model else EnvironmentalFieldLegendModel.HIDDEN
}
