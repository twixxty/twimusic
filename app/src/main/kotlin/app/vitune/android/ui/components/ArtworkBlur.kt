package app.vitune.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.vitune.android.utils.BitmapBlur
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the lava lamp needs, rendered once per artwork.
 *
 * All four layers are already blurred, so at runtime the lava lamp only moves textured quads around
 * with GPU transforms - no `Modifier.blur`/`RenderEffect`, which also means it works on Android 11
 * and below where those do nothing.
 */
@Immutable
class LavaLampAssets(
    /** Blurred artwork, drawn as the base of the background. */
    val base: ImageBitmap,
    /** Blurred, heavily saturated artwork, drawn translucently over [base]. */
    val wash: ImageBitmap,
    /** Blurred top-left quarter of the saturated artwork (square, with a transparent soft border). */
    val topLeft: ImageBitmap,
    /** Blurred bottom-right quarter of the saturated artwork (square, with a transparent soft border). */
    val bottomRight: ImageBitmap,
    /**
     * Quarter bitmaps carry transparent padding so their blurred edges fade out softly instead of
     * being cut off. This is `paddedSize / contentSize`, used to keep the visible content the same
     * size no matter how much padding was added.
     */
    val quarterScale: Float
)

/**
 * Renders the blurred images used by the player backgrounds. Blurring happens exactly once per
 * artwork, on a tiny (<= 128px) copy, off the main thread - the result is then upscaled with
 * bilinear filtering, which is indistinguishable from blurring at full size for such soft images.
 *
 * Blur strengths are expressed as a fraction of the working-copy size so the look is independent of
 * the artwork's resolution.
 */
internal object ArtworkBlur {
    private const val WORKING_SIZE = 128
    private const val BACKGROUND_SIGMA = 0.0625f
    private const val LAVA_WASH_SIGMA = 0.07f
    private const val LAVA_QUARTER_SIGMA = 0.086f
    private const val LAVA_SATURATION = 2.5f
    private const val PADDING_SIGMAS = 3f

    val backgroundCache = PrerenderCache<ImageBitmap>(maxEntries = 4)
    val lavaCache = PrerenderCache<LavaLampAssets>(maxEntries = 3)

    fun renderBackground(source: Bitmap): ImageBitmap? = guarded {
        val small = BitmapBlur.downscale(source, WORKING_SIZE)
        try {
            val sigma = max(small.width, small.height) * BACKGROUND_SIGMA
            BitmapBlur.blurred(small, sigma).toImage()
        } finally {
            if (small !== source) small.recycle()
        }
    }

    fun renderLava(source: Bitmap): LavaLampAssets? = guarded {
        val small = BitmapBlur.downscale(source, WORKING_SIZE)
        val saturated = BitmapBlur.saturated(small, LAVA_SATURATION)
        try {
            val washSigma = max(small.width, small.height) * LAVA_WASH_SIGMA
            val base = BitmapBlur.blurred(small, washSigma).toImage()
            val wash = BitmapBlur.blurred(saturated, washSigma).toImage()

            // Same quarters as before: top-left and bottom-right of the artwork, cropped to the
            // largest centred square so they can be rotated without distortion.
            val quarterWidth = max(1, saturated.width / 2)
            val quarterHeight = max(1, saturated.height / 2)
            val side = min(quarterWidth, quarterHeight)
            val sigma = side * LAVA_QUARTER_SIGMA
            val padding = ceil(sigma * PADDING_SIGMAS).toInt()

            fun quarter(left: Int, top: Int): ImageBitmap {
                val padded = Bitmap.createBitmap(side + 2 * padding, side + 2 * padding, Bitmap.Config.ARGB_8888)
                Canvas(padded).drawBitmap(
                    saturated,
                    Rect(left, top, left + side, top + side),
                    Rect(padding, padding, padding + side, padding + side),
                    null
                )
                return BitmapBlur.blurred(padded, sigma).also { padded.recycle() }.toImage()
            }

            LavaLampAssets(
                base = base,
                wash = wash,
                topLeft = quarter((quarterWidth - side) / 2, (quarterHeight - side) / 2),
                bottomRight = quarter(
                    quarterWidth + (quarterWidth - side) / 2,
                    quarterHeight + (quarterHeight - side) / 2
                ),
                quarterScale = (side + 2 * padding).toFloat() / side
            )
        } finally {
            saturated.recycle()
            if (small !== source) small.recycle()
        }
    }

    // Uploading the texture here (off the main thread) avoids a hitch on the first frame it is drawn.
    private fun Bitmap.toImage(): ImageBitmap {
        prepareToDraw()
        return asImageBitmap()
    }

    // The source can be recycled by the bitmap provider while we are reading it; that must never
    // take the app down, the background just keeps showing whatever it had.
    private inline fun <T : Any> guarded(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** Tiny thread-safe LRU of finished renders so toggling lyrics / the sheet never re-blurs. */
internal class PrerenderCache<T : Any>(maxEntries: Int) {
    private val cache = LruCache<String, T>(maxEntries)

    operator fun get(key: String): T? = cache.get(key)
    operator fun set(key: String, value: T) {
        cache.put(key, value)
    }
}

private fun Bitmap.isUsable() = !isRecycled && width > 0 && height > 0

// Identity is enough to tell artwork apart; the generation id additionally changes if a bitmap is
// ever mutated in place.
private fun Bitmap.cacheKey() = "${System.identityHashCode(this)}:$generationId:${width}x$height"

/**
 * Renders [bitmap] through [render] on a background thread and returns the result, or `null` when
 * there is no (usable) artwork. While a new render is in flight the previous result stays available,
 * so callers can cross-fade from the old artwork to the new one with no flash of empty background.
 * Results are cached, so returning to an artwork already rendered is instant (no placeholder frame).
 */
@Composable
internal fun <T : Any> rememberPrerendered(
    bitmap: Bitmap?,
    cache: PrerenderCache<T>,
    render: (Bitmap) -> T?
): T? {
    val key = remember(bitmap) { bitmap?.takeIf { it.isUsable() }?.cacheKey() }
    val state = produceState<T?>(initialValue = key?.let { cache[it] }, key1 = key) {
        val current = key
        if (current == null || bitmap == null) {
            value = null
            return@produceState
        }
        val cached = cache[current]
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            render(bitmap)?.also { cache[current] = it }
        }
    }
    return state.value
}
