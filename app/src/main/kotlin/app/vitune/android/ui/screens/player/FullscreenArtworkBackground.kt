package app.vitune.android.ui.screens.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.vitune.android.utils.thumbnail
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

internal const val FULLSCREEN_TITLE_TRANSITION_OFFSET_FRACTION = 0.08f
internal const val FULLSCREEN_MIN_FADE_SPAN_FRACTION = 0.18f

private val FullscreenBackdropBlurRadius = 48.dp
private val FullscreenBottomBlurRadius = 24.dp
private const val BLURRED_ARTWORK_SCRIM_ALPHA = 0.12f

internal data class FullscreenTransitionBounds(
    val bottomTransitionStartFraction: Float,
    val bottomTransitionEndFraction: Float,
)

@Composable
internal fun FullscreenThumbnailBackground(
    artworkUri: Uri?,
    fallbackBackgroundColor: Color,
    // Lambdas so animating them only invalidates the draw phase, never a recomposition.
    alpha: () -> Float,
    transitionBounds: FullscreenTransitionBounds?,
    isLandscape: Boolean,
    canvasVideoUrl: String? = null,
    isVideoReady: Boolean = false,
    canvasTall: Boolean = false,
    isBlurredBackgroundStyle: Boolean = true,
    fallbackBackground: @Composable () -> Unit = {},
    // Landscape: lyrics hide the canvas (it fades out, the static cover stays behind it).
    canvasSuppressed: Boolean = false,
    canvasFade: () -> Float = { 1f },
    // Reports the height of the visible (zoomed) cover so the player can morph it elsewhere.
    onArtworkVisibleHeight: (Float) -> Unit = {},
    // Portrait: pushes the Bleed (cover + blur) down while the cover grows into the canvas.
    contentOffsetY: () -> Float = { 0f },
    canvasContent: (@Composable (Modifier, ((ImageBitmap?) -> Unit)?) -> Unit)? = null,
) {
    if (isLandscape) {
        LandscapeFullscreenThumbnailBackground(
            artworkUri = artworkUri,
            fallbackBackgroundColor = fallbackBackgroundColor,
            alpha = alpha,
            canvasVideoUrl = canvasVideoUrl,
            isVideoReady = isVideoReady,
            canvasTall = canvasTall,
            canvasSuppressed = canvasSuppressed,
            canvasFade = canvasFade,
            canvasContent = canvasContent,
        )
        return
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { this.alpha = alpha() }
                .background(fallbackBackgroundColor),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val viewportHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val viewport = IntSize(viewportWidthPx, viewportHeightPx)

        // Only switch to a new song's artwork once its bitmap is ready, so the old Bleed stays until
        // the new one can cross-fade in (nothing blank / low-res flashes in between).
        val shownUri = rememberReadyArtworkUri(artworkUri, viewport)
        val shownBitmap = rememberFullscreenArtworkBitmap(shownUri, viewport)

        val visibleArtHeight = shownBitmap?.let {
            fullscreenArtworkVisibleHeight(viewport.width, viewport.height, it.width, it.height)
        } ?: 0f
        SideEffect { if (visibleArtHeight > 0f) onArtworkVisibleHeight(visibleArtHeight) }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = contentOffsetY() },
        ) {
            Crossfade(
                targetState = shownUri,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
                label = "fullscreenPortraitArtworkCrossfade",
            ) { currentUri ->
                val artworkBitmap = currentUri?.let { loadedArtworkCache[it] }
                if (artworkBitmap != null) {
                    FullscreenArtworkBleed(
                        url = currentUri.toString(),
                        artwork = artworkBitmap,
                        viewport = viewport,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(fallbackBackgroundColor)) {
                        AsyncImage(
                            model = currentUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Protective bottom contrast gradient so title/controls stay readable on bright covers.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.24f),
                            0.78f to Color.Black.copy(alpha = 0.52f),
                            1.0f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun LandscapeFullscreenThumbnailBackground(
    artworkUri: Uri?,
    fallbackBackgroundColor: Color,
    alpha: () -> Float,
    canvasVideoUrl: String? = null,
    isVideoReady: Boolean = false,
    canvasTall: Boolean = false,
    canvasSuppressed: Boolean = false,
    canvasFade: () -> Float = { 1f },
    canvasContent: (@Composable (Modifier, ((ImageBitmap?) -> Unit)?) -> Unit)? = null,
) {
    var canvasFrame by remember(canvasVideoUrl, canvasContent != null) { mutableStateOf<ImageBitmap?>(null) }
    val videoVisible by key(artworkUri, canvasVideoUrl) {
        animateFloatAsState(
            targetValue = if (isVideoReady) 1f else 0f,
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS),
            label = "landscapeFullscreenVideoVisible",
        )
    }
    val hasVideo = !canvasVideoUrl.isNullOrBlank() && canvasContent != null

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { this.alpha = alpha() }
                .background(fallbackBackgroundColor),
    ) {
        val density = LocalDensity.current
        val fullWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val fullHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val artworkSize = IntSize(fullWidthPx, fullHeightPx)
        val shownUri = rememberReadyArtworkUri(artworkUri, artworkSize)

        val layoutDirection = LocalLayoutDirection.current
        val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
        val containerMaxHeight = maxHeight
        val artWidth = maxOf(containerMaxHeight * 1.25f, maxWidth * 0.62f).coerceAtMost(maxWidth * 0.70f)

        Crossfade(
            targetState = shownUri,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
            label = "landscapeFullscreenBackdropCrossfade",
        ) { currentUri ->
            val artworkBitmap = rememberFullscreenArtworkBitmap(currentUri, artworkSize)
            if (artworkBitmap != null) {
                LandscapeBackdrop(artworkBitmap, fullWidthPx, fullHeightPx)
            }
        }

        // The canvas' own colours fade in over the artwork backdrop instead of swapping abruptly.
        if (hasVideo) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = videoVisible * canvasFade() },
            ) {
                val frame = canvasFrame
                if (frame != null) LandscapeBackdrop(frame, fullWidthPx, fullHeightPx)
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.50f to Color.Transparent,
                                    0.78f to Color.Black.copy(alpha = 0.12f),
                                    1.0f to Color.Black.copy(alpha = 0.22f),
                                ),
                        ),
                    ),
        )

        val leftTransitionWidth = if (cutoutStart > 0.dp) maxOf(cutoutStart + 48.dp, artWidth * 0.18f) else 0.dp
        val leftProgressiveFraction =
            if (leftTransitionWidth > 0.dp) {
                (leftTransitionWidth / artWidth).coerceIn(0.15f, 0.25f)
            } else {
                0f
            }
        val leftFadeFraction = leftProgressiveFraction * 0.90f
        val fadeStartFraction = 0.85f
        val fadeEndFraction = 1.0f
        val artworkFadeMask =
            remember(leftFadeFraction, fadeStartFraction, fadeEndFraction) {
                if (leftFadeFraction > 0f) {
                    Brush.horizontalGradient(
                        colorStops =
                            arrayOf(
                                0.00f to Color.Transparent,
                                leftFadeFraction * 0.20f to Color.Black.copy(alpha = 0.25f),
                                leftFadeFraction * 0.40f to Color.Black.copy(alpha = 0.60f),
                                leftFadeFraction * 0.73f to Color.Black.copy(alpha = 0.90f),
                                leftFadeFraction to Color.Black,
                                fadeStartFraction to Color.Black,
                                0.89f to Color.Black.copy(alpha = 0.90f),
                                0.94f to Color.Black.copy(alpha = 0.60f),
                                0.97f to Color.Black.copy(alpha = 0.25f),
                                fadeEndFraction to Color.Transparent,
                            ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colorStops =
                            arrayOf(
                                0.00f to Color.Black,
                                fadeStartFraction to Color.Black,
                                0.89f to Color.Black.copy(alpha = 0.90f),
                                0.94f to Color.Black.copy(alpha = 0.60f),
                                0.97f to Color.Black.copy(alpha = 0.25f),
                                fadeEndFraction to Color.Transparent,
                            ),
                    )
                }
            }

        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(artWidth)
                    .align(Alignment.CenterStart)
                    .artworkHorizontalProgressiveBlur(
                        leftStartFraction = if (leftProgressiveFraction > 0f) 0.02f else 0f,
                        leftEndFraction = leftProgressiveFraction,
                        rightStartFraction = 0.82f,
                        rightEndFraction = 0.98f,
                        maxRadius = 56.dp,
                    ),
        ) {
            Crossfade(
                targetState = shownUri,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
                label = "landscapeFullscreenArtCrossfade",
            ) { currentUri ->
                val coverAlpha = if (!hasVideo || currentUri != shownUri) 1f else (1f - videoVisible).coerceIn(0f, 1f)
                if (coverAlpha > 0f || hasVideo) {
                    AsyncImage(
                        model = currentUri?.thumbnail(max(fullWidthPx, fullHeightPx).coerceAtLeast(1080)),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // The cover is the opaque bottom layer: it only goes away once the
                                    // canvas is fully visible, and comes back as lyrics hide the canvas.
                                    val zoomProgress =
                                        if (!hasVideo || currentUri != shownUri) 0f else videoVisible * canvasFade()
                                    this.alpha = (1f - zoomProgress).coerceIn(0f, 1f)
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                                },
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (hasVideo) {
                val videoWidth = if (canvasTall) minOf(artWidth, containerMaxHeight * 0.92f) else artWidth
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(videoWidth)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                val shown = videoVisible * canvasFade()
                                this.alpha = shown
                                compositingStrategy = CompositingStrategy.Offscreen
                            }.drawWithContent {
                                drawContent()
                                drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                            },
                ) {
                    canvasContent?.invoke(Modifier.fillMaxSize()) { canvasFrame = it }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    }
}

// Decoded artwork, so cross-fading children and returning to a song never start from nothing.
private val loadedArtworkCache = LinkedHashMap<Uri, ImageBitmap>()

private fun cacheArtwork(uri: Uri, bitmap: ImageBitmap) {
    loadedArtworkCache.remove(uri)
    loadedArtworkCache[uri] = bitmap
    while (loadedArtworkCache.size > 6) loadedArtworkCache.remove(loadedArtworkCache.keys.first())
}

/** The artwork uri, but only once its bitmap is decoded (or after a short timeout). */
@Composable
private fun rememberReadyArtworkUri(
    artworkUri: Uri?,
    targetSize: IntSize,
): Uri? {
    val bitmap = rememberFullscreenArtworkBitmap(artworkUri, targetSize)
    var ready by remember { mutableStateOf(artworkUri) }
    LaunchedEffect(artworkUri, bitmap) {
        if (artworkUri == null || bitmap != null) {
            ready = artworkUri
        } else {
            delay(1500)
            ready = artworkUri
        }
    }
    return ready
}

@Composable
private fun rememberFullscreenArtworkBitmap(
    artworkUri: Uri?,
    targetSize: IntSize,
): ImageBitmap? {
    val context = LocalContext.current
    var artwork by remember(artworkUri) { mutableStateOf(artworkUri?.let { loadedArtworkCache[it] }) }

    LaunchedEffect(artworkUri, targetSize) {
        if (artworkUri == null) {
            artwork = null
            return@LaunchedEffect
        }
        val reqWidth = targetSize.width.coerceAtLeast(1)
        val reqHeight = targetSize.height.coerceAtLeast(1)
        val highResUri = artworkUri.thumbnail(max(reqWidth, reqHeight).coerceAtLeast(1080))
        val loaded =
            withContext(Dispatchers.IO) {
                suspend fun execute(data: Uri): ImageBitmap? {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(data)
                            .size(reqWidth, reqHeight)
                            .scale(Scale.FILL)
                            .allowHardware(false)
                            .build()
                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
                    val safeBitmap =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                        } else {
                            bitmap
                        }
                    return safeBitmap.asImageBitmap()
                }
                execute(highResUri) ?: if (highResUri != artworkUri) execute(artworkUri) else null
            }
        if (loaded != null) {
            cacheArtwork(artworkUri, loaded)
            artwork = loaded
        }
    }

    return artwork
}

