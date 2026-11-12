package app.vitune.android.accord.lyrics

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import app.vitune.android.R
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.utils.BitmapBlur
import kotlin.math.abs

class LyricTextView(
    context: Context,
) : View(context) {

    var isBackgroundLine = false
        private set
    var isMagicLyric = false
        private set
    var agent: String? = null
        private set
    var accentColor: Int = Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val magicDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    constructor(context: Context, lyric: LyricBase) : this(context) {
        when (lyric) {
            is Lyric -> {
                this.lyric = lyric.content
                this.isMagicLyric = lyric.isMagicLyric
                this.isBackgroundLine = lyric.isBackground
                this.agent = lyric.agent
                if (lyric.isBackground) {
                    setBackgroundContent()
                    lineTextAlpha = 0f
                }
            }
            is SyncedLyric -> {
                this.syncedLyric = lyric
                this.isBackgroundLine = lyric.isBackground
                this.agent = lyric.agent
                if (lyric.isBackground) {
                    setBackgroundContent()
                    lineTextAlpha = 0f
                }
                liftupWindows = LyricTiming.liftupWindows(
                    relativeTime = lyric.relativeTime,
                    unitCount = lyric.list.size
                )
                liftupProgress = FloatArray(lyric.list.size)
                liftupStartupTimes = LongArray(lyric.list.size) { index ->
                    liftupWindows[index].startMs
                }
            }
            is Creator -> {
                this.isHolding = true
                this.lyric = "Written by: ${lyric.content}"
                setCreatorContent()
            }
        }
    }

    private val horizontalMargin = 12.dp.px.toInt()
    private val verticalMargin = 8.dp.px.toInt()

    private val lyricPaint = TextPaint().apply {
        textSize = NORMAL_TEXT_SIZE.sp.px
        color = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.OVERLAY
        }
        typeface = ResourcesCompat.getFont(context, R.font.inter_bold)
    }

    var lyric: String = ""
        set(value) {
            field = value
            releaseLegacyBlur()
            requestLayout()
            invalidate()
        }
        get() {
            syncedLyric?.let {
                return it.list.joinToString(separator = "") { lyric -> lyric.content }
            }
            return field
        }

    var syncedLyric: SyncedLyric? = null

    var staticLayout: StaticLayout? = null
    var isHolding: Boolean = false
    private var isLineActive: Boolean = false
    private var lineTextAlpha = INACTIVE_LINE_ALPHA
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private var lineTextScale = INACTIVE_LINE_SCALE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private var lineBlurRadius = 0f
        set(value) {
            if (field == value) return
            field = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (value <= 0.5f) {
                    setRenderEffect(null)
                } else if (PlayerPreferences.lyricsOptimizedAndroid12 && FocusRenderEffects != null) {
                    val idx = kotlin.math.round(value).toInt().coerceIn(0, 64)
                    setRenderEffect(FocusRenderEffects[idx])
                } else {
                    setRenderEffect(RenderEffect.createBlurEffect(value, value, Shader.TileMode.CLAMP))
                }
            } else if (value <= 0.5f) {
                releaseLegacyBlur()
            }
            invalidate()
        }
    private var lineTextAlphaAnimator: ValueAnimator? = null
    private var lineTextScaleAnimator: ValueAnimator? = null
    private var lineBlurRadiusAnimator: ValueAnimator? = null
    private val lineStyleInterpolator = DecelerateInterpolator()

    // region Blur on Android 11 and below
    //
    // View.setRenderEffect (and therefore GPU blur) only exists on Android 12+. On older versions a
    // blurred line is instead rendered ONCE into a small bitmap, blurred on the CPU (BitmapBlur) and
    // then simply drawn - with the line's alpha and scale still animating - until its blur radius
    // changes. The bitmap is rendered at a fraction of the view's size (the blur hides the lost
    // detail), so it is tiny, cheap to create and cheap to hold on to.
    private var legacyBlur: Bitmap? = null
    private var legacyBlurWidth = 0
    private var legacyBlurHeight = 0
    private var legacyBlurRadiusPx = 0f
    private var isCapturingBlur = false
    private val legacyBlurPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val legacyBlurSrc = Rect()
    private val legacyBlurDst = RectF()

    private val usesLegacyBlur: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && lineBlurRadius > 0.5f

    private fun releaseLegacyBlur() {
        // Deliberately not recycled: the bitmap may still be referenced by a recorded display list
        // that has not been rendered yet, and recycling it would crash the render thread.
        legacyBlur = null
    }

    private fun obtainLegacyBlur(): Bitmap? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val radius = lineBlurRadius

        legacyBlur?.let {
            if (legacyBlurWidth == w && legacyBlurHeight == h && abs(legacyBlurRadiusPx - radius) < 0.5f) {
                return it
            }
        }

        val downscale = (radius / LEGACY_BLUR_TARGET_SIGMA).toInt().coerceIn(1, LEGACY_BLUR_MAX_DOWNSCALE)
        val bw = (w + downscale - 1) / downscale
        val bh = (h + downscale - 1) / downscale
        val bitmap = try {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return null
        }

        val canvas = Canvas(bitmap)
        canvas.scale(bw.toFloat() / w, bh.toFloat() / h)
        isCapturingBlur = true
        try {
            renderLine(canvas, 1f)
        } finally {
            isCapturingBlur = false
        }

        val pixels = IntArray(bw * bh)
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        BitmapBlur.blurArgb(pixels, bw, bh, radius * bw / w)
        bitmap.setPixels(pixels, 0, bw, 0, 0, bw, bh)

        legacyBlur = bitmap
        legacyBlurWidth = w
        legacyBlurHeight = h
        legacyBlurRadiusPx = radius
        return bitmap
    }

    private fun drawLegacyBlurred(canvas: Canvas) {
        val fade = lineTextAlpha * this.alpha
        if (fade <= 0.001f) return
        val bitmap = obtainLegacyBlur() ?: return

        val count = canvas.save()
        canvas.scale(
            lineTextScale,
            lineTextScale,
            if (isMagicLyric) width / 2f else horizontalMargin.toFloat(),
            height / 2f
        )
        legacyBlurPaint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
        legacyBlurSrc.set(0, 0, bitmap.width, bitmap.height)
        legacyBlurDst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, legacyBlurSrc, legacyBlurDst, legacyBlurPaint)
        canvas.restoreToCount(count)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseLegacyBlur()
    }

    override fun onDetachedFromWindow() {
        releaseLegacyBlur()
        super.onDetachedFromWindow()
    }
    // endregion

    override fun onDraw(canvas: Canvas) {
        if (usesLegacyBlur) {
            drawLegacyBlurred(canvas)
        } else {
            renderLine(canvas, lineTextScale)
        }
    }

    /**
     * Draws the line at [scale]. Also used to capture the line into the legacy blur bitmap, in which
     * case ([isCapturingBlur]) it is drawn at rest and at full opacity: the line's own alpha is
     * applied when the captured bitmap is drawn, so it can keep animating without re-capturing.
     */
    private fun renderLine(canvas: Canvas, scale: Float) {
        if (isMagicLyric) {
            drawMagicLyric(canvas, scale)
            return
        }
        if (!isCapturingBlur && isBackgroundLine && lineTextAlpha <= 0.001f) {
            return
        }
        val count = canvas.save()
        canvas.scale(
            scale,
            scale,
            horizontalMargin.toFloat(),
            height / 2f
        )
        canvas.withTranslation(
            horizontalMargin.toFloat(),
            verticalMargin.toFloat()
        ) {
            if (syncedLyric != null) {
                if (!isCapturingBlur && animationUnit >= 0 && endOffsetChar >= 0) {
                    drawContentLayerWithProgress(canvas)
                    drawHighlightLayer(canvas)
                } else {
                    drawInactiveSyncedContentLayer(canvas)
                }
            } else {
                drawContentLayer(canvas)
            }
        }
        canvas.restoreToCount(count)
    }

    private fun drawMagicLyric(canvas: Canvas, scale: Float = lineTextScale) {
        val count = canvas.save()
        canvas.scale(
            scale,
            scale,
            width / 2f,
            height / 2f
        )

        val dotRadius = 4.5.dp.px
        val dotSpacing = 20.dp.px
        val totalWidth = 2 * dotSpacing
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val now = System.currentTimeMillis()
        val fade = if (isCapturingBlur) 1f else this.alpha * lineTextAlpha

        for (i in 0..2) {
            val phase = (now % 1400L) / 1400.0
            val dotOffset = i * 0.22
            val dotPhase = ((phase - dotOffset + 1.0) % 1.0)
            val pulse = if (dotPhase < 0.40) {
                kotlin.math.sin(dotPhase / 0.40 * Math.PI).toFloat()
            } else {
                0f
            }

            val dotScale = if (isLineActive) 1f + 0.35f * pulse else 0.85f
            val dotAlpha = if (isLineActive) {
                (0.35f + 0.65f * pulse) * fade
            } else {
                0.20f * fade
            }
            val finalAlpha = (dotAlpha * 255).toInt().coerceIn(0, 255)

            magicDotPaint.color = Color.WHITE
            magicDotPaint.alpha = finalAlpha

            if (isLineActive && pulse > 0.05f) {
                val glowCol = if (accentColor != Color.WHITE && accentColor != 0) accentColor else Color.WHITE
                magicDotPaint.setShadowLayer(
                    10.dp.px * pulse,
                    0f,
                    0f,
                    glowCol
                )
            } else {
                magicDotPaint.clearShadowLayer()
            }

            val dotX = startX + i * dotSpacing
            canvas.drawCircle(dotX, centerY, dotRadius * dotScale, magicDotPaint)
        }

        canvas.restoreToCount(count)

        if (isLineActive && !isCapturingBlur) {
            postInvalidateOnAnimation()
        }
    }

    private var animationUnit = -1
    private var animationFraction = 0F

    private var liftupWindows: List<LyricTiming.LiftupWindow> = emptyList()
    private var liftupProgress: FloatArray? = null
    private var liftupStartupTimes: LongArray? = null
    private var liftupReturnAnimator: ValueAnimator? = null
    private val liftupReturnInterpolator = PathInterpolator(0.6F, 0F, 0.2F, 1F)
    private var animationPositionMs = 0L
    private val progressSoFar: Long
        get() = animationPositionMs
    private var returnFraction = 0f

    fun animate(
        itemPos: Int,
        fraction: Float,
        linePositionMs: Long = calculateLinePositionMs(itemPos, fraction)
    ) {
        cancelLiftupReturnAnimator()
        returnFraction = 0f
        animationFraction = fraction
        animationPositionMs = linePositionMs.coerceAtLeast(0L)

        if (itemPos != animationUnit) {
            animationUnit = itemPos
            calculateTargetPosition(animationUnit)
        }

        calculateLiftupProgress()
        invalidate()
    }

    fun resetPlayback() {
        resetPlayback(cancelReturnAnimator = true)
    }

    private fun resetPlayback(cancelReturnAnimator: Boolean) {
        if (cancelReturnAnimator) {
            cancelLiftupReturnAnimator()
        }
        returnFraction = 0f
        animationUnit = -1
        animationFraction = 0f
        animationPositionMs = 0L
        startLine = -1
        endLine = -1
        startOffsetInLinePx = -1f
        endOffsetInLinePx = -1f
        startOffsetChar = -1
        endOffsetChar = -1
        animationLinePx = null
        liftupProgress?.fill(0f)
        invalidate()
    }

    fun markCompleted() {
        val synced = syncedLyric ?: return
        if (synced.list.isEmpty()) return
        animationUnit = synced.list.lastIndex
        animationFraction = 1f
        calculateTargetPosition(animationUnit)
        if (liftupProgress == null) {
            liftupProgress = FloatArray(synced.list.size) { 1f }
        }
    }

    fun clearLiftupProgress() {
        cancelLiftupReturnAnimator()
        returnFraction = 0f
        liftupProgress?.fill(0f)
        invalidate()
    }

    fun animateLiftupProgressToRest(durationMs: Long, delayMs: Long) {
        val progress = liftupProgress
        if (progress == null || animationUnit < 0 || progress.all { it == 0f }) {
            resetPlayback()
            return
        }

        val startProgress = progress.copyOf()
        var wasCancelled = false
        returnFraction = 0f

        cancelLiftupReturnAnimator()
        liftupReturnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            startDelay = delayMs
            interpolator = liftupReturnInterpolator
            addUpdateListener {
                val fraction = it.animatedValue as Float
                returnFraction = fraction
                progress.indices.forEach { index ->
                    progress[index] = startProgress[index] * (1f - fraction)
                }
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    if (!wasCancelled) {
                        liftupReturnAnimator = null
                        returnFraction = 1f
                        resetPlayback(cancelReturnAnimator = false)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                }

                override fun onAnimationRepeat(animation: Animator) = Unit
            })
            start()
        }
    }

    fun setLineActive(active: Boolean) {
        if (isLineActive == active) return
        isLineActive = active
        invalidate()
    }

    fun updateDelayedLineStyle(
        index: Int,
        targetIndex: Int,
        animated: Boolean,
        delayMs: Long,
        enableBlur: Boolean = PlayerPreferences.lyricsLineBlur,
        isAutoScroll: Boolean = true,
        forceActive: Boolean = false
    ) {
        val isActivated = index == targetIndex || forceActive
        val targetAlpha = if (isBackgroundLine && !isActivated) {
            0f
        } else if (!isAutoScroll) {
            if (isActivated) ACTIVE_LINE_ALPHA else 0.75f
        } else {
            if (isActivated) ACTIVE_LINE_ALPHA else INACTIVE_LINE_ALPHA
        }
        val targetScale = if (isActivated) ACTIVE_LINE_SCALE else INACTIVE_LINE_SCALE
        val targetBlurRadius = if (enableBlur && isAutoScroll && !isActivated) {
            (abs(index - targetIndex) * BLUR_RADIUS_STEP.dp.px)
                .coerceAtMost(MAX_BLUR_RADIUS.dp.px)
        } else {
            0f
        }

        if (isActivated || targetBlurRadius <= 0f) {
            // Active line MUST unblur immediately! Never schedule blur on active lines
            lineBlurRadiusAnimator?.cancel()
            lineBlurRadiusAnimator = null
            lineBlurRadius = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(null)
            }
        }

        if (!animated || (targetBlurRadius == 0f && lineBlurRadius == 0f && targetAlpha == lineTextAlpha && targetScale == lineTextScale)) {
            cancelLineStyleAnimations()
            lineTextAlpha = targetAlpha
            lineTextScale = targetScale
            lineBlurRadius = targetBlurRadius
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetBlurRadius <= 0.5f) {
                setRenderEffect(null)
            }
            invalidate()
            return
        }

        val effectiveDelay = if (isActivated) delayMs else delayMs + STYLE_SECONDARY_DELAY_MS
        animateLineTextAlpha(targetAlpha, effectiveDelay)
        animateLineTextScale(targetScale, effectiveDelay)
        if (!isActivated && targetBlurRadius > 0f) {
            animateLineBlurRadius(targetBlurRadius, effectiveDelay)
        }
    }

    fun unblurImmediately() {
        lineBlurRadiusAnimator?.cancel()
        lineBlurRadiusAnimator = null
        lineBlurRadius = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(null)
        }
        invalidate()
    }

    fun isRenderEffectBlurred(): Boolean = lineBlurRadius > 0.5f

    fun resetLineStyle() {
        cancelLineStyleAnimations()
        lineTextAlpha = if (isBackgroundLine) 0f else INACTIVE_LINE_ALPHA
        lineTextScale = INACTIVE_LINE_SCALE
        lineBlurRadius = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(null)
        }
        invalidate()
    }

    private fun animateLineTextAlpha(targetAlpha: Float, delayMs: Long) {
        lineTextAlphaAnimator?.cancel()
        lineTextAlphaAnimator = null
        if (lineTextAlpha == targetAlpha) return
        lineTextAlphaAnimator = ValueAnimator.ofFloat(lineTextAlpha, targetAlpha).apply {
            duration = STYLE_ANIMATION_DURATION_MS
            startDelay = delayMs
            interpolator = lineStyleInterpolator
            addUpdateListener {
                lineTextAlpha = it.animatedValue as Float
            }
            start()
        }
    }

    private fun animateLineTextScale(targetScale: Float, delayMs: Long) {
        lineTextScaleAnimator?.cancel()
        lineTextScaleAnimator = null
        if (lineTextScale == targetScale) return
        lineTextScaleAnimator = ValueAnimator.ofFloat(lineTextScale, targetScale).apply {
            duration = STYLE_ANIMATION_DURATION_MS
            startDelay = delayMs
            interpolator = lineStyleInterpolator
            addUpdateListener {
                lineTextScale = it.animatedValue as Float
            }
            start()
        }
    }

    private fun animateLineBlurRadius(targetRadius: Float, delayMs: Long) {
        lineBlurRadiusAnimator?.cancel()
        lineBlurRadiusAnimator = null
        if (lineBlurRadius == targetRadius) return
        lineBlurRadiusAnimator = ValueAnimator.ofFloat(lineBlurRadius, targetRadius).apply {
            // Below Android 12 every radius change needs a fresh CPU blur, so jump straight to the
            // target (after the delay) instead of re-blurring on every animation frame.
            duration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BLUR_ANIMATION_DURATION_MS else 1L
            startDelay = delayMs
            interpolator = lineStyleInterpolator
            addUpdateListener {
                lineBlurRadius = it.animatedValue as Float
            }
            start()
        }
    }

    private fun cancelLineStyleAnimations() {
        lineTextAlphaAnimator?.cancel()
        lineTextScaleAnimator?.cancel()
        lineBlurRadiusAnimator?.cancel()
        lineTextAlphaAnimator = null
        lineTextScaleAnimator = null
        lineBlurRadiusAnimator = null
    }

    private fun cancelLiftupReturnAnimator() {
        liftupReturnAnimator?.cancel()
        liftupReturnAnimator = null
    }

    private fun calculateLiftupProgress() {
        val starts = liftupStartupTimes ?: return
        val progresses = liftupProgress ?: return

        val n = minOf(starts.size, progresses.size)
        val now = progressSoFar

        for (i in 0 until n) {
            val start = starts[i]
            if (start < 0L) {
                continue
            }
            val elapsed = now - start
            val duration = liftupWindows
                .getOrNull(i)
                ?.durationMs
                ?: LyricTiming.LIFTUP_DURATION_MS
            val p = when {
                elapsed <= 0L -> 0f
                elapsed >= duration -> 1f
                else -> elapsed.toFloat() / duration.toFloat()
            }
            progresses[i] = defaultPathInterpolator.getInterpolation(p)
        }
    }

    private fun calculateLinePositionMs(itemPos: Int, fraction: Float): Long {
        val synced = syncedLyric ?: return 0L
        return LyricTiming.linePositionMs(synced.relativeTime, itemPos, fraction)
    }

    private fun calculateTargetPosition(itemPos: Int) {
        val list = syncedLyric?.list ?: return
        val layout = staticLayout ?: return

        val substringBeforeTarget = list.take(itemPos).joinToString("") { it.content }
        startOffsetChar = substringBeforeTarget.length
        endOffsetChar = startOffsetChar + list[itemPos].content.length

        startLine = layout.getLineForOffset(startOffsetChar)
        endLine = layout.getLineForOffset(endOffsetChar)

        startOffsetInLinePx = layout.getPrimaryHorizontal(startOffsetChar)
        endOffsetInLinePx = layout.getPrimaryHorizontal(endOffsetChar)

        if (endOffsetInLinePx == 0F) {
            endLine --
            endOffsetInLinePx = layout.getLineRight(endLine)
        }

        val lineArray = IntArray(endLine - startLine + 1)
        for (line in startLine..endLine) {
            if (startLine == endLine) {
                lineArray[0] = (endOffsetInLinePx - startOffsetInLinePx).toInt()
                break
            }
            if (line == startLine) {
                lineArray[0] = (layout.getLineRight(line) - layout.getLineLeft(line) - startOffsetInLinePx).toInt()
                continue
            }
            if (line == endLine) {
                lineArray[endLine - startLine] = endOffsetInLinePx.toInt()
                continue
            }
            lineArray[line - startLine] = (layout.getLineRight(line) - layout.getLineLeft(line)).toInt()
        }
        animationLinePx = LinePixels(lineArray)
    }

    private var startLine = -1
    private var endLine = -1
    private var startOffsetInLinePx = -1F
    private var endOffsetInLinePx = -1F
    private var startOffsetChar = -1
    private var endOffsetChar = -1
    private var animationLinePx: LinePixels? = null

    private val defaultPathInterpolator = PathInterpolator(0.2F, 0F, 0F, 1F)

    inner class LinePixels(
        val lineWidths: IntArray,
    ) {
        fun getTotalLineWidth() =
            lineWidths.sum()

        fun getLineAndProgress(animationFraction: Float): Pair<Int, Float> {
            val progressPixel = animationFraction * getTotalLineWidth()
            var accumulatingPixels = 0

            lineWidths.forEachIndexed { index, i ->
                if (accumulatingPixels + i < progressPixel) {
                    accumulatingPixels += i
                } else {
                    return Pair(startLine + index, progressPixel - accumulatingPixels)
                }
            }

            return Pair(startLine, 0f)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LinePixels

            return lineWidths.contentEquals(other.lineWidths)
        }

        override fun hashCode(): Int {
            return lineWidths.contentHashCode()
        }
    }

    private fun drawContentLayerWithProgress(canvas: Canvas) {
        val startOffset = endOffsetChar
        val endOffset = lyric.length
        if (startOffset >= endOffset) return
        val layout = staticLayout ?: return

        val activeWholeLine = isLineActive && syncedLyric == null
        val overlayAlpha = when {
            activeWholeLine -> ACTIVE_OVERLAY_TRANSPARENCY
            isHolding -> HOLDING_OVERLAY_TRANSPARENCY
            else -> INACTIVE_OVERLAY_TRANSPARENCY
        }

        val shadeAlpha = when {
            activeWholeLine -> ACTIVE_SHADE_TRANSPARENCY
            isHolding -> HOLDING_SHADE_TRANSPARENCY
            else -> INACTIVE_SHADE_TRANSPARENCY
        }

        val startLine = layout.getLineForOffset(startOffset)
        val endLine = layout.getLineForOffset(endOffset)

        for (line in startLine..endLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val lineLeft = layout.getLineLeft(line)
            val lineRight = layout.getLineRight(line)

            val startX = if (line == startLine) layout.getPrimaryHorizontal(startOffset) else lineLeft
            val endX = if (line == endLine) layout.getPrimaryHorizontal(endOffset) else lineRight

            canvas.withClip(startX, lineTop, endX, lineBottom) {
                layout.paint.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        blendMode = BlendMode.OVERLAY
                    }
                    style = Paint.Style.FILL
                    alpha = getScopeAlpha(overlayAlpha)
                }
                layout.draw(this)

                layout.paint.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        blendMode = null
                    }
                    style = Paint.Style.FILL
                    alpha = getScopeAlpha(shadeAlpha)
                }
                layout.draw(this)
            }
        }
    }

    private fun drawContentLayer(canvas: Canvas) {
        val layout = staticLayout ?: return

        val activeProgress = if (isLineActive && !isCapturingBlur && syncedLyric == null) {
            ((lineTextAlpha - INACTIVE_LINE_ALPHA) / (ACTIVE_LINE_ALPHA - INACTIVE_LINE_ALPHA)).coerceIn(0f, 1f)
        } else if (isHolding) {
            0.5f
        } else {
            0f
        }

        val overlayAlpha = INACTIVE_OVERLAY_TRANSPARENCY + (ACTIVE_OVERLAY_TRANSPARENCY - INACTIVE_OVERLAY_TRANSPARENCY) * activeProgress
        val shadeAlpha = INACTIVE_SHADE_TRANSPARENCY + (ACTIVE_SHADE_TRANSPARENCY - INACTIVE_SHADE_TRANSPARENCY) * activeProgress

        layout.paint.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = BlendMode.OVERLAY
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(overlayAlpha)
        }
        layout.draw(canvas)

        layout.paint.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = null
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(shadeAlpha)
            if (activeProgress > 0.15f && accentColor != Color.WHITE && accentColor != 0) {
                setShadowLayer(GLOW_EFFECT_RADIUS * 1.5f * activeProgress, 0f, 0f, accentColor)
            }
        }
        layout.draw(canvas)
        layout.paint.clearGlowShadow()
    }

    private fun drawInactiveSyncedContentLayer(canvas: Canvas) {
        val layout = staticLayout ?: return
        layout.paint.apply {
            clearGlowShadow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = BlendMode.OVERLAY
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(inactiveOverlayAlpha())
        }
        layout.draw(canvas)

        layout.paint.apply {
            clearGlowShadow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = null
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(inactiveShadeAlpha())
        }
        layout.draw(canvas)
    }

    private fun drawHighlightLayer(canvas: Canvas) {
        drawTextRange(canvas, animationUnit - 1)
        drawHighlightText(canvas)
    }

    private fun drawTextRange(canvas: Canvas, endUnit: Int) {
        val layout = staticLayout ?: return
        val synced = syncedLyric ?: return
        val progress = liftupProgress ?: return

        if (endUnit !in synced.list.indices) return

        var charOffset = 0

        for (i in synced.list.indices) {
            val content = synced.list[i].content
            val startOffset = charOffset
            val endOffset = charOffset + content.length
            charOffset = endOffset

            if (i > endUnit) continue

            val startLine = layout.getLineForOffset(startOffset)
            val endLine = layout.getLineForOffset(endOffset)

            for (line in startLine..endLine) {
                val lineTop = layout.getLineTop(line).toFloat()
                val lineBottom = layout.getLineBottom(line).toFloat()
                val lineLeft = layout.getLineLeft(line)
                val lineRight = layout.getLineRight(line)

                val startX = if (line == startLine) layout.getPrimaryHorizontal(startOffset) else lineLeft
                var endX = if (line == endLine) layout.getPrimaryHorizontal(endOffset) else lineRight

                if (endX == 0F && line < endLine) {
                    endX = lineRight
                }

                canvas.withClip(startX, lineTop, endX, lineBottom) {
                    drawLiftedHighlightText(
                        layout = layout,
                        liftProgress = progress.getOrElse(i) { 0f },
                        glowEnabled = false
                    )
                }
            }
        }
    }

    private fun drawHighlightText(canvas: Canvas) {
        val layout = staticLayout ?: return
        val linePx = animationLinePx ?: return
        val progress = liftupProgress ?: return

        val (activeLine, pixelProgress) = linePx.getLineAndProgress(animationFraction)

        for (line in activeLine..endLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val lineLeft = layout.getLineLeft(line)
            val lineRight = layout.getLineRight(line)

            val leftDist = if (line == startLine) startOffsetInLinePx else lineLeft
            val startX = if (line == activeLine) leftDist + pixelProgress else leftDist
            val endX = if (line == endLine) endOffsetInLinePx else lineRight
            if (endX > startX) {
                canvas.withClip(startX, lineTop, endX, lineBottom) {
                    val currentLift = progress.getOrElse(animationUnit) { 0f }
                    canvas.translate(0F, -LIFTUP_PX * currentLift)

                    layout.paint.apply {
                        clearGlowShadow()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            blendMode = BlendMode.OVERLAY
                        }
                        style = Paint.Style.FILL
                        alpha = getScopeAlpha(inactiveOverlayAlpha())
                    }
                    layout.draw(this)

                    layout.paint.apply {
                        clearGlowShadow()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            blendMode = null
                        }
                        style = Paint.Style.FILL
                        alpha = getScopeAlpha(inactiveShadeAlpha())
                    }
                    layout.draw(this)
                }
            }
        }

        for (line in startLine..activeLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val lineLeft = layout.getLineLeft(line)
            val lineRight = layout.getLineRight(line)

            val startX = if (line == startLine) startOffsetInLinePx else lineLeft
            val endX = if (line == activeLine) startX + pixelProgress else lineRight

            if (endX > startX) {
                canvas.withClip(startX, lineTop, endX, lineBottom) {
                    val currentLift = progress.getOrElse(animationUnit) { 0f }
                    drawLiftedHighlightText(
                        layout = layout,
                        liftProgress = currentLift,
                        glowEnabled = isLineActive && shouldShowActiveGlow(animationUnit)
                    )
                }
            }
        }
    }

    private fun Canvas.drawLiftedHighlightText(
        layout: StaticLayout,
        liftProgress: Float,
        glowEnabled: Boolean
    ) {
        translate(0F, -LIFTUP_PX * liftProgress)

        layout.paint.apply {
            clearGlowShadow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = BlendMode.OVERLAY
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(inactiveOverlayAlpha())
        }
        layout.draw(this)

        layout.paint.apply {
            clearGlowShadow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = null
            }
            style = Paint.Style.FILL
            alpha = getScopeAlpha(inactiveShadeAlpha())
        }
        layout.draw(this)

        val highlightFade = if (liftupReturnAnimator != null) (1f - returnFraction).coerceIn(0f, 1f) else 1f
        if (highlightFade > 0f) {
            layout.paint.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    blendMode = null
                }
                strokeWidth = 0.5F * highlightFade
                style = Paint.Style.FILL_AND_STROKE
                alpha = getScopeAlpha(ACTIVE_SHADE_TRANSPARENCY * highlightFade)
                setGlowShadow(liftProgress, glowEnabled)
            }
            layout.draw(this)
        }

        layout.paint.apply {
            clearGlowShadow()
            strokeWidth = 0f
            style = Paint.Style.FILL
        }
    }

    private fun inactiveOverlayAlpha(): Float =
        if (isHolding) {
            HOLDING_OVERLAY_TRANSPARENCY
        } else {
            INACTIVE_OVERLAY_TRANSPARENCY
        }

    private fun inactiveShadeAlpha(): Float =
        if (isHolding) {
            HOLDING_SHADE_TRANSPARENCY
        } else {
            INACTIVE_SHADE_TRANSPARENCY
        }

    private fun shouldShowActiveGlow(itemPos: Int): Boolean {
        val synced = syncedLyric ?: return false
        return LyricTiming.shouldShowActiveGlow(synced.relativeTime, itemPos)
    }

    private fun TextPaint.setGlowShadow(liftProgress: Float, enabled: Boolean) {
        val radius = if (enabled && isLineActive) {
            GLOW_EFFECT_RADIUS * triangle(liftProgress)
        } else {
            0F
        }

        if (radius > 0F) {
            val baseColor = if (accentColor != Color.WHITE && accentColor != 0) {
                accentColor
            } else {
                Color.WHITE
            }
            val shadowAlpha = (Color.alpha(baseColor) * this@LyricTextView.alpha * lineTextAlpha).toInt().coerceIn(0, 255)
            val glowColor = (shadowAlpha shl 24) or (baseColor and 0x00FFFFFF)
            setShadowLayer(
                radius,
                0F,
                0F,
                glowColor
            )
        } else {
            clearGlowShadow()
        }
    }

    private fun TextPaint.clearGlowShadow() {
        setShadowLayer(
            0F,
            0F,
            0F,
            Color.TRANSPARENT
        )
    }

    private var alpha = 1F

    override fun setAlpha(alpha: Float) {
        this.alpha = alpha
        invalidate()
    }

    override fun getAlpha(): Float = alpha

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> widthSize
            MeasureSpec.UNSPECIFIED -> widthSize
            else -> widthSize
        }

        if (isMagicLyric) {
            val desiredHeight = (44.dp.px).toInt()
            val measuredHeight = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
                MeasureSpec.UNSPECIFIED -> desiredHeight
                else -> desiredHeight
            }
            setMeasuredDimension(measuredWidth, measuredHeight)
            return
        }

        measureStaticLayout(lyric, measuredWidth)

        val desiredHeight = staticLayout?.height ?: 0

        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize) + verticalMargin * 2
            MeasureSpec.UNSPECIFIED -> desiredHeight + verticalMargin * 2
            else -> desiredHeight
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    fun release() {
        animate().cancel()
        cancelLineStyleAnimations()
        cancelLiftupReturnAnimator()
        releaseLegacyBlur()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(null)
        }
    }

    private fun measureStaticLayout(value: String, measuredWidth: Int) {
        val contentWidth = (measuredWidth - 2 * horizontalMargin).coerceAtLeast(1)
        staticLayout = StaticLayout.Builder
            .obtain(
                value,
                0, value.length,
                lyricPaint,
                contentWidth
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0F, 1.25F)
            .setIncludePad(false)
            .build()
    }

    private fun setCreatorContent() {
        lyricPaint.apply {
            textSize = BG_TEXT_SIZE.sp.px
            typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
        }
    }

    private fun setBackgroundContent() {
        lyricPaint.apply {
            textSize = BG_TEXT_SIZE.sp.px
            typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
        }
    }

    private fun getScopeAlpha(currentAlpha: Float): Int =
        if (isCapturingBlur) {
            (currentAlpha * 255).toInt()
        } else {
            (currentAlpha * 255 * this.alpha * lineTextAlpha).toInt()
        }

    companion object {
        private val FocusRenderEffects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Array<RenderEffect?>(65) { index ->
                if (index == 0) null else RenderEffect.createBlurEffect(index.toFloat(), index.toFloat(), Shader.TileMode.CLAMP)
            }
        } else null

        const val ACTIVE_SHADE_TRANSPARENCY = .85F
        const val ACTIVE_OVERLAY_TRANSPARENCY = 1F

        const val INACTIVE_SHADE_TRANSPARENCY = .25F
        const val INACTIVE_OVERLAY_TRANSPARENCY = .3F

        const val HOLDING_SHADE_TRANSPARENCY = .45F
        const val HOLDING_OVERLAY_TRANSPARENCY = .75F

        const val NORMAL_TEXT_SIZE = 26
        const val BG_TEXT_SIZE = 20

        const val LIFTUP_DURATION = LyricTiming.LIFTUP_DURATION_MS
        const val LIFTUP_PX = 6

        const val GLOW_EFFECT_RADIUS = 7F

        private const val ACTIVE_LINE_ALPHA = .95F
        private const val INACTIVE_LINE_ALPHA = .25F
        private const val ACTIVE_LINE_SCALE = 1F
        private const val INACTIVE_LINE_SCALE = .96F
        private const val MAX_BLUR_RADIUS = 8
        private const val BLUR_RADIUS_STEP = 2
        private const val STYLE_ANIMATION_DURATION_MS = 500L
        private const val BLUR_ANIMATION_DURATION_MS = 100L
        private const val STYLE_SECONDARY_DELAY_MS = 250L

        // Legacy (Android 11 and below) blur: aim for a ~2px sigma on the downscaled bitmap, which
        // keeps it tiny while still being smooth once scaled back up.
        private const val LEGACY_BLUR_TARGET_SIGMA = 2.2f
        private const val LEGACY_BLUR_MAX_DOWNSCALE = 12
    }
}
