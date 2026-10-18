package com.greenhands.app.heat.runtime.contracts

/**
 * Injected clock for freshness tests. Physics steps use explicit elapsed dt, not this clock.
 */
fun interface RuntimeClock {
    fun nowMillis(): Long
}

object SystemRuntimeClock : RuntimeClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class FakeRuntimeClock(initialMillis: Long = 0L) : RuntimeClock {
    var millis: Long = initialMillis

    override fun nowMillis(): Long = millis

    fun advanceMillis(delta: Long) {
        millis += delta
    }
}
