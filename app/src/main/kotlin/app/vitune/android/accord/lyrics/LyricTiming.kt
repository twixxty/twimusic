package app.vitune.android.accord.lyrics

object LyricTiming {
    const val DEFAULT_WORD_DURATION_MS = 350L
    const val DEFAULT_LINE_DURATION_MS = 4200L
    const val CREATOR_LINE_DURATION_MS = 3200L
    const val LINE_HOLD_DURATION_MS = 900L

    const val LIFTUP_DURATION_MS = 700L
    const val ACTIVE_GLOW_MIN_DURATION_MS = 700L
    private const val LIFTUP_SOFTENING_THRESHOLD_MS = 420L
    private const val LIFTUP_VERY_SHORT_UNIT_MS = 180L
    private const val LIFTUP_MAX_LOOKAHEAD_UNITS = 2
    private const val LIFTUP_MAX_DURATION_MS = 1400L

    data class LiftupWindow(
        val startMs: Long,
        val durationMs: Long,
    ) {
        val endMs: Long
            get() = startMs + durationMs
    }

    data class UnitProgress(
        val index: Int,
        val fraction: Float,
    )

    fun durationAt(relativeTime: LongArray, index: Int): Long =
        relativeTime.getOrElse(index) { DEFAULT_WORD_DURATION_MS }
            .coerceAtLeast(1L)

    fun linePositionMs(
        relativeTime: LongArray,
        itemPos: Int,
        fraction: Float
    ): Long {
        val completedProgress = unitStartMs(relativeTime, itemPos)
        val currentDuration = durationAt(relativeTime, itemPos)
        return completedProgress + (fraction.coerceIn(0f, 1f) * currentDuration).toLong()
    }

    fun unitStartMs(relativeTime: LongArray, itemPos: Int): Long =
        (0 until itemPos.coerceAtLeast(0)).sumOf { index ->
            durationAt(relativeTime, index)
        }

    fun resolveUnitProgress(
        unitCount: Int,
        relativeTime: LongArray,
        linePositionMs: Long
    ): UnitProgress? {
        if (unitCount <= 0) return null

        var remainingMs = linePositionMs.coerceAtLeast(0L)
        for (index in 0 until unitCount) {
            val safeDuration = durationAt(relativeTime, index)
            if (remainingMs <= safeDuration || index == unitCount - 1) {
                return UnitProgress(
                    index = index,
                    fraction = (remainingMs.toFloat() / safeDuration).coerceIn(0f, 1f)
                )
            }
            remainingMs -= safeDuration
        }

        return null
    }

    fun liftupWindow(
        relativeTime: LongArray,
        unitCount: Int,
        itemPos: Int
    ): LiftupWindow {
        val unitStartMs = unitStartMs(relativeTime, itemPos)
        if (itemPos !in 0 until unitCount) {
            return LiftupWindow(unitStartMs, LIFTUP_DURATION_MS)
        }

        val currentDuration = durationAt(relativeTime, itemPos)
        if (
            currentDuration >= LIFTUP_SOFTENING_THRESHOLD_MS ||
            itemPos == unitCount - 1
        ) {
            return LiftupWindow(unitStartMs, LIFTUP_DURATION_MS)
        }

        var firstDelayedStartOffsetMs = 0L
        var anchorStartOffsetMs = 0L
        var nextStartOffsetMs = 0L
        var lookaheadCount = 0

        while (
            lookaheadCount < LIFTUP_MAX_LOOKAHEAD_UNITS &&
            itemPos + lookaheadCount + 1 < unitCount
        ) {
            nextStartOffsetMs += durationAt(relativeTime, itemPos + lookaheadCount)
            if (nextStartOffsetMs >= LIFTUP_DURATION_MS) {
                break
            }

            if (firstDelayedStartOffsetMs == 0L) {
                firstDelayedStartOffsetMs = nextStartOffsetMs
            }
            anchorStartOffsetMs = nextStartOffsetMs
            lookaheadCount++

            if (currentDuration > LIFTUP_VERY_SHORT_UNIT_MS) {
                break
            }
        }

        if (firstDelayedStartOffsetMs == 0L) {
            return LiftupWindow(unitStartMs, LIFTUP_DURATION_MS)
        }

        val windowEndOffsetMs = anchorStartOffsetMs + LIFTUP_DURATION_MS
        val windowDurationMs = (windowEndOffsetMs - firstDelayedStartOffsetMs)
            .coerceAtLeast(1L)
            .coerceAtMost(LIFTUP_MAX_DURATION_MS)

        return LiftupWindow(
            startMs = unitStartMs + firstDelayedStartOffsetMs,
            durationMs = windowDurationMs
        )
    }

    fun liftupWindows(relativeTime: LongArray, unitCount: Int): List<LiftupWindow> =
        (0 until unitCount).map { index ->
            liftupWindow(relativeTime, unitCount, index)
        }

    fun liftupDuration(
        relativeTime: LongArray,
        unitCount: Int,
        itemPos: Int
    ): Long = liftupWindow(relativeTime, unitCount, itemPos).durationMs

    fun shouldShowActiveGlow(relativeTime: LongArray, itemPos: Int): Boolean =
        durationAt(relativeTime, itemPos) > ACTIVE_GLOW_MIN_DURATION_MS

    fun durationMs(lyric: LyricBase): Long = when (lyric) {
        is SyncedLyric -> if (lyric.list.isNotEmpty()) {
            lyric.list.indices.sumOf { index ->
                durationAt(lyric.relativeTime, index)
            } + LINE_HOLD_DURATION_MS
        } else {
            DEFAULT_LINE_DURATION_MS
        }
        is Creator -> CREATOR_LINE_DURATION_MS
        else -> DEFAULT_LINE_DURATION_MS
    }
}
