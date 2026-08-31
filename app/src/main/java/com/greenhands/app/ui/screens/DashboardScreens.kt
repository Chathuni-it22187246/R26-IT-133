@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenhands.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.UnconnectedEnvironment
import com.greenhands.app.harvest.domain.HarvestSensorUiState
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.session.SessionState
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.ModuleCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ProfileAvatar
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.GhType
import com.greenhands.app.ui.theme.Spacing
import java.util.Calendar

data class DashboardModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val isHeat: Boolean = false
)

@Composable
fun dashboardModules(): List<DashboardModule> = listOf(
    DashboardModule(Routes.SENSOR_PLACEMENT, stringResource(R.string.module_sensor_title), stringResource(R.string.module_sensor_body)),
    DashboardModule(Routes.HEAT_DISTRIBUTION, stringResource(R.string.module_heat_title), stringResource(R.string.module_heat_body), isHeat = true),
    DashboardModule(Routes.HARVESTING, stringResource(R.string.module_harvest_title), stringResource(R.string.module_harvest_body)),
    DashboardModule(Routes.DECISION_MAKING, stringResource(R.string.module_decision_title), stringResource(R.string.module_decision_body))
)

fun comingSoonCopy(componentId: String): Pair<String, String> = when (componentId) {
    Routes.SENSOR_PLACEMENT -> "Sensor Placement" to
        "Sensor Placement is designed to find optimal sensor positions and identify coverage gaps for reliable greenhouse monitoring. This component is coming soon and is not available in this build."
    Routes.HARVESTING -> "Harvesting" to
        "Harvesting is designed to assess crop health and predict the expected harvesting date. This component is coming soon and is not available in this build."
    Routes.DECISION_MAKING -> "Decision Making" to
        "Decision Making is designed to provide recommended decisions and practical actions based on greenhouse and crop conditions. This component is coming soon and is not available in this build."
    else -> "Coming Soon" to
        "This GreenHands component is scheduled for a later phase. The dashboard entry is active so you can return to the workspace without a dead button."
}

@Composable
fun DashboardScreen(
    session: SessionState,
    environment: GreenhouseEnvironmentSnapshot = UnconnectedEnvironment.snapshot,
    onOpenModule: (DashboardModule) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val sensor = HarvestSensorUiState.from(environment)
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> stringResource(R.string.dashboard_greeting_morning)
        hour < 17 -> stringResource(R.string.dashboard_greeting_afternoon)
        else -> stringResource(R.string.dashboard_greeting_evening)
    }
    val heading = session.dashboardTitle(stringResource(R.string.dashboard_title_fallback))
    val modules = dashboardModules()
    ScrollScreen(Modifier.fillMaxSize().statusBarsPadding().testTag("dashboard_grid")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dashboard_greeting")
                )
                Text(
                    heading,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("dashboard_title").semantics { heading() }
                )
                Text(
                    stringResource(R.string.dashboard_workspace),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dashboard_platform")
                )
                Spacer(Modifier.height(Spacing.sm))
                ConnectionStateChip(environment)
            }
            IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.size(Spacing.touch).testTag("dashboard_notifications")
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.cd_notifications))
            }
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier.size(Spacing.touch).testTag("dashboard_profile")
            ) {
                ProfileAvatar(
                    name = session.userName.ifBlank { stringResource(R.string.app_name) },
                    photoPath = session.photoPath,
                    size = 40.dp
                )
            }
        }
        Spacer(Modifier.height(Spacing.section))
        InfoCard(modifier = Modifier.testTag("dashboard_environment")) {
            Text(stringResource(R.string.dashboard_env_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.titleDesc))
            Text(
                stringResource(R.string.dashboard_env_status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_env_status")
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.dashboard_temp_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        sensor.temperatureText,
                        style = GhType.metric,
                        color = ClimateTeal,
                        modifier = Modifier.testTag("dashboard_sample_temp")
                    )
                }
                Column {
                    Text(stringResource(R.string.dashboard_rh_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        sensor.humidityText,
                        style = GhType.metric,
                        color = ClimateTeal,
                        modifier = Modifier.testTag("dashboard_sample_rh")
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(
                        sensor.statusText,
                        modifier = Modifier.testTag("dashboard_sample_values")
                    )
                }
            }
            if (environment.showsLiveTimestamp) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    formatLiveTimestamp(environment.serverTimestampMillis),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("dashboard_live_timestamp")
                )
            }
            Spacer(Modifier.height(Spacing.md))
            SampleTrend()
        }
        Spacer(Modifier.height(Spacing.section))
        Text(stringResource(R.string.dashboard_modules), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        val configuration = LocalConfiguration.current
        val wide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp >= 600
        if (wide) {
            modules.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.related)
                ) {
                    pair.forEach { module ->
                        val index = modules.indexOf(module)
                        DashboardModuleCard(
                            module = module,
                            index = index,
                            modifier = Modifier.weight(1f),
                            onOpenModule = onOpenModule
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Spacing.related))
            }
        } else {
            modules.forEachIndexed { index, module ->
                DashboardModuleCard(
                    module = module,
                    index = index,
                    onOpenModule = onOpenModule
                )
                Spacer(Modifier.height(Spacing.related))
            }
        }
        Spacer(Modifier.height(Spacing.section))
        Text(stringResource(R.string.dashboard_system_info), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        InfoCard(modifier = Modifier.testTag("dashboard_system_info")) {
            EmptyStateText(stringResource(R.string.dashboard_system_info_body))
        }
    }
}

