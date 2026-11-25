package app.vitune.android.ui.screens.player

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal const val CANVAS_CROSSFADE_DURATION_MS = 500

// Blur radii in px at REFERENCE_WIDTH. Fine steps at the low end prevent a visible band at the seam.
private val BLUR_RADII =
    floatArrayOf(0f, 1f, 2f, 3.5f, 5.5f, 8f, 11f, 15f, 20f, 27f, 36f, 48f, 63f, 82f, 105f, 135f, 170f, 210f, 250f)
private const val REFERENCE_WIDTH = 1080f
private const val BLUR_START = 0.70f
private const val BLUR_END = 1.55f

// A steep curve keeps the first blurred rows indistinguishable from the sharp cover.
private const val BLUR_CURVE = 1.8f
private const val BLUR_PASSES = 3
private const val TONE_START = 0.90f
private const val TONE_SPAN = 0.95f
private const val TONE_STRENGTH = 0.90f
private const val SHADOW_LOW = 0.10f
private const val SHADOW_HIGH = 0.45f

// Small blur radii run at full width; everything below DETAIL_HEIGHT uses a quarter-width soft layer.
private const val DETAIL_HEIGHT = 0.40f
private const val SOFT_DIVISOR = 4

private const val HALF_RES_SIGMA = 4f
private const val QUARTER_RES_SIGMA = 8f
private const val GRAIN_TILE = 64
private const val GRAIN_ALPHA = 3
private const val BLEED_CACHE_SIZE = 4

// Reflect only the bottom 18% of rows so no distinct upper feature stretches into the bottom bleed.
private const val BLEED_SOURCE = 0.18f
private const val CROSSFADE_ROWS = 48

// The cover reaches up to 60% of viewport height (capped at 1.6x horizontal zoom).
private const val COVER_HEIGHT_FRACTION = 0.6f
private const val MAX_COVER_ZOOM = 1.6f

internal const val ARTWORK_TEXTURE_SIZE = 128
internal const val ARTWORK_TEXTURE_PADDING = 48

private data class BleedKey(
    val url: String,
    val container: IntSize,
)

private class FullscreenBleed(
    val tone: Color,
    val preview: ImageBitmap?,
    val art: ImageBitmap?,
    val detail: ImageBitmap?,
    val soft: ImageBitmap?,
    val sourceWidth: Int,
)

private val bleedCache = LinkedHashMap<BleedKey, FullscreenBleed>()

private fun pixelsToImageBitmap(
    pixels: IntArray,
    width: Int,
    height: Int,
): ImageBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

