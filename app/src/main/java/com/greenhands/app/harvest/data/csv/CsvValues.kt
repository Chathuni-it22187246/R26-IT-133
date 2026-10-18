package com.greenhands.app.harvest.data.csv

/**
 * Shared CSV cell helpers. Blank and calibration/source placeholders stay null-like —
 * never coerced into invented numeric values.
 */
object CsvValues {
    private val UNKNOWN_OR_PENDING = setOf(
        "TO_BE_SOURCED_OR_FIELD_VALIDATED",
        "TO_BE_CALIBRATED_FROM_REAL_IMAGES",
        "TO_BE_CALIBRATED",
        "TO_BE_VALIDATED",
        "NOT_YET_CALIBRATED"
    )

    fun optionalString(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun isUnknownOrPending(raw: String?): Boolean {
        val value = optionalString(raw) ?: return false
        return value.uppercase() in UNKNOWN_OR_PENDING ||
            value.uppercase().startsWith("TO_BE_")
    }

    /**
     * Numeric parse for single values only. Ranges (e.g. "55-60"), blanks, and
     * TO_BE_* placeholders return null.
     */
    fun optionalDouble(raw: String?): Double? {
        val value = optionalString(raw) ?: return null
        if (isUnknownOrPending(value)) return null
        if (value.contains('-') && !value.startsWith("-")) return null
        return value.toDoubleOrNull()
    }

    fun optionalInt(raw: String?): Int? {
        val value = optionalString(raw) ?: return null
        if (isUnknownOrPending(value)) return null
        if (value.contains('-') && !value.startsWith("-")) return null
        return value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
    }

    fun optionalBoolean(raw: String?): Boolean? {
        val value = optionalString(raw) ?: return null
        if (isUnknownOrPending(value)) return null
        return when (value.lowercase()) {
            "true", "yes", "1", "y" -> true
            "false", "no", "0", "n" -> false
            else -> null
        }
    }

    /** Keeps textual markers (including TO_BE_*) when present; blanks → null. */
    fun optionalMarkerOrText(raw: String?): String? = optionalString(raw)
}
