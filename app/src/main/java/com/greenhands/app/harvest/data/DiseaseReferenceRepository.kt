package com.greenhands.app.harvest.data

import com.greenhands.app.harvest.detection.TomatoDiseaseLabels
import com.greenhands.app.harvest.model.DiseaseReference
import com.greenhands.app.harvest.model.PlantHealthReasons

/**
 * Documented symptoms and recommendations for classifier classes.
 * Kept separate from model inference. Does not invent diagnoses.
 *
 * Tomato_mosaic_virus is never mapped to Cucumber Mosaic Virus (CMV).
 */
data class DiseaseClassReference(
    val rawClassName: String,
    val displayName: String,
    val documentedSymptoms: List<String>,
    val recommendation: String,
    val sourceNote: String
)

class DiseaseReferenceRepository(
    private val csvDiseases: List<DiseaseReference> = emptyList()
) {
    fun lookup(rawClassName: String): DiseaseClassReference {
        val display = TomatoDiseaseLabels.displayName(rawClassName)
        if (TomatoDiseaseLabels.isHealthy(rawClassName)) {
            return DiseaseClassReference(
                rawClassName = rawClassName,
                displayName = display,
                documentedSymptoms = emptyList(),
                recommendation = PlantHealthReasons.RECOMMEND_HEALTHY,
                sourceNote = PlantHealthReasons.SOURCE_CLASSIFIER
            )
        }
        val csv = csvMatch(rawClassName)
        val symptoms = csv?.documentedSymptoms
            ?.split(';', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val recommendation = listOfNotNull(
            PlantHealthReasons.RECOMMEND_GENERIC,
            csv?.diagnosisNote,
            csv?.managementSummary
        ).distinct().joinToString(" ")
        val source = listOfNotNull(
            PlantHealthReasons.SOURCE_CLASSIFIER,
            csv?.sourceUrl,
            csv?.let { PlantHealthReasons.SOURCE_CSV }
        ).joinToString(" · ")
        return DiseaseClassReference(
            rawClassName = rawClassName,
            displayName = display,
            documentedSymptoms = symptoms,
            recommendation = recommendation,
            sourceNote = source
        )
    }

    private fun csvMatch(rawClassName: String): DiseaseReference? {
        val key = rawClassName.trim().lowercase().replace(' ', '_')
        if (key.contains("mosaic")) return null
        val tomato = csvDiseases.filter { it.cropType.equals("Tomato", ignoreCase = true) }
        return tomato.firstOrNull { row ->
            val name = row.diseaseName.lowercase()
            when {
                key == "early_blight" -> name.contains("early blight")
                key == "late_blight" -> name.contains("late blight")
                key == "powdery_mildew" -> name.contains("powdery mildew")
                key.contains("yellow_leaf_curl") || key.contains("tylcv") ->
                    name.contains("yellow leaf curl") || name.contains("tylcv")
                else -> false
            }
        }
    }
}
