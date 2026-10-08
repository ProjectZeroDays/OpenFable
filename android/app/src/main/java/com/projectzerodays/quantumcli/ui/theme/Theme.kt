package com.projectzerodays.quantumcli.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ palette
/** Deep-space background #050810 -> #030509 (near-black with a blue undertone). */
val QuantumBg0 = Color(0xFF050810)
val QuantumBg1 = Color(0xFF030509)

/** Brand accent: cyan #00e5ff. */
val Cyan = Color(0xFF00E5FF)

/** Secondary accent for gradients and highlights: electric violet. */
val Violet = Color(0xFF7C4DFF)

/** soft accents #9fd6ee / #e6f7ff. */
val SoftCyan = Color(0xFF9FD6EE)
val Softest = Color(0xFFE6F7FF)

/** muted text #5f8299 (lifted for AA contrast on dark panels). */
val Muted = Color(0xFF5F8299)

/** danger #ff4b6b / warn #ffb020 / ok #3dfa9d. */
val Danger = Color(0xFFFF4B6B)
val Warn = Color(0xFFFFB020)
val Ok = Color(0xFF3DFA9D)

/** Glass card surfaces — top-lit gradients, never flat fills. */
val Panel = Color(0xF00A1626)
val PanelBright = Color(0xFF0C1E33)

/** Hairline color used for the light-catch edge on cards. */
val Hairline = Color(0x337FE9FF)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00252B),
    primaryContainer = Color(0xFF04222E),
    onPrimaryContainer = Softest,
    secondary = SoftCyan,
    onSecondary = Color(0xFF06222F),
    secondaryContainer = Panel,
    onSecondaryContainer = Softest,
    tertiary = Violet,
    onTertiary = Color(0xFF0B0620),
    background = QuantumBg1,
    onBackground = Softest,
    surface = QuantumBg1,
    onSurface = Softest,
    surfaceVariant = Panel,
    onSurfaceVariant = SoftCyan,
    outline = Muted,
    outlineVariant = Color(0xFF14324A),
    error = Danger,
    onError = Color(0xFF2B0710),
    errorContainer = Color(0xFF3A0E1A),
    onErrorContainer = Danger,
    surfaceTint = Cyan,
)

/**
 * Modern shape scale — soft 14dp cards, 20dp hero surfaces, pill toggles.
 * Applied app-wide via MaterialTheme, so every Button/TextField/Dialog
 * picks it up without per-site edits.
 */
val QuantumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val mono = FontFamily.Monospace

/**
 * Weighted mono hierarchy — display/titles bold and tight, labels medium
 * and letterspaced (the terminal-HUD feel), body regular.
 */
private fun monoStyle(
    size: Int,
    lineH: Int = size + 4,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = mono,
    fontSize = size.sp,
    lineHeight = lineH.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing,
)

val QuantumTypography = Typography(
    displayLarge = monoStyle(48, 52, FontWeight.Bold, (-1).sp),
    displayMedium = monoStyle(40, 44, FontWeight.Bold, (-0.5).sp),
    displaySmall = monoStyle(32, 36, FontWeight.Bold, (-0.5).sp),
    headlineLarge = monoStyle(26, 30, FontWeight.W700),
    headlineMedium = monoStyle(22, 26, FontWeight.W600),
    headlineSmall = monoStyle(18, 22, FontWeight.W600),
    titleLarge = monoStyle(17, 22, FontWeight.W600),
    titleMedium = monoStyle(15, 20, FontWeight.W600, 0.2.sp),
    titleSmall = monoStyle(13, 18, FontWeight.W600, 0.2.sp),
    bodyLarge = monoStyle(14, 20, FontWeight.Normal, 0.1.sp),
    bodyMedium = monoStyle(13, 19, FontWeight.Normal, 0.1.sp),
    bodySmall = monoStyle(11, 16, FontWeight.Normal, 0.2.sp),
    labelLarge = monoStyle(13, 16, FontWeight.W600, 0.6.sp),
    labelMedium = monoStyle(11, 14, FontWeight.W500, 0.8.sp),
    labelSmall = monoStyle(10, 13, FontWeight.W500, 1.0.sp),
)

@Composable
fun QuantumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = QuantumTypography,
        shapes = QuantumShapes,
        content = content
    )
}

// ------------------------------------------------------------- decorations
/**
 * Full-screen ambient backdrop: the deep vertical gradient plus two soft
 * light fields — cyan bleeding in from the top edge, violet warming the
 * bottom-right corner. Gives every screen depth instead of a dead flat fill.
 */
fun Modifier.quantumBackground(): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            listOf(QuantumBg0, QuantumBg1),
            startY = 0f,
            endY = size.height * 1.4f
        )
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Cyan.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, -size.height * 0.12f),
            radius = size.width * 0.95f,
        ),
        radius = size.width * 0.95f,
        center = Offset(size.width * 0.5f, -size.height * 0.12f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Violet.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 1.1f, size.height * 1.12f),
            radius = size.width * 0.9f,
        ),
        radius = size.width * 0.9f,
        center = Offset(size.width * 1.1f, size.height * 1.12f),
    )
}

/** Subtle cyan radial glow drawn behind the composable. */
fun Modifier.quantumGlow(
    color: Color = Cyan,
    alpha: Float = 0.16f,
): Modifier = drawBehind {
    val r = size.maxDimension
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 4f),
            radius = r
        ),
        radius = r,
        center = Offset(size.width / 2f, size.height / 4f)
    )
}

/**
 * Glass card — top-lit surface gradient, a light-catch hairline border
 * (brighter along the top edge, fading to near-invisible at the bottom)
 * and a faint ambient glow. The signature surface of the design system.
 */
fun Modifier.quantumPanel(shape: Shape = RoundedCornerShape(16.dp)): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xF20D1E33), // top: lifted
                Color(0xE6081322), // bottom: sinks into the background
            )
        ),
        shape,
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Cyan.copy(alpha = 0.34f), // light catches the top edge
                Cyan.copy(alpha = 0.07f),
            )
        ),
        shape,
    )
    .quantumGlow(alpha = 0.06f)

/** One-line gradient divider — cyan fading to transparent. */
fun Modifier.quantumDivider(): Modifier = this.background(
    Brush.horizontalGradient(
        listOf(Cyan.copy(alpha = 0.30f), Cyan.copy(alpha = 0.05f), Color.Transparent)
    )
)

@Composable
fun QCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .quantumPanel()
            .padding(contentPadding)
    ) {
        if (title != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                // accent bar — the modern section marker
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(listOf(Cyan, Violet))
                        )
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                )
                androidx.compose.material3.Text(
                    text = title,
                    style = QuantumTypography.labelLarge,
                    color = Softest,
                )
            }
        }
        content()
    }
}
