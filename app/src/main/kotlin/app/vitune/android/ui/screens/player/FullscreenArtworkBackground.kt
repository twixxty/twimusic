package app.vitune.android.ui.screens.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlin.math.min

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
    // Portrait: pushes the Bleed (cover + blur) down while fading out.
    contentOffsetY: () -> Float = { 0f },
    // Portrait: drawn ABOVE a Wide canvas; only the lower part (the blur behind the controls) is
    // visible and it melts into the canvas above it.
    overlayMode: Boolean = false,
    // Portrait: draw the Bleed cover art underneath while waiting for / crossfading into the canvas.
    showStaticBleed: Boolean = true,
    canvasContent: (@Composable (Modifier, ((ImageBitmap?) -> Unit)?) -> Unit)? = null,
    // Portrait Bleed: the cover / canvas lives in a hero layer that morphs cover rect -> full bleed
    // window -> lyrics header exactly like the Wide canvas (see PlayerHero.kt). `alpha` then only
    // drives the blurred backdrop behind it. All lambdas are read in the draw phase only.
    hero: CoverHeroState? = null,
    lyricsProgress: () -> Float = { 0f },
    heroProgress: () -> Float = { 1f },
    // Wide overlay: first frame of the canvas; the bottom blur is built from it instead of the cover.
    blurFrame: ImageBitmap? = null,
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

    // The bottom blur is built from the FIRST frame of the canvas (kept for as long as this canvas
    // lives), so it matches the video instead of the cover art. Sampling stops once it is captured.
    var canvasFrame by remember(canvasVideoUrl, canvasContent != null) { mutableStateOf<ImageBitmap?>(null) }
    val hasClipFrame by remember(canvasVideoUrl, canvasContent != null) { derivedStateOf { canvasFrame != null } }
    val frameSink: ((ImageBitmap?) -> Unit)? =
        if (canvasFrame == null) {
            { frame: ImageBitmap? -> if (frame != null && canvasFrame == null) canvasFrame = frame }
        } else {
            null
        }
    val clipBleedAlpha by animateFloatAsState(
        targetValue = if (hasClipFrame) 1f else 0f,
        animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = FastOutSlowInEasing),
        label = "fullscreenClipBleedAlpha",
    )
    val animatedVideoVisible by key(artworkUri, canvasVideoUrl) {
        animateFloatAsState(
            targetValue = if (isVideoReady) 1f else 0f,
            animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = FastOutSlowInEasing),
            label = "fullscreenVideoVisible",
        )
    }
    val videoVisible = if (showStaticBleed) animatedVideoVisible else if (isVideoReady) 1f else 0f
    val hasVideo = !overlayMode && !canvasVideoUrl.isNullOrBlank() && canvasContent != null
    val videoBottomMask = remember {
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0.0f to Color.Black,
                    0.66f to Color.Black,
                    0.84f to Color.Black.copy(alpha = 0.55f),
                    1.0f to Color.Transparent,
                ),
        )
    }

    val heroMorph = hero != null && !overlayMode

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
        SideEffect {
            if (visibleArtHeight > 0f) onArtworkVisibleHeight(visibleArtHeight)
        }

        val titleEndFraction = transitionBounds?.bottomTransitionEndFraction?.coerceIn(0.54f, 0.66f) ?: 0.62f
        val bleedCanvasHeightPx = if (visibleArtHeight > 0f) {
            max(visibleArtHeight, viewportHeightPx * titleEndFraction)
                .coerceIn(viewportHeightPx * 0.54f, viewportHeightPx * 0.65f)
        } else {
            viewportHeightPx * 0.60f
        }
        val bleedCanvasHeightDp = with(density) { bleedCanvasHeightPx.toDp() }
        val seam = (bleedCanvasHeightPx / viewportHeightPx.toFloat()).coerceIn(0.48f, 0.66f)
        val seamFadeStart = (seam - 0.16f).coerceAtLeast(0.30f)
        val coverToCanvasScale = if (hasVideo && visibleArtHeight > 0f) {
            (bleedCanvasHeightPx / visibleArtHeight).coerceIn(1f, 1.35f)
        } else {
            1f
        }

        // Everything that is not the cover / canvas itself: fades with `alpha`.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        this.alpha = alpha()
                        if (overlayMode) compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        if (overlayMode) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.46f to Color.Transparent,
                                    0.74f to Color.Black,
                                    1.0f to Color.Black,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .background(fallbackBackgroundColor),
        ) {
        if (showStaticBleed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = contentOffsetY()
                        if (coverToCanvasScale != 1f) {
                            val scale = 1f + (coverToCanvasScale - 1f) * videoVisible
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    },
            ) {
                Crossfade(
                    targetState = shownUri,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS, easing = LinearEasing),
                    label = "fullscreenPortraitArtworkCrossfade",
                ) { currentUri ->
                    val showBleedCover = !hasVideo || videoVisible < 0.999f || currentUri != shownUri
                    if (showBleedCover) {
                        val artworkBitmap = if (currentUri == shownUri) {
                            shownBitmap ?: currentUri?.let { loadedArtworkCache[it] }
                        } else {
                            currentUri?.let { loadedArtworkCache[it] }
                        }
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
            }
        }

        if (overlayMode && blurFrame != null) {
            var frameShown by remember(blurFrame) { mutableStateOf(false) }
            LaunchedEffect(blurFrame) { frameShown = true }
            val frameAlpha by animateFloatAsState(
                targetValue = if (frameShown) 1f else 0f,
                animationSpec = tween(400, easing = LinearEasing),
                label = "wideOverlayFrameBlur",
            )
            Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = frameAlpha }) {
                FullscreenMirroredBlurCanvas(
                    artwork = blurFrame,
                    mirrorSeamFraction = seam,
                    mirrorFadeStart = seamFadeStart,
                    scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
                )
            }
        }

        if (hasVideo) {
            // Keep BackgroundVideoView composed even when videoVisible == 0f so ExoPlayer prepares
            // and remains cached across lyrics toggles, fading in smoothly once the first frame arrives.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = videoVisible },
            ) {
                if (!isBlurredBackgroundStyle) {
                    fallbackBackground()
                }
                CanvasFrameBackdrop(
                    frame = { canvasFrame },
                    fallback = shownBitmap,
                )
                // Cover-based blur until the first canvas frame arrives, then it cross-fades to that.
                Crossfade(
                    targetState = canvasFrame,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(400, easing = LinearEasing),
                    label = "fullscreenBleedBlurSource",
                ) { frame ->
                    val source = frame ?: shownBitmap
                    if (source != null) {
                        FullscreenMirroredBlurCanvas(
                            artwork = source,
                            mirrorSeamFraction = seam,
                            mirrorFadeStart = seamFadeStart,
                            scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
                        )
                    }
                }

                if (!heroMorph) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(bleedCanvasHeightDp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = videoBottomMask, blendMode = BlendMode.DstIn)
                            },
                    ) {
                        canvasContent(Modifier.fillMaxSize(), frameSink)
                    }
                }
            }
        }
        } // backdrop

        if (hero != null && heroMorph) {
            val slotHeightPx = if (hasVideo) bleedCanvasHeightPx else visibleArtHeight
            val slotHeightDp = with(density) { slotHeightPx.toDp() }
            val artHeightDp = with(density) { visibleArtHeight.toDp() }
            val headerRadiusPx = with(density) { 12.dp.toPx() }
            val containerWidthPx = shownBitmap?.let { visibleArtHeight * it.width / it.height.coerceAtLeast(1) } ?: viewportWidthPx.toFloat()
            // The slot never moves; only the layer inside it does, so the measured natural bounds stay
            // stable while the morph runs.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(slotHeightDp)
                    .align(Alignment.TopCenter)
                    .heroCanvasSlot(hero),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            hero.applyCanvasLayer(
                                scope = this,
                                lyricsProgress = lyricsProgress(),
                                canvasProgress = heroProgress(),
                                coverRadius = 0f,
                                headerRadius = headerRadiusPx,
                            )
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // The soft bottom edge turns solid as the artwork lands in the lyrics header.
                                val progress = lyricsProgress()
                                drawRect(
                                    brush = if (progress <= 0.001f) videoBottomMask else heroLandingMask(progress),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        // Static Bleed: the cover only joins in once the morph starts (at rest the
                        // backdrop already draws it with its progressive blur).
                        val cover = shownBitmap
                        if (showStaticBleed && cover != null && visibleArtHeight > 0f) {
                            Image(
                                bitmap = cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(artHeightDp)
                                        .graphicsLayer {
                                            transformOrigin = TransformOrigin(0.5f, 0f)
                                            val progress = lyricsProgress()
                                            val rest = 1f + (coverToCanvasScale - 1f) * videoVisible
                                            // The window is cover-filled with the header square, which would
                                            // show a zoomed-in centre crop of this (zoomed) Bleed cover.
                                            // Scale towards "whole square art in the window" instead, so it
                                            // already looks like the header cover when it lands.
                                            val windowSide = min(viewportWidthPx.toFloat(), slotHeightPx)
                                            val fit = windowSide / min(containerWidthPx, visibleArtHeight).coerceAtLeast(1f)
                                            val scale = rest + (fit - rest) * progress
                                            scaleX = scale
                                            scaleY = scale
                                            translationY = progress * (slotHeightPx / 2f - visibleArtHeight * scale / 2f)
                                            // Fully opaque from the first moment of the flight; only the last
                                            // sliver on the way back fades into the backdrop's own cover.
                                            this.alpha = (progress * 30f).coerceIn(0f, 1f)
                                        },
                            )
                        }
                        if (hasVideo) {
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    // Static Bleed: the canvas gives way to the (opaque) cover under it as
                                    // the window nears the header instead of being cut away at the end.
                                    val landing = if (showStaticBleed) 1f - ((lyricsProgress() - 0.1f) / 0.7f).coerceIn(0f, 1f) else 1f
                                    this.alpha = videoVisible * landing
                                },
                            ) {
                                canvasContent(Modifier.fillMaxSize(), frameSink)
                            }
                        }
                    }
                }
            }
        }

        if (!overlayMode) {
            // Protective bottom contrast gradient so title/controls stay readable on bright covers & canvases.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha() }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.44f to Color.Transparent,
                                0.58f to Color.Black.copy(alpha = 0.26f),
                                0.70f to Color.Black.copy(alpha = 0.48f),
                                0.85f to Color.Black.copy(alpha = 0.68f),
                                1.0f to Color.Black.copy(alpha = 0.80f),
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun heroLandingMask(progress: Float): Brush =
    Brush.verticalGradient(
        colorStops =
            arrayOf(
                0.0f to Color.Black,
                0.66f to Color.Black,
                0.84f to Color.Black.copy(alpha = 0.55f + 0.45f * progress),
                1.0f to Color.Black.copy(alpha = progress),
            ),
    )

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
                                    // The cover zooms towards the canvas while it fades into it.
                                    val zoom = 1f + 0.10f * zoomProgress
                                    scaleX = zoom
                                    scaleY = zoom
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
                                // ...and the canvas settles in from a slight zoom (zoom-out).
                                val zoom = 1.10f - 0.10f * shown
                                scaleX = zoom
                                scaleY = zoom
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
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
                .background(
                    Brush.horizontalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.62f to Color.Black.copy(alpha = 0.22f),
                                0.82f to Color.Black.copy(alpha = 0.42f),
                                1.0f to Color.Black.copy(alpha = 0.56f),
                            ),
                    ),
                ),
        )
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

@Composable
private fun CanvasFrameBackdrop(
    frame: () -> ImageBitmap?,
    fallback: ImageBitmap?,
) {
    val bitmap = frame() ?: fallback ?: return
    FullscreenBlurredBackdropCanvas(
        artwork = bitmap,
        scrimAlpha = BLURRED_ARTWORK_SCRIM_ALPHA,
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
