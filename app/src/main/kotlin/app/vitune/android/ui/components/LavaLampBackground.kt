package app.vitune.android.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

private const val ARTWORK_CROSSFADE_MS = 600

/**
 * Animated lava lamp background (ported from Accord's BlendView): saturated quarters of the album
 * artwork slowly rotating inside a rotating frame, over a soft wash of the artwork.
 *
 * Every layer is blurred **once**, off the main thread, when the artwork changes (see
 * [ArtworkBlur]); while the animation runs the GPU only rotates pre-blurred textures. That makes it
 * far cheaper than live-blurring a full-screen layer every frame, and - unlike `Modifier.blur` - it
 * also works on Android 11 and below.
 *
 * @param overlayAlpha read during the draw phase only, so animating it never recomposes anything.
 */
@Composable
fun LavaLampBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    overlayAlpha: () -> Float = { 0.40f },
    fallbackColor: Color = Color(0xff121216)
) {
    val assets = rememberPrerendered(bitmap, ArtworkBlur.lavaCache, ArtworkBlur::renderLava)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        // The infinite transition lives inside the content, so nothing keeps ticking frames when
        // there is no artwork to animate.
        ArtworkTransition(target = assets, label = "lavaLampArtwork") { current ->
            LavaLampLayers(current)
        }

        Scrim(
            overlayAlpha = overlayAlpha,
            gradientStart = 0.65f,
            gradientEndAlpha = 0.55f
        )
    }
}

@Composable
private fun LavaLampLayers(assets: LavaLampAssets) {
    val transition = rememberInfiniteTransition(label = "lavaLamp")
    // These are deliberately NOT read with `by`: they are only read inside graphicsLayer blocks, so
    // each frame only updates GPU layer properties - no recomposition, no re-recording.
    val frameRotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(44000, easing = LinearEasing), RepeatMode.Restart),
        label = "lavaLampFrameRotation"
    )
    val topLeftRotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "lavaLampTopLeftRotation"
    )
    val bottomRightRotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(34000, easing = LinearEasing), RepeatMode.Restart),
        label = "lavaLampBottomRightRotation"
    )

    // The quarters were rendered with transparent padding so their blur fades out softly; grow the
    // box by the same amount (and shift the offset to match) so the visible content keeps its
    // original 320dp size and position.
    val quarterBox = QUARTER_SIZE * assets.quarterScale
    val quarterPadding = (quarterBox - QUARTER_SIZE) / 2

    Box(modifier = Modifier.fillMaxSize()) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.3f
                    scaleY = 1.3f
                }
        ) {
            Image(
                bitmap = assets.wash,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                        alpha = 0.55f
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = frameRotation.value }
            ) {
                Image(
                    bitmap = assets.topLeft,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .size(quarterBox)
                        .align(Alignment.TopStart)
                        .offset(x = (-30).dp - quarterPadding, y = (-30).dp - quarterPadding)
                        .graphicsLayer {
                            rotationZ = topLeftRotation.value
                            scaleX = 2.2f
                            scaleY = 2.2f
                        }
                )

                Image(
                    bitmap = assets.bottomRight,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .size(quarterBox)
                        .align(Alignment.BottomEnd)
                        .offset(x = 30.dp + quarterPadding, y = 30.dp + quarterPadding)
                        .graphicsLayer {
                            rotationZ = bottomRightRotation.value
                            scaleX = 2.2f
                            scaleY = 2.2f
                        }
                )
            }
        }
    }
}

private val QUARTER_SIZE = 320.dp

/**
 * Blurred artwork background. The blur is rendered once per artwork (off the main thread) and just
 * drawn as an image - no live blur - so it is cheap, and works on every Android version.
 *
 * @param overlayAlpha read during the draw phase only, so animating it never recomposes anything.
 */
@Composable
fun BlurredArtworkBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    overlayAlpha: () -> Float = { 0.35f },
    fallbackColor: Color = Color(0xff121216)
) {
    val blurred = rememberPrerendered(bitmap, ArtworkBlur.backgroundCache, ArtworkBlur::renderBackground)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        ArtworkTransition(target = blurred, label = "blurredArtwork") { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.30f
                        scaleY = 1.30f
                    }
            )
        }

        Scrim(
            overlayAlpha = overlayAlpha,
            gradientStart = 0.60f,
            gradientEndAlpha = 0.50f
        )
    }
}

/**
 * Fades new artwork in *over* the old one, which stays fully opaque until the new one has finished
 * fading in. (A plain cross-fade would fade both at once and dip to the fallback colour halfway.)
 */
@Composable
private fun <T : Any> ArtworkTransition(
    target: T?,
    label: String,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(tween(ARTWORK_CROSSFADE_MS)) togetherWith
                fadeOut(tween(durationMillis = 1, delayMillis = ARTWORK_CROSSFADE_MS))
        },
        label = label
    ) { value ->
        if (value != null) content(value)
    }
}

/** A flat dark tint plus a bottom gradient, drawn in a single pass. */
@Composable
private fun Scrim(
    overlayAlpha: () -> Float,
    gradientStart: Float,
    gradientEndAlpha: Float
) {
    val gradient = remember(gradientStart, gradientEndAlpha) {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            gradientStart to Color.Transparent,
            1.0f to Color.Black.copy(alpha = gradientEndAlpha)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black.copy(alpha = overlayAlpha().coerceIn(0f, 1f)))
                drawRect(gradient)
            }
    )
}