private val grainBrush: ShaderBrush by lazy {
    val random = Random(0)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) { (GRAIN_ALPHA shl 24) or if (random.nextBoolean()) 0xFFFFFF else 0 }
    ShaderBrush(ImageShader(pixelsToImageBitmap(pixels, GRAIN_TILE, GRAIN_TILE), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Exact Metrolist static artwork renderer for Fullscreen Artwork portrait mode:
 * top-pinned cover zoomed cleanly up to 60% height, continuing below into a Catmull-Rom stretched
 * bottom reflection under a 3-pass multi-radius progressive blur (0..250px) pulled toward an extracted
 * dark muted shadow tone and topped with subtle film grain.
 */
@Composable
internal fun FullscreenArtworkBleed(
    url: String,
    artwork: ImageBitmap?,
    viewport: IntSize,
) {
    if (artwork == null || viewport.width <= 0 || viewport.height <= 0 || artwork.width <= 0 || artwork.height <= 0) return

    val coverHeight = viewport.width * artwork.height / artwork.width.toFloat()
    val zoom = (viewport.height * COVER_HEIGHT_FRACTION / coverHeight).coerceIn(1f, MAX_COVER_ZOOM)
    val container = IntSize((viewport.width * zoom).roundToInt().coerceAtLeast(1), viewport.height)
    val key = BleedKey(url, container)
    val artHeight = (container.width * artwork.height / artwork.width.toFloat()).roundToInt().coerceAtLeast(1)
    val detailTop = fullscreenArtworkDetailTop(container.width, artHeight)
    val softTop = detailTop + ceil(DETAIL_HEIGHT * container.width / SOFT_DIVISOR).toInt() * SOFT_DIVISOR

    var bleed by remember(key) { mutableStateOf(bleedCache[key]) }
    LaunchedEffect(key, artwork) {
        val current = bleed
        if (container.height <= detailTop || (current?.detail != null && current.sourceWidth >= artwork.width)) {
            return@LaunchedEffect
        }
        val coarse = current ?: withContext(Dispatchers.Default) {
            renderCoarseBleed(artwork, container, artHeight, detailTop)
        }
        bleed = coarse
        bleed = withContext(Dispatchers.Default) {
            val artPixels = artwork.downscaled(container.width, artHeight)
            renderBleedBitmaps(artPixels, container, artHeight, detailTop, softTop, coarse, artwork.width)
        }.also {
            bleedCache[key] = it
            if (bleedCache.size > BLEED_CACHE_SIZE) bleedCache.remove(bleedCache.keys.first())
        }
    }

    val previewAlpha by animateFloatAsState(
        targetValue = if (bleed?.preview != null) 1f else 0f,
        animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS),
        label = "fullscreenBleedPreviewAlpha",
    )
    val bleedAlpha by animateFloatAsState(
        targetValue = if (bleed?.detail != null) 1f else 0f,
        animationSpec = tween(CANVAS_CROSSFADE_DURATION_MS),
        label = "fullscreenBleedDetailAlpha",
    )

    Canvas(Modifier.fillMaxSize()) {
        bleed?.let { drawRect(it.tone) }
        translate(left = (size.width - container.width) / 2f) {
            drawBleed(artwork, container.width, artHeight, detailTop, softTop, bleed, previewAlpha, bleedAlpha)
        }
    }
}

internal fun fullscreenArtworkDetailTop(
    width: Int,
    artHeight: Int,
): Int {
    val start = min(BLUR_START * width, (artHeight - CROSSFADE_ROWS).coerceAtLeast(0).toFloat())
    return (start / SOFT_DIVISOR).toInt() * SOFT_DIVISOR
}

private fun DrawScope.drawBleed(
    artwork: ImageBitmap,
    width: Int,
    artHeight: Int,
    detailTop: Int,
    softTop: Int,
    bleed: FullscreenBleed?,
    previewAlpha: Float,
    bleedAlpha: Float,
) {
    drawImage(artwork, dstSize = IntSize(width, artHeight), filterQuality = FilterQuality.High)
    bleed?.preview?.let {
        drawImage(
            it,
            dstOffset = IntOffset(0, detailTop),
            dstSize = IntSize(it.width * SOFT_DIVISOR, it.height * SOFT_DIVISOR),
            alpha = previewAlpha,
            filterQuality = FilterQuality.Low,
        )
    }
    bleed?.art?.let { drawImage(it, alpha = bleedAlpha) }
    bleed?.detail?.let { detail ->
        drawImage(detail, dstOffset = IntOffset(0, detailTop), alpha = bleedAlpha)
        bleed.soft?.let {
            drawImage(
                it,
                dstOffset = IntOffset(0, softTop),
                dstSize = IntSize(it.width * SOFT_DIVISOR, it.height * SOFT_DIVISOR),
                alpha = bleedAlpha,
                filterQuality = FilterQuality.Low,
            )
        }
        if (size.height > detailTop) {
            drawRect(
                grainBrush,
                topLeft = Offset(0f, detailTop.toFloat()),
                size = Size(width.toFloat(), size.height - detailTop),
                alpha = bleedAlpha,
            )
        }
    }
}

private suspend fun renderBleedBitmaps(
    art: IntArray,
    container: IntSize,
    artHeight: Int,
    detailTop: Int,
    softTop: Int,
    coarse: FullscreenBleed,
    sourceWidth: Int,
): FullscreenBleed {
    val tone = coarse.tone.toArgb()
    val width = container.width
    val canvas = bleedCanvas(art, width, artHeight, container.height)
    val detailBottom = min(softTop, container.height)
    val detailRows = renderBleedRows(canvas, width, container.height, width.toFloat(), detailTop, detailBottom - 1, tone)
    fadeInTopRows(detailRows, width, CROSSFADE_ROWS)
    val artBitmap = pixelsToImageBitmap(art, width, artHeight)
    val detail = pixelsToImageBitmap(detailRows, width, detailBottom - detailTop)
    if (container.height <= softTop) return FullscreenBleed(coarse.tone, coarse.preview, artBitmap, detail, null, sourceWidth)

    val softWidth = (width + SOFT_DIVISOR - 1) / SOFT_DIVISOR
    val softHeight = (container.height + SOFT_DIVISOR - 1) / SOFT_DIVISOR
    val softCanvas = canvas.downsampled(width, container.height, 0, container.height - 1, SOFT_DIVISOR)
    val soft = softBitmap(softCanvas, softWidth, softHeight, width, softTop / SOFT_DIVISOR, tone, 0)
    return FullscreenBleed(coarse.tone, coarse.preview, artBitmap, detail, soft, sourceWidth)
}

private suspend fun renderCoarseBleed(
    artwork: ImageBitmap,
    container: IntSize,
    artHeight: Int,
    detailTop: Int,
): FullscreenBleed {
    val width = ((container.width + SOFT_DIVISOR - 1) / SOFT_DIVISOR).coerceAtLeast(1)
    val artRows = ((artHeight + SOFT_DIVISOR - 1) / SOFT_DIVISOR).coerceAtLeast(1)
    val height = ((container.height + SOFT_DIVISOR - 1) / SOFT_DIVISOR).coerceAtLeast(1)
    val small = artwork.downscaled(width, artRows)
    val tone = bleedTone(small)
    val canvas = bleedCanvas(small, width, artRows, height)
    val firstRow = (detailTop / SOFT_DIVISOR).coerceIn(0, (height - 1).coerceAtLeast(0))
    val preview = softBitmap(canvas, width, height, container.width, firstRow, tone, CROSSFADE_ROWS / SOFT_DIVISOR)
    return FullscreenBleed(Color(tone), preview, null, null, null, 0)
}

private suspend fun softBitmap(
    canvas: IntArray,
    width: Int,
    height: Int,
    containerWidth: Int,
    firstRow: Int,
    tone: Int,
    fadeRows: Int,
): ImageBitmap {
    val rows = renderBleedRows(canvas, width, height, containerWidth.toFloat() / SOFT_DIVISOR, firstRow, height - 1, tone)
    fadeInTopRows(rows, width, fadeRows)
    return pixelsToImageBitmap(rows, width, height - firstRow)
}

private fun fadeInTopRows(
    pixels: IntArray,
    width: Int,
    fadeRows: Int,
) {
    if (width <= 0) return
    for (row in 0 until min(fadeRows, pixels.size / width)) {
        val alpha = (smootherstep((row + 0.5f) / fadeRows) * 255).roundToInt()
        for (i in row * width until (row + 1) * width) pixels[i] = (alpha shl 24) or (pixels[i] and 0xFFFFFF)
    }
}

private fun ImageBitmap.downscaled(
    width: Int,
    height: Int,
): IntArray {
    val target = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(target), Size(width.toFloat(), height.toFloat())) {
        drawImage(this@downscaled, dstSize = IntSize(width, height), filterQuality = FilterQuality.High)
    }
    return IntArray(width * height).also { target.readPixels(it) }
}

private fun smootherstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

private fun luminance(argb: Int): Int =
    299 * ((argb shr 16) and 0xFF) + 587 * ((argb shr 8) and 0xFF) + 114 * (argb and 0xFF)

