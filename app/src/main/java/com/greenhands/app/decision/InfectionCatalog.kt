package com.greenhands.app.decision

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

data class InfectionRecord(
    val plantType: String,
    val infectionShortName: String,
    val infectionFullName: String,
    val severityLevel: String,
    val visibleSymptoms: String,
    val treatmentDescription: String,
    val biologicalControl: String,
    val chemicalControl: String,
    val preventionSteps: String
) {
    fun toDecisionResponse(): InfectionDecisionResponse = InfectionDecisionResponse(
        plantType = plantType,
        infectionShortName = infectionShortName,
        infectionFullName = infectionFullName,
        severityLevel = severityLevel,
        visibleSymptoms = visibleSymptoms,
        treatmentDescription = treatmentDescription,
        biologicalControl = biologicalControl,
        chemicalControl = chemicalControl,
        preventionSteps = preventionSteps
    )
}

object InfectionCatalog {
    private const val ASSET_PATH = "ml/plant_infections_dataset.csv"
    private val tokenRegex = Regex("[a-z0-9]+")

    fun loadFromAssets(context: Context): List<InfectionRecord> {
        context.assets.open(ASSET_PATH).use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val header = parseCsvLine(reader.readLine().orEmpty())
            val index = header.mapIndexed { i, name -> name.trim() to i }.toMap()
            fun col(row: List<String>, name: String): String =
                row.getOrElse(index[name] ?: -1) { "" }.trim()

            return reader.lineSequence()
                .filter { it.isNotBlank() }
                .map { parseCsvLine(it) }
                .filter { it.size >= 9 }
                .map { row ->
                    InfectionRecord(
                        plantType = col(row, "plant_type"),
                        infectionShortName = col(row, "infection_short_name"),
                        infectionFullName = col(row, "infection_full_name"),
                        severityLevel = col(row, "severity_level"),
                        visibleSymptoms = col(row, "visible_symptoms"),
                        treatmentDescription = col(row, "treatment_description"),
                        biologicalControl = col(row, "biological_control"),
                        chemicalControl = col(row, "chemical_control"),
                        preventionSteps = col(row, "prevention_steps")
                    )
                }
                .toList()
        }
    }

    fun match(
        records: List<InfectionRecord>,
        query: String? = null,
        symptoms: String? = null,
        plantType: String? = null,
        infectionName: String? = null
    ): InfectionRecord? {
        if (records.isEmpty()) return null
        val tokens = tokensOf(query, symptoms, plantType, infectionName)
        val plant = plantType?.trim()?.lowercase(Locale.US).orEmpty()
        val name = (infectionName ?: query).orEmpty().trim().lowercase(Locale.US)

        var best = records.first()
        var bestScore = -1.0
        records.forEach { row ->
            val short = row.infectionShortName.lowercase(Locale.US)
            val full = row.infectionFullName.lowercase(Locale.US)
            val haystack = listOf(
                row.plantType, row.infectionShortName, row.infectionFullName, row.visibleSymptoms
            ).joinToString(" ").lowercase(Locale.US)
            var score = 0.0
            if (name.isNotBlank() && name == short) score += 20.0
            else if (name.isNotBlank() && short.contains(name)) score += 12.0
            else if (name.isNotBlank() && full.contains(name)) score += 8.0
            if (plant.isNotBlank() && plant == row.plantType.lowercase(Locale.US)) score += 6.0
            tokens.forEach { token ->
                score += when {
                    short.contains(token) -> 3.0
                    full.contains(token) -> 2.0
                    haystack.contains(token) -> 1.0
                    else -> 0.0
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = row
            }
        }
        if (bestScore <= 0 && tokens.isNotEmpty() && plant.isNotBlank()) {
            records.firstOrNull { it.plantType.equals(plantType, ignoreCase = true) }?.let { return it }
        }
        return best
    }

    fun parseCsvLine(line: String): List<String> {
        val result = ArrayList<String>(9)
        val sb = StringBuilder()
        var inQuotes = false
        line.forEach { c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString())
        return result
    }

    private fun tokensOf(vararg parts: String?): List<String> {
        val blob = parts.filterNotNull().joinToString(" ").lowercase(Locale.US)
        return tokenRegex.findAll(blob).map { it.value }.filter { it.length > 2 }.toList()
    }
}
