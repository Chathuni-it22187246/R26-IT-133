package com.greenhands.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.ui.components.GreenhouseHeaderVisual
import com.greenhands.app.ui.components.GreenHandsLogo
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.GhType
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.ui.theme.SplashDelayMs
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var hasNavigated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasNavigated) return@LaunchedEffect
        delay(SplashDelayMs)
        if (!hasNavigated) {
            hasNavigated = true
            onFinished()
        }
    }

    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(Spacing.xl)
                .testTag("splash_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GreenHandsLogo(size = 120.dp, modifier = Modifier.scale(scale))
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                stringResource(R.string.app_name),
                style = GhType.hero,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xxxl))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .testTag("splash_loading")
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.splash_preparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onHaveAccount: () -> Unit
) {
    ScrollScreen(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("welcome_screen")
    ) {
        Spacer(Modifier.height(Spacing.sm))
        GreenhouseHeaderVisual(Modifier.testTag("welcome_hero"))
        Spacer(Modifier.height(Spacing.section))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("welcome_app_name")
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.welcome_title),
            style = GhType.hero,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().testTag("welcome_headline")
        )
        Spacer(Modifier.height(Spacing.titleDesc))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("welcome_body")
        )
        Spacer(Modifier.height(Spacing.section))
        PrimaryActionButton(
            text = stringResource(R.string.action_get_started),
            onClick = onGetStarted,
            modifier = Modifier.testTag("welcome_get_started")
        )
        Spacer(Modifier.height(Spacing.related))
        SecondaryActionButton(
            text = stringResource(R.string.action_sign_in),
            onClick = onHaveAccount,
            modifier = Modifier.testTag("welcome_have_account")
        )
    }
}
