package com.greenhands.app.heat.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.appLabel
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Spacing

@Composable
fun SourcesScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var cropFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val sources = CropProfileRegistry.citations.filter { source ->
        val matchesCrop = cropFilter == null || source.supportedCrops.any { it.id == cropFilter }
        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            source.title.contains(q, true) ||
            source.authorsOrOrganisation.contains(q, true) ||
            source.supportedCrops.any { it.displayName.contains(q, true) }
        matchesCrop && matchesQuery
    }
    ScreenScaffold(title = stringResource(R.string.sources_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("sources_screen")) {
            SectionHeading(
                title = stringResource(R.string.sources_heading),
                subtitle = stringResource(R.string.sources_subtitle)
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.sources_count, sources.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sources_search"),
                label = { Text(stringResource(R.string.sources_search)) },
                singleLine = true
            )
            Spacer(Modifier.height(Spacing.md))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item {
                    FilterChip(
                        selected = cropFilter == null,
                        onClick = { cropFilter = null },
                        label = { Text(stringResource(R.string.sources_filter_all)) }
                    )
                }
                items(Crop.selectable()) { crop ->
                    FilterChip(
                        selected = cropFilter == crop.id,
                        onClick = { cropFilter = crop.id },
                        label = { Text(crop.displayName) },
                        modifier = Modifier.testTag("source_filter_${crop.id}")
                    )
                }
            }
            Spacer(Modifier.height(Spacing.section))
            if (sources.isEmpty()) {
                EmptyStateText(stringResource(R.string.sources_empty))
            }
            sources.forEach { source ->
                InfoCard(modifier = Modifier.testTag("source_${source.id}")) {
                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(source.authorsOrOrganisation, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${stringResource(R.string.source_year)} ${source.year}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        source.supportedCrops.joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        source.supportedParameters.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    StatusChip(
                        source.evidenceLevel.appLabel(),
                        modifier = Modifier.testTag("source_evidence_${source.id}")
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        source.geographicApplicability,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        source.locationInSource,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    if (source.doiOrUrl.startsWith("http")) {
                        SecondaryActionButton(
                            text = stringResource(R.string.source_open),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.doiOrUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.source_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.testTag("source_open_${source.id}")
                        )
                    } else {
                        Text(source.doiOrUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(Spacing.related))
            }
        }
    }
}
