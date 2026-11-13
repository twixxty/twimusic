package com.mocharealm.accompanist.lyrics.ui.internal.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.vitune.android.utils.BitmapBlur
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Blur for lyric lines on Android 11 and below.
 *
 * RenderEffect / Modifier.blur only exist on Android 12+ and are silent no-ops before that, which
 * is why inactive lines were never blurred on those versions. Here a line that needs a blur is
 * recorded into a GraphicsLayer ONCE, snapshotted, shrunk, blurred on the CPU off the main thread
 * (the same BitmapBlur used for the artwork background) and from then on simply drawn as one
 * bitmap. The line's own scale/alpha animation and the list scrolling keep running on the GPU
 * layer above, so the steady-state cost is a single textured quad per inactive line.
 *
 * A line is only re-captured when its blur bucket (distance from the focused line) or its size
 * changes, never per frame. Lines with no blur are drawn live and untouched, so the focused line's
 * karaoke animation is unaffected.
 */

/** Blur radii are snapped to this many pixels, so a line is re-captured at most a few times. */
private const val BUCKET_STEP_PX = 2f

/** The CPU blur is run on a copy shrunk until its working sigma is about this many pixels. */
private const val WORKING_SIGMA_PX = 2.5f
private const val MAX_DOWNSCALE = 6

/** Frames to wait for the draw pass to record the layer before giving up on a capture. */
private const val RECORD_WAIT_FRAMES = 10

private class LegacyBlurImage(
    val bucket: Int,
    val size: IntSize,
    /** The bitmap covers the line plus this many pixels on every side, so the blur can spill out. */
    val margin: Int,
    val image: ImageBitmap,
)

private fun bucketOf(radiusPx: Float): Int =
    if (!radiusPx.isFinite() || radiusPx < BUCKET_STEP_PX / 2f) 0
    else (radiusPx / BUCKET_STEP_PX).roundToInt().coerceAtLeast(1)

/**
 * Returns a modifier that blurs the content it is applied to by [radiusPx] (a Gaussian sigma in
 * pixels, the same unit as `BlurEffect`). Only meant for API < 31; the caller reads [radiusPx]
 * from animated state, and it is observed outside composition so animating it never recomposes.
 */
@Composable
internal fun rememberLegacyLyricsBlur(radiusPx: () -> Float): Modifier {
    val layer = rememberGraphicsLayer()
    val currentRadius by rememberUpdatedState(radiusPx)
    val maxMarginPx = with(LocalDensity.current) { 24.dp.toPx() }

    var bucket by remember { mutableIntStateOf(0) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var blurred by remember { mutableStateOf<LegacyBlurImage?>(null) }

    LaunchedEffect(Unit) {
        snapshotFlow { bucketOf(currentRadius()) }.collect { bucket = it }
    }

    LaunchedEffect(bucket, measured) {
        if (bucket == 0 || measured.width <= 0 || measured.height <= 0) {
            blurred = null
            return@LaunchedEffect
        }
        val existing = blurred
        if (existing != null && existing.bucket == bucket && existing.size == measured) {
            return@LaunchedEffect
        }

        // The draw pass records the layer once this bucket is observed; wait until it has.
        var frames = 0
        while (layer.size != measured && frames < RECORD_WAIT_FRAMES) {
            withFrameNanos { }
            frames++
        }
        if (layer.size != measured) return@LaunchedEffect

        val sigma = bucket * BUCKET_STEP_PX
        val result =
            try {
                val snapshot = layer.toImageBitmap().asAndroidBitmap()
                withContext(Dispatchers.Default) {
                    blurSnapshot(snapshot, sigma, maxMarginPx.toInt(), bucket, measured)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        if (result != null) blurred = result
    }

    return Modifier
        .onSizeChanged { measured = it }
        .drawWithContent {
            val currentBucket = bucket
            if (currentBucket == 0) {
                drawContent()
                return@drawWithContent
            }
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            val lineSize = IntSize(w, h)
            val image = blurred?.takeIf { it.size == lineSize }

            // Keep the snapshot source current until the blur for this bucket has landed.
            if (image == null || image.bucket != currentBucket) {
                layer.record(this, layoutDirection, lineSize) { this@drawWithContent.drawContent() }
            }
            if (image != null) {
                drawImage(
                    image = image.image,
                    dstOffset = IntOffset(-image.margin, -image.margin),
                    dstSize = IntSize(w + 2 * image.margin, h + 2 * image.margin),
                    filterQuality = FilterQuality.Low,
                )
            } else {
                drawLayer(layer)
            }
        }
}

private fun blurSnapshot(
    snapshot: Bitmap,
    sigma: Float,
    maxMarginPx: Int,
    bucket: Int,
    size: IntSize,
): LegacyBlurImage? {
    val source =
        if (snapshot.config == Bitmap.Config.HARDWARE) {
            snapshot.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else snapshot

    val margin = ceil(sigma * 2f).toInt().coerceIn(0, maxMarginPx.coerceAtLeast(0))
    val downscale = (sigma / WORKING_SIGMA_PX).toInt().coerceIn(1, MAX_DOWNSCALE)
    val paddedWidth = source.width + 2 * margin
    val paddedHeight = source.height + 2 * margin
    val bw = ((paddedWidth + downscale - 1) / downscale).coerceAtLeast(2)
    val bh = ((paddedHeight + downscale - 1) / downscale).coerceAtLeast(2)

    val small =
        try {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }
    val canvas = Canvas(small)
    canvas.scale(bw.toFloat() / paddedWidth, bh.toFloat() / paddedHeight)
    canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

    val pixels = IntArray(bw * bh)
    small.getPixels(pixels, 0, bw, 0, 0, bw, bh)
    BitmapBlur.blurArgb(pixels, bw, bh, sigma * bw / paddedWidth)
    small.setPixels(pixels, 0, bw, 0, 0, bw, bh)

    return LegacyBlurImage(bucket, size, margin, small.asImageBitmap())
}
