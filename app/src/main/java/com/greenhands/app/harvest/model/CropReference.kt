package com.greenhands.app.harvest.model

/**
 * Row from 01_crop_reference.csv.
 * Range fields stay textual (e.g. "21-24") — not invented midpoints.
 */
data class CropReference(
    val cropType: String,
    val scientificName: String?,
    val optimumTemperatureC: String?,
    val soilPhRange: String?,
    val transplantAfterSowingDays: String?,
    val harvestIndicator: String?,
    val postharvestNote: String?,
    val sourceUrl: String?,
    val sourceNote: String?
)
