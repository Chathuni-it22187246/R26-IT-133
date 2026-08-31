package com.greenhands.app.harvest.data.csv

import android.content.Context
import java.io.IOException

data class CsvTable(
    val headers: List<String>,
    val rows: List<Map<String, String>>,
    val sourceName: String = ""
)

data class CsvLoadException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reads UTF-8 CSV text from assets or raw strings and maps rows by header name.
 * Supports quoted fields (including commas inside quotes).
 */
class AssetCsvLoader {

    fun readAssetText(context: Context, assetFileName: String): String {
        return try {
            context.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw CsvLoadException("Missing or unreadable asset: $assetFileName", e)
        }
    }

    fun readTableFromAsset(context: Context, assetFileName: String): CsvTable {
        return parseTable(readAssetText(context, assetFileName), sourceName = assetFileName)
    }

    fun parseTable(csvText: String, sourceName: String = ""): CsvTable {
        val normalized = csvText.removePrefix("\uFEFF")
        val lines = normalized
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw CsvLoadException("CSV is empty${sourceLabel(sourceName)}")
        }

        val headers = parseCsvLine(lines.first()).map { it.trim().removePrefix("\uFEFF") }
        if (headers.isEmpty() || headers.any { it.isEmpty() }) {
            throw CsvLoadException("CSV header row is invalid${sourceLabel(sourceName)}")
        }
        if (headers.toSet().size != headers.size) {
            throw CsvLoadException("CSV header row has duplicate columns${sourceLabel(sourceName)}")
        }

        val rows = lines.drop(1).mapIndexedNotNull { index, line ->
            val cells = parseCsvLine(line)
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            if (cells.size != headers.size) {
                throw CsvLoadException(
                    "Malformed row ${index + 2}: expected ${headers.size} columns, found ${cells.size}" +
                        sourceLabel(sourceName)
                )
            }
            headers.indices.associate { i -> headers[i] to cells[i].trim() }
        }
        return CsvTable(headers = headers, rows = rows, sourceName = sourceName)
    }

    fun requireColumns(table: CsvTable, required: Collection<String>) {
        val missing = required.filterNot { it in table.headers }
        if (missing.isNotEmpty()) {
            throw CsvLoadException(
                "Missing columns ${missing.joinToString()}${sourceLabel(table.sourceName)}"
            )
        }
    }

    /**
     * RFC4180-style split: commas outside quotes; "" inside quotes → ".
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun sourceLabel(sourceName: String): String =
        if (sourceName.isBlank()) "" else " ($sourceName)"

    companion object {
        const val CROP_REFERENCE = "01_crop_reference.csv"
        const val VARIETY_REFERENCE = "02_variety_reference.csv"
        const val DISEASE_REFERENCE = "03_disease_reference.csv"
        const val HARVEST_RULES = "04_harvest_rules.csv"
        const val REAL_OBSERVATIONS = "05_real_observations_updated.csv"
    }
}
