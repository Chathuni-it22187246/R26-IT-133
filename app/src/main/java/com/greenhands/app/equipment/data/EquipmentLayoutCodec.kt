package com.greenhands.app.equipment.data

import com.greenhands.app.equipment.domain.InstanceNumbering
import com.greenhands.app.equipment.domain.UserPlacementCoordinates
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.MountingType
import com.greenhands.app.equipment.model.PlacementValidity

/**
 * Versioned layout codec. Unknown future fields are ignored. Missing fields use defaults
 * so older stored records continue to decode.
 */
object EquipmentLayoutCodec {
    const val SCHEMA_V1 = 1
    const val SCHEMA_V2 = UserPlacementCoordinates.SCHEMA_VERSION
    private const val RECORD_SEPARATOR = "\u001e"
    private const val INSTANCE_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    fun encode(layout: EquipmentLayout): String {
        val toWrite = if (layout.schemaVersion < SCHEMA_V2) {
            layout.copy(
                schemaVersion = SCHEMA_V2,
                instances = layout.instances.map { inst ->
                    val (point, yaw) = UserPlacementCoordinates.migrateFromLengthXWidthY(
                        inst.positionM,
                        inst.yawDeg
                    )
                    inst.copy(positionM = point, yawDeg = yaw)
                }
            )
        } else {
            layout.copy(schemaVersion = SCHEMA_V2)
        }
        val fields = linkedMapOf(
            "v" to toWrite.schemaVersion.toString(),
            "layoutId" to toWrite.layoutId,
            "configId" to toWrite.configurationId,
            "userId" to toWrite.userId,
            "len" to toWrite.lengthM.toString(),
            "wid" to toWrite.widthM.toString(),
            "hgt" to toWrite.heightM.toString(),
            "rows" to toWrite.cropRowCount.toString(),
            "ovC" to (toWrite.countOverrideCirculation?.toString() ?: ""),
            "ovE" to (toWrite.countOverrideExhaust?.toString() ?: ""),
            "ovF" to (toWrite.countOverrideFogger?.toString() ?: ""),
            "assumptions" to (toWrite.recommendationAssumptions ?: ""),
            "origin" to toWrite.origin.name,
            "saved" to if (toWrite.explicitlySaved) "1" else "0",
            "padP" to when (toWrite.coolingPadPresent) {
                true -> "1"
                false -> "0"
                null -> ""
            },
            "padW" to (toWrite.coolingPadWidthM?.toString() ?: ""),
            "padH" to (toWrite.coolingPadHeightM?.toString() ?: ""),
            "padE" to (toWrite.coolingPadEfficiencyFraction?.toString() ?: ""),
            "padWet" to when (toWrite.coolingPadWetActive) {
                true -> "1"
                false -> "0"
                null -> ""
            },
            "padWall" to (toWrite.coolingPadWall ?: ""),
            "circCfm" to (toWrite.circulationRatedCfm?.toString() ?: ""),
            "circM3s" to (toWrite.circulationRatedM3s?.toString() ?: ""),
            "circThrow" to (toWrite.circulationThrowRadiusM?.toString() ?: ""),
            "exhM3s" to (toWrite.exhaustRatedAirflowM3s?.toString() ?: ""),
            "exhCfm" to (toWrite.exhaustRatedAirflowCfm?.toString() ?: ""),
            "fogKgS" to (toWrite.foggerWaterFlowKgS?.toString() ?: ""),
            "fogLph" to (toWrite.foggerWaterFlowLph?.toString() ?: ""),
            "fogSpray" to (toWrite.foggerSprayRadiusM?.toString() ?: ""),
            "fogEta" to (toWrite.foggerEvaporationEfficiency?.toString() ?: ""),
            "inst" to toWrite.instances.joinToString(INSTANCE_SEPARATOR) { encodeInstance(it) }
        )
        return fields.entries.joinToString("|") { "${it.key}=${escape(it.value)}" }
    }

