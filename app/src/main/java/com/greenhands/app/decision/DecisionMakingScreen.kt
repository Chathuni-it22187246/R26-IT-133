package com.greenhands.app.decision

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.GreenhouseHealthLevel
import com.greenhands.app.environment.combinedHealthFromReadings
import com.greenhands.app.environment.formatHealthSummary
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/** Muted emerald tones for Decision Mode — easier on the eyes against dark backgrounds. */
private val Emerald950_40 = Color(0xFF022C22).copy(alpha = 0.40f)
private val Emerald800_40 = Color(0xFF065F46).copy(alpha = 0.40f)
private val EmeraldMuted = Color(0xFF047857)
private val EmeraldMutedSoft = Color(0xFF34D399).copy(alpha = 0.35f)
private val TealDeep = Color(0xFF0F766E)
private val EmeraldDeep = Color(0xFF064E3B)

@Composable
fun DecisionMakingScreen(
    onBack: () -> Unit,
    onOpenInfectionScan: (String, String?) -> Unit = { _, _ -> },
    onOpenInfectionRecord: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isManualMode by remember { mutableStateOf(false) }
    var showManualWarningDialog by remember { mutableStateOf(false) }
    var showAutoWarningDialog by remember { mutableStateOf(false) }

    var aiDecisions by remember { mutableStateOf<List<DecisionResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var heaterSpeedPercent by remember { mutableStateOf<Float?>(null) }
    var activeClimateActuator by remember { mutableStateOf("") }
    var environment by remember {
        mutableStateOf(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.PREVIEW
            )
        )
    }
    val apiService = remember { DecisionApiService.create() }

    val activeInfectionCount = remember(aiDecisions, environment.infectionCount) {
        val fromDecisions = aiDecisions.count {
            it.decisionId != "error" &&
                !it.displayUrgency.equals("Error", ignoreCase = true) &&
                !it.isHeaterAction &&
                !it.isFanAction &&
                !it.isWaterAction &&
                (it.kind.isBlank() || it.kind.equals("infection", ignoreCase = true))
        }
        maxOf(fromDecisions, environment.infectionCount)
    }

    // Live greenhouse telemetry + heater model. Non-optimal climate triggers
    // POST /api/v1/predict-heater so the backend logs an Automated Mode card.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val telemetry = apiService.getGreenhouseTelemetry()
                val connection = when (telemetry.connectionState.uppercase(Locale.US)) {
                    "LIVE" -> GreenhouseConnectionState.LIVE
                    "OFFLINE_DELAYED" -> GreenhouseConnectionState.OFFLINE_DELAYED
                    else -> GreenhouseConnectionState.PREVIEW
                }
                environment = GreenhouseEnvironmentSnapshot(
                    connectionState = connection,
                    temperatureC = telemetry.temperatureC,
                    relativeHumidityPercent = telemetry.humidityPercent,
                    lightLux = telemetry.lightLux,
                    infectionCount = telemetry.infectionCount,
                    sensorOrGreenhouseId = telemetry.sensorId.ifBlank { null },
                    serverTimestampMillis = System.currentTimeMillis(),
                    health = telemetry.health,
                    healthSummary = telemetry.healthSummary,
                    healthColor = telemetry.healthColor,
                    climateLevel = telemetry.climateLevel,
                    infectionLevel = telemetry.infectionLevel
                )
                heaterSpeedPercent = telemetry.heaterSpeed.toFloat()
                activeClimateActuator = telemetry.activeActuator
                val target = telemetry.targetTemperature
                val tempOffTarget = abs(telemetry.temperatureC - target) >= 0.15
                val climateOff = !telemetry.climateOptimal
                if (tempOffTarget || climateOff) {
                    val prediction = apiService.predictHeater(
                        HeaterPredictRequest(
                            currentTemperature = telemetry.temperatureC,
                            targetTemperature = target,
                            humidity = telemetry.humidityPercent
                        )
                    )
                    heaterSpeedPercent = prediction.heaterSpeed.toFloat()
                }
            } catch (_: Exception) {
                environment = environment.copy(
                    connectionState = if (environment.serverTimestampMillis == null) {
                        GreenhouseConnectionState.PREVIEW
                    } else {
                        GreenhouseConnectionState.OFFLINE_DELAYED
                    }
                )
            }
            delay(2_000)
        }
    }

    // Automated mode: poll ALL active decisions (one per infection_log.txt line).
    LaunchedEffect(isManualMode) {
        if (isManualMode) return@LaunchedEffect
        var firstLoad = true
        while (true) {
            try {
                if (firstLoad) isLoading = true
                val response = apiService.getActiveAiDecisions()
                val fingerprint = response.decisions.joinToString("|") {
                    "${it.decisionId}:${it.kind}:${it.lifecycle}:${it.heaterSpeed}:${it.updatedAt}"
                }
                val currentFingerprint = aiDecisions.joinToString("|") {
                    "${it.decisionId}:${it.kind}:${it.lifecycle}:${it.heaterSpeed}:${it.updatedAt}"
                }
                if (fingerprint != currentFingerprint || firstLoad) {
                    aiDecisions = withSingleActiveClimateAction(
                        response.decisions,
                        environment.temperatureC,
                        environment.relativeHumidityPercent
                    )
                }
            } catch (e: Exception) {
                if (firstLoad || aiDecisions.isEmpty()) {
                    aiDecisions = listOf(
                        DecisionResponse(
                            title = "Connection Error",
                            description = "Could not reach Python AI Server: ${e.localizedMessage}",
                            urgency = "Error",
                            infectionName = "",
                            severityLevel = "Error",
                            immediateAction = "Start the backend (uvicorn ai_server:app --port 8002), then keep this screen open.",
                            decisionId = "error"
                        )
                    )
                }
            } finally {
                isLoading = false
                firstLoad = false
            }
            delay(2_500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
            )
            Text(
                text = "AI Decision Making",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                GreenhouseStatusCard(
                    environment = environment,
                    infectionCount = activeInfectionCount
                )
            }
            item {
                DecisionModeSection(
                    isManual = isManualMode,
                    onAutomatedSelected = {
                        if (isManualMode) { showAutoWarningDialog = true }
                    },
                    onManualSelected = {
                        if (!isManualMode) { showManualWarningDialog = true }
                    },
                    onAdvancedInfectionCheckup = { onOpenInfectionScan("Plant", null) }
                )
            }

            if (isManualMode) {
                item {
                    ManualControllerSection()
                }
            } else {
                item {
                    DecisionTabsAndList(
                        aiDecisions = withSingleActiveClimateAction(
                            aiDecisions,
                            environment.temperatureC,
                            environment.relativeHumidityPercent
                        ),
                        isLoading = isLoading,
                        heaterSpeedPercent = heaterSpeedPercent,
                        activeClimateActuator = activeClimateActuator,
                        onOpenInfectionRecord = onOpenInfectionRecord
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showManualWarningDialog) {
        Dialog(onDismissRequest = { showManualWarningDialog = false }) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Switch to Manual Decisions?", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showManualWarningDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { isManualMode = true; showManualWarningDialog = false }, modifier = Modifier.weight(1f)) { Text("Yes, Switch") }
                    }
                }
            }
        }
    }

    if (showAutoWarningDialog) {
        Dialog(onDismissRequest = { showAutoWarningDialog = false }) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Switch to Automated Decisions?", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showAutoWarningDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { isManualMode = false; showAutoWarningDialog = false }, modifier = Modifier.weight(1f)) { Text("Switch Auto") }
                    }
                }
            }
        }
    }
}

