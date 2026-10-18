package com.greenhands.app.heat.advisory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing

/**
 * Non-blocking Material 3 banner. Must not be shown as a repeatedly opening dialog.
 */
@Composable
fun TooColdAdvisoryBanner(
    advisory: TooColdAdvisory,
    onViewSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("too_cold_advisory"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Text(
                text = stringResource(R.string.too_cold_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("too_cold_title")
            )
            Text(
                text = "${advisory.currentLabel} · ${advisory.optimalLabel}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("too_cold_temperatures")
            )
            Text(
                text = stringResource(R.string.too_cold_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("too_cold_body")
            )
            Button(
                onClick = {
                    TooColdAdvisoryEvaluator.onViewSuggestions(onViewSuggestions)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("too_cold_view_suggestions")
            ) {
                Text(
                    text = stringResource(R.string.too_cold_view_suggestions),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
