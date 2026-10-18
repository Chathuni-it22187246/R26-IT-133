package com.greenhands.app.equipment.domain

/**
 * SI conversions for recommendation inputs. Factors are exact identities or
 * documented engineering conversions, not greenhouse design laws.
 */
object AirflowUnits {
    /** International foot: 1 ft = 0.3048 m exactly. */
    const val METRES_PER_FOOT = 0.3048

    /** 1 m² = 1 / 0.3048² ft². */
    const val SQUARE_FEET_PER_SQUARE_METRE = 1.0 / (METRES_PER_FOOT * METRES_PER_FOOT)

    /** 1 cubic foot = 0.3048³ m³. */
    const val CUBIC_METRES_PER_CUBIC_FOOT = METRES_PER_FOOT * METRES_PER_FOOT * METRES_PER_FOOT

    /** 1 minute = 60 s. */
    const val SECONDS_PER_MINUTE = 60.0

    /** Density of liquid water used only to convert L/h → kg/s. PROJECT_ASSUMPTION 1000 kg/m³. */
    const val WATER_DENSITY_KG_M3 = 1000.0

    fun squareMetresToSquareFeet(areaM2: Double): Double {
        requireFiniteNonNegative(areaM2, "areaM2")
        return areaM2 * SQUARE_FEET_PER_SQUARE_METRE
    }

    fun squareFeetToSquareMetres(areaFt2: Double): Double {
        requireFiniteNonNegative(areaFt2, "areaFt2")
        return areaFt2 / SQUARE_FEET_PER_SQUARE_METRE
    }

    fun cubicFeetPerMinuteToM3s(cfm: Double): Double {
        requireFiniteNonNegative(cfm, "cfm")
        return cfm * CUBIC_METRES_PER_CUBIC_FOOT / SECONDS_PER_MINUTE
    }

    fun m3sToCubicFeetPerMinute(flowM3s: Double): Double {
        requireFiniteNonNegative(flowM3s, "flowM3s")
        return flowM3s * SECONDS_PER_MINUTE / CUBIC_METRES_PER_CUBIC_FOOT
    }

    fun litresPerHourToKgS(litresPerHour: Double): Double {
        requireFiniteNonNegative(litresPerHour, "litresPerHour")
        val m3s = litresPerHour / 1000.0 / 3600.0
        return m3s * WATER_DENSITY_KG_M3
    }

    fun kgSToLitresPerHour(kgS: Double): Double {
        requireFiniteNonNegative(kgS, "kgS")
        return kgS / WATER_DENSITY_KG_M3 * 1000.0 * 3600.0
    }

    private fun requireFiniteNonNegative(value: Double, name: String) {
        require(value.isFinite() && value >= 0.0) { "$name must be finite and >= 0" }
    }
}
