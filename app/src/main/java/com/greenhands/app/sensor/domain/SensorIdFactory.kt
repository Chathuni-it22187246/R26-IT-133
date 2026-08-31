package com.greenhands.app.sensor.domain

object SensorIdFactory {
    fun idFor(sequence: Int): String = "S$sequence"
}
