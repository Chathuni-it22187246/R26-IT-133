package com.greenhands.app.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirebaseTelemetrySafetyTest {

    @Test
    fun firebaseDataSourceDoesNotUseWriteApis() {
        val source = sourceFile("FirebaseCurrentTelemetryDataSource.kt")
        val weather = sourceFile("FirebaseCurrentWeatherDataSource.kt")
        val path = sourceFile("CurrentTelemetryDto.kt")
        val weatherPath = sourceFile("CurrentWeatherDto.kt")
        assertTrue(source.contains("addValueEventListener"))
        assertTrue(source.contains("removeEventListener"))
        assertTrue(weather.contains("addValueEventListener"))
        assertTrue(weather.contains("removeEventListener"))
        listOf(source, weather).forEach { text ->
            assertFalse(text.contains(".setValue("))
            assertFalse(text.contains(".updateChildren("))
            assertFalse(text.contains(".push("))
            assertFalse(text.contains(".removeValue("))
            assertFalse(text.contains("telemetry/history"))
            assertFalse(text.contains("weather/history"))
            assertFalse(text.contains("getReferenceFromUrl"))
        }
        assertTrue(path.contains("greenhouses"))
        assertTrue(path.contains("GH_001"))
        assertTrue(path.contains("current"))
        assertTrue(source.contains("GreenhouseTelemetryPath.currentSegments"))
        assertTrue(weather.contains("GreenhouseWeatherPath.currentSegments"))
        assertTrue(weatherPath.contains("weather"))
        assertFalse(weather.contains("telemetry/history"))
    }

    @Test
    fun androidTelemetryDoesNotEmbedAdminCredentialsOrLogTokens() {
        val auth = sourceFile("FirebaseAnonymousAuthClient.kt")
        val holder = sourceFile("GreenHandsTelemetry.kt")
        val combined = auth + holder +
            sourceFile("FirebaseCurrentTelemetryDataSource.kt") +
            sourceFile("FirebaseCurrentWeatherDataSource.kt")
        assertFalse(combined.contains("service-account"))
        assertFalse(combined.contains("private_key"))
        assertFalse(combined.contains("Log.d"))
        assertFalse(combined.contains("Log.i"))
        assertFalse(combined.contains("getIdToken"))
        assertFalse(combined.contains("asia-southeast1.firebasedatabase.app"))
        assertFalse(combined.contains("AIza"))
        assertFalse(combined.contains("open-meteo.com"))
        assertFalse(combined.contains("HttpURLConnection"))
        assertFalse(combined.contains("OkHttp"))
    }

    private fun sourceFile(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/greenhands/app/telemetry/$name"),
            File("app/src/main/java/com/greenhands/app/telemetry/$name")
        )
        val file = candidates.first { it.exists() }
        return file.readText()
    }
}
