package app.vitune.android.accord.lyrics

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ScrollView
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import app.vitune.android.preferences.PlayerPreferences

class LyricViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    init {
        UiUtils.init(context)
    }

    private val itemSpacing = ITEM_SPACING.dp.px.toInt()
    private val topPadding: Int
        get() = (scrollViewHeight * 0.40f).toInt().coerceAtLeast(140.dp.px.toInt())
    private val scrollInterpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)

    private val lyrics: MutableList<LyricBase> = mutableListOf()
    private val lineStartTimes: MutableList<Long> = mutableListOf()
    private val lineEndTimes: MutableList<Long> = mutableListOf()
    private var currentPrimaryLineIndex = -1
    private var currentActiveIndices = setOf<Int>()

    var accentColor: Int = android.graphics.Color.WHITE
        set(value) {
            field = value
            forEach { child ->
                (child as? LyricTextView)?.accentColor = value
            }
        }

    var durationMs: Long = 0L
        private set

    var onLineClick: ((index: Int, timeMs: Long) -> Unit)? = null
    var positionProvider: (() -> Long)? = null
    var isPlayingProvider: (() -> Boolean)? = null

    private var lastRawPositionMs = 0L
    private var lastRawPositionTimeMs = 0L
    private var lastEmittedPositionMs = 0L

    fun getSmoothPositionMs(): Long {
        val rawPos = positionProvider?.invoke() ?: 0L
        val isPlaying = isPlayingProvider?.invoke() ?: false
        val now = SystemClock.elapsedRealtime()

        if (!isPlaying) {
            lastRawPositionMs = rawPos
            lastRawPositionTimeMs = now
            lastEmittedPositionMs = rawPos
            return rawPos
        }

        // Seek or large track jump (> 1000ms): immediately sync ground truth
        if (kotlin.math.abs(rawPos - lastEmittedPositionMs) > 1000L) {
            lastRawPositionMs = rawPos
            lastRawPositionTimeMs = now
            lastEmittedPositionMs = rawPos
            return rawPos
        }

        if (rawPos != lastRawPositionMs) {
            lastRawPositionMs = rawPos
            lastRawPositionTimeMs = now
        }

        // Safe monotonic interpolation for frame smoothness (up to 500ms between ExoPlayer clock updates)
        val elapsed = if (lastRawPositionTimeMs > 0L) {
            (now - lastRawPositionTimeMs).coerceIn(0L, 500L)
        } else {
            0L
        }

        val computed = rawPos + elapsed
        // Strictly monotonic: time NEVER travels backwards during playback
        lastEmittedPositionMs = maxOf(lastEmittedPositionMs, computed)
        return lastEmittedPositionMs
    }

    var autoScrollEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            val primary = currentPrimaryLineIndex
            forEachIndexed { childIndex, child ->
                val view = child as? LyricTextView ?: return@forEachIndexed
                val isActive = childIndex in currentActiveIndices
                view.updateDelayedLineStyle(
                    index = childIndex,
                    targetIndex = primary,
                    animated = true,
                    delayMs = 0L,
                    enableBlur = PlayerPreferences.lyricsLineBlur,
                    isAutoScroll = value,
                    forceActive = isActive
                )
            }
        }

    private var isLoopRunning = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow || !isLoopRunning) return
            val parentScrollView = parent as? ScrollView
            if (parentScrollView != null && positionProvider != null) {
                val pos = getSmoothPositionMs()
                updatePlaybackPosition(
                    scrollView = parentScrollView,
                    positionMs = pos,
                    autoScroll = autoScrollEnabled,
                    enableBlur = PlayerPreferences.lyricsLineBlur
                )
            }
            if (isAttachedToWindow && isLoopRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun startFrameLoop() {
        if (isLoopRunning) return
        isLoopRunning = true
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFrameLoop() {
        isLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFrameLoop()
    }

    override fun onDetachedFromWindow() {
        stopPlayback()
        stopFrameLoop()
        super.onDetachedFromWindow()
    }

    fun updateLyrics(
        lyrics: List<LyricBase>,
        startTimes: List<Long>? = null,
        endTimes: List<Long>? = null
    ) {
        stopPlayback()

        this.lyrics.clear()
        this.lyrics.addAll(lyrics)
        if (startTimes != null && startTimes.size == lyrics.size) {
            lineStartTimes.clear()
            lineStartTimes.addAll(startTimes)
            lineEndTimes.clear()
            if (endTimes != null && endTimes.size == lyrics.size) {
                lineEndTimes.addAll(endTimes)
            } else {
                for (i in lyrics.indices) {
                    val duration = LyricTiming.durationMs(lyrics[i])
                    lineEndTimes.add(startTimes[i] + duration)
                }
            }
            durationMs = if (lineEndTimes.isNotEmpty()) lineEndTimes.maxOrNull() ?: (startTimes.last() + 5000L) else 0L
        } else {
            rebuildTimeline()
        }

        forEach { child: View ->
            (child as? LyricTextView)?.release()
        }
        removeAllViews()
        lyrics.forEachIndexed { index, line ->
            val view = LyricTextView(context, line)
            view.accentColor = accentColor
            view.alpha = currentAlpha ?: 1f
            view.updateDelayedLineStyle(
                index = index,
                targetIndex = 0,
                animated = false,
                delayMs = 0L,
                enableBlur = PlayerPreferences.lyricsLineBlur,
                isAutoScroll = autoScrollEnabled
            )
            view.setOnClickListener {
                val time = lineStartTimes.getOrNull(index) ?: 0L
                onLineClick?.invoke(index, time)
            }
            addView(view)
        }

        currentPrimaryLineIndex = -1
        currentActiveIndices = emptySet()
        requestLayout()
    }

    var scrollViewHeight: Int = resources.displayMetrics.heightPixels
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun stopPlayback() {
        lastRawPositionMs = 0L
        lastRawPositionTimeMs = 0L
        lastEmittedPositionMs = 0L
        currentPrimaryLineIndex = -1
        currentActiveIndices = emptySet()
        forEach { child ->
            child.animate().cancel()
            child.translationY = 0f
            (child as? LyricTextView)?.resetPlayback()
            (child as? LyricTextView)?.setLineActive(false)
            (child as? LyricTextView)?.resetLineStyle()
        }
    }

    private fun getActiveLines(positionMs: Long): Pair<Int, Set<Int>> {
        if (lyrics.isEmpty()) return -1 to emptySet()

        val active = mutableSetOf<Int>()
        for (i in lyrics.indices) {
            val start = lineStartTimes.getOrElse(i) { 0L }
            val rawEnd = lineEndTimes.getOrElse(i) {
                if (i + 1 < lineStartTimes.size) lineStartTimes[i + 1] else start + 4000L
            }
            // Seamless handoff: eliminate dead-zone gap between sequential lines without boundary collision
            val hasNextSequential = i + 1 < lineStartTimes.size && rawEnd <= lineStartTimes[i + 1]
            val end = if (hasNextSequential) lineStartTimes[i + 1] else rawEnd
            val isActive = if (hasNextSequential) {
                positionMs >= start && positionMs < end
            } else {
                positionMs in start..end
            }
            if (isActive) {
                active.add(i)
            }
        }

        if (active.isEmpty()) {
            val fallback = lineStartTimes.indexOfLast { it <= positionMs }.coerceIn(0, lyrics.lastIndex)
            return fallback to setOf(fallback)
        }

        // When multiple lines overlap (e.g. main line and background / subtext):
        // Prioritize the main non-background, non-magic line as the primary scroll target
        val primary = active.firstOrNull { index ->
            when (val item = lyrics.getOrNull(index)) {
                is Lyric -> !item.isBackground && !item.isMagicLyric
                is SyncedLyric -> !item.isBackground
                else -> true
            }
        } ?: active.first()

        return primary to active
    }

    fun updatePlaybackPosition(
        scrollView: ScrollView,
        positionMs: Long,
        autoScroll: Boolean = true,
        enableBlur: Boolean = PlayerPreferences.lyricsLineBlur
    ) {
        if (lyrics.isEmpty() || childCount == 0 || lineStartTimes.isEmpty()) return

        scrollViewHeight = scrollView.height
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val (primaryIndex, activeIndices) = getActiveLines(safePositionMs)

        val previousActiveIndices = currentActiveIndices
        currentActiveIndices = activeIndices

        val activeIndicesChanged = activeIndices != previousActiveIndices
        if (primaryIndex != currentPrimaryLineIndex) {
            scrollToLine(
                scrollView = scrollView,
                index = primaryIndex,
                animated = currentPrimaryLineIndex != -1,
                shouldScrollScrollView = autoScroll
            )
        } else if (activeIndicesChanged) {
            // Update line styles immediately when active indices change,
            // so simultaneous lines (e.g. background vocals) unblur and highlight instantly
            forEachIndexed { childIndex, child ->
                val lyricTextView = child as? LyricTextView ?: return@forEachIndexed
                val isActive = childIndex in activeIndices
                val wasActive = childIndex in previousActiveIndices
                if (isActive != wasActive) {
                    lyricTextView.setLineActive(isActive)
                    lyricTextView.updateDelayedLineStyle(
                        index = childIndex,
                        targetIndex = primaryIndex,
                        animated = true,
                        delayMs = 0L,
                        enableBlur = enableBlur,
                        isAutoScroll = autoScroll,
                        forceActive = isActive
                    )
                }
            }
        }

        // Update progress on all active lines
        for (activeIdx in activeIndices) {
            if (activeIdx in 0 until childCount) {
                val child = getChildAt(activeIdx) as? LyricTextView ?: continue
                child.setLineActive(true)
                val linePositionMs = safePositionMs - lineStartTimes[activeIdx]
                updateLineProgress(child, lyrics[activeIdx], linePositionMs)
            }
        }

        // If any line was previously active and is no longer active, update active flag
        for (prevIdx in previousActiveIndices) {
            if (prevIdx !in activeIndices && prevIdx in 0 until childCount) {
                val child = getChildAt(prevIdx) as? LyricTextView ?: continue
                child.setLineActive(false)
            }
        }
    }

    fun scrollToLine(
        scrollView: ScrollView,
        index: Int,
        animated: Boolean = true,
        shouldScrollScrollView: Boolean = true
    ) {
        if (index !in 0 until childCount) return

        setCurrentLine(index, animated)

        if (!shouldScrollScrollView) return

        val currentOffset = scrollView.scrollY.toFloat()
        val maxScrollOffset = (measuredHeight - scrollView.height).coerceAtLeast(0)
        val targetOffset = (getChildAt(index).top - topPadding).coerceIn(0, maxScrollOffset).toFloat()
        val deltaOffset = targetOffset - currentOffset

        if (!animated || deltaOffset == 0f) {
            scrollView.scrollTo(0, targetOffset.toInt())
            forEach { child ->
                child.animate().cancel()
                child.translationY = 0f
            }
            return
        }

        scrollView.scrollTo(0, targetOffset.toInt())
        forEachIndexed { childIndex, child ->
            val currentTranslation = child.translationY
            child.animate().cancel()
            child.translationY = currentTranslation + deltaOffset
            child.animate()
                .translationY(0f)
                .setDuration(SCROLL_ANIMATION_DURATION_MS)
                .setInterpolator(scrollInterpolator)
                .setStartDelay(getLineAnimationDelay(childIndex, index))
                .start()
        }
    }

    private fun setCurrentLine(index: Int, animated: Boolean) {
        if (currentPrimaryLineIndex == index) return
        val shouldAnimate = animated && currentPrimaryLineIndex != -1
        val previousLineIndex = currentPrimaryLineIndex
        currentPrimaryLineIndex = index

        val activeWindowStart = maxOf(0, minOf(if (previousLineIndex >= 0) previousLineIndex - 2 else index - 2, index - 2))
        val activeWindowEnd = minOf(childCount - 1, maxOf(previousLineIndex + 2, index + 2))

        forEachIndexed { childIndex, child ->
            val lyricTextView = child as? LyricTextView ?: return@forEachIndexed
            val isActive = childIndex in currentActiveIndices
            if (childIndex == previousLineIndex && shouldAnimate && !isActive) {
                lyricTextView.markCompleted()
                lyricTextView.animateLiftupProgressToRest(
                    durationMs = SCROLL_ANIMATION_DURATION_MS,
                    delayMs = 0L
                )
            } else if (!isActive && childIndex !in activeWindowStart..activeWindowEnd) {
                if (childIndex > index) {
                    lyricTextView.resetPlayback()
                } else {
                    lyricTextView.markCompleted()
                }
            }
            lyricTextView.setLineActive(isActive)

            val animateThis = shouldAnimate && (childIndex in activeWindowStart..activeWindowEnd)
            lyricTextView.updateDelayedLineStyle(
                index = childIndex,
                targetIndex = index,
                animated = animateThis,
                delayMs = 0L,
                enableBlur = PlayerPreferences.lyricsLineBlur,
                isAutoScroll = autoScrollEnabled,
                forceActive = isActive
            )
        }
    }

    private fun getLineAnimationDelay(index: Int, targetIndex: Int): Long = 0L

    private fun updateLineProgress(child: LyricTextView, lyric: LyricBase, linePositionMs: Long) {
        val syncedLyric = lyric as? SyncedLyric ?: return
        if (linePositionMs < 0L) {
            // Before word 0 starts singing (lead-in / pre-roll):
            // Hold at syllable 0, 0% progress so all words remain properly masked as unsung
            child.animate(
                itemPos = 0,
                fraction = 0f,
                linePositionMs = 0L
            )
            return
        }
        val unitProgress = LyricTiming.resolveUnitProgress(
            unitCount = syncedLyric.list.size,
            relativeTime = syncedLyric.relativeTime,
            linePositionMs = linePositionMs
        ) ?: return

        child.animate(
            itemPos = unitProgress.index,
            fraction = unitProgress.fraction,
            linePositionMs = linePositionMs
        )
    }

    private fun rebuildTimeline() {
        lineStartTimes.clear()
        lineEndTimes.clear()
        var cursor = 0L
        lyrics.forEach { lyric ->
            lineStartTimes += cursor
            val dur = LyricTiming.durationMs(lyric)
            lineEndTimes += (cursor + dur)
            cursor += dur
        }
        durationMs = cursor
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var top = topPadding
        forEachIndexed { index: Int, child: View ->
            val height = child.measuredHeight
            child.layout(0, top, child.measuredWidth, top + height)
            top += height + if (index != childCount - 1) itemSpacing else 0
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalHeight = topPadding
        var maxWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)

            measureChild(child, widthMeasureSpec, heightMeasureSpec)

            totalHeight += child.measuredHeight + if (i != childCount - 1) itemSpacing else 0
            maxWidth = maxOf(maxWidth, child.measuredWidth)
        }

        if (childCount > 0) {
            val creatorHeight = if (lyrics.lastOrNull() is Creator) {
                getChildAt(childCount - 1).measuredHeight
            } else {
                0
            }
            val lastRealLyricHeight = if (lyrics.lastOrNull() is Creator && childCount >= 2) {
                getChildAt(childCount - 2).measuredHeight
            } else {
                getChildAt(childCount - 1).measuredHeight
            }

            val bottomPadding =
                (scrollViewHeight - creatorHeight - lastRealLyricHeight - topPadding).coerceAtLeast(0)
            totalHeight += bottomPadding
        }

        val width = resolveSize(maxWidth, widthMeasureSpec)
        val height = resolveSize(totalHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    private var currentAlpha: Float? = null

    override fun setAlpha(alpha: Float) {
        currentAlpha = alpha

        forEach { child: View ->
            child.alpha = alpha
        }

        invalidate()
    }

    override fun getAlpha(): Float = currentAlpha ?: 1f

    companion object {
        const val ITEM_SPACING = 16

        private const val SCROLL_ANIMATION_DURATION_MS = 380L
        private const val LINE_ANIMATION_MIN_DELAY_MS = 0L
        private const val LINE_ANIMATION_DELAY_STEP_MS = 0L
        private const val LINE_ANIMATION_MAX_DELAY_MS = 0L
    }
}
