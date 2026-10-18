package com.greenhands.app.telemetry

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Authenticated read-only listener for current modelled outside weather.
 * Listeners only; no Realtime Database writes.
 */
class FirebaseCurrentWeatherDataSource(
    private val database: () -> FirebaseDatabase = { FirebaseDatabase.getInstance() }
) : CurrentWeatherDataSource {
    override fun listen(onEvent: (Result<CurrentWeatherDto?>) -> Unit): TelemetrySubscription {
        var reference = database().reference
        for (segment in GreenhouseWeatherPath.currentSegments) {
            reference = reference.child(segment)
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onEvent(Result.success(FirebaseWeatherSnapshotReader.read(snapshot)))
            }

            override fun onCancelled(error: DatabaseError) {
                onEvent(Result.failure(error.toException()))
            }
        }
        reference.addValueEventListener(listener)
        return TelemetrySubscription {
            reference.removeEventListener(listener)
        }
    }
}

internal object FirebaseWeatherSnapshotReader {
    fun read(snapshot: DataSnapshot): CurrentWeatherDto? {
        if (!snapshot.exists()) return null
        return CurrentWeatherDto(
            outsideTemperatureC = TelemetryNumbers.asFiniteDouble(
                snapshot.child("outside_temperature_c").value
            ),
            outsideHumidityPct = TelemetryNumbers.asFiniteDouble(
                snapshot.child("outside_humidity_pct").value
            ),
            source = TelemetryNumbers.asText(snapshot.child("source").value),
            locationName = TelemetryNumbers.asText(snapshot.child("location_name").value),
            observedAt = TelemetryNumbers.asText(snapshot.child("observed_at").value),
            fetchedAt = TelemetryNumbers.asText(snapshot.child("fetched_at").value),
            serverTimestampMs = TelemetryNumbers.asLong(snapshot.child("server_timestamp_ms").value),
            status = TelemetryNumbers.asText(snapshot.child("status").value)
        )
    }
}
