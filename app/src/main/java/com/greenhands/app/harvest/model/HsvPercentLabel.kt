package com.greenhands.app.harvest.model

fun hsvPercentLabel(value: Float): String =
    String.format(java.util.Locale.US, "%.0f%%", value)
