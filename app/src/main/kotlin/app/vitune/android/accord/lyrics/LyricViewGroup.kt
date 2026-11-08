package app.vitune.android.accord.lyrics

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ScrollView
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed

class LyricViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    init {
        UiUtils.init(context)
    }

    private val itemSpacing = ITEM_SPACING.dp.px.toInt()
    private val topPadding = TOP_PADDING.dp.px.toInt()
    private val scrollInterpolator = PathInterpolator(0.6f, 0f, 0.2f, 1f)

    private val lyrics: MutableList<LyricBase> = mutableListOf()
    private val lineStartTimes: MutableList<Long> = mutableListOf()
    private var currentLineIndex = -1
    var durationMs: Long = 0L
        private set

    var onLineClick: ((index: Int, timeMs: Long) -> Unit)? = null

    fun updateLyrics(lyrics: List<LyricBase>, startTimes: List<Long>? = null) {
        stopPlayback()

        this.lyrics.clear()
        this.lyrics.addAll(lyrics)
        if (startTimes != null && startTimes.size == lyrics.size) {
            lineStartTimes.clear()
            lineStartTimes.addAll(startTimes)
            durationMs = if (startTimes.isNotEmpty()) startTimes.last() + 5000L else 0L
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
                delayMs = 0L
            )
            view.setOnClickListener {
                val time = lineStartTimes.getOrNull(index) ?: 0L
                onLineClick?.invoke(index, time)
            }
            addView(view)
        }

        currentLineIndex = -1
        requestLayout()
    }

    var scrollViewHeight: Int = resources.displayMetrics.heightPixels
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun stopPlayback() {
        currentLineIndex = -1
        forEach { child ->
            child.animate().cancel()
            child.translationY = 0f
            (child as? LyricTextView)?.resetPlayback()
            (child as? LyricTextView)?.setLineActive(false)
            (child as? LyricTextView)?.resetLineStyle()
        }
    }

    fun updatePlaybackPosition(scrollView: ScrollView, positionMs: Long, autoScroll: Boolean = true) {
        if (lyrics.isEmpty() || childCount == 0 || lineStartTimes.isEmpty()) return

        scrollViewHeight = scrollView.height
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val targetIndex = getCurrentLyricsLineIndex(safePositionMs)

        if (targetIndex != currentLineIndex) {
            scrollToLine(
                scrollView = scrollView,
                index = targetIndex,
                animated = currentLineIndex != -1,
                shouldScrollScrollView = autoScroll
            )
        }

        val child = getChildAt(targetIndex) as? LyricTextView ?: return
        val linePositionMs = safePositionMs - lineStartTimes[targetIndex]
        updateLineProgress(child, lyrics[targetIndex], linePositionMs)
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
        if (currentLineIndex == index) return
        val shouldAnimate = animated && currentLineIndex != -1
        val previousLineIndex = currentLineIndex
        currentLineIndex = index
        forEachIndexed { childIndex, child ->
            val lyricTextView = child as? LyricTextView ?: return@forEachIndexed
            if (childIndex == previousLineIndex && shouldAnimate) {
                lyricTextView.animateLiftupProgressToRest(
                    durationMs = SCROLL_ANIMATION_DURATION_MS,
                    delayMs = getLineAnimationDelay(childIndex, index)
                )
            } else if (childIndex != index) {
                lyricTextView.resetPlayback()
            }
            lyricTextView.setLineActive(childIndex == index)
            lyricTextView.updateDelayedLineStyle(
                index = childIndex,
                targetIndex = index,
                animated = shouldAnimate,
                delayMs = getLineAnimationDelay(childIndex, index)
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

    private fun getCurrentLyricsLineIndex(positionMs: Long): Int {
        val index = lineStartTimes.indexOfLast { it <= positionMs }
        return index.coerceIn(0, lyrics.lastIndex)
    }

    private fun rebuildTimeline() {
        lineStartTimes.clear()
        var cursor = 0L
        lyrics.forEach { lyric ->
            lineStartTimes += cursor
            cursor += LyricTiming.durationMs(lyric)
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

    override fun onDetachedFromWindow() {
        stopPlayback()
        super.onDetachedFromWindow()
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
        const val TOP_PADDING = 48

        private const val SCROLL_ANIMATION_DURATION_MS = 700L
        private const val LINE_ANIMATION_MIN_DELAY_MS = 10L
        private const val LINE_ANIMATION_DELAY_STEP_MS = 20L
        private const val LINE_ANIMATION_MAX_DELAY_MS = 190L
    }
}
