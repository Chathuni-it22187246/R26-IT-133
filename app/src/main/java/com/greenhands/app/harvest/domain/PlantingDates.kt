package com.greenhands.app.harvest.domain

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Transplant date helpers for harvest session state.
 * Does not estimate remaining maturity days.
 */
object PlantingDates {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    fun isNotAfterToday(utcMidnightMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return daysSinceTransplant(utcMidnightMillis, nowMillis) != null
    }

    fun daysSinceTransplant(utcMidnightMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int? =
        daysSincePlanting(utcMidnightMillis, nowMillis)

    fun daysSincePlanting(utcMidnightMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int? {
        if (utcMidnightMillis < 0L) return null
        val plantedLocal = utcDateToLocalMidnightMillis(utcMidnightMillis)
        val todayLocal = localMidnightMillis(nowMillis)
        if (plantedLocal > todayLocal) return null
        return TimeUnit.MILLISECONDS.toDays(todayLocal - plantedLocal).toInt()
    }

    fun formatDisplay(utcMidnightMillis: Long, locale: Locale = Locale.getDefault()): String {
        val cal = Calendar.getInstance(utc).apply { timeInMillis = utcMidnightMillis }
        val local = Calendar.getInstance().apply {
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(local.timeInMillis))
    }

    fun localYear(nowMillis: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)

    private fun utcDateToLocalMidnightMillis(utcMidnightMillis: Long): Long {
        val utcCal = Calendar.getInstance(utc).apply { timeInMillis = utcMidnightMillis }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun localMidnightMillis(nowMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