@Composable
fun GreenhouseStatusCard(
    environment: GreenhouseEnvironmentSnapshot,
    infectionCount: Int
) {
    val healthLevel = if (
        environment.health.isNotBlank() &&
        infectionCount <= environment.infectionCount
    ) {
        environment.healthLevel
    } else {
        combinedHealthFromReadings(
            temperatureC = environment.temperatureC,
            humidityPercent = environment.relativeHumidityPercent,
            infectionCount = infectionCount
        )
    }
    val summary = if (
        environment.healthSummary.isNotBlank() &&
        infectionCount <= environment.infectionCount
    ) {
        environment.healthSummary
    } else {
        formatHealthSummary(
            healthLevel,
            environment.temperatureC,
            environment.relativeHumidityPercent,
            infectionCount
        )
    }
    val accent = when (healthLevel) {
        GreenhouseHealthLevel.OPTIMAL -> Color(0xFF1B7A4A)
        GreenhouseHealthLevel.WARNING -> Color(0xFFC79100)
        GreenhouseHealthLevel.CRITICAL -> Color(0xFFC62828)
        GreenhouseHealthLevel.STANDBY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bannerColor by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 450),
        label = "greenhouse_health_color"
    )
    val tempValue = environment.temperatureC?.let {
        String.format(Locale.US, "%.1f °C", it)
    } ?: "-- °C"
    val moistureValue = environment.relativeHumidityPercent?.let {
        String.format(Locale.US, "%.0f %%", it)
    } ?: "-- %"

    val tempStatus = if (environment.temperatureC == null) "Standby" else climateStatus(
        value = environment.temperatureC,
        optimalMin = 22.0,
        optimalMax = 28.0
    )
    val moistureStatus = if (environment.relativeHumidityPercent == null) "Standby" else climateStatus(
        value = environment.relativeHumidityPercent,
        optimalMin = 55.0,
        optimalMax = 75.0
    )
    val infectionStatus = when {
        infectionCount <= 0 -> "Clear"
        infectionCount == 1 -> "Alert"
        else -> "Warning"
    }

    val modeLabel = when (environment.connectionState) {
        GreenhouseConnectionState.LIVE -> "Live"
        GreenhouseConnectionState.OFFLINE_DELAYED -> "Delayed"
        GreenhouseConnectionState.PREVIEW -> "Standby"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("greenhouse_health_banner")
            .border(1.dp, bannerColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(bannerColor)
            )
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(bannerColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Greenhouse Health",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        modeLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = healthLevel.label,
                    color = bannerColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("greenhouse_health_level")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.testTag("greenhouse_health_summary")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SensorItem("Temperature", tempValue, tempStatus)
                    SensorItem("Soil Moisture", moistureValue, moistureStatus)
                    InfectionSensorItem(count = infectionCount, status = infectionStatus)
                }
            }
        }
    }
}

