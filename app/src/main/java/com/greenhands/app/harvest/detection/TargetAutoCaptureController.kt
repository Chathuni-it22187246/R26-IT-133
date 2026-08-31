package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Combines validation, stability, cooldown, and target-region crop.
 * HSV / harvest / plant-health logic must run only when [TargetCaptureTick.captureFrame]
 * is non-null.
 */
class TargetAutoCaptureController(
    private val expected: ScanTargetType,
    private val modelReady: Boolean = true,
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT,
    private val validator: TargetValidator = TargetValidator(calibration),
    private val stability: StabilityTracker = StabilityTracker(calibration),
    private val cropper: TargetRegionCropper = TargetRegionCropper(calibration),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var lastCaptureAtMs: Long = 0L
    private var capturedThisLock: Boolean = false
    private var focusedThisLock: Boolean = false

    fun resetLock() {
        stability.reset()
        capturedThisLock = false
        focusedThisLock = false
    }

    /**
     * Primary scan path: hybrid visual validation, then stability, focus, crop.
     * Does not require TFLite model confidence.
     */
    fun onHybridFrame(
        frame: HarvestArgbFrame,
        result: HybridValidationResult,
        analyzing: Boolean
    ): TargetCaptureTick {
        val geometryValid = result.isValidTarget
        val candidate = if (geometryValid) result.toDetection() else null
        val streak = stability.update(candidate)
        val stable = stability.isStable()
        val validation = result.toTargetValidationResult(stable)
        if (!validation.readyForManualCapture) {
            capturedThisLock = false
            focusedThisLock = false
            return TargetCaptureTick(
                phase = HarvestScanPhase.SEARCHING,
                validation = validation,
                detection = result.toDetection().takeIf { result.showGuideBox },
                shouldRequestFocus = false,
                captureFrame = null
            )
        }
        return finishTick(
            frame = frame,
            validation = validation,
            streak = streak,
            analyzing = analyzing
        )
    }

    fun onFrame(
        frame: HarvestArgbFrame,
        detections: List<TargetDetection>,
        analyzing: Boolean
    ): TargetCaptureTick {
        if (!modelReady) {
            resetLock()
            return TargetCaptureTick(
                phase = HarvestScanPhase.MODEL_UNAVAILABLE,
                validation = validator.validate(expected, emptyList(), modelReady = false),
                detection = null,
                shouldRequestFocus = false,
                captureFrame = null
            )
        }
        val geometry = validator.validate(
            expected = expected,
            detections = detections,
            modelReady = true,
            stable = false
        )
        val candidate = if (geometry.readyForManualCapture) geometry.detection else null
        val streak = stability.update(candidate)
        val stable = stability.isStable()
        val validation = validator.validate(
            expected = expected,
            detections = detections,
            modelReady = true,
            stable = stable
        )
        if (!validation.readyForManualCapture) {
            capturedThisLock = false
            focusedThisLock = false
            return TargetCaptureTick(
                phase = HarvestScanPhase.SEARCHING,
                validation = validation,
                detection = validation.detection,
                shouldRequestFocus = false,
                captureFrame = null
            )
        }
        return finishTick(
            frame = frame,
            validation = validation,
            streak = streak,
            analyzing = analyzing
        )
    }

    private fun finishTick(
        frame: HarvestArgbFrame,
        validation: TargetValidationResult,
        streak: Int,
        analyzing: Boolean
    ): TargetCaptureTick {
        val now = clock()
        val cooldownElapsed = now - lastCaptureAtMs >= calibration.autoCaptureCooldownMs
        val shouldFocus = !focusedThisLock && streak >= calibration.focusMinStreak
        if (shouldFocus) focusedThisLock = true
        val canAutoCapture = validation.readyForAutoCapture &&
            !analyzing &&
            !capturedThisLock &&
            cooldownElapsed
        if (canAutoCapture) {
            capturedThisLock = true
            lastCaptureAtMs = now
            val crop = cropper.crop(frame, validation.detection!!.boundingBox)
            return TargetCaptureTick(
                phase = HarvestScanPhase.CAPTURING,
                validation = validation,
                detection = validation.detection,
                shouldRequestFocus = shouldFocus,
                captureFrame = crop
            )
        }
        val phase = when {
            analyzing -> HarvestScanPhase.ANALYZING
            capturedThisLock -> HarvestScanPhase.RESULT
            streak >= calibration.requiredStableFrames -> HarvestScanPhase.HOLD_STEADY
            streak >= calibration.focusMinStreak -> HarvestScanPhase.HOLD_STEADY
            else -> HarvestScanPhase.TARGET_DETECTED
        }
        return TargetCaptureTick(
            phase = phase,
            validation = validation,
            detection = validation.detection,
            shouldRequestFocus = shouldFocus,
            captureFrame = null
        )
    }

    fun cropForManual(frame: HarvestArgbFrame, detection: TargetDetection): HarvestArgbFrame =
        cropper.crop(frame, detection.boundingBox)
}
