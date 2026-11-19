package app.vitune.android.service

import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.vitune.android.listentogether.ListenTogetherPlayback
import app.vitune.android.listentogether.network.TrackInfo
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.utils.shouldBePlaying
import app.vitune.core.ui.utils.SongBundleAccessor
import app.vitune.core.ui.utils.songBundle

/** Parses "m:ss" / "h:mm:ss" duration labels to milliseconds. */
internal fun String?.durationTextToMillis(): Long {
    val parts = this?.split(':')?.map { it.trim().toLongOrNull() ?: return 0L } ?: return 0L
    return parts.fold(0L) { total, part -> total * 60 + part } * 1000L
}

/** Local files cannot be played by other users, so they are never shared. */
fun MediaItem.toTrackInfo(durationMs: Long? = null): TrackInfo? {
    if (isLocal || mediaId.isBlank()) return null
    val metadata = mediaMetadata
    return TrackInfo(
        id = mediaId,
        title = metadata.title?.toString().orEmpty(),
        artist = metadata.artist?.toString().orEmpty(),
        album = metadata.albumTitle?.toString(),
        duration = durationMs?.takeIf { it > 0L }
            ?: metadata.extras?.songBundle?.durationText.durationTextToMillis(),
        thumbnail = metadata.artworkUri?.toString()
    )
}

internal fun TrackInfo.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(thumbnail?.toUri())
            .setExtras(
                SongBundleAccessor.bundle {
                    durationText = duration
                        .takeIf { it > 0L }
                        ?.let { DateUtils.formatElapsedTime(it / 1000L) }
                    artistNames = artist.takeIf { it.isNotBlank() }?.let { listOf(it) }
                }
            )
            .build()
    )
    .build()

/** Exposes the service's ExoPlayer to the Listen Together client. Main thread only. */
internal class ListenTogetherPlaybackAdapter(
    private val player: ExoPlayer
) : ListenTogetherPlayback {
    private val items: List<MediaItem>
        get() = List(player.mediaItemCount) { player.getMediaItemAt(it) }

    override fun currentTrack(): TrackInfo? = player.currentMediaItem?.toTrackInfo(
        durationMs = player.duration.takeIf { it != C.TIME_UNSET }
    )

    override fun isPlaying() = player.shouldBePlaying
    override fun positionMs() = player.currentPosition.coerceAtLeast(0L)
    override fun durationMs() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    // Volume normalization owns ExoPlayer's volume, so it is only shared when that is off.
    override fun volume() = if (PlayerPreferences.volumeNormalization) 1f else player.volume

    override fun upcoming(): List<TrackInfo> {
        val start = player.currentMediaItemIndex + 1
        if (player.mediaItemCount == 0 || start >= player.mediaItemCount) return emptyList()
        return (start until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrackInfo() }
    }

    override fun isReady(trackId: String) =
        player.currentMediaItem?.mediaId == trackId && player.playbackState == Player.STATE_READY

    override fun hasFailed(trackId: String) =
        player.currentMediaItem?.mediaId == trackId && player.playerError != null

    override fun playQueue(current: TrackInfo, upcoming: List<TrackInfo>, title: String?) {
        player.playWhenReady = false
        player.setMediaItems(
            listOf(current.toMediaItem()) + upcoming.map { it.toMediaItem() },
            0,
            C.TIME_UNSET
        )
        player.prepare()
    }

    override fun replaceUpcoming(tracks: List<TrackInfo>) {
        val current = player.currentMediaItemIndex
        if (player.mediaItemCount == 0) return
        val existing = items.drop(current + 1)
        if (existing.map { it.mediaId } == tracks.map { it.id }) return
        val byId = existing.groupBy { it.mediaId }.mapValues { ArrayDeque(it.value) }
        val merged = tracks.map { track ->
            byId[track.id]?.removeFirstOrNull() ?: track.toMediaItem()
        }
        if (current + 1 < player.mediaItemCount) {
            player.removeMediaItems(current + 1, player.mediaItemCount)
        }
        player.addMediaItems(merged)
    }

    override fun skipNext() = player.seekToNextMediaItem()
    override fun skipPrevious() = player.seekToPreviousMediaItem()

    override fun setPlaying(playing: Boolean) {
        if (player.playWhenReady == playing) return
        player.playWhenReady = playing
        if (playing && player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun setVolume(volume: Float) {
        if (!PlayerPreferences.volumeNormalization) player.volume = volume
    }

    override fun pauseAndClear() {
        player.playWhenReady = false
        player.clearMediaItems()
    }
}