private fun climateStatus(value: Double?, optimalMin: Double, optimalMax: Double): String {
    val reading = value ?: return "—"
    return when {
        reading < optimalMin -> "Low"
        reading > optimalMax -> "High"
        else -> "Optimal"
    }
}

@Composable
fun SensorItem(title: String, value: String, status: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
    }
}

@Composable
private fun InfectionSensorItem(count: Int, status: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Infections",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (count > 0) Color(0xFFE53935)
                        else Color(0xFF1B7A4A)
                    )
            )
        }
        Text(
            count.toString(),
            color = if (count > 0) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            status,
            color = if (count > 0) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun DecisionModeSection(
    isManual: Boolean,
    onAutomatedSelected: () -> Unit,
    onManualSelected: () -> Unit,
    onAdvancedInfectionCheckup: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val arrowOffset by animateDpAsState(
        targetValue = if (pressed) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "infection_checkup_arrow"
    )
    val modeShape = RoundedCornerShape(16.dp)
    val autoSurface = if (!isManual) Emerald950_40 else MaterialTheme.colorScheme.surface
    val manualSurface = if (isManual) Emerald950_40 else MaterialTheme.colorScheme.surface
    val autoAccent = if (!isManual) EmeraldMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val manualAccent = if (isManual) EmeraldMuted else MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Text(
            "Decision Mode",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = EmeraldDeep.copy(alpha = 0.45f),
                    spotColor = TealDeep.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(EmeraldDeep, TealDeep)
                    )
                )
                .border(1.dp, EmeraldMutedSoft, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onAdvancedInfectionCheckup
                )
                .testTag("advanced_infection_checkup_button")
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = Color(0xFFE6FFFA)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Advanced Infection Checkup & Solution",
                    color = Color(0xFFE6FFFA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFE6FFFA),
                    modifier = Modifier.padding(start = arrowOffset)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!isManual) {
                            Modifier.border(1.dp, Emerald800_40, modeShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onAutomatedSelected() },
                colors = CardDefaults.cardColors(containerColor = autoSurface),
                shape = modeShape
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = autoAccent)
                        if (!isManual) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Automated Decisions",
                        color = if (!isManual) EmeraldMuted else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "AI analyzes crop diseases.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Card(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isManual) {
                            Modifier.border(1.dp, Emerald800_40, modeShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onManualSelected() },
                colors = CardDefaults.cardColors(containerColor = manualSurface),
                shape = modeShape
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = manualAccent)
                        if (isManual) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Manual Decisions",
                        color = if (isManual) EmeraldMuted else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Manually review actions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private enum class IntensityMode { Percent, Level }

private data class ManualComponentDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onLabel: String,
    val offLabel: String,
    val intensityMode: IntensityMode,
    val intensityMin: Float,
    val intensityMax: Float,
    val intensitySteps: Int,
    val defaultIntensity: Float,
    val intensityLabel: String
)

private data class ManualComponentRuntime(
    val isActive: Boolean = false,
    val intensity: Float = 50f,
    val durationMinutes: Int = 5,
    val remainingSeconds: Int = 0
)

