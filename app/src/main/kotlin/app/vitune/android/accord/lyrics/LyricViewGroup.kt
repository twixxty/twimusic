package app.vitune.android.accord.lyrics

import android.content.Context
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
        get() = (scrollViewHeight * 0.32f).toInt().coerceAtLeast(100.dp.px.toInt())
    private val scrollInterpolator = PathInterpolator(0.6f, 0f, 0.2f, 1f)

    private val lyrics: MutableList<LyricBase> = mutableListOf()
    private val lineStartTimes: MutableList<Long> = mutableListOf()
    private val lineEndTimes: MutableList<Long> = mutableListOf()
    private var currentPrimaryLineIndex = -1
    private var currentActiveIndices = setOf<Int>()

    var durationMs: Long = 0L
        private set

    var onLineClick: ((index: Int, timeMs: Long) -> Unit)? = null
    var positionProvider: (() -> Long)? = null
    var isPlayingProvider: (() -> Boolean)? = null

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
            val provider = positionProvider
            val isPlaying = isPlayingProvider?.invoke() ?: false
            val parentScrollView = parent as? ScrollView
            if (provider != null && parentScrollView != null) {
                val pos = provider()
                updatePlaybackPosition(
                    scrollView = parentScrollView,
                    positionMs = pos,
                    autoScroll = autoScrollEnabled,
                    enableBlur = PlayerPreferences.lyricsLineBlur
                )
            }
            if (isPlaying && isLoopRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isLoopRunning = false
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
            val end = lineEndTimes.getOrElse(i) {
                if (i + 1 < lineStartTimes.size) lineStartTimes[i + 1] else start + 4000L
            }
            if (positionMs in start..end) {
                active.add(i)
            }
        }

        if (active.isEmpty()) {
            val fallback = lineStartTimes.indexOfLast { it <= positionMs }.coerceIn(0, lyrics.lastIndex)
            return fallback to setOf(fallback)
        }

        // When multiple lines overlap (e.g. main line and background / subtext):
        // Prioritize the main non-background line as the primary scroll target
        val primary = active.firstOrNull { index ->
            when (val item = lyrics.getOrNull(index)) {
                is Lyric -> !item.isBackground
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

        if (primaryIndex != currentPrimaryLineIndex) {
            scrollToLine(
                scrollView = scrollView,
                index = primaryIndex,
                animated = currentPrimaryLineIndex != -1,
                shouldScrollScrollView = autoScroll
            )
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

        // If any line was previously active and is no longer active, reset its playback & active flag
        for (prevIdx in previousActiveIndices) {
            if (prevIdx !in activeIndices && prevIdx in 0 until childCount) {
                val child = getChildAt(prevIdx) as? LyricTextView ?: continue
                child.setLineActive(false)
                child.resetPlayback()
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
        forEachIndexed { childIndex, child ->
            val lyricTextView = child as? LyricTextView ?: return@forEachIndexed
            val isActive = childIndex in currentActiveIndices
            if (childIndex == previousLineIndex && shouldAnimate && !isActive) {
                lyricTextView.animateLiftupProgressToRest(
                    durationMs = SCROLL_ANIMATION_DURATION_MS,
                    delayMs = getLineAnimationDelay(childIndex, index)
                )
            } else if (!isActive) {
                lyricTextView.resetPlayback()
            }
            lyricTextView.setLineActive(isActive)
            lyricTextView.updateDelayedLineStyle(
                index = childIndex,
                targetIndex = index,
                animated = shouldAnimate,
                delayMs = getLineAnimationDelay(childIndex, index),
                enableBlur = PlayerPreferences.lyricsLineBlur,
                isAutoScroll = autoScrollEnabled,
                forceActive = isActive
            )
        }
    }

    private fun getLineAnimationDelay(index: Int, targetIndex: Int): Long =
        if (index < targetIndex) {
            0L
        } else {
            ((index - targetIndex) * LINE_ANIMATION_DELAY_STEP_MS + LINE_ANIMATION_MIN_DELAY_MS)
                .coerceAtMost(LINE_ANIMATION_MAX_DELAY_MS)
        }

    private fun updateLineProgress(child: LyricTextView, lyric: LyricBase, linePositionMs: Long) {
        val syncedLyric = lyric as? SyncedLyric ?: return
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

        private const val SCROLL_ANIMATION_DURATION_MS = 700L
        private const val LINE_ANIMATION_MIN_DELAY_MS = 10L
        private const val LINE_ANIMATION_DELAY_STEP_MS = 20L
        private const val LINE_ANIMATION_MAX_DELAY_MS = 190L
    }
}
