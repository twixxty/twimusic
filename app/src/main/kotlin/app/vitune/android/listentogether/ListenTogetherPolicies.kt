/**
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist), GPL-3.0.
 */

package app.vitune.android.listentogether

import app.vitune.android.listentogether.network.PlaybackActionPayload
import app.vitune.android.listentogether.network.PlaybackActions
import app.vitune.android.listentogether.network.RoomState
import app.vitune.android.listentogether.network.TrackInfo
import kotlin.math.abs

class PlaybackRevisionFilter(initialRevision: Long = 0L) {
    var revision: Long = initialRevision
        private set

    fun accept(candidate: Long): Boolean {
        if (candidate <= 0L) return true
        if (candidate < revision) return false
        revision = maxOf(revision, candidate)
        return true
    }

    fun reset(value: Long = 0L) {
        revision = value
    }
}

object ListenTogetherProtocolReducer {
    @Suppress("CyclomaticComplexMethod")
    fun reduce(state: RoomState, action: PlaybackActionPayload): RoomState {
        val updateTime = action.serverTime ?: state.lastUpdate
        val revision = maxOf(state.revision, action.revision)
        return when (action.action) {
            PlaybackActions.PLAY -> state.copy(
                isPlaying = true,
                position = action.position ?: state.position,
                lastUpdate = updateTime,
                revision = revision
            )

            PlaybackActions.PAUSE -> state.copy(
                isPlaying = false,
                position = action.position ?: state.position,
                lastUpdate = updateTime,
                revision = revision
            )

            PlaybackActions.SEEK -> state.copy(
                position = action.position ?: state.position,
                lastUpdate = updateTime,
                revision = revision
            )

            PlaybackActions.CHANGE_TRACK -> state.copy(
                currentTrack = action.trackInfo,
                position = action.position ?: 0L,
                isPlaying = false,
                lastUpdate = updateTime,
                queue = if (action.replaceQueue || action.revision > 0L) action.queue.orEmpty()
                else state.queue,
                queueTitle = action.queueTitle,
                revision = revision
            )

            PlaybackActions.SYNC_QUEUE -> state.copy(
                queue = action.queue.orEmpty(),
                queueTitle = action.queueTitle,
                revision = revision
            )

            PlaybackActions.QUEUE_ADD -> state.copy(
                queue = canonicalQueueAfterAdd(state.queue, action),
                revision = revision
            )

            PlaybackActions.QUEUE_REMOVE -> state.copy(
                queue = if (action.revision > 0L) action.queue.orEmpty()
                else state.queue.filterNot { it.id == action.trackId },
                revision = revision
            )

            PlaybackActions.QUEUE_CLEAR -> state.copy(queue = emptyList(), revision = revision)

            PlaybackActions.SET_VOLUME -> state.copy(
                volume = action.volume?.coerceIn(0f, 1f) ?: state.volume,
                revision = revision
            )

            else -> state.copy(revision = revision)
        }
    }

    private fun canonicalQueueAfterAdd(
        current: List<TrackInfo>,
        action: PlaybackActionPayload
    ): List<TrackInfo> {
        if (action.revision > 0L) return action.queue.orEmpty()
        val track = action.trackInfo ?: return current
        return if (action.insertNext == true) listOf(track) + current else current + track
    }
}

/** Seek when the guest is far from the host; otherwise leave it alone. */
fun needsSeek(currentPositionMs: Long, targetPositionMs: Long, threshold: Long = 750L) =
    abs(targetPositionMs - currentPositionMs) > threshold

/** Exponential backoff with a cap, matching the cadence of the upstream client. */
fun reconnectDelayMillis(attempt: Int): Long =
    (1_000L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(30_000L)
