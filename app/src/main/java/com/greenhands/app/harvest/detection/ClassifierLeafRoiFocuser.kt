package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HsvConverter
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Classifier-only whole-leaf crop. Does not change hybrid scan validation or HSV.
 *
 * The hybrid crop is already the leaf-gate target (plus crop padding). This
 * focuser reuses that accepted region as the prior and builds a silhouette from
 * green, yellow-green, yellow, brown, and necrotic tissue — not from green alone.
 * Skin and filled paper/table backgrounds are excluded. Spots inside the leaf
 * are kept as part of the silhouette. Result is a padded square so 224×224 is a
 * uniform resize, not a stretch.
 */
object ClassifierLeafRoiFocuser {
    const val PAD_RATIO = 0.12f
    const val MIN_SIDE = 8
    const val MIN_PIXELS = 16
    const val MIN_PIXEL_RATIO = 0.008f
    const val MIN_TIGHT_VEG_FILL = 0.38f
    const val MIN_INIMAGE_SQUARE_VEG_FILL = 0.50f
    /** Hybrid crop is gate bbox + 10% pad; the inner box is the accepted target. */
    const val GATE_INSET_RATIO = 0.08f
    const val WALL_EXTENT = 0.88f
    const val WALL_AREA_RATIO = 0.40f
    const val SOLID_WALL_EXTENT = 0.92f

    fun focus(hybridCrop: HarvestArgbFrame): ClassifierLeafRoiResult {
        val width = hybridCrop.width
        val height = hybridCrop.height
        val total = (width * height).coerceAtLeast(1)
        val gateBox = insetBox(width, height, GATE_INSET_RATIO)
        if (width < MIN_SIDE || height < MIN_SIDE) {
            return unreliable(hybridCrop, "crop_too_small", gateBox = gateBox)
        }
        val families = Array(total) { i ->
            HybridColorClassifier.familyFromArgb(hybridCrop.argb[i])
        }
        val skin = BooleanArray(total) { i ->
            isLikelySkin(hybridCrop.argb[i], families[i])
        }
        val minPixels = max(MIN_PIXELS, (total * MIN_PIXEL_RATIO).toInt())
        val greenMask = colorMask(families, skin, width, height) { it == HybridColorFamily.GREEN }
        val yellowMask = colorMask(families, skin, width, height) {
            it == HybridColorFamily.YELLOW
        }
        val brownMask = colorMask(families, skin, width, height) {
            it == HybridColorFamily.BROWN || it == HybridColorFamily.DARK_RED_BROWN
        }
        val orangeMask = colorMask(families, skin, width, height) {
            it == HybridColorFamily.ORANGE
        }
        val seed = selectSeed(
            listOf(greenMask, yellowMask, brownMask),
            width,
            height,
            minPixels,
            total,
            gateBox
        ) ?: return unreliable(
            hybridCrop,
            "no_connected_leaf_region",
            gateBox = gateBox,
            greenBox = boundsOf(greenMask, width, height),
            skinRejected = countTrue(skin)
        )
        val silhouette = growSilhouette(
            seed = seed,
            extras = listOf(greenMask, yellowMask, brownMask, orangeMask),
            width = width,
            height = height,
            minPixels = minPixels,
            total = total,
            gateBox = gateBox
        )
        fillEnclosedHoles(silhouette, width, height)
        for (i in silhouette.indices) {
            if (skin[i]) silhouette[i] = false
        }
        val leafBox = boundsOf(silhouette, width, height)
            ?: return unreliable(
                hybridCrop,
                "empty_leaf_mask",
                gateBox = gateBox,
                greenBox = boundsOf(greenMask, width, height),
                skinRejected = countTrue(skin)
            )
        val padded = padBox(leafBox, width, height, PAD_RATIO)
        val tightVeg = vegetationPercent(silhouette, padded, width)
        val skinInPad = countInBox(skin, padded, width)
        val backgroundInPad = countBackgroundRejected(
            families,
            skin,
            silhouette,
            padded,
            width
        )
        if (tightVeg < MIN_TIGHT_VEG_FILL) {
            val attempted = squareFromParent(hybridCrop, silhouette, padded, width, height)
            return unreliable(
                hybridCrop,
                "vegetation_fill_too_low",
                attempted = attempted.frame,
                bbox = leafBox,
                vegetationPercent = attempted.vegetationPercent,
                retainedPercent = areaPercent(attempted.frame, total),
                gateBox = gateBox,
                greenBox = boundsOf(greenMask, width, height),
                squareBox = attempted.box,
                skinRejected = skinInPad,
                backgroundRejected = backgroundInPad
            )
        }
        val inImage = inImageSquare(padded, width, height)
        val inImageVeg = vegetationPercent(silhouette, inImage, width)
        val squareFrame: HarvestArgbFrame
        val squareVeg: Float
        val squareBox: IntBox
        if (inImage.isSquare() &&
            inImage.side() >= MIN_SIDE &&
            inImageVeg >= MIN_INIMAGE_SQUARE_VEG_FILL
        ) {
            squareFrame = crop(hybridCrop, inImage)
            squareVeg = inImageVeg
            squareBox = inImage
        } else {
            val tight = crop(hybridCrop, padded)
            val fill = meanVegetationColor(hybridCrop, silhouette)
            squareFrame = letterboxToSquare(tight, fill)
            squareVeg = vegetationPercent(
                silhouette,
                padded,
                width,
                squareArea = squareFrame.width * squareFrame.height
            )
            squareBox = padded
        }
        if (squareFrame.width < MIN_SIDE || squareFrame.height < MIN_SIDE) {
            return unreliable(
                hybridCrop,
                "focused_roi_too_small",
                attempted = squareFrame,
                bbox = leafBox,
                gateBox = gateBox,
                greenBox = boundsOf(greenMask, width, height),
                squareBox = squareBox,
                skinRejected = skinInPad,
                backgroundRejected = backgroundInPad
            )
        }
        return ClassifierLeafRoiResult(
            frame = squareFrame,
            usedFocusedRoi = true,
            originalWidth = width,
            originalHeight = height,
            roiWidth = squareFrame.width,
            roiHeight = squareFrame.height,
            retainedPercent = areaPercent(squareFrame, total),
            vegetationPercent = squareVeg * 100f,
            bboxLeft = leafBox.x0,
            bboxTop = leafBox.y0,
            bboxRight = leafBox.x1,
            bboxBottom = leafBox.y1,
            source = "focused",
            reason = "whole_leaf_silhouette_square",
            gateBboxLabel = gateBox.label(),
            greenCoreBboxLabel = boundsOf(greenMask, width, height)?.label() ?: "none",
            wholeLeafBboxLabel = leafBox.label(),
            squareBboxLabel = squareBox.label(),
            skinRejectedPixels = skinInPad,
            backgroundRejectedPixels = backgroundInPad
        )
    }

