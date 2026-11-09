package app.vitune.android.utils

import android.content.Context
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.vitune.android.Database
import app.vitune.android.inTransaction
import app.vitune.android.models.Playlist
import app.vitune.android.models.SongPlaylistMap
import app.vitune.android.preferences.AccountPreferences
import app.vitune.core.ui.utils.SongBundleAccessor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

object YouTubeSync {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val SongItem.asMediaItem: MediaItem
        get() = MediaItem.Builder()
            .setMediaId(id)
            .setUri(id)
            .setCustomCacheKey(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artists.joinToString(", ") { it.name })
                    .setAlbumTitle(album?.name)
                    .setArtworkUri(thumbnail.toUri())
                    .setExtras(
                        SongBundleAccessor.bundle {
                            albumId = album?.id
                            durationText = duration?.let { DateUtils.formatElapsedTime(it.toLong()) }
                            artistNames = artists.map { it.name }
                            artistIds = artists.mapNotNull { it.id }
                            explicit = this@asMediaItem.explicit
                        }
                    )
                    .build()
            )
            .build()

    suspend fun syncAll(context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!AccountPreferences.isLoggedIn) {
            return@withContext Result.failure(IllegalStateException("Not logged in to YouTube Music"))
        }
        if (_isSyncing.value) {
            return@withContext Result.success("Sync already in progress")
        }
        _isSyncing.value = true
        _syncStatus.value = "Fetching library..."

        // Ensure YouTube instance has active auth state from preferences
        if (YouTube.cookie.isNullOrBlank()) {
            YouTube.cookie = AccountPreferences.innerTubeCookie
            YouTube.visitorData = AccountPreferences.visitorData
            YouTube.dataSyncId = AccountPreferences.dataSyncId
            YouTube.authUser = AccountPreferences.innerTubeAuthUser
        }

        try {
            var playlistCount = 0
            var songCount = 0

            // 1. Fetch user's YouTube Music playlists
            val libraryResult = YouTube.library("FEmusic_liked_playlists").completed()
                .recoverCatching { YouTube.library("FEmusic_library_playlists").completed().getOrThrow() }
                .recoverCatching { YouTube.library("FEmusic_library_corpus_playlists").completed().getOrThrow() }
                .recoverCatching { YouTube.library("FEmusic_library_landing").completed().getOrThrow() }

            val playlistItems = libraryResult.getOrNull()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            val existingPlaylists = Database.allPlaylists().first()

            playlistItems.forEachIndexed { index, playlistItem ->
                try {
                    _syncStatus.value = "Syncing playlist ${index + 1}/${playlistItems.size}..."
                    val browseId = "VL${playlistItem.id}"
                    val localPlaylist = existingPlaylists.find { it.browseId == browseId || it.name == playlistItem.title }
                        ?: run {
                            val newId = Database.insert(
                                Playlist(
                                    name = playlistItem.title,
                                    browseId = browseId,
                                    thumbnail = playlistItem.thumbnail
                                )
                            ).takeIf { it > 0 } ?: (Database.playlistByBrowseId(browseId).first()?.id ?: 0L)
                            Playlist(id = newId, name = playlistItem.title, browseId = browseId, thumbnail = playlistItem.thumbnail)
                        }

                    val playlistPage = YouTube.playlist(playlistItem.id).completed().getOrNull()
                    val songs: List<SongItem> = playlistPage?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        inTransaction {
                            Database.clearPlaylist(localPlaylist.id)
                            val mediaItems = songs.map { it.asMediaItem }
                            mediaItems.forEach { Database.insert(it) }
                            val maps = mediaItems.mapIndexed { idx, item ->
                                SongPlaylistMap(
                                    songId = item.mediaId,
                                    playlistId = localPlaylist.id,
                                    position = idx
                                )
                            }
                            Database.insertSongPlaylistMaps(maps)
                        }
                        playlistCount++
                        songCount += songs.size
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to sync playlist: ${playlistItem.title}")
                }
            }

            // 2. Sync Liked Songs ("LM" or "VLLM")
            try {
                _syncStatus.value = "Syncing liked songs..."
                val likedResult = YouTube.playlist("LM").completed().recoverCatching {
                    YouTube.playlist("VLLM").completed().getOrThrow()
                }
                val likedSongs: List<SongItem> = likedResult.getOrNull()?.songs.orEmpty()
                if (likedSongs.isNotEmpty()) {
                    inTransaction {
                        likedSongs.forEach { songItem ->
                            val mediaItem = songItem.asMediaItem
                            Database.insert(mediaItem)
                            Database.like(songItem.id, System.currentTimeMillis())
                        }
                    }
                    songCount += likedSongs.size
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to sync liked songs")
            }

            _lastSyncTime.value = System.currentTimeMillis()
            val summary = "Synced $playlistCount playlists ($songCount songs)"
            context?.let { ctx ->
                withContext(Dispatchers.Main) {
                    ctx.toast(summary)
                }
            }
            Result.success(summary)
        } catch (e: Exception) {
            Timber.e(e, "YouTube sync failed")
            context?.let { ctx ->
                withContext(Dispatchers.Main) {
                    ctx.toast("YouTube sync failed: ${e.message}")
                }
            }
            Result.failure(e)
        } finally {
            _isSyncing.value = false
            _syncStatus.value = ""
        }
    }
}
