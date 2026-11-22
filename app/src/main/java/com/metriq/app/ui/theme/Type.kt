package com.metriq.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metriq.app.R

/** Variable Geist asset registered for every weight so semibold/bold requests resolve. */
val Geist = FontFamily(
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.SemiBold),
    Font(R.font.geist, FontWeight.Bold),
)

/** Space Grotesk is used exclusively for numeric readouts. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

private fun geist(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun number(size: Int, line: Int, tracking: Float) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/**
 * Every Material 3 slot is Geist so existing `MaterialTheme.typography.*` call sites
 * keep compiling and pick up the new identity automatically.
 */
val MetriqTypography = Typography(
    displayLarge = geist(44, 46, FontWeight.SemiBold, -1.6f),
    displayMedium = geist(30, 34, FontWeight.SemiBold, -0.8f),
    displaySmall = geist(24, 28, FontWeight.SemiBold, -0.4f),
    headlineLarge = geist(30, 34, FontWeight.SemiBold, -0.8f),
    headlineMedium = geist(20, 25, FontWeight.SemiBold, -0.3f),
    headlineSmall = geist(20, 25, FontWeight.SemiBold, -0.3f),
    titleLarge = geist(20, 25, FontWeight.SemiBold, -0.3f),
    titleMedium = geist(15, 21, FontWeight.Medium),
    titleSmall = geist(13, 18, FontWeight.Medium),
    bodyLarge = geist(15, 21),
    bodyMedium = geist(15, 21),
    bodySmall = geist(13, 18),
    labelLarge = geist(12, 15, FontWeight.Medium),
    labelMedium = geist(12, 15, FontWeight.Medium),
    labelSmall = geist(11, 14, FontWeight.Medium),
)

private val DisplayStyle = geist(44, 46, FontWeight.SemiBold, -1.6f)
private val H1Style = geist(30, 34, FontWeight.SemiBold, -0.8f)
private val H2Style = geist(20, 25, FontWeight.SemiBold, -0.3f)
private val BodyStyle = geist(15, 21)
private val BodyStrongStyle = geist(15, 21, FontWeight.Medium)
private val LabelStyle = geist(12, 15, FontWeight.Medium)
private val CaptionStyle = geist(11, 14)
private val Number40Style = number(40, 42, -1.4f)
private val Number24Style = number(24, 28, -0.6f)

/** Semantic type scale for the redesigned screens. */
val Typography.display: TextStyle get() = DisplayStyle
val Typography.h1: TextStyle get() = H1Style
val Typography.h2: TextStyle get() = H2Style
val Typography.body: TextStyle get() = BodyStyle
val Typography.bodyStrong: TextStyle get() = BodyStrongStyle
val Typography.label: TextStyle get() = LabelStyle
val Typography.caption: TextStyle get() = CaptionStyle
val Typography.number40: TextStyle get() = Number40Style
val Typography.number24: TextStyle get() = Number24Style