private val ManualInteractiveComponents = listOf(
    ManualComponentDef(
        id = "heating",
        title = "Heating Unit",
        description = "Maintains optimal greenhouse temperature.",
        icon = Icons.Default.Thermostat,
        accentColor = Color(0xFFFF9800),
        onLabel = "ON",
        offLabel = "STANDBY",
        intensityMode = IntensityMode.Percent,
        intensityMin = 0f,
        intensityMax = 100f,
        intensitySteps = 100,
        defaultIntensity = 60f,
        intensityLabel = "Heat Intensity"
    ),
    ManualComponentDef(
        id = "water",
        title = "Water Pump & Irrigation System",
        description = "Manages drip irrigation and soil moisture.",
        icon = Icons.Default.WaterDrop,
        accentColor = Color(0xFF2196F3),
        onLabel = "ON",
        offLabel = "OFF",
        intensityMode = IntensityMode.Percent,
        intensityMin = 0f,
        intensityMax = 100f,
        intensitySteps = 100,
        defaultIntensity = 70f,
        intensityLabel = "Flow Rate"
    ),
    ManualComponentDef(
        id = "nutrient",
        title = "Nutrient Dosing Pump & Fertigation System",
        description = "Injects precise liquid nutrients into the water line.",
        icon = Icons.Default.Science,
        accentColor = Color(0xFF9C27B0),
        onLabel = "DOSING",
        offLabel = "STANDBY",
        intensityMode = IntensityMode.Level,
        intensityMin = 1f,
        intensityMax = 10f,
        intensitySteps = 9,
        defaultIntensity = 5f,
        intensityLabel = "Dose Level"
    )
)

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatIntensity(def: ManualComponentDef, value: Float): String {
    return when (def.intensityMode) {
        IntensityMode.Percent -> "${value.toInt()}%"
        IntensityMode.Level -> "Level ${value.toInt()}"
    }
}