internal fun bleedTone(art: IntArray): Int {
    if (art.isEmpty()) return 0xFF121212.toInt()
    val byLuminance = art.sortedBy(::luminance)
    val from = (art.size * SHADOW_LOW).toInt().coerceIn(0, art.lastIndex)
    val to = max((art.size * SHADOW_HIGH).toInt(), from + 1).coerceAtMost(art.size)
    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in byLuminance.subList(from, to)) {
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = (to - from) * 255f
    val r = red / count
    val g = green / count
    val b = blue / count
    val high = maxOf(r, g, b)
    val low = minOf(r, g, b)
    val delta = high - low
    val lightness = (high + low) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
    val hue =
        when {
            delta == 0f -> 0f
            high == r -> 60f * (((g - b) / delta + 6f) % 6f)
            high == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
    return Color.hsl(hue, min(saturation, 0.5f), (lightness * 1.10f).coerceIn(0.05f, 0.22f)).toArgb()
}

internal fun bleedCanvas(
    art: IntArray,
    width: Int,
    artHeight: Int,
    height: Int,
): IntArray {
    val canvas = IntArray(width * height)
    art.copyInto(canvas, 0, 0, width * min(artHeight, height))
    val stripRows = (artHeight * BLEED_SOURCE).roundToInt().coerceIn(minOf(2, artHeight), artHeight)
    val below = (height - artHeight).coerceAtLeast(1)
    for (y in artHeight until height) {
        val at = (y - artHeight) * (stripRows - 1f) / below
        val base = at.toInt()
        val t = at - base
        val weights =
            floatArrayOf(
                ((-0.5f * t + 1f) * t - 0.5f) * t,
                (1.5f * t - 2.5f) * t * t + 1f,
                ((-1.5f * t + 2f) * t + 0.5f) * t,
                (0.5f * t - 0.5f) * t * t,
            )
        for (x in 0 until width) {
            var red = 0f
            var green = 0f
            var blue = 0f
            for (tap in 0..3) {
                val row = (artHeight - 1 - (base - 1 + tap).coerceIn(0, stripRows - 1)).coerceIn(0, artHeight - 1)
                val p = art[row * width + x]
                red += weights[tap] * ((p shr 16) and 0xFF)
                green += weights[tap] * ((p shr 8) and 0xFF)
                blue += weights[tap] * (p and 0xFF)
            }
            canvas[y * width + x] =
                (0xFF shl 24) or
                (red.roundToInt().coerceIn(0, 255) shl 16) or
                (green.roundToInt().coerceIn(0, 255) shl 8) or
                blue.roundToInt().coerceIn(0, 255)
        }
    }
    return canvas
}

private fun IntArray.downsampled(
    width: Int,
    height: Int,
    top: Int,
    bottom: Int,
    factor: Int,
): IntArray {
    val outWidth = (width + factor - 1) / factor
    val outHeight = (bottom - top + factor) / factor
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (sy in top + y * factor until min(top + (y + 1) * factor, min(bottom + 1, height))) {
                for (sx in x * factor until min((x + 1) * factor, width)) {
                    val p = this[sy * width + sx]
                    red += (p shr 16) and 0xFF
                    green += (p shr 8) and 0xFF
                    blue += p and 0xFF
                    count++
                }
            }
            val safeCount = count.coerceAtLeast(1)
            out[y * outWidth + x] = (0xFF shl 24) or ((red / safeCount) shl 16) or ((green / safeCount) shl 8) or (blue / safeCount)
        }
    }
    return out
}

private fun blurRadiusAt(row: Float): Float {
    if (row <= BLUR_START) return 0f
    return BLUR_RADII.last() * ((row - BLUR_START) / (BLUR_END - BLUR_START)).coerceAtMost(1f).pow(BLUR_CURVE)
}

internal suspend fun renderBleedRows(
    canvas: IntArray,
    width: Int,
    height: Int,
    unit: Float,
    firstRow: Int,
    lastRow: Int,
    tone: Int,
): IntArray {
    val rows = (lastRow - firstRow + 1).coerceAtLeast(1)
    val radii = FloatArray(rows) { blurRadiusAt((firstRow + it) / unit.coerceAtLeast(1f)) }
    val upper = IntArray(rows) { BLUR_RADII.indexOfFirst { level -> level >= radii[it] }.coerceAtLeast(0) }
    val lower = IntArray(rows) { max(upper[it] - 1, 0) }
    val usedFirst = IntArray(upper.max() + 1) { Int.MAX_VALUE }
    val usedLast = IntArray(usedFirst.size) { -1 }
    for (row in 0 until rows) {
        for (level in intArrayOf(lower[row], upper[row])) {
            usedFirst[level] = min(usedFirst[level], firstRow + row)
            usedLast[level] = max(usedLast[level], firstRow + row)
        }
    }
    val levels =
        coroutineScope {
            usedFirst.indices
                .map { level ->
                    async(Dispatchers.Default) {
                        val top = usedFirst[level]
                        if (usedLast[level] < 0) {
                            IntArray(0)
                        } else if (level == 0) {
                            canvas.copyOfRange(top * width, (usedLast[level] + 1) * width)
                        } else {
                            blurredRows(canvas, width, height, top, usedLast[level], BLUR_RADII[level] * unit / REFERENCE_WIDTH / 2f)
                        }
                    }
                }.awaitAll()
        }

    val toneRed = (tone shr 16) and 0xFF
    val toneGreen = (tone shr 8) and 0xFF
    val toneBlue = tone and 0xFF
    val out = IntArray(rows * width)
    for (row in 0 until rows) {
        val low = lower[row]
        val high = upper[row]
        val mix = if (high == low) 0f else (radii[row] - BLUR_RADII[low]) / (BLUR_RADII[high] - BLUR_RADII[low])
        val lowerLevel = levels[low]
        val upperLevel = levels[high]
        val lowerStart = (firstRow + row - usedFirst[low]) * width
        val upperStart = (firstRow + row - usedFirst[high]) * width
        val toneMix = TONE_STRENGTH * smootherstep(((firstRow + row) / unit.coerceAtLeast(1f) - TONE_START) / TONE_SPAN)
        for (x in 0 until width) {
            val a = lowerLevel[lowerStart + x]
            val b = upperLevel[upperStart + x]
            out[row * width + x] =
                (0xFF shl 24) or
                (towards(blend(a shr 16, b shr 16, mix), toneRed, toneMix) shl 16) or
                (towards(blend(a shr 8, b shr 8, mix), toneGreen, toneMix) shl 8) or
                towards(blend(a, b, mix), toneBlue, toneMix)
        }
    }
    return out
}

