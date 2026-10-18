package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.preview3d.domain.CropRowGeometry
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Deterministic, dimension-driven visualization lattice. Sampling density is a
 * rendering resolution, not a physical coefficient. Crop rows are not clipping
 * boundaries. Positions stay inside the greenhouse envelope.
 *
 * User frame: X = width, Y = length, Z = height.
 */
object EnvironmentalFieldLattice {
    fun plan(
        widthM: Double,
        lengthM: Double,
        heightM: Double,
        canopyHeightM: Double? = null,
        maxCells: Int = EnvironmentalFieldVisual.MAX_CELLS
    ): EnvironmentalFieldLatticePlan? {
        if (!finitePositive(widthM) || !finitePositive(lengthM) || !finitePositive(heightM)) {
            return null
        }
        val cap = maxCells.coerceAtMost(EnvironmentalFieldVisual.MAX_CELLS).coerceAtLeast(1)
        val countZ = heightLevelCount(heightM).coerceAtLeast(1)
        val (countX, countY) = xyCounts(widthM, lengthM, countZ, cap)
        val zCount = min(countZ, max(1, cap / (countX * countY).coerceAtLeast(1)))
        val heights = heightLevels(heightM, canopyHeightM, zCount) ?: return null
        val xs = axisSamples(widthM, countX)
        val ys = axisSamples(lengthM, countY)
        val positions = ArrayList<DomainPoint>(countX * countY * heights.size)
        val levels = ArrayList<EnvironmentalFieldLevel>(countX * countY * heights.size)
        heights.forEach { (level, zM) ->
            ys.forEach { yM ->
                xs.forEach { xM ->
                    val point = DomainPoint(xM = xM, yM = yM, zM = zM)
                    if (insideEnvelope(point, widthM, lengthM, heightM)) {
                        positions += point
                        levels += level
                    }
                }
            }
        }
        if (positions.isEmpty()) return null
        val limitedPositions: List<DomainPoint>
        val limitedLevels: List<EnvironmentalFieldLevel>
        if (positions.size > cap) {
            limitedPositions = positions.take(cap)
            limitedLevels = levels.take(cap)
        } else {
            limitedPositions = positions
            limitedLevels = levels
        }
        return EnvironmentalFieldLatticePlan(
            widthM = widthM,
            lengthM = lengthM,
            heightM = heightM,
            countX = countX,
            countY = countY,
            countZ = heights.size,
            spacingXM = widthM / countX,
            spacingYM = lengthM / countY,
            cropZM = heights.firstOrNull { it.first == EnvironmentalFieldLevel.CROP }?.second
                ?: heights.first().second,
            middleZM = heights.firstOrNull { it.first == EnvironmentalFieldLevel.MIDDLE }?.second
                ?: heights[heights.size / 2].second,
            upperZM = heights.firstOrNull { it.first == EnvironmentalFieldLevel.UPPER }?.second
                ?: heights.last().second,
            positions = limitedPositions,
            levels = limitedLevels
        )
    }

    fun empty(): EnvironmentalFieldLatticePlan = EnvironmentalFieldLatticePlan(
        widthM = 0.0,
        lengthM = 0.0,
        heightM = 0.0,
        countX = 1,
        countY = 1,
        countZ = 1,
        spacingXM = 0.0,
        spacingYM = 0.0,
        cropZM = 0.0,
        middleZM = 0.0,
        upperZM = 0.0,
        positions = emptyList(),
        levels = emptyList()
    )

    fun cellBox(
        lattice: EnvironmentalFieldLatticePlan,
        sample: EnvironmentalFieldSample
    ): EnvironmentalFieldCellBox {
        val halfX = 0.5 * lattice.spacingXM * EnvironmentalFieldAppearance.PATCH_FILL
        val halfY = 0.5 * lattice.spacingYM * EnvironmentalFieldAppearance.PATCH_FILL
        val halfZ = 0.5 * EnvironmentalFieldAppearance.PATCH_THICKNESS_M
        val wall = EnvironmentalFieldAppearance.WALL_CLEARANCE_M
        val floor = EnvironmentalFieldAppearance.FLOOR_CLEARANCE_M
        val roof = EnvironmentalFieldAppearance.ROOF_CLEARANCE_M
        val x = sample.positionM.xM
        val y = sample.positionM.yM
        val z = sample.positionM.zM
        return EnvironmentalFieldCellBox(
            sample = sample,
            minXM = (x - halfX).coerceAtLeast(wall),
            maxXM = (x + halfX).coerceAtMost(lattice.widthM - wall),
            minYM = (y - halfY).coerceAtLeast(wall),
            maxYM = (y + halfY).coerceAtMost(lattice.lengthM - wall),
            minZM = (z - halfZ).coerceAtLeast(floor),
            maxZM = (z + halfZ).coerceAtMost(lattice.heightM - roof)
        )
    }

    internal fun axisSamples(lengthM: Double, count: Int): List<Double> {
        val n = count.coerceAtLeast(1)
        if (n == 1) return listOf(lengthM * 0.5)
        return List(n) { index -> ((index + 0.5) / n) * lengthM }
    }