    fun decode(raw: String): EquipmentLayout? {
        if (raw.isBlank()) return null
        val map = raw.split("|").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to unescape(part.substring(idx + 1))
        }.toMap()
        val layoutId = map["layoutId"].orEmpty().ifBlank { return null }
        val configId = map["configId"].orEmpty().ifBlank { return null }
        val userId = map["userId"].orEmpty()
        val length = map["len"].parseDouble() ?: return null
        val width = map["wid"].parseDouble() ?: return null
        val height = map["hgt"].parseDouble() ?: return null
        val storedVersion = map["v"]?.toIntOrNull() ?: SCHEMA_V1
        val rawInstances = InstanceNumbering.assignMissing(decodeInstances(map["inst"].orEmpty()))
        val instances = if (storedVersion < SCHEMA_V2) {
            rawInstances.map { inst ->
                val (point, yaw) = UserPlacementCoordinates.migrateFromLengthXWidthY(inst.positionM, inst.yawDeg)
                inst.copy(positionM = point, yawDeg = yaw)
            }
        } else {
            rawInstances
        }
        return EquipmentLayout(
            layoutId = layoutId,
            configurationId = configId,
            userId = userId,
            schemaVersion = SCHEMA_V2,
            lengthM = length,
            widthM = width,
            heightM = height,
            cropRowCount = map["rows"]?.toIntOrNull() ?: 3,
            instances = instances,
            countOverrideCirculation = map["ovC"].parseInt(),
            countOverrideExhaust = map["ovE"].parseInt(),
            countOverrideFogger = map["ovF"].parseInt(),
            recommendationAssumptions = map["assumptions"]?.ifBlank { null },
            origin = map["origin"]?.let { runCatching { LayoutOrigin.valueOf(it) }.getOrNull() }
                ?: LayoutOrigin.UNSPECIFIED,
            explicitlySaved = map["saved"] == "1",
            coolingPadPresent = when (map["padP"]) {
                "1" -> true
                "0" -> false
                else -> null
            },
            coolingPadWidthM = map["padW"].parseDouble(),
            coolingPadHeightM = map["padH"].parseDouble(),
            coolingPadEfficiencyFraction = map["padE"].parseDouble(),
            coolingPadWetActive = when (map["padWet"]) {
                "1" -> true
                "0" -> false
                else -> null
            },
            coolingPadWall = map["padWall"]?.ifBlank { null },
            circulationRatedCfm = map["circCfm"].parseDouble(),
            circulationRatedM3s = map["circM3s"].parseDouble(),
            circulationThrowRadiusM = map["circThrow"].parseDouble(),
            exhaustRatedAirflowM3s = map["exhM3s"].parseDouble(),
            exhaustRatedAirflowCfm = map["exhCfm"].parseDouble(),
            foggerWaterFlowKgS = map["fogKgS"].parseDouble(),
            foggerWaterFlowLph = map["fogLph"].parseDouble(),
            foggerSprayRadiusM = map["fogSpray"].parseDouble(),
            foggerEvaporationEfficiency = map["fogEta"].parseDouble()
        )
    }

    fun encodeList(layouts: List<EquipmentLayout>): String =
        layouts.joinToString(RECORD_SEPARATOR) { encode(it) }

    fun decodeList(raw: String?): List<EquipmentLayout> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { decode(it) }
    }

    private fun encodeInstance(instance: EquipmentInstance): String {
        val fields = listOf(
            instance.id,
            instance.type.name,
            instance.positionM.xM.toString(),
            instance.positionM.yM.toString(),
            instance.positionM.zM.toString(),
            instance.yawDeg.toString(),
            instance.assetVersion,
            instance.mounting.name,
            EquipmentRuntimeState.NOT_RUNNING.name,
            if (instance.fromSuggestion) "1" else "0",
            instance.validity.isValid.toString(),
            instance.validity.reasons.joinToString("/"),
            instance.displayNumber.toString()
        )
        return fields.joinToString(FIELD_SEPARATOR) { escapeInstance(it) }
    }

    private fun decodeInstances(raw: String): List<EquipmentInstance> {
        if (raw.isBlank()) return emptyList()
        return raw.split(INSTANCE_SEPARATOR).mapNotNull { token ->
            val p = token.split(FIELD_SEPARATOR).map { unescapeInstance(it) }
            if (p.size < 6) return@mapNotNull null
            val type = runCatching { EquipmentType.valueOf(p[1]) }.getOrNull() ?: return@mapNotNull null
            val x = p[2].toDoubleOrNull() ?: return@mapNotNull null
            val y = p[3].toDoubleOrNull() ?: return@mapNotNull null
            val z = p[4].toDoubleOrNull() ?: return@mapNotNull null
            val yaw = p[5].toDoubleOrNull() ?: return@mapNotNull null
            val spec = EquipmentAssetCatalog.spec(type)
            val fromSuggestion = p.getOrNull(9) == "1"
            val valid = p.getOrNull(10)?.toBooleanStrictOrNull() ?: true
            val reasons = p.getOrNull(11)?.split("/")?.filter { it.isNotBlank() }.orEmpty()
            val displayNumber = p.getOrNull(12)?.toIntOrNull() ?: 0
            EquipmentAssetCatalog.newInstance(
                type = type,
                id = p[0],
                positionM = DomainPoint(x, y, z),
                yawDeg = yaw,
                validity = if (valid) PlacementValidity.VALID else PlacementValidity(false, reasons),
                fromSuggestion = fromSuggestion,
                displayNumber = displayNumber
            ).copy(
                assetVersion = p.getOrNull(6)?.ifBlank { spec.let { EquipmentAssetCatalog.ASSET_VERSION } }
                    ?: EquipmentAssetCatalog.ASSET_VERSION,
                mounting = p.getOrNull(7)?.let { runCatching { MountingType.valueOf(it) }.getOrNull() }
                    ?: spec.mounting,
                runtimeState = EquipmentRuntimeState.NOT_RUNNING
            )
        }
    }

    private fun escape(value: String): String =
        value.replace("|", "%7C").replace(RECORD_SEPARATOR, "%1E")

    private fun unescape(value: String): String =
        value.replace("%7C", "|").replace("%1E", RECORD_SEPARATOR)

    private fun escapeInstance(value: String): String =
        value.replace(FIELD_SEPARATOR, "%2C").replace(INSTANCE_SEPARATOR, "%3B").replace("|", "%7C")

    private fun unescapeInstance(value: String): String =
        value.replace("%2C", FIELD_SEPARATOR).replace("%3B", INSTANCE_SEPARATOR).replace("%7C", "|")

    private fun String?.parseDouble(): Double? = this?.ifBlank { null }?.toDoubleOrNull()
    private fun String?.parseInt(): Int? = this?.ifBlank { null }?.toIntOrNull()
}
