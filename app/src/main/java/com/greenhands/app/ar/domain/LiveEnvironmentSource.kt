package com.greenhands.app.ar.domain

/**
 * Live inside-climate acquisition contract.
 *
 * Visualization start/stop must not [disconnect] or [close] this source.
 * Networking is not implemented in this phase; tests use an in-memory fake.
 */
interface LiveEnvironmentSource {
    fun latest(): ArLiveEnvironmentReading
    fun disconnect()
    fun close()
}

class InMemoryLiveEnvironmentSource(
    initial: ArLiveEnvironmentReading = ArLiveEnvironmentReading.DISCONNECTED
) : LiveEnvironmentSource {
    private var reading: ArLiveEnvironmentReading = initial
    var disconnectCount: Int = 0
        private set
    var closeCount: Int = 0
        private set

    override fun latest(): ArLiveEnvironmentReading = reading

    fun publish(next: ArLiveEnvironmentReading) {
        reading = next
    }

    override fun disconnect() {
        disconnectCount++
    }

    override fun close() {
        closeCount++
    }
}