private fun blend(
    from: Int,
    to: Int,
    amount: Float,
): Float = (from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * amount

private fun towards(
    value: Float,
    target: Int,
    amount: Float,
): Int = (value + (target - value) * amount).roundToInt().coerceIn(0, 255)

private fun blurredRows(
    canvas: IntArray,
    width: Int,
    height: Int,
    firstRow: Int,
    lastRow: Int,
    sigma: Float,
): IntArray {
    val factor =
        when {
            sigma >= QUARTER_RES_SIGMA -> 4
            sigma >= HALF_RES_SIGMA -> 2
            else -> 1
        }
    val scaled = sigma / factor
    val half = (sqrt(1f + 4f * scaled * scaled) - 1f) / 2f
    val margin = (ceil(BLUR_PASSES * half).toInt() + 1) * factor
    val sliceTop = max(firstRow - margin, 0) / factor * factor
    val sliceBottom = min(lastRow + margin, height - 1)
    val sliceWidth = ((width + factor - 1) / factor).coerceAtLeast(1)
    val sliceHeight = ((sliceBottom - sliceTop + factor) / factor).coerceAtLeast(1)
    var slice = canvas.downsampled(width, height, sliceTop, sliceBottom, factor)
    repeat(BLUR_PASSES) {
        val horizontal = IntArray(slice.size)
        blurPass(slice, horizontal, lines = sliceHeight, length = sliceWidth, stride = 1, lineStride = sliceWidth, half = half)
        val vertical = IntArray(slice.size)
        blurPass(horizontal, vertical, lines = sliceWidth, length = sliceHeight, stride = sliceWidth, lineStride = 1, half = half)
        slice = vertical
    }
    return upsampledRows(slice, sliceWidth, sliceHeight, width, firstRow - sliceTop, lastRow - sliceTop, factor)
}

private fun upsampledRows(
    small: IntArray,
    smallWidth: Int,
    smallHeight: Int,
    width: Int,
    first: Int,
    last: Int,
    factor: Int,
): IntArray {
    val left = IntArray(width)
    val right = IntArray(width)
    val rightWeight = FloatArray(width)
    for (x in 0 until width) {
        val at = ((x + 0.5f) / factor - 0.5f).coerceIn(0f, smallWidth - 1f)
        left[x] = at.toInt()
        right[x] = min(left[x] + 1, smallWidth - 1)
        rightWeight[x] = at - left[x]
    }
    val out = IntArray((last - first + 1) * width)
    for (row in first..last) {
        val at = ((row + 0.5f) / factor - 0.5f).coerceIn(0f, smallHeight - 1f)
        val top = at.toInt() * smallWidth
        val bottom = min(at.toInt() + 1, smallHeight - 1) * smallWidth
        val bottomWeight = at - at.toInt()
        for (x in 0 until width) {
            val a = small[top + left[x]]
            val b = small[top + right[x]]
            val c = small[bottom + left[x]]
            val d = small[bottom + right[x]]
            val wx = rightWeight[x]
            out[(row - first) * width + x] =
                (0xFF shl 24) or
                (bilinear(a shr 16, b shr 16, c shr 16, d shr 16, wx, bottomWeight) shl 16) or
                (bilinear(a shr 8, b shr 8, c shr 8, d shr 8, wx, bottomWeight) shl 8) or
                bilinear(a, b, c, d, wx, bottomWeight)
        }
    }
    return out
}

private fun bilinear(
    a: Int,
    b: Int,
    c: Int,
    d: Int,
    x: Float,
    y: Float,
): Int {
    val top = blend(a, b, x)
    val bottom = blend(c, d, x)
    return (top + (bottom - top) * y).roundToInt().coerceIn(0, 255)
}

private fun blurPass(
    src: IntArray,
    dst: IntArray,
    lines: Int,
    length: Int,
    stride: Int,
    lineStride: Int,
    half: Float,
) {
    val whole = half.toInt()
    val edge = half - whole
    val norm = 1f / (2 * whole + 1 + 2f * edge)
    for (line in 0 until lines) {
        val start = line * lineStride
        var red = 0
        var green = 0
        var blue = 0
        for (i in -whole..whole) {
            val p = src[start + i.coerceIn(0, length - 1) * stride]
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (i in 0 until length) {
            val before = src[start + max(i - whole - 1, 0) * stride]
            val after = src[start + min(i + whole + 1, length - 1) * stride]
            val r = ((red + edge * (((before shr 16) and 0xFF) + ((after shr 16) and 0xFF))) * norm).roundToInt()
            val g = ((green + edge * (((before shr 8) and 0xFF) + ((after shr 8) and 0xFF))) * norm).roundToInt()
            val b = ((blue + edge * ((before and 0xFF) + (after and 0xFF))) * norm).roundToInt()
            dst[start + i * stride] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val leaving = src[start + max(i - whole, 0) * stride]
            red += ((after shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((after shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (after and 0xFF) - (leaving and 0xFF)
        }
    }
}

internal fun blurArtworkPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    sigma: Int,
    sigmaX: Int = sigma,
) {
    require(width > 0 && height > 0 && pixels.size == width * height)
    if (sigma <= 0) return
    val radius = ((sqrt(4.0 * sigma * sigma + 1.0) - 1.0) / 2.0).roundToInt().coerceAtLeast(1)
    val radiusX = ((sqrt(4.0 * sigmaX * sigmaX + 1.0) - 1.0) / 2.0).roundToInt().coerceAtLeast(1)
    val channels = Array(4) { FloatArray(pixels.size) }
    pixels.forEachIndexed { i, color ->
        val alpha = (color ushr 24).toFloat()
        channels[0][i] = alpha
        channels[1][i] = ((color ushr 16) and 255) * alpha / 255f
        channels[2][i] = ((color ushr 8) and 255) * alpha / 255f
        channels[3][i] = (color and 255) * alpha / 255f
    }
    val scratch = FloatArray(pixels.size)
    val divisor = (radius * 2 + 1).toFloat()
    val divisorX = (radiusX * 2 + 1).toFloat()
    repeat(3) {
        for (channel in channels) {
            for (y in 0 until height) {
                val row = y * width
                var sum = 0f
                for (x in -radiusX..radiusX) sum += channel[row + x.coerceIn(0, width - 1)]
                for (x in 0 until width) {
                    scratch[row + x] = sum / divisorX
                    sum += channel[row + (x + radiusX + 1).coerceAtMost(width - 1)] - channel[row + (x - radiusX).coerceAtLeast(0)]
                }
            }
            for (x in 0 until width) {
                var sum = 0f
                for (y in -radius..radius) sum += scratch[y.coerceIn(0, height - 1) * width + x]
                for (y in 0 until height) {
                    channel[y * width + x] = sum / divisor
                    sum +=
                        scratch[(y + radius + 1).coerceAtMost(height - 1) * width + x] - scratch[(y - radius).coerceAtLeast(0) * width + x]
                }
            }
        }
    }
    for (i in pixels.indices) {
        val alpha = channels[0][i].roundToInt().coerceIn(0, 255)
        fun ch(index: Int): Int = if (alpha == 0) 0 else (channels[index][i] * 255f / channels[0][i]).roundToInt().coerceIn(0, 255)
        pixels[i] = (alpha shl 24) or (ch(1) shl 16) or (ch(2) shl 8) or ch(3)
    }
}

private data class LegacyArtworkBlurKey(
    val source: Bitmap,
    val generation: Int,
    val radius: Int,
)

private val legacyArtworkBlurCache = LruCache<LegacyArtworkBlurKey, ImageBitmap>(4)
private val legacyArtworkBlurMutex = Mutex()

internal suspend fun legacyArtworkBlur(
    source: Bitmap,
    radius: Int,
): ImageBitmap =
    withContext(Dispatchers.Default) {
        require(radius > 0)
        legacyArtworkBlurMutex.withLock {
            val key = LegacyArtworkBlurKey(source, source.generationId, radius)
            legacyArtworkBlurCache.get(key)?.let { return@withLock it }
            val bitmap =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
                    source.copy(Bitmap.Config.ARGB_8888, false) ?: source
                } else {
                    source
                }
            val scale = ARTWORK_TEXTURE_SIZE.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1)
            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaledRaw = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val scaled = scaledRaw.copy(Bitmap.Config.ARGB_8888, true) ?: scaledRaw
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            blurArtworkPixels(pixels, width, height, radius)
            scaled.setPixels(pixels, 0, width, 0, 0, width, height)
            scaled.asImageBitmap().also { legacyArtworkBlurCache.put(key, it) }
        }
    }

internal val artworkBackgroundShader =
    """
    uniform shader artwork;
    uniform shader previous;
    uniform float2 viewport;
    uniform float2 ratios;
    uniform float3 cosines;
    uniform float3 sines;
    uniform float crossfade;
    uniform float liquid;
    uniform float4 crop;
    uniform float4 treatment;
    uniform float2 mirror;

    float2 unrotate(float2 p, float c, float s) {
        return float2(c*p.x + s*p.y, -s*p.x + c*p.y);
    }
    half4 over(half4 a, half4 b) { return a + b*(1.0-a.a); }
    half4 sampleArt(float2 uv, float old) {
        float2 p = uv*${ARTWORK_TEXTURE_SIZE}.0 + ${ARTWORK_TEXTURE_PADDING}.0;
        return old > 0.5 ? previous.eval(p) : artwork.eval(p);
    }
    half4 layers(float2 p, float ratio, float old, float alpha) {
        float d = max(viewport.x, viewport.y)*1.3;
        float2 pivot = float2(d*0.5);
        float2 offset = (viewport-float2(d))*0.5;
        float2 dimensions = float2(d*ratio, d);
        float2 a = (unrotate(p-offset-pivot, cosines.x, sines.x)+pivot)/dimensions;
        float2 b = (unrotate(p-offset+viewport*float2(0.95,0.7)-pivot, cosines.y, sines.y)+pivot)/dimensions;
        float2 q = unrotate(p-viewport*0.5, cosines.z, sines.z)+viewport*0.5;
        float2 c = (unrotate(q-offset+viewport*float2(0.5,-0.7)-pivot, cosines.z, sines.z)+pivot)/dimensions;
        half4 first = sampleArt(a,old)*alpha;
        half4 second = sampleArt(b,old)*alpha;
        half4 third = sampleArt(c,old)*alpha;
        return third + (second + first*(1.0-second.a))*(1.0-third.a);
    }
    half4 main(float2 p) {
        if (liquid > 0.5) {
            p = (p-viewport*0.5)/1.3+viewport*0.5;
            half4 color = layers(p, ratios.x, 0.0, crossfade);
            if (crossfade < 1.0) color = over(color, layers(p, ratios.y, 1.0, 1.0-crossfade));
            color = over(half4(0.0,0.0,0.0,128.0/255.0), color);
            return over(half4(13.0/255.0),color);
        }
        half4 color;
        if (mirror.x > 0.0 && p.y > mirror.x) {
            float d = (p.y-mirror.x)/viewport.y;
            float spread = viewport.x*0.12*d;
            float2 m = float2(p.x, mirror.x-0.4*(p.y-mirror.x));
            half4 reflection = half4(0.0);
            for (int i = 0; i < 12; i++) {
                float r = sqrt((float(i)+0.5)/12.0)*spread;
                float a = float(i)*2.39996;
                reflection += sampleArt((m+r*float2(cos(a),sin(a))-crop.xy)/crop.zw,0.0)*(1.0/12.0);
            }
            half4 edge = half4(0.0);
            for (int i = 0; i < 5; i++) {
                edge += sampleArt((float2(viewport.x*(0.1+0.2*float(i)),mirror.x)-crop.xy)/crop.zw,0.0)*0.2;
            }
            edge = half4(edge.rgb*0.88, edge.a);
            color = reflection + (edge-reflection)*smoothstep(0.0, 0.3, d);
        } else {
            color = sampleArt((p-crop.xy)/crop.zw,0.0);
        }
        color = over(half4(0.0,0.0,0.0,treatment.x),color);
        float f = p.y/viewport.y;
        if (treatment.y > 0.5) {
            float white = 0.12*max(0.0,1.0-f/0.52);
            float black = 0.42*max(0.0,(f-0.52)/0.48);
            color = over(half4(white,white,white,white),color);
            color = over(half4(0.0,0.0,0.0,black),color);
        }
        if (mirror.x > 0.0) {
            color *= clamp((p.y-mirror.y)/max(1.0,mirror.x-mirror.y),0.0,1.0);
        }
        return color;
    }
    """.trimIndent()

private fun liquidRotationAngles(timeMs: Long): FloatArray =
    floatArrayOf(
        (-2.0 * PI * (timeMs % 130_000L) / 130_000.0).toFloat(),
        (2.0 * PI * (timeMs % 90_000L) / 90_000.0).toFloat(),
        (2.0 * PI * (timeMs % 70_000L) / 70_000.0).toFloat(),
    )

internal fun artworkBlurBucket(
    radiusPx: Float,
    displayedHeight: Float,
): Int = (ceil(radiusPx * ARTWORK_TEXTURE_SIZE / displayedHeight.coerceAtLeast(1f) / 2f).toInt() * 2).coerceIn(2, 16)

internal fun artworkLegacyBlurRadius(
    radiusPx: Float,
    displayedHeight: Float,
    textureHeight: Int,
): Int = (artworkBlurBucket(radiusPx, displayedHeight) * textureHeight.toFloat() / ARTWORK_TEXTURE_SIZE).toInt().coerceAtLeast(1)

internal data class ArtworkBackdropStyle(
    val blurRadiusPx: Float,
    val scrimAlpha: Float = 0f,
    val lyricsGradient: Boolean = false,
    val verticalBias: Float = 0f,
    val mirrorSeam: Float = 0f,
    val mirrorFadeStart: Float = 0f,
    val pendingScrimAlpha: Float = scrimAlpha,
    val cropOverride: FloatArray? = null,
)

internal fun artworkCrop(
    width: Float,
    height: Float,
    ratio: Float,
    style: ArtworkBackdropStyle,
): FloatArray {
    style.cropOverride?.let { return it }
    val imageHeight = if (style.mirrorSeam > 0f) height * style.mirrorSeam else max(height, width / ratio)
    val imageWidth = imageHeight * ratio
    return floatArrayOf(
        (width - imageWidth) / 2f,
        if (style.mirrorSeam > 0f) 0f else (height - imageHeight) * (1f + style.verticalBias) / 2f,
        imageWidth,
        imageHeight,
    )
}

private data class TextureKey(
    val image: Bitmap,
    val radius: Int,
    val liquid: Boolean,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ArtworkShaderRenderer {
    private val shader = runCatching { RuntimeShader(artworkBackgroundShader) }.getOrNull()
    var available by mutableStateOf(shader != null)
        private set
    private val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    private val textures = linkedMapOf<TextureKey, BitmapShader>()

    private fun texture(
        image: Bitmap,
        radius: Int,
        liquid: Boolean,
    ): BitmapShader {
        val key = TextureKey(image, radius, liquid)
        textures.remove(key)?.let {
            textures[key] = it
            return it
        }
        val pad = ARTWORK_TEXTURE_PADDING.toFloat()
        val size = ARTWORK_TEXTURE_SIZE.toFloat()
        if (image.width <= 64 && image.height <= 64) {
            val w = image.width.coerceAtLeast(1)
            val h = image.height.coerceAtLeast(1)
            val blurredImage = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(w * h)
            image.getPixels(pixels, 0, w, 0, 0, w, h)
            blurArtworkPixels(pixels, w, h, 6, 6)
            blurredImage.setPixels(pixels, 0, w, 0, 0, w, h)
            val fastShader =
                BitmapShader(blurredImage, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(
                        Matrix().apply {
                            setScale(size / w, size / h)
                            postTranslate(pad, pad)
                        },
                    )
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                }
            if (textures.size >= 4) textures.remove(textures.keys.first())
            textures[key] = fastShader
            return fastShader
        }
        val side = ARTWORK_TEXTURE_SIZE + ARTWORK_TEXTURE_PADDING * 2
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val layer = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
        if (liquid) layer.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
        if (liquid) {
            canvas.drawBitmap(image, null, RectF(pad, pad, pad + size, pad + size), layer)
        } else {
            layer.shader =
                BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(
                        Matrix().apply {
                            setScale(size / image.width.coerceAtLeast(1), size / image.height.coerceAtLeast(1))
                            postTranslate(pad, pad)
                        },
                    )
                }
            canvas.drawPaint(layer)
        }
        val pixels = IntArray(side * side)
        bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
        blurArtworkPixels(
            pixels,
            side,
            side,
            radius,
            (radius.toFloat() * image.height.coerceAtLeast(1) / image.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1),
        )
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        val result =
            BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            ).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
        if (textures.size >= 4) textures.remove(textures.keys.first())
        textures[key] = result
        return result
    }

    fun draw(
        scope: DrawScope,
        active: Bitmap,
        previous: Bitmap?,
        alpha: Float,
        time: Long,
        style: ArtworkBackdropStyle? = null,
    ): Boolean {
        val shader = shader ?: return false
        if (!available || scope.size.width <= 0f || scope.size.height <= 0f) return false
        return try {
            val w = scope.size.width
            val h = scope.size.height
            val ratio = active.width.toFloat() / active.height.coerceAtLeast(1)
            val crop = artworkCrop(w, h, ratio, style ?: ArtworkBackdropStyle(0f))
            val radius = if (style == null) 7 else artworkBlurBucket(style.blurRadiusPx, crop[3])
            val activeTexture = texture(active, radius, style == null)
            val previousTexture = previous?.let { texture(it, radius, style == null) } ?: activeTexture
            shader.setInputShader("artwork", activeTexture)
            shader.setInputShader("previous", previousTexture)
            shader.setFloatUniform("viewport", w, h)
            shader.setFloatUniform("ratios", ratio, previous?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: ratio)
            val angles = liquidRotationAngles(time)
            shader.setFloatUniform("cosines", cos(angles[0]), cos(angles[1]), cos(angles[2]))
            shader.setFloatUniform("sines", sin(angles[0]), sin(angles[1]), sin(angles[2]))
            shader.setFloatUniform("crossfade", if (previous == null) 1f else alpha)
            shader.setFloatUniform("liquid", if (style == null) 1f else 0f)
            shader.setFloatUniform("crop", crop)
            shader.setFloatUniform("treatment", style?.scrimAlpha ?: 0f, if (style?.lyricsGradient == true) 1f else 0f, 0f, 0f)
            shader.setFloatUniform("mirror", (style?.mirrorSeam ?: 0f) * h, (style?.mirrorFadeStart ?: 0f) * h)
            paint.shader = shader
            scope.drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, w, h, paint) }
            true
        } catch (_: Exception) {
            available = false
            false
        }
    }
}

