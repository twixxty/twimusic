/*
 * Copyright (C) 2026 twixxty
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package app.vitune.android.ui.screens.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared "hero" transition between the three places the artwork can live in the portrait player:
 *
 *  1. the cover slot (rounded square in the middle of the screen)
 *  2. the wide canvas (full-bleed block at the top of the screen)
 *  3. the lyrics header (small rounded square left of the title / artist)
 *
 * Nothing is re-composed or re-parented while animating. Every layer (static cover, canvas video)
 * stays exactly where it lives in the tree and is only moved / scaled / clipped in the graphics
 * layer towards an animated "window" rectangle, so the whole morph runs in the draw phase and
 * never triggers a layout or a recomposition per frame. The canvas video is never recreated, so it
 * keeps playing through the transition.
 *
 * The cover is always the opaque bottom layer of a crossfade, which avoids the see-through dip a
 * naive "fade A out, fade B in" produces.
 */

/** Emphasized decelerate: quick start, long soft landing. */
val HeroEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
const val HeroLyricsDurationMillis = 450
const val HeroCanvasDurationMillis = 550

/**
 * Once the window is this close to the lyrics header slot the real header cover takes over. Both
 * show the same image at the same place, so the swap is invisible, but it has to be a hard swap:
 * cross-fading two identical opaque images would briefly let the background shine through.
 */
private const val HandoffThreshold = 0.999f

@Stable
class CoverHeroState {
    private var root: LayoutCoordinates? = null
    private var cover: LayoutCoordinates? = null
    private var canvas: LayoutCoordinates? = null
    private var header: LayoutCoordinates? = null
    private var cachedHeader: Rect? = null
    private var lastCoverRect: Rect? = null
    private var lastCanvasRect: Rect? = null
    private var lastHeaderRect: Rect? = null

    /** Bumped when any tracked node is re-positioned so draw-phase readers re-run. */
    private var layoutTick by mutableIntStateOf(0)

    internal fun onRoot(coordinates: LayoutCoordinates) {
        val sizeChanged = root?.size != coordinates.size
        root = coordinates
        if (sizeChanged) layoutTick++
    }

    internal fun onCover(coordinates: LayoutCoordinates) {
        cover = coordinates
        val r = boundsOf(coordinates)
        if (r != lastCoverRect) {
            lastCoverRect = r
            layoutTick++
        }
    }

    internal fun onCanvas(coordinates: LayoutCoordinates) {
        canvas = coordinates
        val r = boundsOf(coordinates)
        if (r != lastCanvasRect) {
            lastCanvasRect = r
            layoutTick++
        }
    }

    internal fun onHeader(coordinates: LayoutCoordinates) {
        header = coordinates
        val r = boundsOf(coordinates)
        if (r != lastHeaderRect) {
            lastHeaderRect = r
            layoutTick++
        }
    }

    /** The header slot is going away for good (e.g. the player layout changed). */
    fun clearHeader() {
        header = null
        cachedHeader = null
        lastHeaderRect = null
        layoutTick++
    }

    private fun observeLayout() {
        @Suppress("UNUSED_VARIABLE")
        val ignored = layoutTick
    }

    private fun boundsOf(target: LayoutCoordinates?): Rect? {
        val rootCoordinates = root ?: return null
        if (target == null || !rootCoordinates.isAttached || !target.isAttached) return null

        val bounds = rootCoordinates.localBoundingBoxOf(target, clipBounds = false)
        return bounds.takeIf { it.width > 1f && it.height > 1f }
    }

    private fun coverRect(): Rect? {
        observeLayout()
        return boundsOf(cover)
    }

    private fun canvasRect(): Rect? {
        observeLayout()
        return boundsOf(canvas)
    }

    /**
     * The header slot lives inside an AnimatedVisibility that is still alive while the reverse
     * animation runs, but remember the last known position anyway so a late detach can't make
     * the artwork jump.
     */
    private fun headerRect(): Rect? {
        observeLayout()
        val live = boundsOf(header)
        if (live != null) cachedHeader = live
        return live ?: cachedHeader
    }

    /**
     * Animated lyrics progress (0 = cover, 1 = lyrics). Set by the player; must only be called from
     * the draw phase so reading the underlying animation doesn't recompose anything.
     */
    var lyricsProgress: () -> Float = { 0f }

    /** Side of the (square) lyrics header cover in px, or null when there is none. */
    fun headerSidePx(): Float? = headerRect()?.let { min(it.width, it.height) }

    /** `true` when there is a lyrics header cover to fly to. */
    fun hasHeader() = headerRect() != null

    private fun hasSourceSlot() = coverRect() != null || canvasRect() != null

    /** Alpha of the real cover in the lyrics header: hard swap right before the flight lands. */
    fun headerArtAlpha() = if (!hasSourceSlot() || lyricsProgress() >= HandoffThreshold) 1f else 0f

    /**
     * Alpha of a canvas video that lives *inside* the cover (square canvas). It fades to the
     * static art while the cover flies to the header. Without a header the cover itself fades.
     */
    fun squareCanvasAlpha() =
        if (hasHeader()) (1f - lyricsProgress()).coerceIn(0f, 1f) else 1f

