package app.vitune.android.ui.screens.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

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
    alpha: Float,
    transitionBounds: FullscreenTransitionBounds?,
    isLandscape: Boolean,
    canvasVideoUrl: String? = null,
    isVideoReady: Boolean = false,
    canvasTall: Boolean = false,
    isBlurredBackgroundStyle: Boolean = true,
    fallbackBackground: @Composable () -> Unit = {},
    canvasContent: (@Composable (Modifier, ((ImageBitmap?) -> Unit)?) -> Unit)? = null,
) {
    if (isLandscape) {
        LandscapeFullscreenThumbnailBackground(
            artworkUri = artworkUri,
            fallbackBackgroundColor = fallbackBackgroundColor,
            alpha = alpha,
            canvasVideoUrl = canvasVideoUrl,
            isVideoReady = isVideoReady,
            canvasContent = canvasContent,
        )
        return
    }

    var canvasFrame by remember(canvasVideoUrl, canvasContent != null) { mutableStateOf<ImageBitmap?>(null) }
    // Read frame presence via derivedStateOf so every sampled video frame doesn't recompose the whole background.
    val hasClipFrame by remember(canvasVideoUrl, canvasContent != null) { derivedStateOf { canvasFrame != null } }
    val clipBleedAlpha by animateFloatAsState(
        targetValue = if (hasClipFrame) 1f else 0f,
        animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS),
        label = "fullscreenClipBleedAlpha",
    )
    val videoVisible by key(artworkUri, canvasVideoUrl) {
        animateFloatAsState(
            targetValue = if (isVideoReady) 1f else 0f,
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS),
            label = "fullscreenVideoVisible",
        )
    }

    val hasVideo = !canvasVideoUrl.isNullOrBlank() && canvasContent != null
    val rawStart = transitionBounds?.bottomTransitionStartFraction ?: 0.58f
    val rawEnd = transitionBounds?.bottomTransitionEndFraction ?: 0.66f
    val fadeStart = rawStart.coerceIn(0f, 0.75f)
    val mirrorSeam =
        rawEnd
            .coerceAtLeast(fadeStart + 0.06f)
            .coerceIn(0f, 0.85f)

    fun fadeMask(
        start: Float,
        seam: Float,
    ) = Brush.verticalGradient(
        colorStops =
            arrayOf(
                0.0f to Color.Black,
                (start - 0.03f).coerceAtLeast(0f) to Color.Black,
                start to Color.Black,
                seam to Color.Transparent,
                1.0f to Color.Transparent,
            ),
    )

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .alpha(alpha),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val viewportHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val viewport = IntSize(viewportWidthPx, viewportHeightPx)

        val seam = if (canvasTall) (maxWidth * (4f / 3f) / maxHeight).coerceIn(0.3f, 0.85f) else mirrorSeam
        val seamFadeStart = if (canvasTall) (seam - (mirrorSeam - fadeStart)).coerceAtLeast(0f) else fadeStart
        val artworkFadeMask = fadeMask(seamFadeStart, seam)
        val artworkSize =
            IntSize(
                viewportWidthPx,
                (viewportHeightPx * seam).roundToInt().coerceAtLeast(1),
            )

        // Static covers get Metrolist's uncropped top-pinned bleed; active videos keep the seam + backdrop layout.
        val usesArtworkBleed = !hasVideo && !canvasTall
        if (!usesArtworkBleed && !isBlurredBackgroundStyle) {
            fallbackBackground()
        }

        Crossfade(
            targetState = artworkUri,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
            label = "fullscreenPortraitArtworkCrossfade",
        ) { currentUri ->
            val artworkBitmap = rememberFullscreenArtworkBitmap(currentUri, if (usesArtworkBleed) viewport else artworkSize)
            if (usesArtworkBleed) {
                if (artworkBitmap != null) {
                    FullscreenArtworkBleed(
                        url = currentUri?.toString().orEmpty(),
                        artwork = artworkBitmap,
                        viewport = viewport,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(fallbackBackgroundColor)) {
                        AsyncImage(
                            model = currentUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                                    },
                        )
                    }
                }
                return@Crossfade
            }

            val backdropBitmap = if (canvasTall) null else canvasFrame ?: artworkBitmap
            if (backdropBitmap != null && hasVideo) {
                FullscreenBlurredBackdropCanvas(
                    artwork = backdropBitmap,
                    scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
                )
            }

            if (artworkBitmap != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = 1f - videoVisible }) {
                    FullscreenSharpArtworkCanvas(
                        artwork = artworkBitmap,
                        mirrorSeamFraction = seam,
                        alphaMask = artworkFadeMask,
                    )
                }

                if (isBlurredBackgroundStyle) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                colorStops =
                                                    arrayOf(
                                                        0.0f to Color.Transparent,
                                                        seam to Color.Transparent,
                                                        0.92f to Color.Black.copy(alpha = 0.18f),
                                                        1.0f to Color.Black.copy(alpha = 0.38f),
                                                    ),
                                            ),
                                    )
                                },
                    )
                }

                if (canvasTall) {
                    FullscreenMirroredBlurCanvas(
                        artwork = artworkBitmap,
                        mirrorSeamFraction = seam,
                        mirrorFadeStart = seamFadeStart,
                        scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
                    )
                    ClipFrameBleed(
                        frame = { canvasFrame },
                        alpha = { clipBleedAlpha },
                        seam = seam,
                        fadeStart = seamFadeStart,
                    )
                }
            } else {
                AsyncImage(
                    model = currentUri,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                            },
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (hasVideo) {
            val videoModifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = videoVisible
                        compositingStrategy = CompositingStrategy.Offscreen
                    }.drawWithContent {
                        drawContent()
                        drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                    }
            if (canvasTall) {
                Box(videoModifier) {
                    canvasContent?.invoke(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .align(Alignment.TopCenter),
                    ) { canvasFrame = it }
                }
            } else {
                canvasContent?.invoke(videoModifier) { canvasFrame = it }
            }
        }
    }
}

