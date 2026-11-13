package com.mocharealm.accompanist.lyrics.ui.internal.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import app.vitune.android.utils.BitmapBlur
import kotlin.math.ceil

/*
 * Blur for lyric lines on Android 11 and below.
 *
 * RenderEffect / Modifier.blur only exist on Android 12+ and are silent no-ops before that. Every
 * glyph of a lyric line is already rasterized into a software-backed atlas page, so on those
 * versions the line is blurred where the pixels live: the tile is drawn into a small software
 * bitmap, blurred on the CPU (the same BitmapBlur that blurs the artwork) and cached on the tile.
 * Drawing it afterwards is one textured quad, and the line's own scale/alpha animation keeps running
 * on the GPU. No GPU snapshot is involved, which is what made an earlier attempt come back blank.
 */

/** Blur radius of the line being drawn, in pixels (Gaussian sigma). 0 means sharp. */
internal val LocalLyricsLineBlur = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Blur radii are snapped to this many pixels so a tile is blurred only a handful of times. */
internal const val LEGACY_BLUR_STEP_PX = 2f

/** Working sigma after shrinking; the tile is shrunk until its blur radius is about this. */
private const val WORKING_SIGMA_PX = 2.5f
private const val MAX_DOWNSCALE = 6

/**
 * Returns the [width]x[height] region of the software [atlas] at ([left], [top]) shrunk and blurred
 * by [sigma] source pixels, or null if it cannot be produced. Callers draw it stretched back over
 * the region.
 */
internal fun blurAtlasRegion(
    atlas: Bitmap,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    sigma: Float,
): Bitmap? {
    if (atlas.isRecycled || width < 2 || height < 2 || sigma < 0.5f) return null
    val downscale = (sigma / WORKING_SIGMA_PX).toInt().coerceIn(1, MAX_DOWNSCALE)
    val bw = ceil(width / downscale.toFloat()).toInt().coerceAtLeast(2)
    val bh = ceil(height / downscale.toFloat()).toInt().coerceAtLeast(2)
    return try {
        val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Canvas(small)
            .drawBitmap(
                atlas,
                Rect(left, top, left + width, top + height),
                Rect(0, 0, bw, bh),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        val pixels = IntArray(bw * bh)
        small.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        BitmapBlur.blurArgb(pixels, bw, bh, sigma * bw / width)
        small.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        small
    } catch (_: Throwable) {
        null
    }
}
