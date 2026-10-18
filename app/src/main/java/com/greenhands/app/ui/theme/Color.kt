package com.greenhands.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Emerald primary — reserved for primary actions and healthy states. */
val ForestEmerald = Color(0xFF2FBF71)
val ForestEmeraldDeep = Color(0xFF1A7A48)
val ForestEmeraldSoft = Color(0xFFC8F0D8)

/** Turquoise — reserved for environmental data. */
val ClimateTeal = Color(0xFF2EC4B6)
val ClimateTealDeep = Color(0xFF147A72)
val ClimateTealSoft = Color(0xFFC5F3EE)

/** Controlled amber — warnings only. */
val AmberWarning = Color(0xFFE0A84A)
val AmberWarningDeep = Color(0xFF8A6A12)
val AmberWarningSoft = Color(0xFFFFE6B0)

/** Soft red — error and destructive only. */
val SoftError = Color(0xFFE57373)
val SoftErrorDeep = Color(0xFF8F2F2C)
val SoftErrorSoft = Color(0xFFFFDAD6)

/** Dark forest surfaces. */
val NightBg = Color(0xFF0B0F0C)
val NightSurface = Color(0xFF141A16)
val NightElevated = Color(0xFF1C2420)
val NightBorder = Color(0xFF2A3330)
val NightText = Color(0xFFF2F5F3)
val NightMuted = Color(0xFF8A9A91)

/** Light surfaces. */
val DayBg = Color(0xFFF4F7F5)
val DaySurface = Color(0xFFFFFFFF)
val DayElevated = Color(0xFFEEF3F0)
val DayBorder = Color(0xFFD5DED8)
val DayText = Color(0xFF12201A)
val DayMuted = Color(0xFF4A5C55)

@Deprecated("Use ForestEmerald", ReplaceWith("ForestEmerald"))
val LeafGreen = ForestEmerald
@Deprecated("Use ForestEmeraldDeep", ReplaceWith("ForestEmeraldDeep"))
val LeafGreenDark = ForestEmeraldDeep
@Deprecated("Use ClimateTeal", ReplaceWith("ClimateTeal"))
val TealAccent = ClimateTeal
@Deprecated("Use ClimateTealDeep", ReplaceWith("ClimateTealDeep"))
val TealDeep = ClimateTealDeep
@Deprecated("Use SoftError", ReplaceWith("SoftError"))
val CriticalRed = SoftError
@Deprecated("Use SoftErrorDeep", ReplaceWith("SoftErrorDeep"))
val CriticalRedDark = SoftErrorDeep
@Deprecated("Use NightBg", ReplaceWith("NightBg"))
val CharcoalBg = NightBg
@Deprecated("Use NightSurface", ReplaceWith("NightSurface"))
val CharcoalSurface = NightSurface
@Deprecated("Use NightElevated", ReplaceWith("NightElevated"))
val CharcoalVariant = NightElevated
@Deprecated("Use NightText", ReplaceWith("NightText"))
val MistText = NightText
@Deprecated("Use NightMuted", ReplaceWith("NightMuted"))
val MistMuted = NightMuted
@Deprecated("Use DayBg", ReplaceWith("DayBg"))
val LightBg = DayBg
@Deprecated("Use DaySurface", ReplaceWith("DaySurface"))
val LightSurface = DaySurface
@Deprecated("Use DayElevated", ReplaceWith("DayElevated"))
val LightVariant = DayElevated
@Deprecated("Use DayText", ReplaceWith("DayText"))
val InkText = DayText
@Deprecated("Use DayMuted", ReplaceWith("DayMuted"))
val InkMuted = DayMuted
val AmberDeep = AmberWarningDeep
