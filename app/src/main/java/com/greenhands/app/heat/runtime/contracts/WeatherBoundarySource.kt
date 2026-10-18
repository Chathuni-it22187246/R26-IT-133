package com.greenhands.app.heat.runtime.contracts

/**
 * Outside weather-boundary acquisition contract.
 *
 * Visualization start/stop must not [disconnect] or [close] this source.
 * Networking is not implemented in this phase; tests use an in-memory fake.
 */
interface WeatherBoundarySource {
    fun latest(): WeatherBoundary
    fun disconnect()
    fun close()
}

class InMemoryWeatherBoundarySource(
    initial: WeatherBoundary = WeatherBoundary.missing()
) : WeatherBoundarySource {
    private var boundary: WeatherBoundary = initial
    var disconnectCount: Int = 0
        private set
    var closeCount: Int = 0
        private set

    override fun latest(): WeatherBoundary = boundary

    fun publish(next: WeatherBoundary) {
        boundary = next
    }

    override fun disconnect() {
        disconnectCount++
    }

    override fun close() {
        closeCount++
    }
}
