package com.greenhands.app.heat.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPhysicsSchedulerTest {

    @Test
    fun thirtyAndSixtyFpsProduceTheSameAcceptedStepsOverEqualElapsedTime() {
        val elapsedS = 2.0
        val steps30 = acceptedSteps(fps = 30, elapsedS = elapsedS)
        val steps60 = acceptedSteps(fps = 60, elapsedS = elapsedS)
        val steps90 = acceptedSteps(fps = 90, elapsedS = elapsedS)
        assertEquals(2, steps30)
        assertEquals(steps30, steps60)
        assertEquals(steps30, steps90)
    }

    @Test
    fun framesBelowThePublicationIntervalDoNotExecuteTheSolver() {
        val scheduler = DemoPhysicsScheduler()
        val interval = scheduler.config.publicationIntervalS
        val dt = interval / 4.0
        var steps = 0
        repeat(3) {
            steps += scheduler.acceptFrame(dt).stepCount
        }
        assertEquals(0, steps)
        val crossing = scheduler.acceptFrame(dt)
        assertEquals(1, crossing.stepCount)
        assertEquals(interval, crossing.stepSizeS, 0.0)
    }

    @Test
    fun longPauseDoesNotCauseUnlimitedCatchUp() {
        val scheduler = DemoPhysicsScheduler()
        repeat(10) { scheduler.acceptFrame(1.0 / 60.0) }
        val paused = scheduler.acceptFrame(30.0)
        assertEquals(0, paused.stepCount)
        assertEquals(0.0, scheduler.accumulatorS(), 0.0)

        val burst = DemoPhysicsScheduler(
            DemoPhysicsSchedulerConfig(maxAcceptedStepsPerTick = 1)
        )
        val capped = burst.acceptFrame(1.5)
        assertTrue(capped.stepCount <= 1)
        val leftover = burst.acceptFrame(0.1)
        assertTrue(leftover.stepCount <= 1)
    }

    @Test
    fun resetClearsTheAccumulator() {
        val scheduler = DemoPhysicsScheduler()
        scheduler.acceptFrame(0.4)
        assertTrue(scheduler.accumulatorS() > 0.0)
        scheduler.reset()
        assertEquals(0.0, scheduler.accumulatorS(), 0.0)
        assertEquals(0, scheduler.acceptFrame(0.4).stepCount)
    }

    private fun acceptedSteps(fps: Int, elapsedS: Double): Int {
        val scheduler = DemoPhysicsScheduler()
        val dt = 1.0 / fps.toDouble()
        val frames = kotlin.math.round(elapsedS / dt).toInt()
        var steps = 0
        repeat(frames) {
            steps += scheduler.acceptFrame(dt).stepCount
        }
        return steps
    }
}
