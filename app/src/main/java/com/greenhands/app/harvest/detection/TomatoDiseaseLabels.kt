package com.greenhands.app.harvest.detection

/**
 * Raw class names from tomato_disease_labels.txt and user-facing display names.
 * Tomato_mosaic_virus is Tomato Mosaic Virus — never Cucumber Mosaic Virus.
 */
object TomatoDiseaseLabels {
    const val HEALTHY = "healthy"

    val DEFAULT_ORDER: List<String> = listOf(
        "Bacterial_spot",
        "Early_blight",
        "Late_blight",
        "Leaf_Mold",
        "Septoria_leaf_spot",
        "Spider_mites Two-spotted_spider_mite",
        "Target_Spot",
        "Tomato_Yellow_Leaf_Curl_Virus",
        "Tomato_mosaic_virus",
        HEALTHY,
        "powdery_mildew"
    )

    fun displayName(rawClassName: String): String {
        val key = rawClassName.trim()
        return DISPLAY_NAMES[key] ?: DISPLAY_NAMES[normalizeKey(key)] ?: humanize(key)
    }

    fun isHealthy(rawClassName: String): Boolean =
        rawClassName.trim().equals(HEALTHY, ignoreCase = true)

    fun labelAt(index: Int, labels: List<String>): String {
        labels.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { return it }
        return DEFAULT_ORDER.getOrElse(index) { "class_$index" }
    }

    private fun normalizeKey(raw: String): String =
        raw.trim().replace(' ', '_').lowercase()

    private fun humanize(raw: String): String =
        raw.replace('_', ' ').trim()

    private val DISPLAY_NAMES: Map<String, String> = mapOf(
        "Bacterial_spot" to "Bacterial Spot",
        "Early_blight" to "Early Blight",
        "Late_blight" to "Late Blight",
        "Leaf_Mold" to "Leaf Mold",
        "Septoria_leaf_spot" to "Septoria Leaf Spot",
        "Spider_mites Two-spotted_spider_mite" to "Spider Mites / Two-Spotted Spider Mite",
        "Target_Spot" to "Target Spot",
        "Tomato_Yellow_Leaf_Curl_Virus" to "Tomato Yellow Leaf Curl Virus",
        "Tomato_mosaic_virus" to "Tomato Mosaic Virus",
        HEALTHY to "Healthy",
        "powdery_mildew" to "Powdery Mildew"
    )
}
