package com.greenhands.app.heat.data

import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.domain.ProfileMigration212
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.PROFILE_VERSION
import com.greenhands.app.heat.model.SchedulePeriod

data class HeatWorkspace(
    val selectedCrop: Crop? = null,
    val selectedStageByCrop: Map<String, String> = emptyMap(),
    val configurations: Map<String, HeatConfiguration> = emptyMap(),
    val schemaVersion: Int = HeatConfigCodec.SCHEMA_V2,
    val migratedFromV1: Boolean = false,
    val lastSchedulePeriod: SchedulePeriod = SchedulePeriod.DAY
) {
    fun current(): HeatConfiguration {
        val crop = selectedCrop ?: return HeatConfiguration()
        val stageId = selectedStageByCrop[crop.id] ?: return HeatConfiguration(crop = crop)
        return configurations[HeatConfigCodec.key(crop.id, stageId)]
            ?: HeatConfiguration(crop = crop, stage = runCatching { CropDefaults.stage(crop, stageId) }.getOrNull())
    }

    fun withSaved(config: HeatConfiguration): HeatWorkspace {
        val crop = config.crop ?: return this
        val stageId = config.stage?.id ?: return copy(selectedCrop = crop)
        val key = HeatConfigCodec.key(crop.id, stageId)
        return copy(
            selectedCrop = crop,
            selectedStageByCrop = selectedStageByCrop + (crop.id to stageId),
            configurations = configurations + (key to config)
        )
    }
}

object HeatConfigCodec {
    const val SCHEMA_V1 = 1
    const val SCHEMA_V2 = 2

    fun key(cropId: String, stageId: String): String = "${cropId}_$stageId"

    fun encode(config: HeatConfiguration): String {
        val fields = linkedMapOf(
            "crop" to (config.crop?.id ?: ""),
            "stage" to (config.stage?.id ?: ""),
            "dayT" to (config.dayTemperatureC?.toString() ?: ""),
            "nightT" to (config.nightTemperatureC?.toString() ?: ""),
            "meanT" to (config.dailyMeanTemperatureC?.toString() ?: ""),
            "dayRh" to (config.dayHumidityPercent?.toString() ?: config.targetHumidityPercent?.toString() ?: ""),
            "nightRh" to (config.nightHumidityPercent?.toString() ?: ""),
            "mode" to config.controlMode.name,
            "csp" to (config.circulation?.csp?.toString() ?: ""),
            "cdp" to (config.circulation?.cdp?.toString() ?: ""),
            "con" to (config.circulation?.con?.toString() ?: ""),
            "esp" to (config.exhaust?.esp?.toString() ?: ""),
            "edp" to (config.exhaust?.edp?.toString() ?: ""),
            "eon" to (config.exhaust?.eon?.toString() ?: ""),
            "fsp" to (config.fogger?.fsp?.toString() ?: ""),
            "fon" to (config.fogger?.fon?.toString() ?: ""),
            "fdp" to (config.fogger?.fdp?.toString() ?: ""),
            "nCsp" to (config.nightCirculation?.csp?.toString() ?: ""),
            "nCdp" to (config.nightCirculation?.cdp?.toString() ?: ""),
            "nCon" to (config.nightCirculation?.con?.toString() ?: ""),
            "nEsp" to (config.nightExhaust?.esp?.toString() ?: ""),
            "nEdp" to (config.nightExhaust?.edp?.toString() ?: ""),
            "nEon" to (config.nightExhaust?.eon?.toString() ?: ""),
            "nFsp" to (config.nightFogger?.fsp?.toString() ?: ""),
            "nFon" to (config.nightFogger?.fon?.toString() ?: ""),
            "nFdp" to (config.nightFogger?.fdp?.toString() ?: ""),
            "saved" to config.saved.toString(),
            "custom" to config.valuesAreCustomised.toString(),
            "profile" to (config.profileVersion ?: ""),
            "lastProfile" to (config.lastSuggestedProfileVersion ?: "")
        )
        return fields.entries.joinToString("|") { "${it.key}=${it.value}" }
    }

