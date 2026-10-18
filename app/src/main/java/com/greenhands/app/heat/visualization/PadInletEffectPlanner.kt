package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.adapter.FanPadLayoutResolver
import com.greenhands.app.heat.runtime.contracts.FanPadFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ResolvedFanPadBoundary
import kotlin.math.min

/**
 * Builds the pad panel and inlet-stream visualization from the accepted field.
 * The physical panel is a greenhouse boundary and does not require Visualization
 * to be running. Does not add a renderer-side \(T=T_0-kx\) decay.
 */
object PadInletEffectPlanner {
    fun plan(
        visualizationRunning: Boolean,
        exhaustOn: Boolean,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        thresholds: EquipmentRuntimeThresholds,
        targetTemperatureC: Double?,
        colorSnapshot: PhysicsFieldSnapshot? = snapshot,
        resolved: ResolvedFanPadBoundary? = null,
        showActiveEffects: Boolean = true
    ): PadInletEffectFrame {
        val pad = resolved?.let { FanPadBoundaryResolver.snapshotOf(it) }
            ?: snapshot?.fanPad
            ?: FanPadFieldSnapshot.NONE
        if (!pad.present || !pad.enabled) return PadInletEffectFrame.EMPTY
        val wall = pad.wall ?: resolved?.wall
        if (wall == null) {
            return PadInletEffectFrame(
                fanPad = pad,
                limitation = pad.layoutExplanation ?: pad.missingInputs.firstOrNull()
            )
        }
        val wallRun = FanPadLayoutResolver.wallRunM(wall, lengthM, widthM)
        val configuredWidth = (resolved?.widthM ?: padWidthFallback(wallRun))
            .coerceAtLeast(0.0)
        val configuredHeight = (resolved?.heightM ?: heightM)
            .coerceAtLeast(0.0)
        val (panelWidth, panelHeight) = PadPanelLayout.sizeInsideWall(
            configuredWidth,
            configuredHeight,
            wallRun,
            heightM
        )
        if (panelWidth <= 0.0 || panelHeight <= 0.0) {
            return PadInletEffectFrame(
                fanPad = pad,
                limitation = pad.layoutExplanation ?: pad.missingInputs.firstOrNull()
            )
        }
        val panel = panel(wall, lengthM, widthM, panelWidth, panelHeight, pad)
        val exhaustRunning = exhaustOn || anyExhaustOn(instances, runtimeById)
        val quantitative = resolved?.quantitativeAvailable == true || pad.quantitativeAvailable
        val wetCoolInlet = visualizationRunning &&
            showActiveEffects &&
            exhaustRunning &&
            pad.wetPadActive &&
            quantitative
        val stream = if (wetCoolInlet) {
            val (dx, dy, dz) = FanPadLayoutResolver.inwardDomain(wall)
            val origin = panel.originM
            val span = FanPadLayoutResolver.airflowSpanM(wall, lengthM, widthM)
            val path = min(PadInletVisual.PATH_M, span * 0.85)
            PadInletStreamSpec(
                originM = origin,
                yawDeg = yawForInward(wall),
                inwardDomainDx = dx,
                inwardDomainDy = dy,
                inwardDomainDz = dz,
                pathM = path,
                arrowCount = PadInletVisual.MAX_INLET_ARROWS,
                wetPadActive = pad.wetPadActive,
                claimsEvaporativeCooling = true,
                targetTemperatureC = targetTemperatureC,
                cdpC = thresholds.circulation?.cdp,
                conC = thresholds.circulation?.con,
                eonC = thresholds.exhaust?.eon
            )
        } else {
            null
        }
        return PadInletEffectFrame(
            panel = panel,
            stream = stream,
            snapshot = snapshot?.takeIf { it.finiteValues },
            colorSnapshot = (colorSnapshot ?: snapshot)?.takeIf { it.finiteValues },
            fanPad = pad,
            limitation = pad.layoutExplanation ?: pad.missingInputs.firstOrNull()
        )
    }

    fun inletTravelsPadTowardExhaust(wall: GreenhouseEndWall): Boolean {
        val (dx, dy, dz) = FanPadLayoutResolver.inwardDomain(wall)
        return when (wall) {
            GreenhouseEndWall.NEAR_Y0 -> dy > 0.0 && dx == 0.0 && dz == 0.0
            GreenhouseEndWall.FAR_Y_LENGTH -> dy < 0.0 && dx == 0.0 && dz == 0.0
            GreenhouseEndWall.NEAR_X0 -> dx > 0.0 && dy == 0.0 && dz == 0.0
            GreenhouseEndWall.FAR_X_WIDTH -> dx < 0.0 && dy == 0.0 && dz == 0.0
        }
    }

    fun writesRendererDistanceDecay(): Boolean = Phase3D2B2A.WRITES_RENDERER_DISTANCE_DECAY

    fun sharedPanelCount(exhaustCountOnSameWall: Int): Int =
        if (exhaustCountOnSameWall >= 1) 1 else 0

    private fun padWidthFallback(wallRunM: Double): Double = wallRunM

    private fun panel(
        wall: GreenhouseEndWall,
        lengthM: Double,
        widthM: Double,
        padWidthM: Double,
        padHeightM: Double,
        pad: FanPadFieldSnapshot
    ): PadPanelSpec = PadPanelSpec(
        wall = wall,
        originM = PadPanelLayout.visualOrigin(lengthM, widthM, wall, padHeightM),
        widthM = padWidthM,
        heightM = padHeightM,
        wetPadActive = pad.wetPadActive,
        yawDeg = yawForInward(wall)
    )

    private fun yawForInward(wall: GreenhouseEndWall): Double = when (wall) {
        GreenhouseEndWall.NEAR_Y0 -> 90.0
        GreenhouseEndWall.FAR_Y_LENGTH -> 270.0
        GreenhouseEndWall.NEAR_X0 -> 0.0
        GreenhouseEndWall.FAR_X_WIDTH -> 180.0
    }

    private fun anyExhaustOn(
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>
    ): Boolean = instances.any { inst ->
        !inst.isGhost &&
            inst.type == EquipmentType.EXHAUST_FAN &&
            runtimeById[inst.id] == EquipmentRuntimeState.ON
    }
}
