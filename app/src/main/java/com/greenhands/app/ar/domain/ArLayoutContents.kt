package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.render.EquipmentSceneMapper
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * AR shows the current in-memory layout only. An empty layout stays empty —
 * no Demo profile or suggestion engine is applied here.
 */
object ArLayoutContents {
    fun visibleEquipment(instances: List<EquipmentInstance>): List<EquipmentInstance> =
        instances.filter { !it.isGhost }

    fun visibleEquipment(layout: EquipmentLayout?): List<EquipmentInstance> {
        if (layout == null) return emptyList()
        if (!layout.shouldRestoreAsPlaced()) return emptyList()
        return visibleEquipment(layout.instances)
    }

    fun placements(
        instances: List<EquipmentInstance>,
        layout: SceneLayout
    ): List<EquipmentScenePlacement> =
        EquipmentSceneMapper.placements(visibleEquipment(instances), layout)

    fun preservesIdsAndCoordinates(source: List<EquipmentInstance>, shown: List<EquipmentInstance>): Boolean {
        if (source.size != shown.size) return false
        return source.zip(shown).all { (a, b) ->
            a.id == b.id &&
                a.displayLabel() == b.displayLabel() &&
                a.positionM.xM == b.positionM.xM &&
                a.positionM.yM == b.positionM.yM &&
                a.positionM.zM == b.positionM.zM &&
                a.yawDeg == b.yawDeg
        }
    }
}