    private fun frameOf(
        lyrics: Float,
        canvasProgress: Float,
        coverRadius: Float,
        headerRadius: Float
    ): Frame? {
        val canvasSlot = canvasRect()
        val coverBounds = coverRect() ?: canvasSlot ?: return null
        val canvasBounds = if (canvasProgress > 0f) canvasSlot else null
        val headerBounds = headerRect()
        val morph = headerBounds != null
        val canvasWeight = if (canvasBounds != null) canvasProgress else 0f

        var window = if (canvasBounds != null) lerp(coverBounds, canvasBounds, canvasWeight) else coverBounds
        var radius = lerp(coverRadius, 0f, canvasWeight)

        if (headerBounds != null) {
            window = lerp(window, headerBounds, lyrics)
            radius = lerp(radius, headerRadius, lyrics)
        }

        // How much of the window currently shows the canvas video.
        val canvasVisibility = if (morph) canvasWeight * (1f - lyrics) else canvasWeight

        return Frame(
            window = window,
            radius = radius,
            coverAlpha = when {
                morph && lyrics >= HandoffThreshold -> 0f
                morph -> 1f - canvasVisibility
                else -> (1f - canvasWeight) * (1f - lyrics)
            },
            canvasAlpha = when {
                morph -> if (canvasVisibility > 0.002f) 1f else 0f
                canvasWeight > 0f -> 1f - lyrics
                else -> 0f
            },
            atRest = lyrics <= 0.0005f && canvasWeight <= 0.0005f,
            cover = coverBounds,
            canvas = canvasBounds
        )
    }

    /** Graphics layer block for the static cover (the Thumbnail). */
    fun applyCoverLayer(
        scope: GraphicsLayerScope,
        lyricsProgress: Float,
        canvasProgress: Float,
        coverRadius: Float,
        headerRadius: Float
    ) {
        val frame = frameOf(lyricsProgress, canvasProgress, coverRadius, headerRadius)
        if (frame == null) {
            // Not measured yet (or not a portrait layout): behave like a plain fade.
            scope.alpha = (1f - lyricsProgress).coerceIn(0f, 1f)
            return
        }

        scope.applyWindow(natural = frame.cover, frame = frame, alpha = frame.coverAlpha)
    }

    /** Graphics layer block for the wide canvas video. */
    fun applyCanvasLayer(
        scope: GraphicsLayerScope,
        lyricsProgress: Float,
        canvasProgress: Float,
        coverRadius: Float,
        headerRadius: Float
    ) {
        val frame = frameOf(lyricsProgress, canvasProgress, coverRadius, headerRadius)
        val natural = frame?.canvas
        if (frame == null || natural == null) {
            scope.alpha = if (canvasProgress > 0f) (1f - lyricsProgress).coerceIn(0f, 1f) else 0f
            return
        }

        scope.applyWindow(natural = natural, frame = frame, alpha = frame.canvasAlpha)
    }

    private fun GraphicsLayerScope.applyWindow(natural: Rect, frame: Frame, alpha: Float) {
        val window = frame.window
        // "Cover fill": the layer always covers the whole window, extra content is cropped. This is
        // what keeps a square canvas / cover undistorted while the window changes aspect ratio.
        val scale = max(window.width / natural.width, window.height / natural.height)
        val dx = window.center.x - natural.center.x
        val dy = window.center.y - natural.center.y

        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationX = dx
        translationY = dy

        val isIdentity = abs(scale - 1f) < 0.001f && abs(dx) < 0.5f && abs(dy) < 0.5f
        val needsClip = !(frame.atRest || (isIdentity && frame.radius < 0.5f))

        clip = needsClip
        shape = if (needsClip) {
            // The clip lives in the layer's own (un-scaled) space, so bring the window back into it.
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val halfWidth = window.width / (2f * scale)
            val halfHeight = window.height / (2f * scale)

            WindowClipShape(
                rect = Rect(
                    left = centerX - halfWidth,
                    top = centerY - halfHeight,
                    right = centerX + halfWidth,
                    bottom = centerY + halfHeight
                ),
                radius = frame.radius / scale
            )
        } else RectangleShape
    }

    private class Frame(
        val window: Rect,
        val radius: Float,
        val coverAlpha: Float,
        val canvasAlpha: Float,
        val atRest: Boolean,
        val cover: Rect,
        val canvas: Rect?
    )
}

private data class WindowClipShape(
    val rect: Rect,
    val radius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ) = Outline.Rounded(
        RoundRect(
            rect = rect,
            cornerRadius = CornerRadius(radius.coerceIn(0f, min(rect.width, rect.height) / 2f))
        )
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

private fun lerp(start: Rect, stop: Rect, fraction: Float) = Rect(
    left = lerp(start.left, stop.left, fraction),
    top = lerp(start.top, stop.top, fraction),
    right = lerp(start.right, stop.right, fraction),
    bottom = lerp(start.bottom, stop.bottom, fraction)
)

/** Marks the node every tracked position is measured against. */
fun Modifier.heroRoot(hero: CoverHeroState) = onGloballyPositioned { hero.onRoot(it) }

/** Marks the (un-transformed) slot the static cover normally lives in. */
fun Modifier.heroCoverSlot(hero: CoverHeroState) = onGloballyPositioned { hero.onCover(it) }

/** Marks the (un-transformed) block the wide canvas normally fills. */
fun Modifier.heroCanvasSlot(hero: CoverHeroState) = onGloballyPositioned { hero.onCanvas(it) }

/** Marks the cover slot to the left of the title / artist while lyrics are open. */
fun Modifier.heroHeaderSlot(hero: CoverHeroState) = onGloballyPositioned { hero.onHeader(it) }