    /**
     * Bright reddish-orange skin. Dark necrotic brown and yellow diseased
     * tissue are not treated as skin.
     */
    internal fun isLikelySkin(argb: Int, family: HybridColorFamily): Boolean {
        if (family == HybridColorFamily.GREEN || family == HybridColorFamily.YELLOW) {
            return false
        }
        val hsv = HsvConverter.fromArgb(argb)
        if (family == HybridColorFamily.BROWN || family == HybridColorFamily.DARK_RED_BROWN) {
            if (hsv.value < 0.48f) return false
        }
        val reddish = hsv.hue < 32f || hsv.hue >= 335f
        val satOk = hsv.saturation in 0.10f..0.75f
        val valOk = hsv.value in 0.48f..0.98f
        return reddish && satOk && valOk
    }

    private fun colorMask(
        families: Array<HybridColorFamily>,
        skin: BooleanArray,
        width: Int,
        height: Int,
        accept: (HybridColorFamily) -> Boolean
    ): BooleanArray {
        val out = BooleanArray(width * height)
        for (i in out.indices) {
            out[i] = !skin[i] && accept(families[i])
        }
        return out
    }

    private fun selectSeed(
        masks: List<BooleanArray>,
        width: Int,
        height: Int,
        minPixels: Int,
        total: Int,
        gateBox: IntBox
    ): BooleanArray? {
        var best: Component? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (mask in masks) {
            for (component in components(mask, width, height, minPixels)) {
                if (isWall(component.region, total)) continue
                val score = seedScore(component.region, width, height, gateBox)
                if (score > bestScore) {
                    bestScore = score
                    best = component
                }
            }
        }
        return best?.toMask(width, height)
    }

