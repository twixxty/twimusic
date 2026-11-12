package app.vitune.android.ui.screens.home

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.preferences.AccountPreferences
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.query
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.ShimmerHost
import app.vitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vitune.android.ui.components.themed.TextPlaceholder
import app.vitune.android.ui.items.AlbumItem
import app.vitune.android.ui.items.AlbumItemPlaceholder
import app.vitune.android.ui.items.ArtistItem
import app.vitune.android.ui.items.ArtistItemPlaceholder
import app.vitune.android.ui.items.PlaylistItem
import app.vitune.android.ui.items.PlaylistItemPlaceholder
import app.vitune.android.ui.items.SongItem
import app.vitune.android.ui.items.SongItemPlaceholder
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.YouTubeSync
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.center
import app.vitune.android.utils.color
import app.vitune.android.utils.forcePlay
import app.vitune.android.utils.playingSong
import app.vitune.android.utils.rememberSnapLayoutInfo
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.compose.persist.persist
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isLandscape
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.NavigationEndpoint
import app.vitune.providers.innertube.models.Thumbnail
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.relatedPage
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

sealed class DynamicHomeItem {
    abstract val key: String
    data class Song(val song: Innertube.SongItem) : DynamicHomeItem() {
        override val key: String get() = "song_${song.key}"
    }
    data class Album(val album: Innertube.AlbumItem) : DynamicHomeItem() {
        override val key: String get() = "album_${album.key}"
    }
    data class Playlist(val playlist: Innertube.PlaylistItem) : DynamicHomeItem() {
        override val key: String get() = "playlist_${playlist.key}"
    }
    data class Artist(val artist: Innertube.ArtistItem) : DynamicHomeItem() {
        override val key: String get() = "artist_${artist.key}"
    }
}

data class DynamicHomeSection(
    val id: String,
    val title: String,
    val label: String? = null,
    val items: List<DynamicHomeItem>
)

private fun relatedPageToSections(
    related: Innertube.RelatedPage,
    quickPicksTitle: String,
    relatedAlbumsTitle: String,
    similarArtistsTitle: String,
    recommendedPlaylistsTitle: String
): List<DynamicHomeSection> {
    val list = mutableListOf<DynamicHomeSection>()
    val songs = related.songs
    if (!songs.isNullOrEmpty()) {
        list.add(
            DynamicHomeSection(
                id = "related_songs",
                title = quickPicksTitle,
                items = songs.map { DynamicHomeItem.Song(it) }
            )
        )
    }
    val albums = related.albums
    if (!albums.isNullOrEmpty()) {
        list.add(
            DynamicHomeSection(
                id = "related_albums",
                title = relatedAlbumsTitle,
                items = albums.map { DynamicHomeItem.Album(it) }
            )
        )
    }
    val artists = related.artists
    if (!artists.isNullOrEmpty()) {
        list.add(
            DynamicHomeSection(
                id = "related_artists",
                title = similarArtistsTitle,
                items = artists.map { DynamicHomeItem.Artist(it) }
            )
        )
    }
    val playlists = related.playlists
    if (!playlists.isNullOrEmpty()) {
        list.add(
            DynamicHomeSection(
                id = "related_playlists",
                title = recommendedPlaylistsTitle,
                items = playlists.map { DynamicHomeItem.Playlist(it) }
            )
        )
    }
    return list
}

