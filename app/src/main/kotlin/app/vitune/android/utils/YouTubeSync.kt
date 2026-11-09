package app.vitune.android.utils

import android.content.Context
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vitune.android.Database
import app.vitune.android.inTransaction
import app.vitune.android.models.Playlist
import app.vitune.android.models.SongPlaylistMap
import app.vitune.android.preferences.AccountPreferences
import app.vitune.core.ui.utils.SongBundleAccessor
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.NavigationEndpoint
import app.vitune.providers.innertube.models.Thumbnail
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

object YouTubeSync {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    // Cached home feed songs (account-personalized quick picks)
    private val _homeFeedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val homeFeedSongs: StateFlow<List<SongItem>> = _homeFeedSongs.asStateFlow()

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

    /** Ensure both Metrolist YouTube client and Innertube have auth credentials from preferences. */
    fun ensureAuth() {
        if (AccountPreferences.innerTubeCookie.isNotBlank()) {
            YouTube.cookie = AccountPreferences.innerTubeCookie
            Innertube.cookie = AccountPreferences.innerTubeCookie
        }
        if (AccountPreferences.visitorData.isNotBlank()) {
            YouTube.visitorData = AccountPreferences.visitorData
            Innertube.visitorData = AccountPreferences.visitorData
        }
        if (AccountPreferences.dataSyncId.isNotBlank()) {
            YouTube.dataSyncId = AccountPreferences.dataSyncId
            Innertube.dataSyncId = AccountPreferences.dataSyncId
        }
        YouTube.authUser = AccountPreferences.innerTubeAuthUser
        Innertube.authUser = AccountPreferences.innerTubeAuthUser
        YouTube.useLoginForBrowse = AccountPreferences.useLoginForBrowse
    }

    private val syncScope = CoroutineScope(Dispatchers.IO)

    /**
     * Push a like/unlike action to YouTube Music immediately in background.
     * Called when the user taps the heart button in player, menu, or notifications.
     * [liked] = true means the song is now liked; false means unliked.
     */
    fun pushLikeToYouTube(videoId: String, liked: Boolean) {
        if (!AccountPreferences.isLoggedIn) return
        syncScope.launch {
            ensureAuth()
            runCatching {
                YouTube.likeVideo(videoId, liked)
            }.onFailure { e ->
                Timber.w(e, "Failed to push like/unlike to YouTube: $videoId liked=$liked")
            }
        }
    }

    /**
     * Fetch personalized home feed from the account and convert to Innertube.RelatedPage.
     * Contains user's Quick Picks, Listen Again, Mixes, Recommended Albums, Similar Artists.
     */
    suspend fun fetchAccountHomeFeed(): Innertube.RelatedPage? = withContext(Dispatchers.IO) {
        if (!AccountPreferences.isLoggedIn) return@withContext null
        ensureAuth()
        runCatching {
            val homePage = YouTube.home().getOrNull() ?: return@runCatching null
            val songs = mutableListOf<Innertube.SongItem>()
            val albums = mutableListOf<Innertube.AlbumItem>()
            val playlists = mutableListOf<Innertube.PlaylistItem>()
            val artists = mutableListOf<Innertube.ArtistItem>()

            homePage.sections.forEach { section ->
                section.items.forEach { ytItem ->
                    when (ytItem) {
                        is SongItem -> {
                            songs.add(
                                Innertube.SongItem(
                                    info = Innertube.Info(
                                        name = ytItem.title,
                                        endpoint = NavigationEndpoint.Endpoint.Watch(videoId = ytItem.id)
                                    ),
                                    authors = ytItem.artists.map { artist ->
                                        Innertube.Info(
                                            name = artist.name,
                                            endpoint = artist.id?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
                                        )
                                    },
                                    album = ytItem.album?.let { alb ->
                                        Innertube.Info(
                                            name = alb.name,
                                            endpoint = NavigationEndpoint.Endpoint.Browse(browseId = alb.id)
                                        )
                                    },
                                    durationText = ytItem.duration?.let { DateUtils.formatElapsedTime(it.toLong()) },
                                    explicit = ytItem.explicit,
                                    thumbnail = Thumbnail(url = ytItem.thumbnail, height = null, width = null)
                                )
                            )
                        }
                        is com.metrolist.innertube.models.AlbumItem -> {
                            albums.add(
                                Innertube.AlbumItem(
                                    info = Innertube.Info(
                                        name = ytItem.title,
                                        endpoint = NavigationEndpoint.Endpoint.Browse(browseId = ytItem.browseId)
                                    ),
                                    authors = ytItem.artists?.map { artist ->
                                        Innertube.Info(
                                            name = artist.name,
                                            endpoint = artist.id?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
                                        )
                                    },
                                    year = ytItem.year?.toString(),
                                    thumbnail = Thumbnail(url = ytItem.thumbnail, height = null, width = null)
                                )
                            )
                        }
                        is com.metrolist.innertube.models.PlaylistItem -> {
                            val browseId = if (ytItem.id.startsWith("VL")) ytItem.id else "VL${ytItem.id}"
                            playlists.add(
                                Innertube.PlaylistItem(
                                    info = Innertube.Info(
                                        name = ytItem.title,
                                        endpoint = NavigationEndpoint.Endpoint.Browse(browseId = browseId)
                                    ),
                                    channel = ytItem.author?.let { artist ->
                                        Innertube.Info(
                                            name = artist.name,
                                            endpoint = artist.id?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
                                        )
                                    },
                                    songCount = null,
                                    thumbnail = ytItem.thumbnail?.let { Thumbnail(url = it, height = null, width = null) }
                                )
                            )
                        }
                        is com.metrolist.innertube.models.ArtistItem -> {
                            artists.add(
                                Innertube.ArtistItem(
                                    info = Innertube.Info(
                                        name = ytItem.title,
                                        endpoint = NavigationEndpoint.Endpoint.Browse(browseId = ytItem.id)
                                    ),
                                    subscribersCountText = null,
                                    thumbnail = ytItem.thumbnail?.let { Thumbnail(url = it, height = null, width = null) }
                                )
                            )
                        }
                        else -> Unit
                    }
                }
            }

            Innertube.RelatedPage(
                songs = songs.distinctBy { it.key }.take(40).ifEmpty { null },
                albums = albums.distinctBy { it.key }.take(20).ifEmpty { null },
                playlists = playlists.distinctBy { it.key }.take(20).ifEmpty { null },
                artists = artists.distinctBy { it.key }.take(20).ifEmpty { null }
            )
        }.getOrNull()
    }

