package com.greenhands.app.harvest.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object HarvestScanTimestamps {
    fun formatList(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val format = SimpleDateFormat("d MMM yyyy • HH:mm", locale)
        format.timeZone = timeZone
        return format.format(Date(epochMillis))
    }
}
