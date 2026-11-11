package app.vitune.android.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-CPU blur helpers that behave identically on every Android version.
 *
 * `Modifier.blur`, `RenderEffect` and `View.setRenderEffect` only exist on Android 12 (API 31)+, so
 * on Android 11 and below they are silent no-ops. Everything that needs a blur on those versions -
 * and everything that is cheaper to blur *once* than on every frame - goes through here.
 *
 * The blur is a true Gaussian approximation (three successive box blurs, the same technique browsers
 * and image editors use) with edge-clamping, so it is smooth, has no stack-blur banding and is
 * O(pixels) regardless of the radius. It is meant to be run on small (<= ~256px) bitmaps and then
 * scaled up with bilinear filtering, which is visually indistinguishable from blurring the full-size
 * image but orders of magnitude cheaper.
 */
object BitmapBlur {
    /**
     * Blurs [pixels] (straight ARGB_8888, as returned by [Bitmap.getPixels]) in place.
     * [sigma] is the Gaussian standard deviation in pixels - the same unit as
     * `RenderEffect.createBlurEffect(radius, ...)` and `Modifier.blur(radius)`.
     *
     * Alpha is handled correctly (colours are blurred premultiplied, so transparent areas never bleed
     * dark halos), and fully opaque / single-colour inputs take faster paths automatically.
     */
    fun blurArgb(pixels: IntArray, width: Int, height: Int, sigma: Float) {
        if (sigma < MIN_SIGMA || width < 2 || height < 2) return
        val count = width * height
        require(pixels.size >= count) { "pixels too small for ${width}x$height" }

        var opaque = true
        var mono = true
        var monoRgb = -1
        for (i in 0 until count) {
            val p = pixels[i]
            val alpha = p ushr 24
            if (alpha != 255) opaque = false
            if (alpha != 0) {
                val rgb = p and 0xFFFFFF
                if (monoRgb == -1) monoRgb = rgb
                else if (mono && !isNear(rgb, monoRgb)) mono = false
            }
        }
        if (monoRgb == -1) return // fully transparent, nothing to blur

        val radii = boxRadii(sigma)
        val scratch = FloatArray(count)

        if (mono && !opaque) {
            // e.g. white text on transparent: only alpha varies, so blur just that one plane.
            val a = FloatArray(count) { (pixels[it] ushr 24).toFloat() }
            blurPlane(a, scratch, width, height, radii)
            for (i in 0 until count) {
                pixels[i] = (a[i].roundToInt().coerceIn(0, 255) shl 24) or monoRgb
            }
            return
        }

        val r = FloatArray(count)
        val g = FloatArray(count)
        val b = FloatArray(count)
        if (opaque) {
            for (i in 0 until count) {
                val p = pixels[i]
                r[i] = ((p shr 16) and 0xFF).toFloat()
                g[i] = ((p shr 8) and 0xFF).toFloat()
                b[i] = (p and 0xFF).toFloat()
            }
            blurPlane(r, scratch, width, height, radii)
            blurPlane(g, scratch, width, height, radii)
            blurPlane(b, scratch, width, height, radii)
            for (i in 0 until count) {
                pixels[i] = (0xFF shl 24) or
                    (r[i].roundToInt().coerceIn(0, 255) shl 16) or
                    (g[i].roundToInt().coerceIn(0, 255) shl 8) or
                    b[i].roundToInt().coerceIn(0, 255)
            }
            return
        }

        val a = FloatArray(count)
        for (i in 0 until count) {
            val p = pixels[i]
            val alpha = (p ushr 24).toFloat()
            val factor = alpha / 255f
            a[i] = alpha
            r[i] = ((p shr 16) and 0xFF) * factor
            g[i] = ((p shr 8) and 0xFF) * factor
            b[i] = (p and 0xFF) * factor
        }
        blurPlane(a, scratch, width, height, radii)
        blurPlane(r, scratch, width, height, radii)
        blurPlane(g, scratch, width, height, radii)
        blurPlane(b, scratch, width, height, radii)
        for (i in 0 until count) {
            val alpha = a[i]
            if (alpha < 0.5f) {
                pixels[i] = 0
                continue
            }
            val unpremultiply = 255f / alpha
            pixels[i] = (alpha.roundToInt().coerceIn(0, 255) shl 24) or
                ((r[i] * unpremultiply).roundToInt().coerceIn(0, 255) shl 16) or
                ((g[i] * unpremultiply).roundToInt().coerceIn(0, 255) shl 8) or
                (b[i] * unpremultiply).roundToInt().coerceIn(0, 255)
        }
    }