@Composable
internal fun MetrolistBlurredArtworkBackground(
    artwork: ImageBitmap,
    modifier: Modifier = Modifier,
    blurRadiusPx: Float,
    scrimAlpha: Float = 0f,
    lyricsGradient: Boolean = false,
    verticalBias: Float = 0f,
    mirrorSeam: Float = 0f,
    mirrorFadeStart: Float = 0f,
    pendingScrimAlpha: Float = scrimAlpha,
    cropOverride: FloatArray? = null,
) {
    val style =
        ArtworkBackdropStyle(
            blurRadiusPx = blurRadiusPx,
            scrimAlpha = scrimAlpha,
            lyricsGradient = lyricsGradient,
            verticalBias = verticalBias,
            mirrorSeam = mirrorSeam,
            mirrorFadeStart = mirrorFadeStart,
            pendingScrimAlpha = pendingScrimAlpha,
            cropOverride = cropOverride,
        )
    val renderer = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ArtworkShaderRenderer() else null }
    val bitmap =
        remember(artwork) {
            artwork.asAndroidBitmap().let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.config == Bitmap.Config.HARDWARE) {
                    it.copy(Bitmap.Config.ARGB_8888, false) ?: it
                } else {
                    it
                }
            }
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer?.available == true) {
        Canvas(modifier.fillMaxSize()) { renderer.draw(this, bitmap, null, 1f, 0L, style) }
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && style.blurRadiusPx > 0f) {
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            val cropHeight =
                artworkCrop(
                    with(density) { maxWidth.toPx() },
                    with(density) { maxHeight.toPx() },
                    bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1),
                    style,
                )[3]
            val imageSide = max(bitmap.width, bitmap.height).coerceAtLeast(1)
            val scaledHeight = (ARTWORK_TEXTURE_SIZE.toFloat() * bitmap.height / imageSide).roundToInt().coerceAtLeast(1)
            val radius = artworkLegacyBlurRadius(style.blurRadiusPx, cropHeight, scaledHeight)
            var blurred by remember(artwork) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(artwork, radius) {
                blurred = legacyArtworkBlur(bitmap, radius)
            }
            LegacyArtworkBackground(
                blurred ?: artwork,
                Modifier.fillMaxSize(),
                if (blurred == null) style.copy(scrimAlpha = style.pendingScrimAlpha) else style,
            )
        }
    } else {
        LegacyArtworkBackground(artwork, modifier, style)
    }
}

