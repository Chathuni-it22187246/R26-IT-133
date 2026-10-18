package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic research-guided starting positions. Distances are starting points,
 * not installation requirements.
 */
object PlacementSuggester {
    fun suggest(
        context: PlacementContext,
        circulationCount: Int,
        exhaustCount: Int,
        foggerCount: Int,
        exhaustOnFarWall: Boolean = true,
        idFactory: (EquipmentType, Int) -> String = { type, index ->
            "suggest_${type.name.lowercase()}_$index"
        }
    ): List<EquipmentInstance> {
        val result = ArrayList<EquipmentInstance>()
        result += circulationLoop(context, circulationCount.coerceAtLeast(0), idFactory)
        result += exhaustEndWall(context, exhaustCount.coerceAtLeast(0), exhaustOnFarWall, idFactory)
        result += foggerOverhead(context, foggerCount.coerceAtLeast(0), idFactory)
        return result.map { instance ->
            val snapped = when (instance.type) {
                EquipmentType.EXHAUST_FAN -> PlacementValidator.snapExhaust(instance, context)
                EquipmentType.FOGGER -> PlacementValidator.clampOverhead(instance, context)
                else -> instance
            }
            val validity = PlacementValidator.validate(snapped, result.filter { it.id != snapped.id }, context)
            snapped.copy(validity = validity, fromSuggestion = true)
        }
    }

    /**
     * Alternating long-side bays, opposing yaw, staggered along length so jets do not
     * short-circuit directly across the house (UConn HAF arrangement, RESEARCH_GUIDANCE).
     */
    fun circulationLoop(
        context: PlacementContext,
        count: Int,
        idFactory: (EquipmentType, Int) -> String
    ): List<EquipmentInstance> {
        if (count <= 0) return emptyList()
        val aisles = aisleCentres(context)
        val halfWidth = EquipmentAssetCatalog.circulation.dimensions.widthM * 0.5
        val xMin = halfWidth + PlacementValidator.WALL_INSET_M
        val xMax = context.widthM - halfWidth - PlacementValidator.WALL_INSET_M
        val xLo = min(xMin, xMax)
        val xHi = max(xMin, xMax)
        val xNear = aisles.first().coerceIn(xLo, xHi)
        val xFar = aisles.last().coerceIn(xLo, xHi)
        val z = PlacementValidator.circulationZ(context)
        val endMargin = max(1.0, context.lengthM * 0.12)
        return (0 until count).map { index ->
            val t = if (count == 1) 0.5 else index.toDouble() / (count - 1).coerceAtLeast(1)
            val y = endMargin + t * (context.lengthM - 2.0 * endMargin)
            val even = index % 2 == 0
            val x = if (even) xNear else xFar
            val yaw = if (even) 90.0 else 270.0
            EquipmentAssetCatalog.newInstance(
                type = EquipmentType.CIRCULATION_FAN,
                id = idFactory(EquipmentType.CIRCULATION_FAN, index),
                positionM = DomainPoint(x, y, z),
                yawDeg = yaw,
                fromSuggestion = true,
                displayNumber = index + 1
            )
        }
    }

    fun exhaustEndWall(
        context: PlacementContext,
        count: Int,
        farWall: Boolean,
        idFactory: (EquipmentType, Int) -> String
    ): List<EquipmentInstance> {
        if (count <= 0) return emptyList()
        val y = if (farWall) context.lengthM else 0.0
        val yaw = if (farWall) 90.0 else 270.0
        val z = ((context.canopyHeightM + context.heightM) * 0.5)
            .coerceIn(0.7, (context.heightM - 0.5).coerceAtLeast(0.7))
        val margin = max(0.6, context.widthM * 0.12)
        return (0 until count).map { index ->
            val t = if (count == 1) 0.5 else (index + 1.0) / (count + 1.0)
            val x = margin + t * (context.widthM - 2.0 * margin)
            EquipmentAssetCatalog.newInstance(
                type = EquipmentType.EXHAUST_FAN,
                id = idFactory(EquipmentType.EXHAUST_FAN, index),
                positionM = DomainPoint(x, y, z),
                yawDeg = yaw,
                fromSuggestion = true,
                displayNumber = index + 1
            )
        }
    }

    /**
     * Uniform overhead distribution, biased into aisles between crop rows when possible.
     * Does not copy a single experiment's nozzle spacing.
     */
    fun foggerOverhead(
        context: PlacementContext,
        count: Int,
        idFactory: (EquipmentType, Int) -> String
    ): List<EquipmentInstance> {
        if (count <= 0) return emptyList()
        val z = PlacementValidator.overheadZ(context)
        val aisles = aisleCentres(context)
        val alongCount = ceil(count.toDouble() / aisles.size).toInt().coerceAtLeast(1)
        val yMargin = max(0.6, context.lengthM * 0.10)
        val placed = ArrayList<EquipmentInstance>(count)
        var n = 0
        for (iy in 0 until alongCount) {
            val ty = if (alongCount == 1) 0.5 else (iy + 0.5) / alongCount
            val y = yMargin + ty * (context.lengthM - 2.0 * yMargin)
            for (x in aisles) {
                if (n >= count) break
                placed += EquipmentAssetCatalog.newInstance(
                    type = EquipmentType.FOGGER,
                    id = idFactory(EquipmentType.FOGGER, n),
                    positionM = DomainPoint(x, y, z),
                    yawDeg = 0.0,
                    fromSuggestion = true,
                    displayNumber = n + 1
                )
                n++
            }
            if (n >= count) break
        }
        return placed
    }

    fun aisleCentres(context: PlacementContext): List<Double> {
        val crops = context.cropBoxes.sortedBy { it.minXM }
        if (crops.isEmpty()) return listOf(context.widthM * 0.5)
        val aisles = ArrayList<Double>()
        val first = crops.first()
        if (first.minXM > 0.25) aisles += first.minXM * 0.5
        for (i in 0 until crops.lastIndex) {
            val gap = (crops[i].maxXM + crops[i + 1].minXM) * 0.5
            aisles += gap
        }
        val last = crops.last()
        if (context.widthM - last.maxXM > 0.25) aisles += (last.maxXM + context.widthM) * 0.5
        return aisles.ifEmpty { listOf(context.widthM * 0.5) }
    }

    fun defaultGhostPosition(type: EquipmentType, context: PlacementContext): Pair<DomainPoint, Double> {
        return when (type) {
            EquipmentType.CIRCULATION_FAN -> {
                val list = circulationLoop(context, 1) { _, _ -> "ghost" }
                list.first().positionM to list.first().yawDeg
            }
            EquipmentType.EXHAUST_FAN -> {
                val list = exhaustEndWall(context, 1, farWall = true) { _, _ -> "ghost" }
                list.first().positionM to list.first().yawDeg
            }
            EquipmentType.FOGGER -> {
                val list = foggerOverhead(context, 1) { _, _ -> "ghost" }
                list.first().positionM to list.first().yawDeg
            }
        }
    }
}