    fun decode(raw: String): HeatConfiguration? {
        if (raw.isBlank()) return null
        val map = raw.split("|").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
        val crop = Crop.fromId(map["crop"].orEmpty().ifBlank { null })
        val stageId = map["stage"].orEmpty().ifBlank { null }
        val stage = if (crop != null && stageId != null) {
            runCatching { CropDefaults.stage(crop, stageId) }.getOrElse {
                GrowthStage(stageId, stageId, stageId, "")
            }
        } else {
            null
        }
        val decoded = HeatConfiguration(
            crop = crop,
            stage = stage,
            dayTemperatureC = map["dayT"].parseDouble(),
            nightTemperatureC = map["nightT"].parseDouble(),
            dailyMeanTemperatureC = map["meanT"].parseDouble(),
            targetTemperatureC = map["topt"].parseDouble(),
            targetHumidityPercent = map["rh"].parseDouble() ?: map["dayRh"].parseDouble(),
            dayHumidityPercent = map["dayRh"].parseDouble() ?: map["rh"].parseDouble(),
            nightHumidityPercent = map["nightRh"].parseDouble(),
            controlMode = runCatching { ControlMode.valueOf(map["mode"] ?: "AUTOMATIC") }
                .getOrDefault(ControlMode.AUTOMATIC),
            circulation = thresholds(map["csp"], map["cdp"], map["con"]) { csp, cdp, con ->
                CirculationThresholds(csp, cdp, con)
            },
            exhaust = thresholds(map["esp"], map["edp"], map["eon"]) { esp, edp, eon ->
                ExhaustThresholds(esp, edp, eon)
            },
            fogger = thresholds(map["fsp"], map["fon"], map["fdp"]) { fsp, fon, fdp ->
                FoggerThresholds(fsp, fon, fdp)
            },
            nightCirculation = thresholds(map["nCsp"], map["nCdp"], map["nCon"]) { csp, cdp, con ->
                CirculationThresholds(csp, cdp, con)
            },
            nightExhaust = thresholds(map["nEsp"], map["nEdp"], map["nEon"]) { esp, edp, eon ->
                ExhaustThresholds(esp, edp, eon)
            },
            nightFogger = thresholds(map["nFsp"], map["nFon"], map["nFdp"]) { fsp, fon, fdp ->
                FoggerThresholds(fsp, fon, fdp)
            },
            saved = map["saved"]?.toBooleanStrictOrNull() ?: false,
            valuesAreCustomised = map["custom"]?.toBooleanStrictOrNull() ?: false,
            profileVersion = map["profile"].orEmpty().ifBlank { null },
            lastSuggestedProfileVersion = map["lastProfile"].orEmpty().ifBlank { null }
        )
        return ProfileMigration212.migrate(decoded)
    }

    fun migrateV1(
        cropId: String?,
        stageId: String?,
        targetTemp: Double?,
        targetRh: Double?,
        mode: String?,
        csp: Double?,
        cdp: Double?,
        con: Double?,
        esp: Double?,
        edp: Double?,
        eon: Double?,
        fsp: Double?,
        fon: Double?,
        fdp: Double?,
        saved: Boolean
    ): HeatWorkspace {
        val crop = Crop.fromId(cropId) ?: Crop.TOMATO
        val resolvedStageId = stageId ?: return HeatWorkspace(
            selectedCrop = crop,
            schemaVersion = SCHEMA_V2,
            migratedFromV1 = true
        )
        val stage = runCatching { CropDefaults.stage(crop, resolvedStageId) }.getOrNull()
            ?: GrowthStage(resolvedStageId, resolvedStageId, resolvedStageId, "")
        val config = ProfileMigration212.migrate(
            HeatConfiguration(
                crop = crop,
                stage = stage,
                targetTemperatureC = targetTemp,
                targetHumidityPercent = targetRh,
                dayHumidityPercent = targetRh,
                controlMode = runCatching { ControlMode.valueOf(mode ?: "AUTOMATIC") }
                    .getOrDefault(ControlMode.AUTOMATIC),
                circulation = if (csp != null && cdp != null && con != null) {
                    CirculationThresholds(csp, cdp, con)
                } else {
                    null
                },
                exhaust = if (esp != null && edp != null && eon != null) {
                    ExhaustThresholds(esp, edp, eon)
                } else {
                    null
                },
                fogger = if (fsp != null && fon != null && fdp != null) {
                    FoggerThresholds(fsp, fon, fdp)
                } else {
                    null
                },
                saved = saved,
                valuesAreCustomised = true,
                profileVersion = PROFILE_VERSION,
                lastSuggestedProfileVersion = "2.0.0"
            )
        )
        val key = key(crop.id, resolvedStageId)
        return HeatWorkspace(
            selectedCrop = crop,
            selectedStageByCrop = mapOf(crop.id to resolvedStageId),
            configurations = mapOf(key to config),
            schemaVersion = SCHEMA_V2,
            migratedFromV1 = true
        )
    }

    private fun <T> thresholds(
        a: String?,
        b: String?,
        c: String?,
        build: (Double, Double, Double) -> T
    ): T? {
        val va = a.parseDouble()
        val vb = b.parseDouble()
        val vc = c.parseDouble()
        return if (va != null && vb != null && vc != null) build(va, vb, vc) else null
    }

    private fun String?.parseDouble(): Double? = this?.ifBlank { null }?.toDoubleOrNull()
}
