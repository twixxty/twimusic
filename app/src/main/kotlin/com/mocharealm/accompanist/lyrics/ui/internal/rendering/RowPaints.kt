package com.mocharealm.accompanist.lyrics.ui.internal.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

internal const val FocusedRowUnlitAlpha = 0.2f

/**
 * Glow blur does not depend on the text color, so every row shares one set instead of each line
 * allocating (and later natively creating) 64 identical RenderEffects as it enters the cache.
 */
private val SharedGlowBlurEffects =
    Array<RenderEffect?>(65) {
        if (it == 0) null else BlurEffect(10f * it / 64f, 10f * it / 64f, TileMode.Decal)
    }

/** Color-dependent paints are cached; playback selects a shadow without allocating one. */
internal class RowPaints(private val color: Color) {
    // Only the uncached fallback draw path reads these, so build them on first use.
    val shadows: Array<Shadow> by
        lazy(LazyThreadSafetyMode.NONE) {
            Array(65) { index ->
                val strength = index / 64f
                Shadow(
                    color.copy(alpha = color.alpha * 0.4f * strength),
                    Offset.Zero,
                    10f * strength,
                )
            }
        }
    val blurEffects: Array<RenderEffect?> get() = SharedGlowBlurEffects
    val phoneticColor = color
    val debugStroke = Stroke(1f)
}