    private fun seedScore(
        region: ConnectedRegion,
        width: Int,
        height: Int,
        gateBox: IntBox
    ): Float {
        val dx = region.centroidX / width.toFloat() - 0.5f
        val dy = region.centroidY / height.toFloat() - 0.5f
        val dist = sqrt(dx * dx + dy * dy)
        val central = (1f - dist / 0.75f).coerceIn(0.12f, 1f)
        val inGate = region.centroidX >= gateBox.x0 &&
            region.centroidX < gateBox.x1 &&
            region.centroidY >= gateBox.y0 &&
            region.centroidY < gateBox.y1
        val gateBoost = if (inGate) 1.45f else 0.85f
        return region.pixelArea * central * gateBoost
    }

    private fun growSilhouette(
        seed: BooleanArray,
        extras: List<BooleanArray>,
        width: Int,
        height: Int,
        minPixels: Int,
        total: Int,
        gateBox: IntBox
    ): BooleanArray {
        val out = seed.copyOf()
        var box = boundsOf(out, width, height) ?: return out
        var changed = true
        val merged = HashSet<Int>()
        val catalog = ArrayList<Component>()
        for (mask in extras) {
            catalog.addAll(components(mask, width, height, minPixels))
        }
        while (changed) {
            changed = false
            val dilated = out.copyOf()
            dilate(dilated, width, height, 1)
            for (index in catalog.indices) {
                if (!merged.add(index)) continue
                val component = catalog[index]
                if (isWall(component.region, total)) {
                    merged.remove(index)
                    continue
                }
                if (!shouldMerge(component, box, dilated, width, height, gateBox)) {
                    merged.remove(index)
                    continue
                }
                for (p in component.pixels) out[p] = true
                box = boundsOf(out, width, height) ?: box
                changed = true
            }
        }
        return out
    }

    private fun shouldMerge(
        component: Component,
        seedBox: IntBox,
        dilatedSeed: BooleanArray,
        width: Int,
        height: Int,
        gateBox: IntBox
    ): Boolean {
        var inside = 0
        var touches = false
        for (p in component.pixels) {
            if (dilatedSeed[p]) touches = true
            val x = p % width
            val y = p / width
            if (x >= seedBox.x0 && x < seedBox.x1 && y >= seedBox.y0 && y < seedBox.y1) {
                inside++
            }
        }
        val fracInside = inside.toFloat() / component.count.coerceAtLeast(1).toFloat()
        if (fracInside >= 0.40f) return true
        if (!touches) return false
        val cx = component.region.centroidX
        val cy = component.region.centroidY
        val inGate = cx >= gateBox.x0 && cx < gateBox.x1 && cy >= gateBox.y0 && cy < gateBox.y1
        if (touchesBorder(component.region, width, height) && fracInside < 0.25f) {
            return false
        }
        return inGate || fracInside >= 0.18f
    }

    private fun fillEnclosedHoles(mask: BooleanArray, width: Int, height: Int) {
        val outside = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        var sp = 0
        fun tryPush(p: Int) {
            if (p !in mask.indices || mask[p] || outside[p]) return
            outside[p] = true
            stack[sp++] = p
        }
        for (x in 0 until width) {
            tryPush(x)
            tryPush((height - 1) * width + x)
        }
        for (y in 0 until height) {
            tryPush(y * width)
            tryPush(y * width + width - 1)
        }
        while (sp > 0) {
            val p = stack[--sp]
            val x = p % width
            val y = p / width
            if (x > 0) tryPush(p - 1)
            if (x + 1 < width) tryPush(p + 1)
            if (y > 0) tryPush(p - width)
            if (y + 1 < height) tryPush(p + width)
        }
        var holes = 0
        var leaf = 0
        for (i in mask.indices) {
            if (mask[i]) leaf++
            else if (!outside[i]) holes++
        }
        if (holes == 0 || holes > leaf * 4 / 5) return
        for (i in mask.indices) {
            if (!mask[i] && !outside[i]) mask[i] = true
        }
    }