    /**
     * Fetch personalized home feed songs from the account.
     * Returns the flat list of SongItem from all home sections (quick picks, etc.)
     */
    suspend fun fetchHomeFeedSongs(): List<SongItem> = withContext(Dispatchers.IO) {
        if (!AccountPreferences.isLoggedIn) return@withContext emptyList()
        ensureAuth()
        runCatching {
            val homePage = YouTube.home().getOrNull() ?: return@runCatching emptyList()
            homePage.sections
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .take(40)
        }.getOrElse { e ->
            Timber.w(e, "Failed to fetch home feed songs")
            emptyList()
        }.also { songs ->
            _homeFeedSongs.value = songs
        }
    }

    /**
     * Full sync: playlists + liked songs pull from YTM account.
     * Also pushes local likes to account and pre-fetches home feed.
     */
    suspend fun syncAll(context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!AccountPreferences.isLoggedIn) {
            return@withContext Result.failure(IllegalStateException("Not logged in to YouTube Music"))
        }
        if (_isSyncing.value) {
            return@withContext Result.success("Sync already in progress")
        }
        _isSyncing.value = true
        _syncStatus.value = "Fetching library..."

        ensureAuth()

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

            // 2. Pull liked songs from YTM account → mark them liked locally
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

            // 3. Push any locally-liked songs back to YTM that aren't yet on the account
            try {
                _syncStatus.value = "Pushing local likes..."
                val localLiked = Database.favoritesByLikedAtDesc().first()
                val remoteIds = runCatching {
                    YouTube.playlist("LM").completed().getOrNull()?.songs.orEmpty()
                        .map { it.id }.toSet()
                }.getOrElse { emptySet() }
                localLiked.filter { it.id !in remoteIds }.forEach { song ->
                    runCatching { YouTube.likeVideo(song.id, true) }
                        .onFailure { e -> Timber.w(e, "Failed to push like for ${song.id}") }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to push local likes")
            }

            // 4. Fetch personalized home feed for quick picks
            try {
                _syncStatus.value = "Fetching home feed..."
                fetchHomeFeedSongs()
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch home feed")
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

    /** Schedule periodic background sync (every 6 hours) using WorkManager. */
    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<YTMSyncWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            YTMSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Cancel periodic background sync on logout. */
    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(YTMSyncWorker.WORK_NAME)
    }
}

/** WorkManager worker that runs YouTubeSync.syncAll() periodically. */
class YTMSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "ytm_account_sync"
    }

    override suspend fun doWork(): Result {
        if (!AccountPreferences.isLoggedIn) return Result.success()
        return YouTubeSync.syncAll(applicationContext)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
    }
}