    /** Returns a blurred copy of [source]. The source is never modified. */
    fun blurred(source: Bitmap, sigma: Float): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        blurArgb(pixels, width, height, sigma)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Downscales [source] so its longest side is at most [maxDimension], never upscaling.
     * Halves repeatedly (each halving is an exact 2x2 average) before the final bilinear step, so
     * large artwork is properly averaged instead of aliased. The returned bitmap may be [source]
     * itself when no scaling was needed - callers must not recycle or modify it.
     */
    fun downscale(source: Bitmap, maxDimension: Int): Bitmap {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            source.config == Bitmap.Config.HARDWARE
        ) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else source

        val longest = max(base.width, base.height)
        if (longest <= maxDimension) return base

        val ratio = maxDimension.toFloat() / longest
        val targetWidth = max(1, (base.width * ratio).roundToInt())
        val targetHeight = max(1, (base.height * ratio).roundToInt())

        var current = base
        while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        if (current.width != targetWidth || current.height != targetHeight) {
            val next = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
            if (current !== source) current.recycle()
            current = next
        }
        return current
    }

    /** Returns a copy of [source] with its colour saturation multiplied by [saturation]. */
    fun saturated(source: Bitmap, saturation: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    // region internals

    private const val MIN_SIGMA = 0.4f
    private const val MONO_TOLERANCE = 4

    private fun isNear(a: Int, b: Int) =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= MONO_TOLERANCE &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= MONO_TOLERANCE &&
            abs((a and 0xFF) - (b and 0xFF)) <= MONO_TOLERANCE

    /**
     * Radii of three box blurs whose combination approximates a Gaussian of the given sigma
     * (W. Kutskir, "Fastest Gaussian Blur").
     */
    private fun boxRadii(sigma: Float): IntArray {
        val passes = 3
        val ideal = sqrt(12f * sigma * sigma / passes + 1f)
        var lower = floor(ideal).toInt()
        if (lower % 2 == 0) lower--
        lower = max(lower, 1)
        val upper = lower + 2
        val median = ((12f * sigma * sigma - passes * lower * lower - 4f * passes * lower - 3f * passes) /
            (-4f * lower - 4f)).roundToInt()
        return IntArray(passes) { i -> ((if (i < median) lower else upper) - 1) / 2 }
    }

    private fun blurPlane(plane: FloatArray, scratch: FloatArray, width: Int, height: Int, radii: IntArray) {
        for (radius in radii) {
            if (radius <= 0) continue
            boxBlurHorizontal(plane, scratch, width, height, radius)
            boxBlurVertical(scratch, plane, width, height, radius)
        }
    }

    private fun boxBlurHorizontal(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        val inverse = 1f / (2 * radius + 1)
        val last = width - 1
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (k in -radius..radius) sum += src[row + k.coerceIn(0, last)]
            dst[row] = sum * inverse
            for (x in 1 until width) {
                sum += src[row + min(x + radius, last)] - src[row + max(x - radius - 1, 0)]
                dst[row + x] = sum * inverse
            }
        }
    }

    private fun boxBlurVertical(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        val inverse = 1f / (2 * radius + 1)
        val last = height - 1
        // Accumulates one running sum per column so memory is read row by row (cache friendly).
        val sums = FloatArray(width)
        for (k in -radius..radius) {
            val row = k.coerceIn(0, last) * width
            for (x in 0 until width) sums[x] += src[row + x]
        }
        for (x in 0 until width) dst[x] = sums[x] * inverse
        for (y in 1 until height) {
            val add = min(y + radius, last) * width
            val remove = max(y - radius - 1, 0) * width
            val out = y * width
            for (x in 0 until width) {
                sums[x] += src[add + x] - src[remove + x]
                dst[out + x] = sums[x] * inverse
            }
        }
    }

    // endregion
}