@Composable
private fun ConnectionStateChip(environment: GreenhouseEnvironmentSnapshot) {
    val label = when (environment.connectionState) {
        GreenhouseConnectionState.PREVIEW -> stringResource(R.string.dashboard_preview_mode)
        GreenhouseConnectionState.LIVE -> "Live"
        GreenhouseConnectionState.OFFLINE_DELAYED -> "Offline"
        GreenhouseConnectionState.DISCONNECTED -> "No live sensor data"
        GreenhouseConnectionState.CONNECTING -> "Connecting to sensor..."
    }
    StatusChip(
        text = label,
        modifier = Modifier.testTag("dashboard_connection_state")
    )
}

private fun formatLiveTimestamp(millis: Long?): String {
    if (millis == null) return ""
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis) % 24
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
}

@Composable
private fun DashboardModuleCard(
    module: DashboardModule,
    index: Int,
    onOpenModule: (DashboardModule) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (index) {
        0 -> ClimateTeal
        1 -> ForestEmerald
        2 -> ClimateTeal
        else -> ForestEmerald
    }
    val status = when {
        module.isHeat -> stringResource(R.string.module_heat_status)
        index == 0 -> stringResource(R.string.module_sensor_status)
        index == 2 -> stringResource(R.string.module_harvest_status)
        else -> stringResource(R.string.module_decision_status)
    }
    ModuleCard(
        title = module.title,
        description = module.subtitle,
        status = status,
        accent = accent,
        modifier = modifier.testTag("module_${module.id}"),
        onClick = { onOpenModule(module) }
    ) {
        ModuleGlyph(index)
    }
}

@Composable
private fun SampleTrend() {
    val teal = ClimateTeal
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val path = Path()
        val pts = listOf(0.08f to 0.62f, 0.22f to 0.48f, 0.38f to 0.55f, 0.55f to 0.32f, 0.72f to 0.40f, 0.90f to 0.28f)
        path.moveTo(size.width * pts.first().first, size.height * pts.first().second)
        pts.drop(1).forEach { (x, y) -> path.lineTo(size.width * x, size.height * y) }
        drawPath(path, color = teal, style = Stroke(width = 3f, cap = StrokeCap.Round))
        pts.last().let { (x, y) ->
            drawCircle(teal, radius = 5f, center = Offset(size.width * x, size.height * y))
        }
    }
}

@Composable
private fun ModuleGlyph(index: Int) {
    val color = when (index) {
        0 -> ClimateTeal
        1 -> ForestEmerald
        2 -> ClimateTeal
        else -> ForestEmerald
    }
    Canvas(Modifier.size(22.dp)) {
        when (index) {
            0 -> {
                drawCircle(color, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.3f, size.height * 0.3f))
                drawCircle(color, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.7f, size.height * 0.55f))
                drawCircle(color, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.4f, size.height * 0.78f))
            }
            1 -> {
                drawLine(color, Offset(size.width * 0.2f, size.height * 0.75f), Offset(size.width * 0.5f, size.height * 0.2f), 3f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.75f), 3f, StrokeCap.Round)
            }
            2 -> drawCircle(color, radius = size.minDimension * 0.28f, center = center, style = Stroke(3f))
            else -> {
                drawLine(color, Offset(size.width * 0.2f, size.height * 0.7f), Offset(size.width * 0.5f, size.height * 0.3f), 3f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.3f), Offset(size.width * 0.8f, size.height * 0.55f), 3f, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ComingSoonScreen(componentId: String, onBack: () -> Unit) {
    val (title, body) = comingSoonCopy(componentId)
    val harvestHandoff = HarvestDecisionMakingBridge.latestHandoff.takeIf {
        componentId == Routes.DECISION_MAKING
    }
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("coming_soon")) {
            SectionHeading(title, stringResource(R.string.coming_soon_subtitle))
            Spacer(Modifier.height(Spacing.md))
            EmptyStateText(body)
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.coming_soon_notice))
            if (harvestHandoff != null) {
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(
                    stringResource(R.string.harvest_decision_handoff_ready),
                    modifier = Modifier.testTag("harvest_decision_handoff_notice")
                )
            }
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(stringResource(R.string.coming_soon_back), onBack)
        }
    }
}

@Composable
fun NotificationsScreen(
    notificationsEnabled: Boolean,
    onBack: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.notifications_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding)) {
            if (!notificationsEnabled) {
                DemoNotice(stringResource(R.string.notifications_paused))
                Spacer(Modifier.height(Spacing.md))
            }
            InfoCard {
                Text(stringResource(R.string.notifications_temp), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.notifications_temp_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.notifications_rh), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.notifications_rh_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.notifications_heat), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.notifications_heat_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
