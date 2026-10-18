package com.greenhands.app.preview3d.domain

/**
 * Integer crop-row input for My Greenhouse preview setup.
 * Demo Mode does not use this field and always renders 3 rows.
 */
object CropRowValidation {
    const val DEFAULT = 3
    const val MIN = 1
    const val MAX = 20

    private val wholeNumber = Regex("^-?\\d+$")

    data class Result(
        val value: Int? = null,
        val error: String? = null
    ) {
        val isValid: Boolean get() = value != null && error == null
    }

    fun parse(raw: String): Result {
        if (raw.isBlank()) return Result(error = "Enter the number of crop rows")
        val trimmed = raw.trim()
        if (!wholeNumber.matches(trimmed)) {
            return Result(error = "Enter a whole number")
        }
        val value = trimmed.toIntOrNull()
            ?: return Result(error = "Enter a whole number")
        if (value < MIN || value > MAX) {
            return Result(
                value = value,
                error = "Number of crop rows must be between $MIN and $MAX"
            )
        }
        return Result(value = value)
    }
}
