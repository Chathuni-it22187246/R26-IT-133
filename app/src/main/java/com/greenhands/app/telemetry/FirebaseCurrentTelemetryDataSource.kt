package com.greenhands.app.telemetry

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Authenticated read-only listener for current greenhouse telemetry.
 * Listeners only; no Realtime Database writes.
 */
class FirebaseCurrentTelemetryDataSource(
    private val database: () -> FirebaseDatabase = { FirebaseDatabase.getInstance() }
) : CurrentTelemetryDataSource {
    override fun listen(onEvent: (Result<CurrentTelemetryDto?>) -> Unit): TelemetrySubscription {
        var reference = database().reference
        for (segment in GreenhouseTelemetryPath.currentSegments) {
            reference = reference.child(segment)
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onEvent(Result.success(FirebaseTelemetrySnapshotReader.read(snapshot)))
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

internal object FirebaseTelemetrySnapshotReader {
    fun read(snapshot: DataSnapshot): CurrentTelemetryDto? {
        if (!snapshot.exists()) return null
        val rawSnap = snapshot.child("raw")
        val raw = if (rawSnap.exists()) {
            CurrentTelemetryRawDto(
                htu21dTemperatureC = TelemetryNumbers.asFiniteDouble(rawSnap.child("htu21d_temperature_c").value),
                ds18b20TemperatureC = TelemetryNumbers.asFiniteDouble(rawSnap.child("ds18b20_temperature_c").value),
                htu21dHumidityPct = TelemetryNumbers.asFiniteDouble(rawSnap.child("htu21d_humidity_pct").value)
            )
        } else {
            null
        }
        return CurrentTelemetryDto(
            temperatureC = TelemetryNumbers.asFiniteDouble(snapshot.child("temperature_c").value),
            humidityPct = TelemetryNumbers.asFiniteDouble(snapshot.child("humidity_pct").value),
            temperatureSource = TelemetryNumbers.asText(snapshot.child("temperature_source").value),
            humiditySource = TelemetryNumbers.asText(snapshot.child("humidity_source").value),
            receivedAt = TelemetryNumbers.asText(snapshot.child("received_at").value),
            serverTimestampMs = TelemetryNumbers.asLong(snapshot.child("server_timestamp_ms").value),
            deviceId = TelemetryNumbers.asText(snapshot.child("device_id").value),
            greenhouseId = TelemetryNumbers.asText(snapshot.child("greenhouse_id").value),
            status = TelemetryNumbers.asText(snapshot.child("status").value),
            raw = raw
        )
    }
}
