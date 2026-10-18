package com.greenhands.app.heat.data

object HeatSessionPolicy {
    fun beginSession(stored: UserHeatSession): UserHeatSession {
        val active = stored.active ?: return stored.copy(active = null, recent = stored.recent.newestFirst())
        return stored.copy(
            active = null,
            recent = (listOf(active) + stored.recent.filter { it.id != active.id }).newestFirst()
        )
    }

    fun startConfiguration(stored: UserHeatSession, incoming: HeatSessionRecord): UserHeatSession {
        val current = stored.active
        return when {
            current == null -> stored.copy(active = incoming, recent = stored.recent.newestFirst())
            current.sameLogicalConfiguration(incoming) -> stored.copy(
                active = mergeInto(current, incoming),
                recent = stored.recent.filter { it.id != current.id }.newestFirst()
            )
            else -> stored.copy(
                active = incoming,
                recent = (listOf(current) + stored.recent.filter { it.id != current.id && it.id != incoming.id })
                    .newestFirst()
            )
        }
    }

    fun deleteRecent(stored: UserHeatSession, recordId: String): UserHeatSession {
        if (stored.active?.id == recordId) return stored
        return stored.copy(recent = stored.recent.filter { it.id != recordId }.newestFirst())
    }

    private fun mergeInto(current: HeatSessionRecord, incoming: HeatSessionRecord): HeatSessionRecord = current.copy(
        cropId = incoming.cropId ?: current.cropId,
        cropDisplayName = incoming.cropDisplayName ?: current.cropDisplayName,
        stageId = incoming.stageId ?: current.stageId,
        stageDisplayName = incoming.stageDisplayName ?: current.stageDisplayName,
        dayTemperatureC = incoming.dayTemperatureC ?: current.dayTemperatureC,
        nightTemperatureC = incoming.nightTemperatureC ?: current.nightTemperatureC,
        dayHumidityPercent = incoming.dayHumidityPercent ?: current.dayHumidityPercent,
        nightHumidityPercent = incoming.nightHumidityPercent ?: current.nightHumidityPercent,
        previewMode = incoming.previewMode ?: current.previewMode,
        lengthM = incoming.lengthM ?: current.lengthM,
        widthM = incoming.widthM ?: current.widthM,
        heightM = incoming.heightM ?: current.heightM,
        cropRowCount = incoming.cropRowCount
    )

    private fun List<HeatSessionRecord>.newestFirst(): List<HeatSessionRecord> =
        sortedByDescending { it.createdAtMillis }
}