@Composable
private fun FullscreenBlurredBackdropCanvas(
    artwork: ImageBitmap,
    blurRadius: Dp = FullscreenBackdropBlurRadius,
    scrimAlpha: Float = 0f,
    cropOverride: FloatArray? = null,
) {
    MetrolistBlurredArtworkBackground(
        artwork = artwork,
        modifier = Modifier.fillMaxSize(),
        blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() },
        scrimAlpha = if (supportsNativeBlur()) scrimAlpha else max(scrimAlpha, 0.34f),
        cropOverride = cropOverride,
    )
}

@Composable
private fun LandscapeBackdrop(
    bitmap: ImageBitmap,
    fullWidthPx: Int,
    fullHeightPx: Int,
) {
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val cropOverride =
        if (bw > 0f && bh > 0f) {
            val scale = maxOf(fullWidthPx.toFloat() / bw, fullHeightPx.toFloat() / bh)
            val imgW = bw * scale
            val imgH = bh * scale
            floatArrayOf(
                (fullWidthPx.toFloat() - imgW) / 2f,
                (fullHeightPx.toFloat() - imgH) / 2f,
                imgW,
                imgH,
            )
        } else {
            null
        }
    FullscreenBlurredBackdropCanvas(
        artwork = bitmap,
        blurRadius = FullscreenBackdropBlurRadius,
        scrimAlpha = 0.10f,
        cropOverride = cropOverride,
    )
}