@Composable
private fun LegacyArtworkBackground(
    artwork: ImageBitmap,
    modifier: Modifier,
    style: ArtworkBackdropStyle,
) {
    val density = LocalDensity.current
    val blurModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style.blurRadiusPx > 0f) {
            Modifier.blur(
                radius = with(density) { style.blurRadiusPx.toDp() },
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
        } else {
            Modifier
        }

    Canvas(
        modifier
            .fillMaxSize()
            .then(
                if (style.mirrorSeam > 0f) {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = style.scrimAlpha))
                if (style.lyricsGradient) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.12f),
                            0.52f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.42f),
                        ),
                    )
                }
                if (style.mirrorSeam > 0f) {
                    drawRect(
                        Brush.verticalGradient(style.mirrorFadeStart to Color.Transparent, style.mirrorSeam to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .then(blurModifier),
    ) {
        val crop = artworkCrop(size.width, size.height, artwork.width.toFloat() / artwork.height.coerceAtLeast(1), style)
        fun DrawScope.image() {
            drawImage(
                artwork,
                dstOffset = IntOffset(crop[0].toInt(), crop[1].toInt()),
                dstSize = IntSize(crop[2].toInt().coerceAtLeast(1), crop[3].toInt().coerceAtLeast(1)),
            )
        }
        if (style.mirrorSeam > 0f) {
            val seam = size.height * style.mirrorSeam
            clipRect(top = seam) { withTransform({ scale(1f, -1f, Offset(size.width / 2f, seam)) }) { image() } }
        } else {
            image()
        }
    }
}

internal fun supportsNativeBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private const val PROGRESSIVE_BLUR_HORIZONTAL_H_SKSL = """
uniform shader content;
uniform float blurRadius;
uniform float4 crop;
uniform float leftStartX;
uniform float leftEndX;
uniform float rightStartX;
uniform float rightEndX;

float getIntensity(float x) {
    float left = 0.0;
    if (leftEndX > leftStartX) {
        float f = clamp((leftEndX - x) / max(leftEndX - leftStartX, 1.0), 0.0, 1.0);
        left = f * f * (3.0 - 2.0 * f);
    }
    float right = 0.0;
    if (rightEndX > rightStartX) {
        float f = clamp((x - rightStartX) / max(rightEndX - rightStartX, 1.0), 0.0, 1.0);
        right = f * f * (3.0 - 2.0 * f);
    }
    return max(left, right);
}

float4 blurH(float2 coord, float radius) {
    if (radius <= 0.001) {
        return content.eval(coord);
    }
    float4 result = content.eval(coord);
    float stepDist = radius / 10.0;
    for (float k = 1.0; k <= 10.0; k += 1.0) {
        float weight = exp(-k * k * 0.03462604);
        float2 offset = float2(k * stepDist, 0.0);
        float2 coord1 = clamp(coord - offset, crop.xy, crop.zw);
        float2 coord2 = clamp(coord + offset, crop.xy, crop.zw);
        result += (content.eval(coord1) + content.eval(coord2)) * weight;
    }
    return result / 9.47198;
}

half4 main(float2 coord) {
    float radius = blurRadius * getIntensity(coord.x);
    return half4(blurH(coord, radius));
}
"""

private const val PROGRESSIVE_BLUR_HORIZONTAL_V_SKSL = """
uniform shader content;
uniform float blurRadius;
uniform float4 crop;
uniform float leftStartX;
uniform float leftEndX;
uniform float rightStartX;
uniform float rightEndX;

float getIntensity(float x) {
    float left = 0.0;
    if (leftEndX > leftStartX) {
        float f = clamp((leftEndX - x) / max(leftEndX - leftStartX, 1.0), 0.0, 1.0);
        left = f * f * (3.0 - 2.0 * f);
    }
    float right = 0.0;
    if (rightEndX > rightStartX) {
        float f = clamp((x - rightStartX) / max(rightEndX - rightStartX, 1.0), 0.0, 1.0);
        right = f * f * (3.0 - 2.0 * f);
    }
    return max(left, right);
}

float4 blurV(float2 coord, float radius) {
    if (radius <= 0.001) {
        return content.eval(coord);
    }
    float4 result = content.eval(coord);
    float stepDist = radius / 10.0;
    for (float k = 1.0; k <= 10.0; k += 1.0) {
        float weight = exp(-k * k * 0.03462604);
        float2 offset = float2(0.0, k * stepDist);
        float2 coord1 = clamp(coord - offset, crop.xy, crop.zw);
        float2 coord2 = clamp(coord + offset, crop.xy, crop.zw);
        result += (content.eval(coord1) + content.eval(coord2)) * weight;
    }
    return result / 9.47198;
}

half4 main(float2 coord) {
    float radius = blurRadius * getIntensity(coord.x);
    return half4(blurV(coord, radius));
}
"""

@Composable
internal fun Modifier.artworkHorizontalProgressiveBlur(
    leftStartFraction: Float,
    leftEndFraction: Float,
    rightStartFraction: Float,
    rightEndFraction: Float,
    maxRadius: Dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return artworkHorizontalProgressiveBlurTiramisu(
            leftStartFraction,
            leftEndFraction,
            rightStartFraction,
            rightEndFraction,
            maxRadius,
        )
    }
    return this
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.artworkHorizontalProgressiveBlurTiramisu(
    leftStartFraction: Float,
    leftEndFraction: Float,
    rightStartFraction: Float,
    rightEndFraction: Float,
    maxRadius: Dp,
): Modifier {
    val density = LocalDensity.current
    val shaderH = remember { runCatching { RuntimeShader(PROGRESSIVE_BLUR_HORIZONTAL_H_SKSL) }.getOrNull() }
    val shaderV = remember { runCatching { RuntimeShader(PROGRESSIVE_BLUR_HORIZONTAL_V_SKSL) }.getOrNull() }

    if (shaderH == null || shaderV == null) {
        return this
    }

    return this.graphicsLayer {
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            val maxRadiusPx = with(density) { maxRadius.toPx() }
            val leftStartX = w * leftStartFraction
            val leftEndX = w * leftEndFraction
            val rightStartX = w * rightStartFraction
            val rightEndX = w * rightEndFraction

            shaderH.setFloatUniform("blurRadius", maxRadiusPx)
            shaderH.setFloatUniform("crop", 0f, 0f, w, h)
            shaderH.setFloatUniform("leftStartX", leftStartX)
            shaderH.setFloatUniform("leftEndX", leftEndX)
            shaderH.setFloatUniform("rightStartX", rightStartX)
            shaderH.setFloatUniform("rightEndX", rightEndX)

            val effectH = RenderEffect.createRuntimeShaderEffect(shaderH, "content")

            shaderV.setFloatUniform("blurRadius", maxRadiusPx)
            shaderV.setFloatUniform("crop", 0f, 0f, w, h)
            shaderV.setFloatUniform("leftStartX", leftStartX)
            shaderV.setFloatUniform("leftEndX", leftEndX)
            shaderV.setFloatUniform("rightStartX", rightStartX)
            shaderV.setFloatUniform("rightEndX", rightEndX)

            val effectV = RenderEffect.createRuntimeShaderEffect(shaderV, "content")
            val chained = RenderEffect.createChainEffect(effectV, effectH)

            renderEffect = chained.asComposeRenderEffect()
        }
    }
}