    private fun components(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minPixels: Int
    ): List<Component> {
        if (width <= 0 || height <= 0 || mask.size < width * height) return emptyList()
        val seen = BooleanArray(width * height)
        val stack = IntArray(width * height)
        val current = IntArray(width * height)
        val out = ArrayList<Component>()
        val total = width * height
        for (start in 0 until total) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            var sumX = 0L
            var sumY = 0L
            var perimeter = 0
            while (sp > 0) {
                val p = stack[--sp]
                current[area] = p
                val x = p % width
                val y = p / width
                area++
                sumX += x
                sumY += y
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                for (dx in intArrayOf(-1, 0, 1)) {
                    for (dy in intArrayOf(-1, 0, 1)) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        val inside = nx in 0 until width && ny in 0 until height
                        val nMask = inside && mask[ny * width + nx]
                        if (!nMask && (dx == 0 || dy == 0)) perimeter++
                        if (nMask && !seen[ny * width + nx]) {
                            seen[ny * width + nx] = true
                            stack[sp++] = ny * width + nx
                        }
                    }
                }
            }
            if (area < minPixels) continue
            out.add(
                Component(
                    region = ConnectedRegion(
                        pixelArea = area,
                        minX = minX,
                        minY = minY,
                        maxX = maxX,
                        maxY = maxY,
                        sumX = sumX,
                        sumY = sumY,
                        perimeter = perimeter
                    ),
                    pixels = current.copyOf(area)
                )
            )
        }
        return out
    }

    private fun isWall(region: ConnectedRegion, total: Int): Boolean {
        val areaRatio = region.pixelArea.toFloat() / total.coerceAtLeast(1).toFloat()
        return (region.extent >= WALL_EXTENT && areaRatio >= WALL_AREA_RATIO) ||
            region.extent >= SOLID_WALL_EXTENT
    }

    private fun touchesBorder(region: ConnectedRegion, width: Int, height: Int): Boolean =
        region.minX <= 0 || region.minY <= 0 ||
            region.maxX >= width - 1 || region.maxY >= height - 1

    private fun insetBox(width: Int, height: Int, ratio: Float): IntBox {
        val ix = (width * ratio).toInt()
        val iy = (height * ratio).toInt()
        return IntBox(ix, iy, (width - ix).coerceAtLeast(ix + 1), (height - iy).coerceAtLeast(iy + 1))
    }

    private fun squareFromParent(
        frame: HarvestArgbFrame,
        leafMask: BooleanArray,
        padded: IntBox,
        width: Int,
        height: Int
    ): SquareAttempt {
        val inImage = inImageSquare(padded, width, height)
        val cropBox = if (inImage.side() >= MIN_SIDE) inImage else padded
        val cropped = crop(frame, cropBox)
        val square = if (cropped.width == cropped.height) {
            cropped
        } else {
            letterboxToSquare(cropped, meanVegetationColor(frame, leafMask))
        }
        return SquareAttempt(
            frame = square,
            vegetationPercent = vegetationPercent(leafMask, cropBox, width) * 100f,
            box = cropBox
        )
    }

    private fun inImageSquare(padded: IntBox, width: Int, height: Int): IntBox {
        val needed = max(padded.x1 - padded.x0, padded.y1 - padded.y0)
        val side = needed.coerceAtMost(minOf(width, height))
        val cx = (padded.x0 + padded.x1) / 2
        val cy = (padded.y0 + padded.y1) / 2
        var x0 = cx - side / 2
        var y0 = cy - side / 2
        if (x0 < 0) x0 = 0
        if (y0 < 0) y0 = 0
        if (x0 + side > width) x0 = (width - side).coerceAtLeast(0)
        if (y0 + side > height) y0 = (height - side).coerceAtLeast(0)
        val x1 = (x0 + side).coerceAtMost(width)
        val y1 = (y0 + side).coerceAtMost(height)
        return IntBox(x0, y0, x1, y1)
    }

    private fun padBox(box: IntBox, width: Int, height: Int, ratio: Float): IntBox {
        val bw = box.x1 - box.x0
        val bh = box.y1 - box.y0
        val padX = (bw * ratio).toInt().coerceAtLeast(1)
        val padY = (bh * ratio).toInt().coerceAtLeast(1)
        return IntBox(
            x0 = (box.x0 - padX).coerceAtLeast(0),
            y0 = (box.y0 - padY).coerceAtLeast(0),
            x1 = (box.x1 + padX).coerceAtMost(width),
            y1 = (box.y1 + padY).coerceAtMost(height)
        )
    }

    private fun boundsOf(mask: BooleanArray, width: Int, height: Int): IntBox? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (i in mask.indices) {
            if (!mask[i]) continue
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        if (maxX < 0) return null
        return IntBox(minX, minY, maxX + 1, maxY + 1)
    }

    private fun vegetationPercent(
        leafMask: BooleanArray,
        box: IntBox,
        stride: Int,
        squareArea: Int? = null
    ): Float {
        var n = 0
        for (y in box.y0 until box.y1) {
            val row = y * stride
            for (x in box.x0 until box.x1) {
                if (leafMask[row + x]) n++
            }
        }
        val area = (squareArea ?: ((box.x1 - box.x0) * (box.y1 - box.y0))).coerceAtLeast(1)
        return n.toFloat() / area.toFloat()
    }

    private fun crop(frame: HarvestArgbFrame, box: IntBox): HarvestArgbFrame {
        val outW = (box.x1 - box.x0).coerceAtLeast(1)
        val outH = (box.y1 - box.y0).coerceAtLeast(1)
        val out = IntArray(outW * outH)
        var i = 0
        for (y in box.y0 until box.y1) {
            val row = y * frame.width
            for (x in box.x0 until box.x1) {
                out[i++] = frame.argb[row + x]
            }
        }
        return HarvestArgbFrame(out, outW, outH)
    }

    private fun letterboxToSquare(frame: HarvestArgbFrame, fillArgb: Int): HarvestArgbFrame {
        val side = max(frame.width, frame.height)
        if (frame.width == side && frame.height == side) return frame
        val out = IntArray(side * side) { fillArgb }
        val ox = (side - frame.width) / 2
        val oy = (side - frame.height) / 2
        for (y in 0 until frame.height) {
            val srcRow = y * frame.width
            val dstRow = (y + oy) * side
            for (x in 0 until frame.width) {
                out[dstRow + x + ox] = frame.argb[srcRow + x]
            }
        }
        return HarvestArgbFrame(out, side, side)
    }

    private fun meanVegetationColor(frame: HarvestArgbFrame, leafMask: BooleanArray): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (i in leafMask.indices) {
            if (!leafMask[i]) continue
            val px = frame.argb[i]
            r += (px shr 16) and 0xFF
            g += (px shr 8) and 0xFF
            b += px and 0xFF
            n++
        }
        if (n == 0) return 0xFF1A1A1A.toInt()
        return (0xFF shl 24) or
            ((r / n).toInt() shl 16) or
            ((g / n).toInt() shl 8) or
            (b / n).toInt()
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val src = mask.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!src[y * width + x]) continue
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx in 0 until width) mask[ny * width + nx] = true
                    }
                }
            }
        }
    }

    private fun areaPercent(frame: HarvestArgbFrame, originalPixels: Int): Float =
        (frame.width * frame.height).toFloat() / originalPixels.coerceAtLeast(1).toFloat() * 100f

    private fun countTrue(mask: BooleanArray): Int {
        var n = 0
        for (v in mask) if (v) n++
        return n
    }

    private fun countInBox(mask: BooleanArray, box: IntBox, stride: Int): Int {
        var n = 0
        for (y in box.y0 until box.y1) {
            val row = y * stride
            for (x in box.x0 until box.x1) {
                if (mask[row + x]) n++
            }
        }
        return n
    }

    private fun countBackgroundRejected(
        families: Array<HybridColorFamily>,
        skin: BooleanArray,
        leaf: BooleanArray,
        box: IntBox,
        stride: Int
    ): Int {
        var n = 0
        for (y in box.y0 until box.y1) {
            val row = y * stride
            for (x in box.x0 until box.x1) {
                val i = row + x
                if (leaf[i] || skin[i]) continue
                val f = families[i]
                if (f == HybridColorFamily.PALE || f == HybridColorFamily.OTHER) n++
            }
        }
        return n
    }

    private fun unreliable(
        hybridCrop: HarvestArgbFrame,
        reason: String,
        attempted: HarvestArgbFrame? = null,
        bbox: IntBox? = null,
        vegetationPercent: Float = 0f,
        retainedPercent: Float = 100f,
        gateBox: IntBox? = null,
        greenBox: IntBox? = null,
        squareBox: IntBox? = null,
        skinRejected: Int = 0,
        backgroundRejected: Int = 0
    ) = ClassifierLeafRoiResult(
        frame = attempted,
        usedFocusedRoi = false,
        originalWidth = hybridCrop.width,
        originalHeight = hybridCrop.height,
        roiWidth = attempted?.width ?: hybridCrop.width,
        roiHeight = attempted?.height ?: hybridCrop.height,
        retainedPercent = retainedPercent,
        vegetationPercent = vegetationPercent,
        bboxLeft = bbox?.x0 ?: 0,
        bboxTop = bbox?.y0 ?: 0,
        bboxRight = bbox?.x1 ?: 0,
        bboxBottom = bbox?.y1 ?: 0,
        source = "unreliable",
        reason = reason,
        gateBboxLabel = gateBox?.label() ?: "[0,0,${hybridCrop.width},${hybridCrop.height}]",
        greenCoreBboxLabel = greenBox?.label() ?: "none",
        wholeLeafBboxLabel = bbox?.label() ?: "none",
        squareBboxLabel = squareBox?.label() ?: "none",
        skinRejectedPixels = skinRejected,
        backgroundRejectedPixels = backgroundRejected
    )

    private data class Component(val region: ConnectedRegion, val pixels: IntArray) {
        val count: Int get() = pixels.size
        fun toMask(width: Int, height: Int): BooleanArray {
            val mask = BooleanArray(width * height)
            for (p in pixels) mask[p] = true
            return mask
        }
    }

    private data class IntBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        fun side(): Int = max(x1 - x0, y1 - y0)
        fun isSquare(): Boolean = (x1 - x0) == (y1 - y0) && (x1 - x0) > 0
        fun label(): String = "[$x0,$y0,$x1,$y1]"
    }

    private data class SquareAttempt(
        val frame: HarvestArgbFrame,
        val vegetationPercent: Float,
        val box: IntBox
    )
}

