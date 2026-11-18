package app.vitune.android.service

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.discord.DiscordActivityBuilder
import app.vitune.android.discord.DiscordDefaults
import app.vitune.android.discord.DiscordHttp
import app.vitune.android.discord.DiscordRpcManager
import app.vitune.android.discord.PlaybackNetworkStateStore
import app.vitune.android.discord.PresenceStatus
import app.vitune.android.discord.discordPlaybackTimestamps
import app.vitune.android.discord.resolveDiscordButtonLabel
import app.vitune.android.preferences.DiscordPreferences
import app.vitune.android.utils.shouldBePlaying
import app.vitune.core.ui.utils.songBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors the player's current song to Discord Rich Presence. Main thread only. */
internal class DiscordPresenceController(
    private val context: Context,
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) : Player.Listener {
    private var syncJob: Job? = null
    private var loopJob: Job? = null

    fun start() {
        PlaybackNetworkStateStore.init(context)
        player.addListener(this)
        scope.launch {
            snapshotFlow { DiscordPreferences.enabled }.collect { enabled ->
                if (enabled) {
                    DiscordRpcManager.init(DiscordHttp.client)
                    schedule()
                } else {
                    loopJob?.cancel()
                    DiscordRpcManager.clear()
                }
            }
        }
        scope.launch { DiscordRpcManager.settingsRevision.collect { schedule() } }
    }

    fun release() {
        player.removeListener(this)
        syncJob?.cancel()
        loopJob?.cancel()
        DiscordRpcManager.clear()
    }

    /** Cheap to call repeatedly; updates are coalesced. */
    fun schedule() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(300)
            sync()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = schedule()
    override fun onPlaybackStateChanged(playbackState: Int) = schedule()
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = schedule()
    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) =
        schedule()

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) = schedule()

    private suspend fun sync() {
        if (!DiscordPreferences.enabled || !player.shouldBePlaying) {
            loopJob?.cancel()
            DiscordRpcManager.clear()
            return
        }
        val item = player.currentMediaItem
        if (item == null || item.isLocal) {
            DiscordRpcManager.clear()
            return
        }

        // Keep the activity fresh; Discord drops presence that stops updating.
        if (loopJob?.isActive != true) {
            loopJob = scope.launch {
                while (true) {
                    delay(30_000)
                    schedule()
                }
            }
        }

        val metadata = item.mediaMetadata
        val extras = metadata.extras?.songBundle
        val artistName = metadata.artist?.toString().orEmpty()
            .ifEmpty { context.getString(R.string.unknown) }
        val title = metadata.title?.toString().orEmpty()
        val artistId = extras?.artistIds?.firstOrNull().orEmpty()
        val artistIcon = if (DiscordPreferences.showArtistIcon && artistId.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching { Database.artist(artistId).first()?.thumbnailUrl }.getOrNull()
            }
        } else {
            null
        }
        // The player may have moved on while the database was queried.
        if (player.currentMediaItem?.mediaId != item.mediaId || !player.shouldBePlaying) return

        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val timestamps = discordPlaybackTimestamps(
            now = System.currentTimeMillis(),
            positionMs = player.currentPosition,
            durationMs = duration,
            speed = player.playbackParameters.speed,
            playing = true
        )

        val activity = DiscordActivityBuilder.build(
            songId = item.mediaId,
            thumbnail = metadata.artworkUri?.toString(),
            artistId = artistId,
            albumId = extras?.albumId.orEmpty(),
            artistName = artistName,
            albumName = metadata.albumTitle?.toString(),
            artistThumbnail = artistIcon,
            songTitle = title,
            startTimestamp = timestamps.start,
            endTimestamp = timestamps.end,
            advancedMode = DiscordPreferences.advancedMode,
            activityType = DiscordPreferences.activityType.toIntOrNull() ?: 2,
            activityName = DiscordPreferences.activityName,
            stateTemplate = DiscordPreferences.stateTemplate,
            detailsTemplate = DiscordPreferences.detailsTemplate,
            btn1Enabled = DiscordPreferences.button1Enabled,
            btn1Label = resolveDiscordButtonLabel(
                DiscordPreferences.button1Text,
                DiscordDefaults.BUTTON1_LABEL,
                context.getString(R.string.discord_button1_text_default)
            ),
            btn1Url = DiscordPreferences.button1Url,
            btn2Enabled = DiscordPreferences.button2Enabled,
            btn2Label = resolveDiscordButtonLabel(
                DiscordPreferences.button2Text,
                DiscordDefaults.BUTTON2_LABEL,
                context.getString(R.string.discord_button2_text_default)
            ),
            btn2Url = DiscordPreferences.button2Url,
            basicBtn1Label = context.getString(R.string.discord_button1_text_default),
            basicBtn2Label = context.getString(R.string.discord_button2_text_default),
            unknownAlbum = context.getString(R.string.discord_unknown_album)
        )

        DiscordRpcManager.setActivity(
            activity = activity,
            songId = item.mediaId,
            isPlaying = true,
            status = PresenceStatus.Online
        )
    }
}
