package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import com.mocharealm.accompanist.lyrics.ui.internal.effects.lyricsVisualLayer
import com.mocharealm.accompanist.lyrics.ui.internal.effects.LocalLyricsLineBlur
import com.mocharealm.accompanist.lyrics.ui.internal.effects.tileBlurActive

import android.os.Build
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import app.vitune.android.preferences.PlayerPreferences

/** Upper bound of the blur radius in dp, so far-away lines stay a soft shape instead of vanishing. */
private const val MAX_BLUR_DP = 8f


@Composable
fun LyricsLineItem(
    isFocused: Boolean,
    isRightAligned: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
    blurRadius: () -> Float,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.4f,
    blendMode: BlendMode = BlendMode.SrcOver,
    isInteractive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scaleState =
        animateFloatAsState(
            targetValue = if (isFocused) 1f else 0.98f,
            animationSpec =
                if (isFocused) {
                    tween(durationMillis = 600, easing = LinearOutSlowInEasing)
                } else {
                    tween(durationMillis = 300, easing = EaseInOut)
                },
            label = "scale",
        )

    val alphaState =
        animateFloatAsState(
            targetValue = if (isFocused) activeAlpha else inactiveAlpha,
            label = "alpha",
        )

    // blurRadius() is expressed in dp. It used to be applied as raw pixels, which on a 3x phone is
    // a third of the intended strength - the blur was technically on but practically invisible.
    val density = LocalDensity.current.density
    val currentBlurRadius by rememberUpdatedState(blurRadius)
    val blurPx: () -> Float =
        remember(density) {
            {
                if (!PlayerPreferences.lyricsLineBlur) 0f
                else (currentBlurRadius().takeIf { it.isFinite() } ?: 0f)
                    .coerceIn(0f, MAX_BLUR_DP) * density
            }
        }
    Box(
        modifier =
            modifier.fillMaxWidth().lyricsVisualLayer(observePaint = {}) {
                scaleX = scaleState.value
                scaleY = scaleState.value
                alpha = alphaState.value
                transformOrigin = TransformOrigin(if (isRightAligned) 1f else 0f, 1f)
                this.blendMode = blendMode
                compositingStrategy = CompositingStrategy.Auto

                // Android 12+ with "Optimize lyrics blur" off uses the live GPU blur. Otherwise (and
                // always before Android 12, which has no RenderEffect) the text tiles are drawn
                // pre-blurred, see LegacyLyricsBlur.kt, so no effect is set on the layer.
                renderEffect =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !PlayerPreferences.lyricsOptimizedAndroid12
                    ) {
                        val radius = blurPx()
                        if (radius <= 0.5f) null else BlurEffect(radius, radius, TileMode.Clamp)
                    } else null
            }
    ) {
        // Clip the ripple, never the lyrics that can draw beyond their animated layout height.
        if (isInteractive)
            Box(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onLineClicked, onLongClick = onLinePressed)
            )
        // Pre-blurred text tiles (see LegacyLyricsBlur.kt) are driven by this radius.
        if (tileBlurActive())
            CompositionLocalProvider(LocalLyricsLineBlur provides blurPx) { content() }
        else content()
    }
}