@Composable
private fun LandscapeFullscreenThumbnailBackground(
    artworkUri: Uri?,
    fallbackBackgroundColor: Color,
    alpha: Float,
    canvasVideoUrl: String? = null,
    isVideoReady: Boolean = false,
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
                .alpha(alpha)
                .background(fallbackBackgroundColor),
    ) {
        val density = LocalDensity.current
        val fullWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val fullHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val artworkSize = IntSize(fullWidthPx, fullHeightPx)

        val layoutDirection = LocalLayoutDirection.current
        val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
        val artWidth = maxOf(maxHeight * 1.25f, maxWidth * 0.62f).coerceAtMost(maxWidth * 0.70f)

        Crossfade(
            targetState = artworkUri,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
            label = "landscapeFullscreenBackdropCrossfade",
        ) { currentUri ->
            val artworkBitmap = rememberFullscreenArtworkBitmap(currentUri, artworkSize)
            val backdropBitmap = canvasFrame ?: artworkBitmap
            if (backdropBitmap != null) {
                val bw = backdropBitmap.width.toFloat()
                val bh = backdropBitmap.height.toFloat()
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
                    artwork = backdropBitmap,
                    blurRadius = FullscreenBackdropBlurRadius,
                    scrimAlpha = 0.10f,
                    cropOverride = cropOverride,
                )
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
                targetState = artworkUri,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
                label = "landscapeFullscreenArtCrossfade",
            ) { currentUri ->
                val showStaticCover = !hasVideo || !isVideoReady
                if (showStaticCover) {
                    AsyncImage(
                        model = currentUri?.thumbnail(max(fullWidthPx, fullHeightPx).coerceAtLeast(1080)),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                                },
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (hasVideo) {
                val videoModifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = videoVisible
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.drawWithContent {
                            drawContent()
                            drawRect(brush = artworkFadeMask, blendMode = BlendMode.DstIn)
                        }
                canvasContent?.invoke(videoModifier) { canvasFrame = it }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
    }
}

@Composable
private fun rememberFullscreenArtworkBitmap(
    artworkUri: Uri?,
    targetSize: IntSize,
): ImageBitmap? {
    val context = LocalContext.current
    var artwork by remember(artworkUri) { mutableStateOf<ImageBitmap?>(null) }

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
            artwork = loaded
        }
    }

    return artwork
}

private data class FullscreenCropTransform(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
)

private fun calculateFullscreenCropTransform(
    imageWidth: Float,
    imageHeight: Float,
    dstWidth: Float,
    dstHeight: Float,
): FullscreenCropTransform {
    val scale = max(dstWidth / imageWidth.coerceAtLeast(1f), dstHeight / imageHeight.coerceAtLeast(1f))
    val width = imageWidth * scale
    val height = imageHeight * scale
    return FullscreenCropTransform(
        offsetX = (dstWidth - width) / 2f,
        offsetY = (dstHeight - height) / 2f,
        width = width,
        height = height,
    )
}

private fun DrawScope.drawFullscreenCrop(
    artwork: ImageBitmap,
    transform: FullscreenCropTransform,
) {
    drawImage(
        image = artwork,
        dstOffset = IntOffset(transform.offsetX.roundToInt(), transform.offsetY.roundToInt()),
        dstSize = IntSize(transform.width.roundToInt().coerceAtLeast(1), transform.height.roundToInt().coerceAtLeast(1)),
    )
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
private fun FullscreenMirroredBlurCanvas(
    artwork: ImageBitmap,
    mirrorSeamFraction: Float,
    mirrorFadeStart: Float,
    blurRadius: Dp = FullscreenBottomBlurRadius,
    scrimAlpha: Float = 0f,
) {
    MetrolistBlurredArtworkBackground(
        artwork = artwork,
        modifier = Modifier.fillMaxSize(),
        blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() },
        scrimAlpha = if (supportsNativeBlur()) scrimAlpha else max(scrimAlpha, 0.24f),
        mirrorSeam = mirrorSeamFraction,
        mirrorFadeStart = mirrorFadeStart,
    )
}

@Composable
private fun ClipFrameBleed(
    frame: () -> ImageBitmap?,
    alpha: () -> Float,
    seam: Float,
    fadeStart: Float,
) {
    val bitmap = frame() ?: return
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }) {
        FullscreenMirroredBlurCanvas(
            artwork = bitmap,
            mirrorSeamFraction = seam,
            mirrorFadeStart = fadeStart,
            scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
        )
    }
}

@Composable
private fun FullscreenSharpArtworkCanvas(
    artwork: ImageBitmap,
    mirrorSeamFraction: Float,
    alphaMask: Brush,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = alphaMask,
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        val density = LocalDensity.current
        val fullWidthPx = with(density) { maxWidth.toPx() }
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val zoneHeightPx = fullHeightPx * mirrorSeamFraction
        val artworkTransform =
            remember(artwork, fullWidthPx, zoneHeightPx) {
                calculateFullscreenCropTransform(
                    imageWidth = artwork.width.toFloat(),
                    imageHeight = artwork.height.toFloat(),
                    dstWidth = fullWidthPx,
                    dstHeight = zoneHeightPx,
                )
            }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFullscreenCrop(artwork, artworkTransform)
        }
    }
}
