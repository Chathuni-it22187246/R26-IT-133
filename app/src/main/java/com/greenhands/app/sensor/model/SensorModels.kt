package com.greenhands.app.sensor.model

const val DEFAULT_GREENHOUSE_WIDTH_CELLS = 12
const val DEFAULT_GREENHOUSE_HEIGHT_CELLS = 8
const val DEFAULT_COVERAGE_RADIUS_CELLS = 2.5

const val DEFAULT_GREENHOUSE_LENGTH_METERS = 12.0
const val DEFAULT_GREENHOUSE_WIDTH_METERS = 8.0
const val DEFAULT_GREENHOUSE_HEIGHT_METERS = 4.0
const val DEFAULT_GREENHOUSE_CELL_SIZE_METERS = 1.0

/** Safety caps for virtual greenhouse generation (development-friendly). */
const val MAX_GREENHOUSE_DIMENSION_METERS = 100.0
const val MAX_GREENHOUSE_HEIGHT_METERS = 20.0
const val MAX_GREENHOUSE_CELLS_PER_SIDE = 80
const val MAX_GREENHOUSE_TOTAL_CELLS = 2_500

/**
 * Physical greenhouse dimensions entered by the user (meters).
 * Logical placement/coverage still use [Greenhouse] cell coordinates.
 */
data class GreenhousePhysicalConfig(
    val lengthMeters: Double = DEFAULT_GREENHOUSE_LENGTH_METERS,
    val widthMeters: Double = DEFAULT_GREENHOUSE_WIDTH_METERS,
    val heightMeters: Double = DEFAULT_GREENHOUSE_HEIGHT_METERS,
    val cellSizeMeters: Double = DEFAULT_GREENHOUSE_CELL_SIZE_METERS
) {
    companion object {
        fun default(): GreenhousePhysicalConfig = GreenhousePhysicalConfig()
    }
}

enum class SensorType {
    TEMPERATURE,
    HUMIDITY,
    SOIL_MOISTURE,
    LIGHT_INTENSITY;

    /** Compact map-marker abbreviation (ID remains separate). */
    val markerAbbreviation: String
        get() = when (this) {
            TEMPERATURE -> "T"
            HUMIDITY -> "H"
            SOIL_MOISTURE -> "SM"
            LIGHT_INTENSITY -> "L"
        }
}

enum class SensorStatus {
    ACTIVE,
    INACTIVE
}

enum class CellCoverageState {
    COVERED,
    OVERLAP,
    BLIND_SPOT
}

enum class ScanMode {
    SIMULATED,
    AR_FUTURE
}

enum class ScanPhase {
    IDLE,
    SCANNING,
    DETECTED
}

data class ScanUiState(
    val mode: ScanMode = ScanMode.SIMULATED,
    val phase: ScanPhase = ScanPhase.IDLE,
    val progressPercent: Int = 0
) {
    val canContinueToPlacement: Boolean get() = phase == ScanPhase.DETECTED
    val isScanning: Boolean get() = phase == ScanPhase.SCANNING
}

val SCAN_PROGRESS_STEPS = listOf(0, 20, 40, 60, 80, 100)
const val DEFAULT_SCAN_STEP_DELAY_MS = 250L

/**
 * Greenhouse extent in grid-cell coordinates, independent of UI pixels.
 * Valid sensor positions satisfy `0 <= x < widthCells` and `0 <= y < heightCells`.
 * Cell (column, row) is evaluated at integer coordinates `(column, row)`.
 */
data class Greenhouse(
    val widthCells: Int = DEFAULT_GREENHOUSE_WIDTH_CELLS,
    val heightCells: Int = DEFAULT_GREENHOUSE_HEIGHT_CELLS
) {
    init {
        require(widthCells > 0) { "Greenhouse width must be at least 1 cell" }
        require(heightCells > 0) { "Greenhouse height must be at least 1 cell" }
    }

    val totalCells: Int get() = widthCells * heightCells

    val columns: IntRange get() = 0 until widthCells

    val rows: IntRange get() = 0 until heightCells

    fun contains(x: Double, y: Double): Boolean =
        x >= 0.0 && x < widthCells && y >= 0.0 && y < heightCells
}

data class Sensor(
    val id: String,
    val type: SensorType = SensorType.TEMPERATURE,
    val x: Double,
    val y: Double,
    val coverageRadius: Double = DEFAULT_COVERAGE_RADIUS_CELLS,
    val status: SensorStatus = SensorStatus.ACTIVE
)

data class GridCell(
    val x: Int,
    val y: Int,
    val state: CellCoverageState,
    val coveringSensorIds: List<String> = emptyList()
)

/**
 * Coverage for one sensor-type group (or aggregate monitoring when overlap is unused).
 *
 * For a single [SensorType]:
 * - COVERED = exactly one active sensor of that type
 * - OVERLAP = two or more active sensors of that same type
 * - BLIND_SPOT = none of that type
 *
 * For aggregate monitoring ([CoverageByType.monitoring]):
 * - COVERED = any active sensor of any type reaches the cell
 * - OVERLAP is always empty (cross-type co-coverage is not overlap)
 * - BLIND_SPOT = no active sensor of any type reaches the cell
 */
data class CoverageResult(
    val greenhouse: Greenhouse,
    val cells: List<GridCell>,
    val totalCells: Int,
    val coveredCells: Int,
    val overlapCells: Int,
    val blindSpotCells: Int,
    val overallCoveragePercent: Double,
    val goodCoveragePercent: Double,
    val overlapPercent: Double,
    val blindSpotPercent: Double
) {
    fun cell(x: Int, y: Int): GridCell? = cells.find { it.x == x && it.y == y }
}

/**
 * Independent coverage per [SensorType], plus aggregate monitoring coverage.
 * Same-type overlap is never mixed across types.
 */
data class CoverageByType(
    val greenhouse: Greenhouse,
    val byType: Map<SensorType, CoverageResult>,
    /** Cells reached by any sensor type; never uses OVERLAP for cross-type co-coverage. */
    val monitoring: CoverageResult
) {
    fun forType(type: SensorType): CoverageResult =
        byType[type] ?: error("Missing coverage for $type")

    operator fun get(type: SensorType): CoverageResult = forType(type)
}
