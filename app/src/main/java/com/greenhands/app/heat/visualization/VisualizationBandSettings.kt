package com.greenhands.app.heat.visualization

/**
 * Product visualization settings. These are control/display bands, not crop-safety limits
 * and not Phase 3A coefficients.
 */
object VisualizationBandSettings {
    /** Half-width of the target temperature colour band, °C. */
    const val TARGET_BAND_HALF_WIDTH_C = 0.5
}

object Phase3D2B1 {
    const val STATUS = "Phase 3D.2B.1 physical AR circulation-ring rendering proof"
    const val RENDERS_EXHAUST = false
    const val RENDERS_FOG = false
    const val RENDERS_FIELD_LAYERS = false
    const val ADDS_LAYER_LEGEND = false
    const val ADDS_NETWORKING = false
    const val ANIMATES_GLB = false
    const val PERSISTS_EFFECTS = false
}

object Phase3D2B2A {
    const val STATUS = "Phase 3D.2B.2A evaporative cooling-pad inlet boundary and spatial exhaust-cooling gradient"
    const val RENDERS_PAD_INLET = true
    const val RENDERS_FOG = false
    const val RENDERS_FIELD_LAYERS = false
    const val ADDS_NETWORKING = false
    const val ANIMATES_GLB = false
    const val ADDS_PAD_GLB = false
    const val CHANGES_PHASE_3A_EQUATIONS = false
    const val WRITES_RENDERER_DISTANCE_DECAY = false
    const val PUBLIC_CIRCULATION_PROBES = false
    const val PUBLIC_FOGGER_PROBES = false
}

object PadInletAppearance {
    const val STYLE_NAME = "framed rectangular cooling-pad panel and separate inlet arrows"
    const val PANEL_THICKNESS_M = PadPanelLayout.THICKNESS_M
    const val PANEL_ALPHA_WET = 0.46f
    const val PANEL_ALPHA_DRY = 0.40f
    const val FRAME_ALPHA = 0.62f
    const val SLAT_COUNT = PadPanelLayout.RIB_COUNT
    const val ARROW_LENGTH_M = 0.16
    const val ARROW_WIDTH_M = 0.07
    const val ARROW_HEIGHT_M = 0.045
    const val MATERIAL_ALPHA = 0.52f
    const val METALLIC = 0.0f
    const val ROUGHNESS = 0.48f
    const val WRITES_EMISSIVE_FACTOR = false
    const val DOUBLE_SIDED = true
    const val CULLING = false
    val WET_PANEL = VisualizationRgb(0.46f, 0.62f, 0.56f, PANEL_ALPHA_WET)
    val DRY_PANEL = VisualizationRgb(0.56f, 0.52f, 0.40f, PANEL_ALPHA_DRY)
    val FRAME = VisualizationRgb(0.36f, 0.38f, 0.32f, FRAME_ALPHA)
    val WET_INLET_ARROW = VisualizationRgb(0.55f, 0.88f, 0.90f, 0.58f)
}

object PadInletVisual {
    const val MAX_INLET_ARROWS = 8
    const val MAX_VISUAL_HZ = 30.0
    const val CYCLE_S = 3.0
    const val PATH_M = 5.0
    const val RENDER_METHOD = "metre-accurate framed pad panel; separate pooled inlet arrows"
    const val PANEL_IS_FIXED_STRUCTURE = true
    const val INLET_ARROWS_ARE_SEPARATE = true
    const val DISPLAY_COLOR_BLEND_S = 0.75
    const val ANIMATION_IS_MEASURED_VELOCITY = false
    const val ARROW_DENSITY_IS_AIRFLOW = false
    const val LABEL_ID = "cooling-pad"
    const val LABEL = "Cooling Pad"
    const val PANEL_VISIBLE_BEFORE_START = true
    const val PANEL_REMAINS_WHEN_EXHAUST_OFF = true
}

object Phase3D2B2 {
    const val STATUS = "Phase 3D.2B.2 physics-driven exhaust-fan AR airflow and 1–5 m temperature visualization"
    const val RENDERS_EXHAUST = true
    const val RENDERS_FOG = false
    const val RENDERS_FIELD_LAYERS = false
    const val ADDS_LAYER_LEGEND = false
    const val ADDS_NETWORKING = false
    const val ANIMATES_GLB = false
    const val PERSISTS_EFFECTS = false
    const val CHANGES_PHASE_3A = false
    const val CHANGES_PHASE_3D2A = false
    const val CHANGES_CIRCULATION_RINGS = false
}