@Composable
fun ManualControllerSection() {
    var componentStates by remember {
        mutableStateOf(
            ManualInteractiveComponents.associate { def ->
                def.id to ManualComponentRuntime(intensity = def.defaultIntensity)
            }
        )
    }
    var dialogComponentId by remember { mutableStateOf<String?>(null) }

    // Live countdown for each active override
    ManualInteractiveComponents.forEach { def ->
        val remaining = componentStates[def.id]?.remainingSeconds ?: 0
        val isActive = componentStates[def.id]?.isActive == true
        LaunchedEffect(def.id, isActive, remaining) {
            if (!isActive || remaining <= 0) return@LaunchedEffect
            delay(1000)
            val current = componentStates[def.id] ?: return@LaunchedEffect
            if (!current.isActive || current.remainingSeconds <= 0) return@LaunchedEffect
            val next = current.remainingSeconds - 1
            componentStates = componentStates + (def.id to if (next <= 0) {
                current.copy(isActive = false, remainingSeconds = 0)
            } else {
                current.copy(remainingSeconds = next)
            })
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Manual Controller",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. Cooling Fans — Automated / Read-Only (no dialog)
        AutomatedControlCard(
            title = "Cooling Fans",
            description = "Regulates air exchange and climate cooling.",
            statusText = "Active - Regulating Air",
            icon = Icons.Default.Air,
            statusColor = EmeraldMuted
        )

        ManualInteractiveComponents.forEach { def ->
            val runtime = componentStates[def.id] ?: ManualComponentRuntime(intensity = def.defaultIntensity)
            InteractiveControlCard(
                def = def,
                runtime = runtime,
                onCardClick = { dialogComponentId = def.id },
                onToggle = { enabled ->
                    componentStates = componentStates + (def.id to if (enabled) {
                        val minutes = runtime.durationMinutes.coerceAtLeast(1)
                        runtime.copy(
                            isActive = true,
                            remainingSeconds = minutes * 60
                        )
                    } else {
                        runtime.copy(isActive = false, remainingSeconds = 0)
                    })
                }
            )
        }
    }

    val dialogDef = ManualInteractiveComponents.firstOrNull { it.id == dialogComponentId }
    if (dialogDef != null) {
        val runtime = componentStates[dialogDef.id]
            ?: ManualComponentRuntime(intensity = dialogDef.defaultIntensity)
        ManualComponentConfigDialog(
            def = dialogDef,
            initial = runtime,
            onClose = { dialogComponentId = null },
            onApply = { intensity, durationMinutes ->
                componentStates = componentStates + (dialogDef.id to ManualComponentRuntime(
                    isActive = true,
                    intensity = intensity,
                    durationMinutes = durationMinutes,
                    remainingSeconds = durationMinutes * 60
                ))
                dialogComponentId = null
            }
        )
    }
}

@Composable
private fun AutomatedControlCard(
    title: String,
    description: String,
    statusText: String,
    icon: ImageVector,
    statusColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Emerald800_40, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Emerald950_40
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald800_40),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = statusColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatActionCardTitle(title),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = EmeraldMuted.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Automated",
                                color = Color(0xFFE6FFFA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked to system control",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Locked to Manual override — managed by the system automatically.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InteractiveControlCard(
    def: ManualComponentDef,
    runtime: ManualComponentRuntime,
    onCardClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val statusColor = if (runtime.isActive) def.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val stateText = when {
        runtime.isActive && runtime.remainingSeconds > 0 ->
            "${def.onLabel} · ${formatIntensity(def, runtime.intensity)} · ${formatCountdown(runtime.remainingSeconds)} left"
        runtime.isActive ->
            "${def.onLabel} · ${formatIntensity(def, runtime.intensity)}"
        else -> def.offLabel
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(def.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(def.icon, contentDescription = null, tint = def.accentColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        def.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        stateText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = runtime.isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = def.accentColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                def.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (runtime.isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap card to adjust intensity & timer",
                    color = def.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap card to configure & apply",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ManualComponentConfigDialog(
    def: ManualComponentDef,
    initial: ManualComponentRuntime,
    onClose: () -> Unit,
    onApply: (intensity: Float, durationMinutes: Int) -> Unit
) {
    var intensity by remember(def.id) {
        mutableFloatStateOf(initial.intensity.coerceIn(def.intensityMin, def.intensityMax))
    }
    var durationMinutes by remember(def.id) {
        mutableIntStateOf(initial.durationMinutes.coerceIn(1, 60))
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(def.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(def.icon, contentDescription = null, tint = def.accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            def.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Manual override configuration",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    def.intensityLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    formatIntensity(def, intensity),
                    color = def.accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = def.intensityMin..def.intensityMax,
                    steps = (def.intensitySteps - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = def.accentColor,
                        activeTrackColor = def.accentColor
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatIntensity(def, def.intensityMin),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        formatIntensity(def, def.intensityMax),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Runtime Duration",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "$durationMinutes min",
                    color = def.accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Slider(
                    value = durationMinutes.toFloat(),
                    onValueChange = { durationMinutes = it.toInt().coerceIn(1, 60) },
                    valueRange = 1f..60f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = def.accentColor,
                        activeTrackColor = def.accentColor
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 min", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text("60 min", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Countdown starts when you tap Apply.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = { onApply(intensity, durationMinutes) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = def.accentColor)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

private enum class AutomatedActionFilter(val label: String) {
    All("All"),
    Infections("Infections"),
    HeaterFans("Heater/Fans"),
    WaterPump("Water Pump"),
    Other("Other actions")
}

private enum class AutomatedActionCategory {
    Infections,
    HeaterFans,
    WaterPump,
    Other
}

private data class AutomatedActionItem(
    val id: String,
    val category: AutomatedActionCategory,
    val title: String,
    val description: String,
    val status: String,
    val icon: ImageVector,
    val accentColor: Color,
    val decision: DecisionResponse? = null,
    val trackedRecordId: String? = null,
    val lifecycle: String = ""
) {
    val isClickable: Boolean
        get() = decision != null || trackedRecordId != null

    val isCompleted: Boolean
        get() = lifecycle.equals("Completed", ignoreCase = true) ||
            status.equals("Completed", ignoreCase = true) ||
            decision?.isCompletedClimateAction == true

    val actionHint: String
        get() = when {
            trackedRecordId != null -> "Tap for infection record and risk history"
            isCompleted && (decision?.isHeaterAction == true || decision?.isFanAction == true) ->
                "Completed climate action"
            decision?.isHeaterAction == true -> "Tap for heater speed and climate details"
            decision?.isFanAction == true || decision?.isWaterAction == true ->
                "Tap for automated actuator details"
            else -> "Tap for step-by-step treatment guide"
        }
}

/** Card titles use a colon separator, e.g. "Powdery Mildew: AI Treatment Guide". */
private fun formatActionCardTitle(title: String): String {
    return title
        .replace(" — ", ": ")
        .replace(" – ", ": ")
        .replace(" - ", ": ")
}

private fun categorizeTrackedRecord(record: TrackedInfectionRecord): AutomatedActionItem {
    return AutomatedActionItem(
        id = "tracked-${record.id}",
        category = AutomatedActionCategory.Infections,
        title = formatActionCardTitle("${record.infectionName}: AI Treatment Guide"),
        description = record.description,
        status = record.currentRisk.riskLevel,
        icon = Icons.Default.SmartToy,
        accentColor = Color(0xFF2E7D32),
        trackedRecordId = record.id
    )
}

private fun categorizeInfectionDecision(decision: DecisionResponse): AutomatedActionItem {
    val category = when {
        decision.isWaterAction -> AutomatedActionCategory.WaterPump
        decision.category.equals("other", ignoreCase = true) -> AutomatedActionCategory.Other
        decision.isHeaterAction || decision.isFanAction ||
            decision.category.equals("heater_fans", ignoreCase = true) ->
            AutomatedActionCategory.HeaterFans
        else -> AutomatedActionCategory.Infections
    }
    val speed = decision.heaterSpeed
    val title = when {
        decision.isHeaterAction ->
            decision.displayTitle.ifBlank {
                if (speed != null) "Heating Unit: ${speed.toInt()}% AI Output"
                else "Heating Unit: AI Climate Control"
            }
        decision.isFanAction ->
            decision.displayTitle.ifBlank { "Cooling Fans: Automated Circulation" }
        decision.isWaterAction ->
            decision.displayTitle.ifBlank { "Water Pump: Automated Irrigation" }
        else -> decision.displayTitle
    }
    val description = decision.immediateAction.ifBlank { decision.description }
    val icon = when {
        decision.isFanAction -> Icons.Default.Air
        decision.isHeaterAction -> Icons.Default.Thermostat
        decision.isWaterAction -> Icons.Default.WaterDrop
        category == AutomatedActionCategory.Other -> Icons.Default.Science
        else -> Icons.Default.SmartToy
    }
    val accent = when {
        decision.isCompletedClimateAction -> Color(0xFF90A4AE)
        decision.isHeaterAction -> Color(0xFFFF9800)
        decision.isFanAction -> Color(0xFF4CAF50)
        decision.isWaterAction -> Color(0xFF2196F3)
        category == AutomatedActionCategory.Other -> Color(0xFF9C27B0)
        else -> Color(0xFF2E7D32)
    }
    val status = when {
        decision.isHeaterAction || decision.isFanAction ->
            if (decision.isCompletedClimateAction) "Completed" else "Active"
        else -> decision.displayUrgency
    }
    return AutomatedActionItem(
        id = decision.decisionId.ifBlank { "action-${decision.lineIndex}-${decision.kind}" },
        category = category,
        title = formatActionCardTitle(title),
        description = description.ifBlank { "AI greenhouse action" },
        status = status,
        icon = icon,
        accentColor = accent,
        decision = decision,
        lifecycle = decision.lifecycle
    )
}

private fun buildSystemAutomatedActions(
    heaterSpeedPercent: Float? = null,
    activeClimateActuator: String = ""
): List<AutomatedActionItem> {
    val fanActive = activeClimateActuator.equals("fan", ignoreCase = true)
    val heaterActive = activeClimateActuator.equals("heater", ignoreCase = true)
    return listOf(
        AutomatedActionItem(
            id = "auto-cooling-fans",
            category = AutomatedActionCategory.HeaterFans,
            title = "Cooling Fans",
            description = if (fanActive) {
                "Live airflow to dump excess heat and mix canopy air."
            } else {
                "Circulation fans standing by after the last cooling cycle."
            },
            status = if (fanActive) "Active" else "Completed",
            icon = Icons.Default.Air,
            accentColor = if (fanActive) Color(0xFF4CAF50) else Color(0xFF90A4AE),
            lifecycle = if (fanActive) "Active" else "Completed"
        ),
        AutomatedActionItem(
            id = "auto-heating-unit",
            category = AutomatedActionCategory.HeaterFans,
            title = "Heating Unit",
            description = if (heaterActive) {
                "Live temperature recovery using the AI heater model."
            } else {
                "Heater cycle finished — waiting for the next cold trigger."
            },
            status = when {
                heaterActive && heaterSpeedPercent != null -> "Active · ${heaterSpeedPercent.toInt()}%"
                heaterActive -> "Active"
                else -> "Completed"
            },
            icon = Icons.Default.Thermostat,
            accentColor = if (heaterActive) Color(0xFFFF9800) else Color(0xFF90A4AE),
            lifecycle = if (heaterActive) "Active" else "Completed"
        ),
        AutomatedActionItem(
            id = "auto-water-pump",
            category = AutomatedActionCategory.WaterPump,
            title = "Water Pump & Irrigation",
            description = "Moisture control and drip irrigation managed by automated thresholds.",
            status = "Monitoring Soil Moisture",
            icon = Icons.Default.WaterDrop,
            accentColor = Color(0xFF2196F3)
        ),
        AutomatedActionItem(
            id = "auto-nutrient",
            category = AutomatedActionCategory.Other,
            title = "Nutrient Dosing / Fertigation",
            description = "General maintenance dosing aligned with crop stage nutrition targets.",
            status = "Ready",
            icon = Icons.Default.Science,
            accentColor = Color(0xFF9C27B0)
        )
    )
}

@Composable
fun DecisionTabsAndList(
    aiDecisions: List<DecisionResponse>,
    isLoading: Boolean,
    heaterSpeedPercent: Float? = null,
    activeClimateActuator: String = "",
    onOpenInfectionRecord: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(AutomatedActionFilter.All) }
    var selectedDecision by remember { mutableStateOf<DecisionResponse?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tracker = remember { InfectionRecordRepository.get(context) }
    val trackedRecords by tracker.records.collectAsState()

    val infectionItems = remember(aiDecisions, trackedRecords) {
        val tracked = trackedRecords.map(::categorizeTrackedRecord)
        val backend = aiDecisions.map(::categorizeInfectionDecision)
        tracked + backend
    }
    val hasHeaterCard = infectionItems.any { it.decision?.isHeaterAction == true }
    val hasFanCard = infectionItems.any { it.decision?.isFanAction == true }
    val hasWaterCard = infectionItems.any {
        it.category == AutomatedActionCategory.WaterPump || it.decision?.isWaterAction == true
    }
    val systemItems = remember(heaterSpeedPercent, hasHeaterCard, hasFanCard, hasWaterCard, activeClimateActuator) {
        buildSystemAutomatedActions(heaterSpeedPercent, activeClimateActuator).filter { item ->
            when (item.id) {
                "auto-heating-unit" -> !hasHeaterCard
                "auto-cooling-fans" -> !hasFanCard
                "auto-water-pump" -> !hasWaterCard
                else -> true
            }
        }
    }
    val allItems = remember(infectionItems, systemItems) { infectionItems + systemItems }

    val filteredItems = remember(selectedFilter, allItems) {
        when (selectedFilter) {
            AutomatedActionFilter.All -> allItems
            AutomatedActionFilter.Infections ->
                allItems.filter { it.category == AutomatedActionCategory.Infections }
            AutomatedActionFilter.HeaterFans ->
                allItems.filter { it.category == AutomatedActionCategory.HeaterFans }
            AutomatedActionFilter.WaterPump ->
                allItems.filter { it.category == AutomatedActionCategory.WaterPump }
            AutomatedActionFilter.Other ->
                allItems.filter { it.category == AutomatedActionCategory.Other }
        }
    }

    Column {
        Text(
            "Automated Actions Bar",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Filter live heater/fan actions — only one climate action stays Active",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AutomatedActionFilter.entries.size) { index ->
                val filter = AutomatedActionFilter.entries[index]
                val selected = selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            filter.label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when (selectedFilter) {
                AutomatedActionFilter.All ->
                    "Active AI Decisions & Actions (${filteredItems.size})"
                else ->
                    "${selectedFilter.label} (${filteredItems.size})"
            },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading && infectionItems.isEmpty() && selectedFilter == AutomatedActionFilter.Infections -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            isLoading && infectionItems.isEmpty() && selectedFilter == AutomatedActionFilter.All -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredItems.forEach { item ->
                        AutomatedActionFilterCard(
                            item = item,
                            onClick = {
                                item.trackedRecordId?.let(onOpenInfectionRecord)
                                    ?: item.decision?.let { selectedDecision = it }
                            }
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            filteredItems.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredItems.forEach { item ->
                        AutomatedActionFilterCard(
                            item = item,
                            onClick = {
                                item.trackedRecordId?.let(onOpenInfectionRecord)
                                    ?: item.decision?.let { selectedDecision = it }
                            }
                        )
                    }
                }
            }
            else -> {
                Text(
                    when (selectedFilter) {
                        AutomatedActionFilter.Infections ->
                            "No infection decisions yet. Add lines to backend/infection_log.txt"
                        AutomatedActionFilter.HeaterFans ->
                            "No heater or fan actions yet. Non-optimal climate will log a heater card."
                        else ->
                            "No actions in “${selectedFilter.label}” right now."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }

    selectedDecision?.let { decision ->
        AiTreatmentDetailDialog(
            decision = decision,
            onClose = { selectedDecision = null }
        )
    }
}

@Composable
private fun AutomatedActionFilterCard(
    item: AutomatedActionItem,
    onClick: () -> Unit
) {
    val clickable = item.isClickable
    val hint = item.actionHint
    val completed = item.isCompleted
    val mutedGray = Color(0xFF90A4AE)
    val accent = if (completed) mutedGray else item.accentColor
    val urgencyColor = when {
        completed -> mutedGray
        item.status.contains("Active", ignoreCase = true) -> accent
        item.status.contains("Critical", ignoreCase = true) -> Color(0xFFE53935)
        item.status.contains("High", ignoreCase = true) -> Color(0xFFFF9800)
        item.status.contains("Error", ignoreCase = true) -> Color(0xFFE53935)
        item.status.contains("Moderate", ignoreCase = true) -> Color(0xFFFBC02D)
        else -> accent
    }
    val liveDecision = item.decision
    val statusLabel = when {
        completed -> "Completed"
        liveDecision != null && liveDecision.isLiveClimateAction && liveDecision.heaterSpeed != null ->
            "Active · ${liveDecision.heaterSpeed.toInt()}%"
        liveDecision?.isLiveClimateAction == true || item.status.contains("Active", ignoreCase = true) ->
            "Active"
        else -> item.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.48f else 1f)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = if (completed) 1.dp else 2.dp,
                color = accent.copy(alpha = if (completed) 0.35f else 0.75f),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                Color(0xFF546E7A).copy(alpha = 0.16f)
            } else if (item.category == AutomatedActionCategory.Infections) {
                Emerald950_40
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatActionCardTitle(item.title),
                    color = if (completed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (completed) FontWeight.Medium else FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2
                )
                if (clickable && !completed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = urgencyColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = urgencyColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AutomatedAiDecisionCard(
    decision: DecisionResponse,
    onClick: () -> Unit
) {
    AutomatedActionFilterCard(
        item = categorizeInfectionDecision(decision),
        onClick = onClick
    )
}

@Composable
private fun AiTreatmentDetailDialog(
    decision: DecisionResponse,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                val climateAction = decision.isHeaterAction || decision.isFanAction || decision.isWaterAction
                val heaterSpeed = decision.heaterSpeed
                val headerIcon = when {
                    decision.isHeaterAction -> Icons.Default.Thermostat
                    decision.isFanAction -> Icons.Default.Air
                    decision.isWaterAction -> Icons.Default.WaterDrop
                    else -> Icons.Default.SmartToy
                }
                val headerTint = when {
                    decision.isHeaterAction -> Color(0xFFFF9800)
                    decision.isFanAction -> Color(0xFF4CAF50)
                    decision.isWaterAction -> Color(0xFF2196F3)
                    else -> MaterialTheme.colorScheme.primary
                }
                val headerTitle = if (decision.isHeaterAction && heaterSpeed != null) {
                    "Heating Unit: ${heaterSpeed.toInt()}% AI Output"
                } else {
                    decision.infectionName.ifBlank { decision.displayTitle }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(headerTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            headerIcon,
                            contentDescription = null,
                            tint = headerTint
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatActionCardTitle(headerTitle),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            if (climateAction) {
                                "Urgency: ${decision.displayUrgency}" +
                                    (decision.climateStatus.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                            } else {
                                "Severity: ${decision.severityLevel.ifBlank { decision.displayUrgency }}"
                            },
                            color = headerTint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (decision.isHeaterAction && heaterSpeed != null) {
                        item {
                            GuideSection(
                                title = "Predicted Heater Speed",
                                body = "${heaterSpeed.toInt()}% output from heater_model.pkl (Random Forest)"
                            )
                        }
                    }
                    if (decision.isHeaterAction) {
                        val climateBits = buildList {
                            decision.currentTemperature?.let { add("Current ${String.format(Locale.US, "%.1f", it)}°C") }
                            decision.targetTemperature?.let { add("Target ${String.format(Locale.US, "%.1f", it)}°C") }
                            decision.humidity?.let { add("Humidity ${it.toInt()}%") }
                        }
                        if (climateBits.isNotEmpty()) {
                            item {
                                GuideSection(
                                    title = "Climate Snapshot",
                                    body = climateBits.joinToString(" · ")
                                )
                            }
                        }
                    }
                    if (decision.immediateAction.isNotBlank()) {
                        item {
                            GuideSection(
                                title = if (climateAction) "Immediate Action" else "Immediate Action / Biological Start",
                                body = decision.immediateAction
                            )
                        }
                    }
                    if (decision.biologicalTreatment.isNotEmpty()) {
                        item {
                            GuideBulletSection(
                                title = if (climateAction) "Control Source" else "Biological Treatment",
                                items = decision.biologicalTreatment
                            )
                        }
                    }
                    if (decision.chemicalControl.isNotEmpty()) {
                        item {
                            GuideBulletSection(
                                title = "Chemical Control Measures",
                                items = decision.chemicalControl
                            )
                        }
                    }
                    if (decision.prevention.isNotEmpty() || decision.environmentalAdjustments.isNotEmpty()) {
                        item {
                            GuideBulletSection(
                                title = if (climateAction) "Climate Notes" else "Prevention & Environmental Adjustments",
                                items = decision.prevention + decision.environmentalAdjustments
                            )
                        }
                    }
                    if (!decision.hasDetailedGuide && decision.description.isNotBlank()) {
                        item {
                            GuideSection(title = "AI Recommendation", body = decision.description)
                        }
                    }
                    if (decision.sources.isNotEmpty()) {
                        item {
                            GuideBulletSection(title = "Sources", items = decision.sources)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun GuideSection(title: String, body: String) {
    Column {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun GuideBulletSection(title: String, items: List<String>) {
    Column {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        items.forEach { item ->
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text("•  ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(
                    item,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ActionCard(title: String, desc: String, status: String, statusColor: Color, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = statusColor)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatActionCardTitle(title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}