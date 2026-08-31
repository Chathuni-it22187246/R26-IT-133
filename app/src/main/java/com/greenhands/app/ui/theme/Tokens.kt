package com.greenhands.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Centralised 8 dp spacing system for GreenHands 2.1.2.
 * Phone horizontal padding is 20 dp; compact 16 dp; large-width 24–32 dp.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = 20.dp
    val screenCompact = 16.dp
    val screenWide = 28.dp
    val afterAppBar = 16.dp
    val section = 24.dp
    val related = 12.dp
    val card = 18.dp
    val field = 16.dp
    val titleDesc = 8.dp
    val touch = 48.dp
    val navClearance = 16.dp
}

object Radii {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
}

object Stroke {
    val hairline = 1.dp
}

object IconSize {
    val sm = 18.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 36.dp
}

object FieldHeight {
    val min = 56.dp
}

object ButtonHeight {
    val min = 52.dp
    val preferred = 56.dp
}

const val SplashDelayMs = 1800L
const val ContentMaxWidth = 800
const val ContentMaxWidthCompact = 720
const val GridMinCell = 168