data class ClassifierLeafRoiResult(
    val frame: HarvestArgbFrame?,
    val usedFocusedRoi: Boolean,
    val originalWidth: Int,
    val originalHeight: Int,
    val roiWidth: Int,
    val roiHeight: Int,
    val retainedPercent: Float,
    val vegetationPercent: Float,
    val bboxLeft: Int,
    val bboxTop: Int,
    val bboxRight: Int,
    val bboxBottom: Int,
    val source: String,
    val reason: String,
    val gateBboxLabel: String = "none",
    val greenCoreBboxLabel: String = "none",
    val wholeLeafBboxLabel: String = "none",
    val squareBboxLabel: String = "none",
    val skinRejectedPixels: Int = 0,
    val backgroundRejectedPixels: Int = 0
) {
    val bboxLabel: String
        get() = "[$bboxLeft,$bboxTop,$bboxRight,$bboxBottom]"

    val roiDebugLine: String
        get() = "gate=$gateBboxLabel greenCore=$greenCoreBboxLabel " +
            "wholeLeaf=$wholeLeafBboxLabel square=$squareBboxLabel " +
            "occupancy=${"%.1f".format(vegetationPercent)}% " +
            "skinRejected=$skinRejectedPixels bgRejected=$backgroundRejectedPixels " +
            "leafFocused=$usedFocusedRoi reason=$reason"
}

sealed class TomatoDiseaseClassifyResult {
    data class Success(val prediction: TomatoDiseasePrediction) : TomatoDiseaseClassifyResult()
    data class UnreliableRoi(val reason: String) : TomatoDiseaseClassifyResult()
    data class Failed(val reason: String) : TomatoDiseaseClassifyResult()
}
