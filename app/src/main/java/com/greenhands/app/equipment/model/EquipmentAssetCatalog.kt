package com.greenhands.app.equipment.model

/**
 * Three reusable GLB sources. Many independent instances share one asset file.
 * Dimensions come from the generated GLB accessors (metres).
 */
object EquipmentAssetCatalog {
    const val ASSET_VERSION = "3b2.1"
    const val ASSET_DIRECTORY = "models/equipment"

    val circulation = AssetSpec(
        type = EquipmentType.CIRCULATION_FAN,
        fileName = EquipmentType.CIRCULATION_FAN.assetFileName,
        mounting = MountingType.SUSPENDED_OR_BAY,
        dimensions = ModelDimensions(widthM = 0.42, depthM = 0.25, heightM = 0.515),
        localAabb = LocalAabb(-0.21, -0.21, -0.16, 0.21, 0.305, 0.09),
        pivotNote = "Housing centre. Hanger extends +Y.",
        forwardNote = "glTF +Z is the discharge/forward direction."
    )

    val exhaust = AssetSpec(
        type = EquipmentType.EXHAUST_FAN,
        fileName = EquipmentType.EXHAUST_FAN.assetFileName,
        mounting = MountingType.END_WALL,
        dimensions = ModelDimensions(widthM = 0.90, depthM = 0.20, heightM = 0.90),
        localAabb = LocalAabb(-0.45, -0.45, -0.16, 0.45, 0.45, 0.04),
        pivotNote = "Wall-contact plane at local Z=0. +Z is outward.",
        forwardNote = "glTF +Z faces out of the greenhouse when mounted correctly."
    )

    val fogger = AssetSpec(
        type = EquipmentType.FOGGER,
        fileName = EquipmentType.FOGGER.assetFileName,
        mounting = MountingType.OVERHEAD,
        dimensions = ModelDimensions(widthM = 0.28, depthM = 0.05, heightM = 0.241),
        localAabb = LocalAabb(-0.14, -0.056, -0.025, 0.14, 0.185, 0.025),
        pivotNote = "Nozzle outlet at origin. Spray is -Y (down).",
        forwardNote = "Pipe runs along local +X; yaw rotates the pipe in the floor plane."
    )

    val all: List<AssetSpec> = listOf(circulation, exhaust, fogger)

    fun spec(type: EquipmentType): AssetSpec = when (type) {
        EquipmentType.CIRCULATION_FAN -> circulation
        EquipmentType.EXHAUST_FAN -> exhaust
        EquipmentType.FOGGER -> fogger
    }

    fun assetPath(type: EquipmentType): String = "$ASSET_DIRECTORY/${type.assetFileName}"

    fun newInstance(
        type: EquipmentType,
        id: String,
        positionM: DomainPoint,
        yawDeg: Double,
        validity: PlacementValidity = PlacementValidity.VALID,
        isGhost: Boolean = false,
        fromSuggestion: Boolean = false,
        displayNumber: Int = 0
    ): EquipmentInstance {
        val spec = spec(type)
        return EquipmentInstance(
            id = id,
            type = type,
            positionM = positionM,
            yawDeg = yawDeg,
            dimensions = spec.dimensions,
            localAabb = spec.localAabb,
            assetPath = assetPath(type),
            assetVersion = ASSET_VERSION,
            mounting = spec.mounting,
            validity = validity,
            runtimeState = EquipmentRuntimeState.NOT_RUNNING,
            isGhost = isGhost,
            fromSuggestion = fromSuggestion,
            displayNumber = displayNumber
        )
    }
}

data class AssetSpec(
    val type: EquipmentType,
    val fileName: String,
    val mounting: MountingType,
    val dimensions: ModelDimensions,
    val localAabb: LocalAabb,
    val pivotNote: String,
    val forwardNote: String
)
