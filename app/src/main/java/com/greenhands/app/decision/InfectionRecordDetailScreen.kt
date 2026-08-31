package com.greenhands.app.decision

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InfectionRecordDetailScreen(
    recordId: String,
    onBack: () -> Unit,
    onUpdateScan: (TrackedInfectionRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { InfectionRecordRepository.get(context) }
    val records by repo.records.collectAsState()
    val record = records.firstOrNull { it.id == recordId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("infection_record_detail")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("infection_record_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Infection Record",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val recordRef = record
        if (recordRef == null) {
            Text(
                "This infection record is no longer available.",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        val trend = InfectionPriority.trend(recordRef.history)
        val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                recordRef.infectionName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                recordRef.infectionFullName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Text(
                recordRef.description,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )

            InfoRow("Plant", recordRef.plantType)
            InfoRow("Detected on", recordRef.targetKind?.ifBlank { null } ?: "Leaf")
            InfoRow("Record created", dateFormat.format(Date(recordRef.createdAtMillis)))
            InfoRow("Estimated days since it formed", "${recordRef.daysAgoFormed()} days ago")

            RiskChip(recordRef.currentRisk.riskLevel)
            TrendBanner(trend)

            Text(
                "Risk Level vs. Date",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RiskHistoryChart(
                    history = recordRef.history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(12.dp)
                        .testTag("infection_risk_chart")
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { onUpdateScan(recordRef) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp)
                .testTag("infection_update_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Update, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Update", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun RiskChip(level: String) {
    val color = when (level.lowercase()) {
        "critical" -> Color(0xFFE53935)
        "high" -> Color(0xFFFF9800)
        "medium" -> Color(0xFFFBC02D)
        else -> Color(0xFF2E7D32)
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
        Text(
            "Current risk: $level",
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TrendBanner(trend: RiskTrend) {
    val (label, color, icon) = when (trend) {
        RiskTrend.Increased -> Triple("Risk has increased since the last check", Color(0xFFE53935), Icons.Default.TrendingUp)
        RiskTrend.Decreased -> Triple("Risk has decreased since the last check", Color(0xFF2E7D32), Icons.Default.TrendingDown)
        RiskTrend.Unchanged -> Triple("Risk is unchanged since the last check", Color(0xFF607D8B), Icons.Default.TrendingFlat)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RiskHistoryChart(
    history: List<RiskSample>,
    modifier: Modifier = Modifier
) {
    val points = history.ifEmpty { return }
    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.US) }
    Canvas(modifier = modifier) {
        val left = 52f
        val right = size.width - 12f
        val top = 18f
        val bottom = size.height - 36f
        val chartW = (right - left).coerceAtLeast(1f)
        val chartH = (bottom - top).coerceAtLeast(1f)

        drawLine(Color(0xFF90A4AE), Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
        drawLine(Color(0xFF90A4AE), Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)

        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#607D8B")
            textSize = 22f
            isAntiAlias = true
        }
        listOf(0 to "Low", 30 to "Med", 55 to "High", 75 to "Crit").forEach { (score, label) ->
            val y = bottom - (score / 100f) * chartH
            drawContext.canvas.nativeCanvas.drawText(label, 4f, y + 8f, axisPaint)
            drawLine(Color(0x228090A0), Offset(left, y), Offset(right, y), strokeWidth = 1f)
        }

        val path = Path()
        points.forEachIndexed { index, sample ->
            val x = if (points.size == 1) left + chartW / 2f else left + chartW * (index / (points.size - 1f))
            val y = bottom - (sample.riskScore / 100f) * chartH
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Color(0xFF2E7D32), radius = 8f, center = Offset(x, y))
            drawContext.canvas.nativeCanvas.drawText(
                dateFormat.format(Date(sample.recordedAtMillis)),
                x - 20f,
                bottom + 28f,
                axisPaint
            )
        }
        drawPath(path, Color(0xFF2E7D32), style = Stroke(width = 5f))
    }
}