    internal fun heightLevelCount(heightM: Double): Int = when {
        heightM >= 1.8 -> EnvironmentalFieldVisual.HEIGHT_LEVELS
        heightM >= 1.0 -> 2
        else -> 1
    }

    internal fun heightLevels(
        heightM: Double,
        canopyHeightM: Double?,
        countZ: Int
    ): List<Pair<EnvironmentalFieldLevel, Double>>? {
        if (!finitePositive(heightM) || countZ < 1) return null
        val floor = EnvironmentalFieldAppearance.FLOOR_CLEARANCE_M
        val roof = EnvironmentalFieldAppearance.ROOF_CLEARANCE_M
        val minZ = floor
        val maxZ = (heightM - roof).coerceAtLeast(minZ + 1e-6)
        val canopy = (canopyHeightM?.takeIf { it.isFinite() && it > 0.0 }
            ?: (heightM * CropRowGeometry.HEIGHT_FILL))
            .coerceIn(minZ, maxZ)
        val cropCap = max(minZ, min(maxZ, maxZ * 0.45))
        val cropZ = (canopy + EnvironmentalFieldAppearance.CANOPY_OFFSET_M)
            .coerceIn(minZ, cropCap)
        val upperZ = maxZ
        val middleZ = 0.5 * (cropZ + upperZ)
        val raw = when (countZ) {
            1 -> listOf(EnvironmentalFieldLevel.MIDDLE to ((minZ + maxZ) * 0.5))
            2 -> listOf(
                EnvironmentalFieldLevel.CROP to cropZ,
                EnvironmentalFieldLevel.UPPER to upperZ
            )
            else -> listOf(
                EnvironmentalFieldLevel.CROP to cropZ,
                EnvironmentalFieldLevel.MIDDLE to middleZ,
                EnvironmentalFieldLevel.UPPER to upperZ
            )
        }
        val separated = ensureSeparation(raw, minZ, maxZ)
        return separated.map { (level, z) -> level to z.coerceIn(minZ, maxZ) }
    }

    private fun ensureSeparation(
        levels: List<Pair<EnvironmentalFieldLevel, Double>>,
        minZ: Double,
        maxZ: Double
    ): List<Pair<EnvironmentalFieldLevel, Double>> {
        if (levels.size <= 1) return levels
        val gap = EnvironmentalFieldAppearance.MIN_LEVEL_SEPARATION_M
        val out = levels.toMutableList()
        for (i in 1 until out.size) {
            val prev = out[i - 1].second
            val cur = out[i].second
            if (cur < prev + gap) {
                out[i] = out[i].first to (prev + gap).coerceAtMost(maxZ)
            }
        }
        for (i in out.lastIndex downTo 1) {
            val next = out[i].second
            val cur = out[i - 1].second
            if (next - cur < gap) {
                out[i - 1] = out[i - 1].first to (next - gap).coerceAtLeast(minZ)
            }
        }
        return out
    }

    private fun xyCounts(
        widthM: Double,
        lengthM: Double,
        countZ: Int,
        maxCells: Int
    ): Pair<Int, Int> {
        val maxXy = max(1, maxCells / countZ.coerceAtLeast(1))
        val preferred = EnvironmentalFieldVisual.PREFERRED_SPACING_M
        var countX = round(widthM / preferred).toInt()
            .coerceAtLeast(EnvironmentalFieldVisual.MIN_COUNT_X)
        var countY = round(lengthM / preferred).toInt()
            .coerceAtLeast(EnvironmentalFieldVisual.MIN_COUNT_Y)
        if (countX * countY <= maxXy) return countX to countY
        val scale = sqrt(maxXy.toDouble() / (countX * countY).toDouble())
        countX = max(
            EnvironmentalFieldVisual.MIN_COUNT_X,
            floor(countX * scale).toInt()
        )
        countY = max(
            EnvironmentalFieldVisual.MIN_COUNT_Y,
            floor(countY * scale).toInt()
        )
        while (countX * countY > maxXy) {
            if (countX >= countY && countX > EnvironmentalFieldVisual.MIN_COUNT_X) {
                countX--
            } else if (countY > EnvironmentalFieldVisual.MIN_COUNT_Y) {
                countY--
            } else {
                break
            }
        }
        while (countX * countY > maxXy && (countX > 2 || countY > 2)) {
            if (countX >= countY && countX > 2) countX-- else countY--
        }
        return countX.coerceAtLeast(1) to countY.coerceAtLeast(1)
    }

    private fun insideEnvelope(
        point: DomainPoint,
        widthM: Double,
        lengthM: Double,
        heightM: Double
    ): Boolean {
        val wall = EnvironmentalFieldAppearance.WALL_CLEARANCE_M
        val floor = EnvironmentalFieldAppearance.FLOOR_CLEARANCE_M
        val roof = EnvironmentalFieldAppearance.ROOF_CLEARANCE_M
        return point.xM >= wall && point.xM <= widthM - wall &&
            point.yM >= wall && point.yM <= lengthM - wall &&
            point.zM >= floor && point.zM <= heightM - roof
    }

    private fun finitePositive(value: Double): Boolean = value.isFinite() && value > 0.0
}
