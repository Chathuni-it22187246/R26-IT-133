package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.data.HeatConfigCodec
import com.greenhands.app.heat.data.HeatSessionRecord
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Persistable My Greenhouse fields. Live readings, ON/OFF runtime, visualization
 * progress and AR pose stay out of storage.
 */
data class PersistableEquipmentPose(
    val id: String,
    val name: String,
    val type: EquipmentType,
    val xM: Double,
    val yM: Double,
    val zM: Double,
    val yawDeg: Double
)

data class PersistableMyGreenhouse(
    val lengthM: Double?,
    val widthM: Double?,
    val heightM: Double?,
    val cropRowCount: Int?,
    val cropId: String?,
    val cropName: String?,
    val stageId: String?,
    val stageName: String?,
    val dayTemperatureC: Double?,
    val nightTemperatureC: Double?,
    val dayHumidityPercent: Double?,
    val nightHumidityPercent: Double?,
    val circulation: CirculationThresholds?,
    val exhaust: ExhaustThresholds?,
    val fogger: FoggerThresholds?,
    val nightCirculation: CirculationThresholds?,
    val nightExhaust: ExhaustThresholds?,
    val nightFogger: FoggerThresholds?,
    val equipment: List<PersistableEquipmentPose>
)

object MyGreenhousePersistence {
    const val SAVE_ACTION = "Save My Greenhouse"
    const val SAVE_CHROME_LABEL = "Save"
    const val DEMO_SESSION_ONLY = "Demo layout is kept for this session only."
    const val SAVED = "My Greenhouse saved"

    val ALLOWED_LAYOUT_KEYS = setOf(
        "v", "layoutId", "configId", "userId", "len", "wid", "hgt", "rows",
        "ovC", "ovE", "ovF", "assumptions", "origin", "saved", "inst",
        "padP", "padW", "padH", "padE", "padWet", "padWall",
        "circCfm", "circM3s", "circThrow", "exhM3s", "exhCfm",
        "fogKgS", "fogLph", "fogSpray", "fogEta"
    )

    val ALLOWED_CONFIG_KEYS = setOf(
        "crop", "stage", "dayT", "nightT", "meanT", "dayRh", "nightRh", "mode",
        "csp", "cdp", "con", "esp", "edp", "eon", "fsp", "fon", "fdp",
        "nCsp", "nCdp", "nCon", "nEsp", "nEdp", "nEon", "nFsp", "nFon", "nFdp",
        "saved", "custom", "profile", "lastProfile"
    )

    private val FORBIDDEN_TOKENS = listOf(
        "visualScale",
        "visualScaleFactor",
        "cameraPosition",
        "arAnchor",
        "behaviorRunning",
        "liveTemperature",
        "liveHumidity",
        "relativeHumidityPercent",
        "connected=true"
    )

    fun allowsPersistentSave(mode: PreviewMode): Boolean = mode == PreviewMode.MY_GREENHOUSE

    fun persistableLayout(layout: EquipmentLayout): EquipmentLayout =
        layout.copy(
            instances = layout.instances.filter { !it.isGhost }.map { inst ->
                inst.copy(runtimeState = EquipmentRuntimeState.NOT_RUNNING)
            }
        )

    fun snapshot(
        config: HeatConfiguration,
        session: HeatSessionRecord?,
        layout: EquipmentLayout
    ): PersistableMyGreenhouse {
        val persistable = persistableLayout(layout)
        return PersistableMyGreenhouse(
            lengthM = persistable.lengthM,
            widthM = persistable.widthM,
            heightM = persistable.heightM,
            cropRowCount = persistable.cropRowCount,
            cropId = config.crop?.id ?: session?.cropId,
            cropName = config.crop?.displayName ?: session?.cropDisplayName,
            stageId = config.stage?.id ?: session?.stageId,
            stageName = config.stage?.shortLabel ?: session?.stageDisplayName,
            dayTemperatureC = config.dayTemperatureC ?: config.targetTemperatureC ?: session?.dayTemperatureC,
            nightTemperatureC = config.nightTemperatureC ?: session?.nightTemperatureC,
            dayHumidityPercent = config.dayHumidityPercent
                ?: config.targetHumidityPercent
                ?: session?.dayHumidityPercent,
            nightHumidityPercent = config.nightHumidityPercent ?: session?.nightHumidityPercent,
            circulation = config.circulation,
            exhaust = config.exhaust,
            fogger = config.fogger,
            nightCirculation = config.nightCirculation,
            nightExhaust = config.nightExhaust,
            nightFogger = config.nightFogger,
            equipment = persistable.instances.map { inst ->
                PersistableEquipmentPose(
                    id = inst.id,
                    name = inst.displayLabel(),
                    type = inst.type,
                    xM = inst.positionM.xM,
                    yM = inst.positionM.yM,
                    zM = inst.positionM.zM,
                    yawDeg = inst.yawDeg
                )
            }
        )
    }

    fun encodedKeys(encoded: String): Set<String> =
        encoded.split("|").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx)
        }.toSet()

    fun containsForbiddenPersistedState(
        encodedLayout: String,
        encodedConfig: String = "",
        encodedSession: String = ""
    ): Boolean {
        val combined = "$encodedLayout|$encodedConfig|$encodedSession"
        if (FORBIDDEN_TOKENS.any { token -> combined.contains(token, ignoreCase = true) }) {
            return true
        }
        if (encodedLayout.contains(",ON,") || encodedLayout.contains(",OFF,")) {
            return true
        }
        return false
    }

    fun layoutKeysAreAllowed(encodedLayout: String): Boolean =
        encodedKeys(encodedLayout).all { it in ALLOWED_LAYOUT_KEYS }

    fun configKeysAreAllowed(encodedConfig: String): Boolean =
        encodedKeys(encodedConfig).all { it in ALLOWED_CONFIG_KEYS }

    fun encodePersistableLayout(layout: EquipmentLayout): String =
        EquipmentLayoutCodec.encode(persistableLayout(layout))

    fun encodePersistableConfig(config: HeatConfiguration): String =
        HeatConfigCodec.encode(config)
}
