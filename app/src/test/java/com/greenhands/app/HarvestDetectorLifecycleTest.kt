package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestYoloDetectTiming
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.SynchronizedTargetDetector
import com.greenhands.app.harvest.detection.TargetDetector
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HarvestDetectorLifecycleTest {

    @Test
    fun repeatedOpenCloseDoesNotLeaveAUsableClosedDetector() {
        repeat(20) {
            val fake = FakeInterpreterDetector()
            val detector = SynchronizedTargetDetector(fake)
            assertTrue(detector.isModelReady)
            assertEquals(1, detector.detect(frame(), 1L).size)
            detector.close()
            assertFalse(detector.isModelReady)
            assertTrue(detector.detect(frame(), 2L).isEmpty())
            assertEquals(1, fake.closeCount)
            detector.close()
            assertEquals(1, fake.closeCount)
        }
        val reopened = SynchronizedTargetDetector(FakeInterpreterDetector())
        assertTrue(reopened.isModelReady)
        assertEquals(1, reopened.detect(frame(), 3L).size)
        reopened.close()
    }

    @Test
    fun closeWaitsForInFlightDetectThenBlocksFurtherInference() {
        val fake = FakeInterpreterDetector()
        val detector = SynchronizedTargetDetector(fake)
        val started = CountDownLatch(1)
        fake.beforeRun = {
            started.countDown()
            Thread.sleep(80)
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Int> { detector.detect(frame(), 1L).size }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            val closeStarted = System.nanoTime()
            detector.close()
            val closeWaitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStarted)
            assertTrue("close should wait for in-flight detect", closeWaitMs >= 40)
            assertEquals(1, future.get(1, TimeUnit.SECONDS).toInt())
            assertFalse(detector.isModelReady)
            assertTrue(detector.detect(frame(), 2L).isEmpty())
            assertEquals(1, fake.detectCount)
            assertEquals(1, fake.closeCount)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun detectAfterCloseDoesNotCallDelegate() {
        val fake = FakeInterpreterDetector()
        val detector = SynchronizedTargetDetector(fake)
        detector.close()
        assertTrue(detector.detectTimed(frame(), 1L).detections.isEmpty())
        assertEquals(0, fake.detectCount)
        assertFalse(fake.ranAfterClose)
    }

    @Test
    fun concurrentDetectIsSerialized() {
        val fake = FakeInterpreterDetector()
        fake.beforeRun = { Thread.sleep(40) }
        val detector = SynchronizedTargetDetector(fake)
        val pool = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)
        val errors = AtomicInteger(0)
        try {
            val tasks = (0 until 2).map {
                pool.submit {
                    try {
                        barrier.await(1, TimeUnit.SECONDS)
                        detector.detect(frame(), 1L)
                    } catch (_: Throwable) {
                        errors.incrementAndGet()
                    }
                }
            }
            tasks.forEach { it.get(2, TimeUnit.SECONDS) }
            assertEquals(0, errors.get())
            assertEquals(1, fake.maxConcurrent)
            assertEquals(2, fake.detectCount)
        } finally {
            detector.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun closedInterpreterExceptionIsRecognizedAndSwallowed() {
        assertTrue(
            SynchronizedTargetDetector.isInterpreterClosed(
                IllegalStateException("Internal error: The Interpreter has already been closed.")
            )
        )
        val fake = FakeInterpreterDetector()
        fake.throwClosedOnRun = true
        val detector = SynchronizedTargetDetector(fake)
        val result = detector.detectTimed(frame(), 1L)
        assertEquals(HarvestYoloDetectTiming.NONE.detections, result.detections)
        assertTrue(result.detections.isEmpty())
        detector.close()
        assertTrue(detector.detect(frame(), 2L).isEmpty())
    }

    private fun frame() = HarvestArgbFrame(IntArray(4) { 0xFF112233.toInt() }, 2, 2)

    private class FakeInterpreterDetector : TargetDetector {
        var detectCount: Int = 0
        var closeCount: Int = 0
        var maxConcurrent: Int = 0
        var ranAfterClose: Boolean = false
        var throwClosedOnRun: Boolean = false
        var beforeRun: () -> Unit = {}

        @Volatile
        private var closed: Boolean = false
        private val concurrent = AtomicInteger(0)

        override val isModelReady: Boolean
            get() = !closed

        override fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> {
            beforeRun()
            if (closed) {
                ranAfterClose = true
                throw IllegalStateException("Internal error: The Interpreter has already been closed.")
            }
            if (throwClosedOnRun) {
                throw IllegalStateException("Internal error: The Interpreter has already been closed.")
            }
            val n = concurrent.incrementAndGet()
            if (n > maxConcurrent) maxConcurrent = n
            try {
                detectCount++
                return listOf(
                    TargetDetection(
                        targetType = ScanTargetType.TOMATO_FRUIT,
                        confidence = 0.80f,
                        boundingBox = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
                        timestampMs = timestampMs
                    )
                )
            } finally {
                concurrent.decrementAndGet()
            }
        }

        override fun close() {
            closed = true
            closeCount++
        }
    }
}
