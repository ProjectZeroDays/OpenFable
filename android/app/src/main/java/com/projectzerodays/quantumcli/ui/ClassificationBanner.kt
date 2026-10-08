package com.projectzerodays.quantumcli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The marking line pinned to the top and bottom of every screen. */
const val CLASSIFICATION_MARKING =
    "CLASSIFIED // TOP SECRET // SI-G // TK // HCS // SAP-ALPHA // NOFORN"

/** Deep classification red — also used for the system bars (see activities). */
val ClassificationRed = Color(0xFF8B1A1A)

/**
 * Fixed classification banner strip: bold white monospace on deep red.
 * The font size auto-fits the full marking line to the available width
 * so the line is never truncated on narrow phones.
 */
@Composable
fun ClassificationBanner() {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(ClassificationRed)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        // monospace glyph ≈ 0.6em wide → fit length × 0.62em into maxWidth.
        val fs = (maxWidth.value / (CLASSIFICATION_MARKING.length * 0.62f))
            .coerceIn(6f, 11f)
        Text(
            CLASSIFICATION_MARKING,
            color = Color.White,
            fontSize = fs.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * App frame: classification banner pinned above and below [content].
 * Inset padding keeps the strips visible if the window ever runs
 * edge-to-edge; it is a no-op (zero insets) on the current target.
 */
@Composable
fun ClassificationFrame(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) { ClassificationBanner() }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        Box(Modifier.fillMaxWidth().navigationBarsPadding()) { ClassificationBanner() }
    }
}