/**
 * Named glass-visible annulus style. Colour still comes from the accepted predicted
 * field and [TemperatureLegendClassifier]; this object only changes contrast,
 * thickness and lighting so rings remain readable through greenhouse glazing.
 */
object CirculationRingAppearance {
    const val STYLE_NAME = "glass-visible annulus"
    /** Visualization radius in domain metres. Not a measured jet radius. */
    const val RADIUS_M = 0.34
    /** Radial thickness of the annulus in domain metres. */
    const val TUBE_M = 0.034
    const val SEGMENT_COUNT = 36
    const val MATERIAL_ALPHA = 0.74f
    const val SELECTED_ALPHA = 0.90f
    const val SELECTED_BRIGHTNESS = 1.16f
    const val METALLIC = 0.0f
    const val ROUGHNESS = 0.22f
    /** Display-only hint. SceneView 2.2.1 [createColorInstance] has no emissiveFactor uniform. */
    const val EMISSIVE_STRENGTH = 0.22f
    const val WRITES_EMISSIVE_FACTOR = false
    const val DOUBLE_SIDED = true
    const val CULLING = false
    /** Far-ring opacity factor for a gradual distance fade. Not a measured jet decay. */
    const val DISTANCE_FADE = 0.62

    fun emphasize(color: VisualizationRgb, selected: Boolean): VisualizationRgb {
        if (!selected || color.isUnavailable) return color
        return VisualizationRgb(
            red = (color.red * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            green = (color.green * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            blue = (color.blue * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            alpha = SELECTED_ALPHA
        )
    }
}

object CirculationRingVisual {
    /** Visible rings per ON circulation fan. Product visualization count, not a jet measurement. */
    const val MAX_RINGS_PER_FAN = 7
    /** Hard cap for Oppo A78 5G: pooled annulus meshes, not per-frame allocations. */
    const val MAX_TOTAL_RINGS = 24
    const val MAX_RING_GROUPS = 4
    /**
     * Rendering method for this correction: one pooled low-poly [io.github.sceneview.node.GeometryNode]
     * annulus per ring, built with SceneView 2.2.1 [io.github.sceneview.geometries.Geometry] (no GLB).
     */
    const val RENDER_METHOD = "pooled GeometryNode annulus"
    /** Visualization radius in domain metres. Not a measured jet radius. */
    const val RADIUS_M = CirculationRingAppearance.RADIUS_M
    /** Radial thickness of the annulus in domain metres. */
    const val TUBE_M = CirculationRingAppearance.TUBE_M
    const val SEGMENT_COUNT = CirculationRingAppearance.SEGMENT_COUNT
    /** Qualitative recycle period. Not a measured air speed. */
    const val CYCLE_S = 3.2
    const val MATERIAL_ALPHA = CirculationRingAppearance.MATERIAL_ALPHA
    const val EXPAND_START = 1.00
    const val EXPAND_END = 1.28
    /** Visual blend duration between accepted 5 s display colour snapshots. */
    const val DISPLAY_COLOR_BLEND_S = 0.75
}

/**
 * Product/display publication interval for Demo T/RH and selected-fan 1–5 m labels.
 * Not a scientific timestep and not the Phase 3A outer step.
 */
object DemoDisplayPublication {
    const val INTERVAL_S = 5.0
    const val PAUSE_RESET_THRESHOLD_S = 8.0
}

/**
 * Named glass-visible exhaust arrow style. Indoor colour still comes from the
 * accepted predicted field; outdoor discharge is always qualitative.
 * Thickness and cycle time are not measured airflow.
 */
object ExhaustAirflowAppearance {
    const val STYLE_NAME = "glass-visible tapered arrow"
    const val ARROW_LENGTH_M = 0.22
    const val ARROW_WIDTH_M = 0.11
    const val ARROW_HEIGHT_M = 0.07
    const val MATERIAL_ALPHA = 0.72f
    const val QUALITATIVE_ALPHA = 0.62f
    const val OUTDOOR_ALPHA_SCALE = 0.52f
    const val SELECTED_ALPHA = 0.88f
    const val SELECTED_BRIGHTNESS = 1.14f
    const val METALLIC = 0.0f
    const val ROUGHNESS = 0.24f
    const val WRITES_EMISSIVE_FACTOR = false
    const val DOUBLE_SIDED = true
    const val CULLING = false
    const val DISTANCE_FADE = 0.48

    fun emphasize(color: VisualizationRgb, selected: Boolean): VisualizationRgb {
        if (!selected || color.isUnavailable) return color
        return VisualizationRgb(
            red = (color.red * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            green = (color.green * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            blue = (color.blue * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            alpha = SELECTED_ALPHA
        )
    }
}

object ExhaustAirflowVisual {
    /** Indoor arrowheads per ON exhaust fan. Product visualization count, not a CFD count. */
    const val MAX_INDOOR_ARROWS_PER_FAN = 6
    /** Short qualitative discharge arrowheads beyond the end wall. */
    const val MAX_OUTDOOR_ARROWS_PER_FAN = 2
    const val MAX_ARROWS_PER_FAN = MAX_INDOOR_ARROWS_PER_FAN + MAX_OUTDOOR_ARROWS_PER_FAN
    const val MAX_TOTAL_ARROWS = 24
    const val MAX_EXHAUST_GROUPS = 4
    const val RENDER_METHOD = "pooled GeometryNode tapered arrow"
    /** Qualitative indoor recycle period. Not a measured air speed. */
    const val CYCLE_S = 2.8
    /** Qualitative outdoor recycle period. Not a measured discharge velocity. */
    const val OUTDOOR_CYCLE_S = 2.2
    /** Product visual update cap. Not a solver timestep. */
    const val MAX_VISUAL_HZ = 30.0
    /**
     * Short outward continuation in domain metres. Qualitative discharge length,
     * not a measured jet or manufacturer throw distance.
     */
    const val OUTWARD_LENGTH_M = 0.55
    const val INDOOR_FAN_REACH_M = 0.0
    const val MATERIAL_ALPHA = ExhaustAirflowAppearance.MATERIAL_ALPHA
    const val DISPLAY_COLOR_BLEND_S = 0.75
    const val MIN_INDOOR_PATH_M = 0.20
}

object Phase3D2B3 {
    const val STATUS = "Phase 3D.2B.3 physics-driven AR fogger mist and humidity visualization"
    const val RENDERS_FOG = true
    const val RENDERS_FIELD_LAYERS = false
    const val ADDS_LAYER_LEGEND = false
    const val ADDS_NETWORKING = false
    const val ANIMATES_GLB = false
    const val ADDS_FOGGER_GLB = false
    const val PERSISTS_EFFECTS = false
    const val CHANGES_PHASE_3A = false
    const val CHANGES_PHASE_3D2A = false
    const val CHANGES_CIRCULATION_RINGS = false
    const val CHANGES_EXHAUST_ARROWS = false
    const val CHANGES_COOLING_PAD = false
    const val PUBLIC_FOGGER_PROBES = false
    const val PUBLIC_CIRCULATION_PROBES = false
    const val WRITES_RENDERER_DISTANCE_DECAY = false
    const val WRITES_EMISSIVE_FACTOR = false
}

/**
 * Named glass-visible fogger mist. Particle colour is qualitative cyan/white.
 * Humidity classification stays in Fog details; circular humidity halos are not drawn in AR.
 * Cone angle and range come from the accepted FoggerSource profile when the UUID is ready.
 */
object FogMistAppearance {
    const val STYLE_NAME = "glass-visible cyan/white moving mist"
    /**
     * Cross-section of a short mist flake in domain metres, tuned for the
     * 0.30 m Demo tabletop. Not a water-drop diameter.
     */
    const val DROPLET_SIZE_M = 0.042
    const val FAR_DROPLET_SIZE_M = 0.038
    const val STREAK_LENGTH_M = 0.072
    const val FAR_STREAK_LENGTH_M = 0.066
    /** Long axis versus width. Values above ~2 read as rain, not mist. */
    const val MAX_STREAK_TO_WIDTH = 1.85
    const val CROSSED_PLANES = true
    const val RAIN_LIKE_VERTICAL_STREAKS = false
    /** Radians away from the spray axis so flakes are not edge-on rain lines. */
    const val MIN_TILT_RAD = 0.40
    const val MAX_TILT_RAD = 0.72
    const val HALO_RADIUS_M = 0.18
    const val HALO_TUBE_M = 0.016
    const val HALO_SEGMENT_COUNT = 20
    const val MATERIAL_ALPHA = 0.46f
    const val NEAR_ALPHA = 0.62f
    const val MID_ALPHA = 0.50f
    const val FAR_ALPHA = 0.42f
    const val HALO_ALPHA = 0.26f
    const val SELECTED_ALPHA = 0.46f
    const val SELECTED_BRIGHTNESS = 1.08f
    const val METALLIC = 0.0f
    const val ROUGHNESS = 0.42f
    const val WRITES_EMISSIVE_FACTOR = false
    const val DOUBLE_SIDED = true
    const val CULLING = false
    val MIST = VisualizationRgb(0.82f, 0.96f, 1.00f, MATERIAL_ALPHA)

    fun emphasizeMist(selected: Boolean): VisualizationRgb {
        if (!selected) return MIST
        return VisualizationRgb(
            red = (MIST.red * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            green = (MIST.green * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            blue = (MIST.blue * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            alpha = SELECTED_ALPHA
        )
    }

    fun emphasizeHalo(color: VisualizationRgb, selected: Boolean): VisualizationRgb {
        if (!selected || color.isUnavailable) return color.withAlpha(HALO_ALPHA)
        return VisualizationRgb(
            red = (color.red * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            green = (color.green * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            blue = (color.blue * SELECTED_BRIGHTNESS).coerceIn(0f, 1f),
            alpha = (HALO_ALPHA * 1.15f).coerceIn(0f, 1f)
        )
    }
}

object FogMistVisual {
    /** Combined-mesh flake count per ON fogger. Not water-flow rate. */
    const val MAX_PARTICLES_PER_FOGGER = 144
    const val MAX_FOGGER_GROUPS = 4
    const val MAX_TOTAL_PARTICLES = MAX_PARTICLES_PER_FOGGER * MAX_FOGGER_GROUPS
    const val MAX_SCENE_NODES_PER_FOGGER = 3
    const val MAX_TOTAL_SCENE_NODES = MAX_FOGGER_GROUPS * MAX_SCENE_NODES_PER_FOGGER
    const val DEPTH_LAYERS = 12
    const val AZIMUTHS_PER_LAYER = 12
    const val MIN_VISIBLE_PARTICLES_PER_FOGGER = 48
    const val MAX_HALOS_PER_FOGGER = 2
    const val MAX_TOTAL_HALOS = 8
    const val RENDER_METHOD = "pooled combined-mesh mist, 3 shells, Geometry.setVertices"
    const val CLOUD_IS_STATIC = false
    const val ANIMATES_BY_VERTEX_BUFFER = true
    const val CREATES_OR_DESTROYS_NODES_PER_FRAME = false
    const val SCENE_VIEW_VERTEX_UPDATE_API = "GeometryNode.updateGeometry"
    /** Qualitative recycle period. Not a measured droplet velocity. */
    const val CYCLE_S = 2.2
    /** Product visual update cap. Not a solver timestep. */
    const val MAX_VISUAL_HZ = 20.0
    const val MAX_FRAME_GAP_S = 0.25
    const val MATERIAL_ALPHA = FogMistAppearance.MATERIAL_ALPHA
    const val DISPLAY_COLOR_BLEND_S = 0.75
    const val ANIMATION_IS_MEASURED_VELOCITY = false
    const val PARTICLE_COUNT_IS_WATER_FLOW = false
    const val PARTICLE_DENSITY_IS_FOG_CONCENTRATION = false
    const val CONE_ANGLE_IS_MANUFACTURER_SPEC = false
    const val RENDERS_SCENE_HUMIDITY_HALOS = false
    const val MIN_PLUME_PATH_M = 0.05
    /**
     * Previous Oppo-correction fallback. Not applied when a quantitatively ready
     * FoggerSource profile exists.
     */
    const val UNUSED_PLUME_HALF_ANGLE_DEG = 18.0
    const val UNUSED_PLUME_LENGTH_M = 0.58
    const val PLUME_HALF_ANGLE_DEG = UNUSED_PLUME_HALF_ANGLE_DEG
    const val PLUME_FULL_ANGLE_DEG = UNUSED_PLUME_HALF_ANGLE_DEG * 2.0
    const val PLUME_LENGTH_M = UNUSED_PLUME_LENGTH_M
    const val MIN_DISTANCE_M = 0.02
    const val NOZZLE_FACE_RADIUS_M = 0.015
    const val MIN_RADIAL_FRACTION = 0.20
    const val INNER_RADIAL_MAX = 0.48
    const val OUTER_RADIAL_MIN = 0.62
    const val COLLINEAR_EPSILON_M = 0.008
    /** Qualitative azimuth wobble in radians. Not measured turbulence. */
    const val DRIFT_AZIMUTH_RAD = 0.18
    /** Qualitative radial pulse, stays inside the accepted cone. */
    const val DRIFT_RADIAL = 0.07
    const val CONE_SLACK_M = 0.030

    fun sceneNodesPerFogger(): Int = MAX_SCENE_NODES_PER_FOGGER

    fun shellIndex(distanceFraction: Double): Int = when {
        distanceFraction < 0.34 -> 0
        distanceFraction < 0.67 -> 1
        else -> 2
    }

    fun shellAlpha(shell: Int): Float = when (shell) {
        0 -> FogMistAppearance.NEAR_ALPHA
        1 -> FogMistAppearance.MID_ALPHA
        else -> FogMistAppearance.FAR_ALPHA
    }
}

object Phase3D2C {
    const val STATUS = "Phase 3D.2C whole-greenhouse temperature and humidity field visualization"
    const val RENDERS_FIELD_LAYERS = true
    const val ADDS_LAYER_LEGEND = true
    const val ADDS_NETWORKING = false
    const val ANIMATES_GLB = false
    const val PERSISTS_LAYER_PREFERENCE = false
    const val CHANGES_PHASE_3A = false
    const val CHANGES_PHASE_3D2A = false
    const val CHANGES_CIRCULATION_RINGS = false
    const val CHANGES_EXHAUST_ARROWS = false
    const val CHANGES_COOLING_PAD = false
    const val CHANGES_FOGGER_MIST = false
    const val PUBLIC_FOGGER_PROBES = false
    const val PUBLIC_CIRCULATION_PROBES = false
    const val WRITES_RENDERER_DISTANCE_DECAY = false
    const val WRITES_EMISSIVE_FACTOR = false
    const val WRITES_VISUAL_TEMPERATURE_DECAY = false
    const val WRITES_VISUAL_HUMIDITY_DECAY = false
    const val TREATS_FOG_DENSITY_AS_RH = false
    const val ADVANCES_SOLVER_FROM_FRAMES = false
}

/**
 * Lightweight multi-level spatial field. Sampling density is a rendering
 * resolution, not a physical coefficient. Colour comes from the accepted
 * snapshot and classifiers; this object only caps geometry for the Oppo A78 5G.
 */
object EnvironmentalFieldAppearance {
    const val STYLE_NAME = "softly translucent spatial patches"
    const val MATERIAL_ALPHA = 0.28f
    const val METALLIC = 0.0f
    const val ROUGHNESS = 0.52f
    const val WRITES_EMISSIVE_FACTOR = false
    const val DOUBLE_SIDED = true
    const val CULLING = false
    const val PATCH_FILL = 0.72
    const val PATCH_THICKNESS_M = 0.07
    const val WALL_CLEARANCE_M = 0.04
    const val FLOOR_CLEARANCE_M = 0.08
    const val ROOF_CLEARANCE_M = 0.16
    const val CANOPY_OFFSET_M = 0.08
    const val MIN_LEVEL_SEPARATION_M = 0.28
}

object EnvironmentalFieldVisual {
    const val MAX_CELLS = 240
    const val MAX_GEOMETRY_NODES = 6
    const val MAX_MATERIALS = 6
    const val HEIGHT_LEVELS = 3
    const val MIN_COUNT_X = 3
    const val MIN_COUNT_Y = 3
    const val PREFERRED_SPACING_M = 1.0
    const val RENDER_METHOD = "pooled GeometryNode combined patches per classification band"
    const val SCENE_VIEW_VERTEX_UPDATE_API = "GeometryNode.updateGeometry"
    const val CREATES_OR_DESTROYS_NODES_PER_CELL = false
    const val CREATES_OR_DESTROYS_NODES_PER_FRAME = false
    const val UPDATES_GEOMETRY_PER_FRAME = false
    const val ADVANCES_SOLVER_FROM_FRAMES = false
    const val NODE_PER_CELL = false
    const val MATERIALS_POOLED = true
    const val ONE_LAYER_AT_A_TIME = true
    const val ANIMATION_IS_CALCULATED_VELOCITY = false
    const val GRID_LINES_DRAWN = false
    const val VERTICAL_CURTAINS = false
    const val OPAQUE_VOLUMES = false
    const val INITIAL_DEMO_LAYER = "TEMPERATURE"
}
