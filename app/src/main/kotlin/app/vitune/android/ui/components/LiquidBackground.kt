package app.vitune.android.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Ultra-optimized Liquid background ported from Metrolist.
 *
 * Saturated artwork is deeply blurred once off the main thread (see [ArtworkBlur.renderLiquid]);
 * while the animation runs, the GPU rotates pre-blurred textured quads with zero per-frame
 * recomposition or CPU software drawing. On Android 12+ (API 31+), hardware GPU blur is applied across the
 * composite layer to create an ultra-smooth, fluid liquid effect without taxing the CPU.
 */
@Composable
fun LiquidBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    overlayAlpha: () -> Float = { 0.40f },
    fallbackColor: Color = Color(0xff121216)
) {
    val assets = rememberPrerendered(bitmap, ArtworkBlur.liquidCache, ArtworkBlur::renderLiquid)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        ArtworkTransition(target = assets, label = "liquidArtwork") { current ->
            LiquidLayers(assets = current, isPlaying = isPlaying)
        }

        Scrim(
            overlayAlpha = overlayAlpha,
            gradientStart = 0.55f,
            gradientEndAlpha = 0.55f
        )
    }
}

@Composable
private fun LiquidLayers(assets: LiquidAssets, isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "liquidLayers")

    // Metrolist timings:
    // Layer 1: 130s counter-clockwise
    // Layer 2: 90s clockwise
    // Layer 3: 70s clockwise
    // NOTE: Stored as State<Float> and read ONLY inside graphicsLayer blocks to avoid recomposition.
    val rotM = transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(130_000, easing = LinearEasing), RepeatMode.Restart),
        label = "liquidRotM"
    )
    val rotN = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
        label = "liquidRotN"
    )
    val rotO = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing), RepeatMode.Restart),
        label = "liquidRotO"
    )

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(48.dp)
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(blurModifier)
    ) {
        val w = maxWidth
        val h = maxHeight
        val maxDim = max(w, h) * 1.6f

        // Base wash covering full screen
        Image(
            bitmap = assets.base,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
        )

        // Layer 1: Center blob (130s CCW)
        Image(
            bitmap = assets.blob,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .size(maxDim)
                .align(Alignment.Center)
                .graphicsLayer {
                    rotationZ = if (isPlaying) rotM.value else 0f
                    scaleX = 1.2f
                    scaleY = 1.2f
                    alpha = 0.90f
                }
        )

        // Layer 2: Top-left blob (90s CW)
        Image(
            bitmap = assets.blob,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .size(maxDim)
                .align(Alignment.Center)
                .offset(x = w * (-0.48f), y = h * (-0.35f))
                .graphicsLayer {
                    rotationZ = if (isPlaying) rotN.value else 0f
                    scaleX = 1.3f
                    scaleY = 1.3f
                    alpha = 0.80f
                }
        )

        // Layer 3: Bottom-right blob (70s CW)
        Image(
            bitmap = assets.blob,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .size(maxDim)
                .align(Alignment.Center)
                .offset(x = w * (-0.25f), y = h * 0.35f)
                .graphicsLayer {
                    rotationZ = if (isPlaying) rotO.value else 0f
                    scaleX = 1.4f
                    scaleY = 1.4f
                    alpha = 0.75f
                }
        )
    }
}