private suspend fun fetchYouTubeMusicHomeSections(): List<DynamicHomeSection>? = withContext(Dispatchers.IO) {
    runCatching {
        YouTubeSync.ensureAuth()
        val homeResult = YouTube.home().getOrNull() ?: return@runCatching null
        if (homeResult.sections.isEmpty()) return@runCatching null

        homeResult.sections.mapIndexedNotNull { index, sec ->
            val items = sec.items.mapNotNull { ytItem ->
                when (ytItem) {
                    is com.metrolist.innertube.models.SongItem -> DynamicHomeItem.Song(
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
                    is com.metrolist.innertube.models.AlbumItem -> DynamicHomeItem.Album(
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
                    is com.metrolist.innertube.models.PlaylistItem -> {
                        val browseId = if (ytItem.id.startsWith("VL")) ytItem.id else "VL${ytItem.id}"
                        DynamicHomeItem.Playlist(
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
                    is com.metrolist.innertube.models.ArtistItem -> DynamicHomeItem.Artist(
                        Innertube.ArtistItem(
                            info = Innertube.Info(
                                name = ytItem.title,
                                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = ytItem.id)
                            ),
                            subscribersCountText = null,
                            thumbnail = ytItem.thumbnail?.let { Thumbnail(url = it, height = null, width = null) }
                        )
                    )
                    else -> null
                }
            }
            if (items.isEmpty()) null
            else DynamicHomeSection(
                id = "yt_sec_${index}_${sec.title.hashCode()}",
                title = sec.title,
                label = sec.label,
                items = items
            )
        }
    }.getOrNull()
}

@Route
@Composable
fun QuickPicks(
    onAlbumClick: (Innertube.AlbumItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    onSearchClick: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var trending by persist<Song?>("home/trending")
    var dynamicSectionsResult by persist<Result<List<DynamicHomeSection>?>?>(tag = "home/dynamicSectionsResult")

    val quickPicksTitle = stringResource(R.string.quick_picks)
    val relatedAlbumsTitle = stringResource(R.string.related_albums)
    val similarArtistsTitle = stringResource(R.string.similar_artists)
    val recommendedPlaylistsTitle = stringResource(R.string.recommended_playlists)

    LaunchedEffect(dynamicSectionsResult, DataPreferences.shouldCacheQuickPicks) {
        if (DataPreferences.shouldCacheQuickPicks) {
            val sections = dynamicSectionsResult?.getOrNull()
            if (!sections.isNullOrEmpty()) {
                val allSongs = sections.flatMap { sec -> sec.items.filterIsInstance<DynamicHomeItem.Song>().map { it.song } }
                val allAlbums = sections.flatMap { sec -> sec.items.filterIsInstance<DynamicHomeItem.Album>().map { it.album } }
                val allArtists = sections.flatMap { sec -> sec.items.filterIsInstance<DynamicHomeItem.Artist>().map { it.artist } }
                val allPlaylists = sections.flatMap { sec -> sec.items.filterIsInstance<DynamicHomeItem.Playlist>().map { it.playlist } }
                DataPreferences.cachedQuickPicks = Innertube.RelatedPage(
                    songs = allSongs.ifEmpty { null },
                    albums = allAlbums.ifEmpty { null },
                    artists = allArtists.ifEmpty { null },
                    playlists = allPlaylists.ifEmpty { null }
                )
            }
        } else {
            DataPreferences.cachedQuickPicks = Innertube.RelatedPage()
        }
    }

    LaunchedEffect(DataPreferences.quickPicksSource, AccountPreferences.isLoggedIn) {
        if (
            DataPreferences.shouldCacheQuickPicks && !DataPreferences.cachedQuickPicks.let {
                it.albums.isNullOrEmpty() &&
                    it.artists.isNullOrEmpty() &&
                    it.playlists.isNullOrEmpty() &&
                    it.songs.isNullOrEmpty()
            } && dynamicSectionsResult == null
        ) {
            dynamicSectionsResult = Result.success(
                relatedPageToSections(
                    related = DataPreferences.cachedQuickPicks,
                    quickPicksTitle = quickPicksTitle,
                    relatedAlbumsTitle = relatedAlbumsTitle,
                    similarArtistsTitle = similarArtistsTitle,
                    recommendedPlaylistsTitle = recommendedPlaylistsTitle
                )
            )
        }

        if (AccountPreferences.isLoggedIn) {
            val sections = fetchYouTubeMusicHomeSections()
            if (!sections.isNullOrEmpty()) {
                dynamicSectionsResult = Result.success(sections)
                return@LaunchedEffect
            }
        }

        suspend fun handleSong(song: Song?) {
            if (dynamicSectionsResult == null || trending?.id != song?.id) {
                val res = Innertube.relatedPage(
                    body = NextBody(videoId = (song?.id ?: "J7p4bzqLvCw"))
                )
                dynamicSectionsResult = res?.map { related ->
                    related?.let {
                        relatedPageToSections(
                            related = it,
                            quickPicksTitle = quickPicksTitle,
                            relatedAlbumsTitle = relatedAlbumsTitle,
                            similarArtistsTitle = similarArtistsTitle,
                            recommendedPlaylistsTitle = recommendedPlaylistsTitle
                        )
                    }
                }
            }
            trending = song
        }

        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending ->
                Database
                    .trending()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()) }

            DataPreferences.QuickPicksSource.LastInteraction ->
                Database
                    .events()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()?.song) }
        }
    }

    val scrollState = rememberScrollState()
    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val [currentMediaId, playing] = playingSong(binder)

    BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor =
            if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.75f

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Vertical)
                        .asPaddingValues()
                )
        ) {
            Header(
                title = stringResource(R.string.quick_picks),
                modifier = Modifier.padding(endPaddingValues)
            )

            dynamicSectionsResult?.getOrNull()?.let { sections ->
                sections.forEachIndexed { secIndex, section ->
                    val isSongsOnly = section.items.isNotEmpty() && section.items.all { it is DynamicHomeItem.Song }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = if (secIndex == 0) 8.dp else 24.dp, bottom = 8.dp)
                            .padding(endPaddingValues)
                    ) {
                        if (!section.label.isNullOrBlank()) {
                            BasicText(
                                text = section.label.uppercase(),
                                style = typography.xxs.semiBold.secondary.color(colorPalette.textSecondary)
                            )
                        }
                        BasicText(
                            text = section.title,
                            style = typography.m.semiBold.color(colorPalette.text)
                        )
                    }

                    if (isSongsOnly) {
                        val songGridState = remember(section.id) { LazyGridState() }
                        val snapLayoutInfo = rememberSnapLayoutInfo(
                            lazyGridState = songGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                (layoutSize * quickPicksLazyGridItemWidthFactor / 2f - itemSize / 2f)
                            }
                        )

                        LazyHorizontalGrid(
                            state = songGridState,
                            rows = GridCells.Fixed(4),
                            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfo),
                            contentPadding = endPaddingValues,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 4)
                        ) {
                            if (secIndex == 0 && trending != null) {
                                val song = trending!!
                                item(key = "trending_${song.id}") {
                                    SongItem(
                                        modifier = Modifier
                                            .combinedClickable(
                                                onLongClick = {
                                                    menuState.display {
                                                        NonQueuedMediaItemMenu(
                                                            onDismiss = menuState::hide,
                                                            mediaItem = song.asMediaItem,
                                                            onRemoveFromQuickPicks = {
                                                                query { Database.clearEventsFor(song.id) }
                                                            }
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    val mediaItem = song.asMediaItem
                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlay(mediaItem)
                                                    binder?.setupRadio(
                                                        NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                                    )
                                                }
                                            )
                                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                            .width(itemInHorizontalGridWidth),
                                        song = song,
                                        thumbnailSize = Dimensions.thumbnails.song,
                                        trailingContent = {
                                            Image(
                                                painter = painterResource(R.drawable.star),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colorPalette.accent),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        showDuration = false,
                                        isPlaying = playing && currentMediaId == song.id
                                    )
                                }
                            }

                            items(
                                items = section.items.filterIsInstance<DynamicHomeItem.Song>(),
                                key = { "${section.id}_${it.key}" }
                            ) { item ->
                                val song = item.song
                                SongItem(
                                    song = song,
                                    thumbnailSize = Dimensions.thumbnails.song,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onLongClick = {
                                                menuState.display {
                                                    NonQueuedMediaItemMenu(
                                                        onDismiss = menuState::hide,
                                                        mediaItem = song.asMediaItem
                                                    )
                                                }
                                            },
                                            onClick = {
                                                val mediaItem = song.asMediaItem
                                                binder?.stopRadio()
                                                binder?.player?.forcePlay(mediaItem)
                                                binder?.setupRadio(
                                                    NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                                )
                                            }
                                        )
                                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                        .width(itemInHorizontalGridWidth),
                                    showDuration = false,
                                    isPlaying = playing && currentMediaId == song.key
                                )
                            }
                        }
                    } else {
                        LazyRow(contentPadding = endPaddingValues) {
                            items(
                                items = section.items,
                                key = { "${section.id}_${it.key}" }
                            ) { item ->
                                when (item) {
                                    is DynamicHomeItem.Album -> {
                                        AlbumItem(
                                            album = item.album,
                                            thumbnailSize = Dimensions.thumbnails.album,
                                            alternative = true,
                                            modifier = Modifier.clickable { onAlbumClick(item.album) }
                                        )
                                    }
                                    is DynamicHomeItem.Playlist -> {
                                        PlaylistItem(
                                            playlist = item.playlist,
                                            thumbnailSize = Dimensions.thumbnails.playlist,
                                            alternative = true,
                                            modifier = Modifier.clickable { onPlaylistClick(item.playlist) }
                                        )
                                    }
                                    is DynamicHomeItem.Artist -> {
                                        ArtistItem(
                                            artist = item.artist,
                                            thumbnailSize = Dimensions.thumbnails.artist,
                                            alternative = true,
                                            modifier = Modifier.clickable { onArtistClick(item.artist) }
                                        )
                                    }
                                    is DynamicHomeItem.Song -> {
                                        SongItem(
                                            song = item.song,
                                            thumbnailSize = Dimensions.thumbnails.song,
                                            modifier = Modifier.clickable {
                                                val mediaItem = item.song.asMediaItem
                                                binder?.stopRadio()
                                                binder?.player?.forcePlay(mediaItem)
                                                binder?.setupRadio(
                                                    NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Unit
            } ?: dynamicSectionsResult?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
