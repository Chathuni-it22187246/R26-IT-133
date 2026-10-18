package com.greenhands.app.telemetry

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object TelemetryNumbers {
    fun asFiniteDouble(value: Any?): Double? {
        val number = when (value) {
            null -> return null
            is Double -> value
            is Float -> value.toDouble()
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Short -> value.toDouble()
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> return null
        } ?: return null
        return number.takeIf { it.isFinite() }
    }

    fun asLong(value: Any?): Long? {
        return when (value) {
            null -> null
            is Long -> value
            is Int -> value.toLong()
            is Short -> value.toLong()
            is Double -> if (value.isFinite()) value.toLong() else null
            is Float -> if (value.isFinite()) value.toLong() else null
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: asFiniteDouble(value)?.toLong()
            else -> null
        }
    }

    fun asText(value: Any?): String? {
        val text = value?.toString()?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() }
    }
}

internal object TelemetryTimestamps {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX"
    )

    fun resolveMillis(serverTimestampMs: Long?, receivedAt: String?): Long? {
        val server = serverTimestampMs
        if (server != null && server > 0L) return server
        return parseIso8601Utc(receivedAt)
    }

    fun parseIso8601Utc(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        for (pattern in patterns) {
            val parsed = parse(value, pattern)
            if (parsed != null) return parsed
        }
        val withZ = if (value.endsWith("Z", ignoreCase = true) || value.contains('+')) {
            value
        } else {
            value + "Z"
        }
        return parse(withZ.substringBefore('.').let { stem ->
            if (stem.endsWith("Z", ignoreCase = true)) stem else "${stem}Z"
        }, "yyyy-MM-dd'T'HH:mm:ss'Z'")
    }

    private fun parse(value: String, pattern: String): Long? {
        return try {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.timeZone = utc
            format.isLenient = false
            format.parse(value)?.time
        } catch (_: ParseException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

fun formatLiveClimateNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value)
