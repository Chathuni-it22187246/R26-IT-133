package com.greenhands.app.heat.data

object HeatSessionCodec {
    private const val RECORD_SEPARATOR = "\u001e"

    fun encode(record: HeatSessionRecord): String {
        val fields = linkedMapOf(
            "id" to record.id,
            "created" to record.createdAtMillis.toString(),
            "crop" to record.cropId.orEmpty(),
            "cropName" to record.cropDisplayName.orEmpty(),
            "stage" to record.stageId.orEmpty(),
            "stageName" to record.stageDisplayName.orEmpty(),
            "dayT" to (record.dayTemperatureC?.toString() ?: ""),
            "nightT" to (record.nightTemperatureC?.toString() ?: ""),
            "dayRh" to (record.dayHumidityPercent?.toString() ?: ""),
            "nightRh" to (record.nightHumidityPercent?.toString() ?: ""),
            "mode" to record.previewMode.orEmpty(),
            "length" to (record.lengthM?.toString() ?: ""),
            "width" to (record.widthM?.toString() ?: ""),
            "height" to (record.heightM?.toString() ?: ""),
            "cropRows" to record.cropRowCount.toString()
        )
        return fields.entries.joinToString("|") { "${it.key}=${escape(it.value)}" }
    }

    fun decode(raw: String): HeatSessionRecord? {
        if (raw.isBlank()) return null
        val map = raw.split("|").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to unescape(part.substring(idx + 1))
        }.toMap()
        val id = map["id"].orEmpty().ifBlank { return null }
        val created = map["created"]?.toLongOrNull() ?: return null
        return HeatSessionRecord(
            id = id,
            createdAtMillis = created,
            cropId = map["crop"].orEmpty().ifBlank { null },
            cropDisplayName = map["cropName"].orEmpty().ifBlank { null },
            stageId = map["stage"].orEmpty().ifBlank { null },
            stageDisplayName = map["stageName"].orEmpty().ifBlank { null },
            dayTemperatureC = map["dayT"].parseDouble(),
            nightTemperatureC = map["nightT"].parseDouble(),
            dayHumidityPercent = map["dayRh"].parseDouble(),
            nightHumidityPercent = map["nightRh"].parseDouble(),
            previewMode = map["mode"].orEmpty().ifBlank { null },
            lengthM = map["length"].parseDouble(),
            widthM = map["width"].parseDouble(),
            heightM = map["height"].parseDouble(),
            cropRowCount = CropRowCount.resolved(map["cropRows"]?.toIntOrNull())
        )
    }

    fun encodeList(records: List<HeatSessionRecord>): String =
        records.joinToString(RECORD_SEPARATOR) { encode(it) }

    fun decodeList(raw: String?): List<HeatSessionRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { decode(it) }
    }

    private fun escape(value: String): String = value.replace("|", "%7C").replace(RECORD_SEPARATOR, "%1E")

    private fun unescape(value: String): String = value.replace("%7C", "|").replace("%1E", RECORD_SEPARATOR)

    private fun String?.parseDouble(): Double? = this?.ifBlank { null }?.toDoubleOrNull()
}
