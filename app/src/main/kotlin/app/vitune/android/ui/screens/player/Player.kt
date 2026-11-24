package app.vitune.android.ui.screens.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import app.vitune.android.ui.components.LavaLampBackground
import app.vitune.android.ui.components.BlurredArtworkBackground
import app.vitune.android.ui.components.LiquidBackground
import app.vitune.android.utils.collectProvidedBitmapAsState
import app.vitune.android.canvas.BackgroundVideoView
import app.vitune.android.utils.FullScreenState
import androidx.core.view.WindowInsetsCompat
import app.vitune.android.canvas.Motionartwork
import app.vitune.android.canvas.MotionartworkPlaybackCache
import app.vitune.android.canvas.AppleMusicMotionartworkProvider
import app.vitune.android.canvas.ViviMusicMotionartworkProvider
import app.vitune.android.canvas.TidalMotionartworkProvider
import app.vitune.android.canvas.SpotifyCanvasProvider
import app.vitune.android.canvas.validateMotionartworkMatch
import app.vitune.android.lyrics.LyricsUtils
import app.vitune.android.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import app.vitune.android.preferences.AppearancePreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import app.vitune.core.ui.favoritesIcon
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.vitune.android.Database
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.ui.toUiMedia
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.query
import app.vitune.android.service.PlayerService
import app.vitune.android.transaction
import app.vitune.android.ui.components.BottomSheet
import app.vitune.android.ui.components.BottomSheetState
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.rememberBottomSheetState
import app.vitune.android.ui.components.themed.BaseMediaItemMenu
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.SliderDialog
import app.vitune.android.ui.components.themed.SliderDialogBody
import app.vitune.android.ui.modifiers.PinchDirection
import app.vitune.android.ui.modifiers.onSwipe
import app.vitune.android.ui.modifiers.pinchToToggle
import app.vitune.android.utils.DisposableListener
import app.vitune.android.utils.Pip
import app.vitune.android.utils.forceSeekToNext
import app.vitune.android.utils.forceSeekToPrevious
import app.vitune.android.utils.positionAndDurationState
import app.vitune.android.utils.rememberEqualizerLauncher
import app.vitune.android.utils.rememberPipHandler
import app.vitune.android.utils.seamlessPlay
import app.vitune.android.utils.bold
import app.vitune.android.utils.getAppIcon
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.shouldBePlaying
import app.vitune.android.utils.thumbnail
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.OnGlobalRoute
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.ThumbnailRoundness
import app.vitune.core.ui.collapsedPlayerProgressBar
import app.vitune.core.ui.utils.isLandscape
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import app.vitune.core.ui.utils.songBundle
import app.vitune.providers.innertube.models.NavigationEndpoint
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

private fun Context.isMobileData(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

@Composable
fun Player(
    layoutState: BottomSheetState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp
    ),
    windowInsets: WindowInsets = WindowInsets.systemBars
) = with(PlayerPreferences) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val [colorPalette, typography, thumbnailCornerSize] = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    val pipHandler = rememberPipHandler()

    PersistMapCleanup(prefix = "queue/suggestions")

    var mediaItem by remember(binder) {
        mutableStateOf(
            value = binder?.player?.currentMediaItem,
            policy = neverEqualPolicy()
        )
    }
    var shouldBePlaying by remember(binder) { mutableStateOf(binder?.player?.shouldBePlaying == true) }

    var canvasArtwork by remember(mediaItem?.mediaId) { mutableStateOf<Motionartwork?>(null) }
    var spotifyCanvasUrl by remember(mediaItem?.mediaId) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        mediaItem?.mediaId,
        PlayerPreferences.enableCanvas,
        PlayerPreferences.canvasLoadOnlyWifi
    ) {
        val item = mediaItem ?: return@LaunchedEffect
        if (!PlayerPreferences.enableCanvas) {
            canvasArtwork = null
            spotifyCanvasUrl = null
            return@LaunchedEffect
        }
        if (PlayerPreferences.canvasLoadOnlyWifi && context.isMobileData()) {
            canvasArtwork = null
            spotifyCanvasUrl = null
            return@LaunchedEffect
        }

        val currentId = item.mediaId
        val rawTitle = item.mediaMetadata.title?.toString() ?: ""
        val title = LyricsUtils.cleanTitleForSearch(rawTitle).ifBlank { rawTitle }
        val rawArtist = item.mediaMetadata.artist?.toString() ?: ""
        val artist = rawArtist.replace(Regex("(?i)\\s*-\\s*topic$"), "").trim()
        val album = item.mediaMetadata.albumTitle?.toString() ?: ""

        MotionartworkPlaybackCache.get(currentId)?.let { cached ->
            canvasArtwork = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"

            val appleArtwork = runCatching {
                (AppleMusicMotionartworkProvider.getBySongArtist(title, artist, album, storefront)
                    ?: if (storefront != "us") AppleMusicMotionartworkProvider.getBySongArtist(title, artist, album, "us") else null)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && validateMotionartworkMatch(it, title, artist, album) }
            }.getOrNull()

            val tidalArtwork = if (appleArtwork == null) {
                runCatching {
                    TidalMotionartworkProvider.getBySongArtist(title, artist, album)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && validateMotionartworkMatch(it, title, artist, album) }
                }.getOrNull()
            } else null

            val viviArtwork = if (appleArtwork == null && tidalArtwork == null) {
                runCatching {
                    ViviMusicMotionartworkProvider.getBySongArtist(title, artist, album)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && validateMotionartworkMatch(it, title, artist, album) }
                }.getOrNull()
            } else null

            val resolvedArtwork = appleArtwork ?: tidalArtwork ?: viviArtwork
            if (resolvedArtwork != null) {
                MotionartworkPlaybackCache.put(currentId, resolvedArtwork)
                withContext(Dispatchers.Main) {
                    canvasArtwork = resolvedArtwork
                }
            } else {
                val token = Spotify.getValidToken()
                if (token != null) {
                    val spTrackId = Spotify.searchTrackRest("$title $artist")
                    if (spTrackId != null) {
                        val spCanvas = SpotifyCanvasProvider.getCanvasUrl(spTrackId, token).getOrNull()
                        if (spCanvas != null) {
                            withContext(Dispatchers.Main) {
                                spotifyCanvasUrl = spCanvas
                            }
                        }
                    }
                }
            }
        }
    }

    var likedAt by remember(mediaItem) {
        mutableStateOf(
            value = null,
            policy = object : SnapshotMutationPolicy<Long?> {
                override fun equivalent(a: Long?, b: Long?): Boolean {
                    mediaItem?.mediaId?.let { videoId ->
                        query {
                            Database.like(videoId, b)
                        }
                        app.vitune.android.utils.YouTubeSync.pushLikeToYouTube(videoId, b != null)
                    }
                    return a == b
                }
            }
        )
    }

    LaunchedEffect(mediaItem) {
        mediaItem?.mediaId?.let { mediaId ->
            Database
                .likedAt(mediaId)
                .distinctUntilChanged()
                .collect { likedAt = it }
        }
    }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(newMediaItem: MediaItem?, reason: Int) {
                mediaItem = newMediaItem
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaItem = player.currentMediaItem
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                mediaItem = player.currentMediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = player.shouldBePlaying
            }
        }
    }

    val (position, duration) = binder?.player.positionAndDurationState()
    val metadata = remember(mediaItem) { mediaItem?.mediaMetadata }
    val extras = remember(metadata) { metadata?.extras?.songBundle }

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    OnGlobalRoute { if (layoutState.expanded) layoutState.collapseSoft() }

    if (mediaItem != null) BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        onDismiss = {
            binder?.let { onDismiss(it) }
            layoutState.dismissSoft()
        },
        backHandlerEnabled = !menuState.isDisplayed,
        collapsedContent = { innerModifier ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .let { modifier ->
                        if (horizontalSwipeToClose) modifier.onSwipe(
                            animateOffset = true,
                            onSwipeOut = { animationJob ->
                                binder?.let { onDismiss(it) }
                                animationJob.join()
                                layoutState.dismissSoft()
                            }
                        ) else modifier
                    }
                    .fillMaxSize()
                    .clip(shape)
                    .background(colorPalette.background1)
                    .drawBehind {
                        drawRect(
                            color = colorPalette.collapsedPlayerProgressBar,
                            topLeft = Offset.Zero,
                            size = Size(
                                width = runCatching {
                                    size.width * (position.toFloat() / duration.absoluteValue)
                                }.getOrElse { 0f },
                                height = size.height
                            )
                        )
                    }
                    .then(innerModifier)
                    .padding(horizontalBottomPaddingValues)
            ) {
                Spacer(modifier = Modifier.width(2.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(Dimensions.items.collapsedPlayerHeight)
                ) {
                    AsyncImage(
                        model = metadata?.artworkUri?.thumbnail(Dimensions.thumbnails.song.px),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .clip(thumbnailCornerSize.coerceAtMost(ThumbnailRoundness.Heavy.dp).roundedShape)
                            .background(colorPalette.background0)
                            .size(48.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(Dimensions.items.collapsedPlayerHeight)
                        .weight(1f)
                ) {
                    AnimatedContent(
                        targetState = metadata?.title?.toString().orEmpty(),
                        label = "",
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { text ->
                        BasicText(
                            text = text,
                            style = typography.xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedVisibility(visible = metadata?.artist != null) {
                        AnimatedContent(
                            targetState = metadata?.artist?.toString().orEmpty(),
                            label = "",
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { text ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                BasicText(
                                    text = text,
                                    style = typography.xs.semiBold.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                AnimatedVisibility(visible = extras?.explicit == true) {
                                    Image(
                                        painter = painterResource(R.drawable.explicit),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.text),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(Dimensions.items.collapsedPlayerHeight)
                ) {
                val isGuest = binder?.listenTogether?.isGuestPlaybackLocked == true
                if (isGuest) {
                    var isMuted by remember { mutableStateOf(binder?.player?.volume == 0f) }
                    IconButton(
                        icon = if (isMuted) R.drawable.volume_mute else R.drawable.volume_up,
                        color = colorPalette.text,
                        onClick = {
                            if (binder?.player?.volume == 0f) {
                                binder?.player?.volume = 1f
                                isMuted = false
                            } else {
                                binder?.player?.volume = 0f
                                isMuted = true
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .size(23.dp)
                    )
                } else {
                    AnimatedVisibility(visible = isShowingPrevButtonCollapsed) {
                        IconButton(
                            icon = R.drawable.play_skip_back,
                            color = colorPalette.text,
                            onClick = { binder?.player?.forceSeekToPrevious() },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                                .size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    if (shouldBePlaying) binder?.player?.pause()
                                    else {
                                        if (binder?.player?.playbackState == Player.STATE_IDLE) binder.player.prepare()
                                        binder?.player?.play()
                                    }
                                },
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .clip(CircleShape)
                    ) {
                        AnimatedPlayPauseButton(
                            playing = shouldBePlaying,
                            color = colorPalette.text,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                                .size(23.dp)
                        )
                    }

                    IconButton(
                        icon = R.drawable.play_skip_forward,
                        color = colorPalette.text,
                        onClick = { binder?.player?.forceSeekToNext() },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .size(20.dp)
                    )
                }
                }

                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    ) {
        var isShowingStatsForNerds by rememberSaveable { mutableStateOf(false) }
        var isShowingLyricsDialog by rememberSaveable { mutableStateOf(false) }
        var isLyricsFullscreen by rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = isShowingLyrics) {
            if (isLyricsFullscreen) {
                isLyricsFullscreen = false
            } else {
                isShowingLyrics = false
            }
        }

        LaunchedEffect(isShowingLyrics) {
            if (!isShowingLyrics) {
                isLyricsFullscreen = false
            }
        }

        LaunchedEffect(shouldBePlaying) {
            if (!shouldBePlaying && isLyricsFullscreen) {
                isLyricsFullscreen = false
            }
        }

        LaunchedEffect(layoutState.expanded) {
            if (!layoutState.expanded && isLyricsFullscreen) {
                isLyricsFullscreen = false
            }
        }

        LaunchedEffect(mediaItem?.mediaId) {
            isLyricsFullscreen = false
        }

        if (isShowingLyricsDialog) LyricsDialog(onDismiss = { isShowingLyricsDialog = false })

        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 64.dp + horizontalBottomPaddingValues.calculateBottomPadding(),
            expandedBound = layoutState.expandedBound
        )

        val allowCanvas = PlayerPreferences.enableCanvas
        val isMobileBlocked = PlayerPreferences.canvasLoadOnlyWifi && context.isMobileData()
        val preferCover = PlayerPreferences.canvasStyle == PlayerPreferences.CanvasStyle.Cover

        val artworkSquareVideo = canvasArtwork?.animated ?: canvasArtwork?.videoUrl
        val artworkTallVideo = canvasArtwork?.animatedTall

        val canvasVideoUrl = if (isMobileBlocked || !allowCanvas) null else {
            if (preferCover) {
                artworkSquareVideo ?: artworkTallVideo ?: spotifyCanvasUrl
            } else {
                artworkTallVideo ?: artworkSquareVideo ?: spotifyCanvasUrl
            }
        }

        val isPlayingState = binder?.player?.isPlaying == true
        val isCanvasActive = !canvasVideoUrl.isNullOrBlank()
        var isVideoReady by remember(canvasVideoUrl) { mutableStateOf(false) }

        val shouldShowInCover = preferCover || (!artworkSquareVideo.isNullOrBlank() && artworkTallVideo.isNullOrBlank() && canvasVideoUrl == artworkSquareVideo)

        var isSquareCanvas by remember(canvasVideoUrl, shouldShowInCover) {
            mutableStateOf(shouldShowInCover)
        }

        val shouldHideStatusBar = PlayerPreferences.hideStatusBarOnCanvas && isCanvasActive && !isSquareCanvas && isVideoReady && !isShowingLyrics
        FullScreenState(
            shown = !shouldHideStatusBar,
            type = WindowInsetsCompat.Type.statusBars()
        )

        val containerModifier = Modifier
            .padding(
                windowInsets
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
            .let {
                if (isLandscape) it
                else it.padding(bottom = playerBottomSheetState.collapsedBound)
            }

        val canvasContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            val videoUrl = canvasVideoUrl
            val landscape = isLandscape
            if (!videoUrl.isNullOrBlank()) {
                Box(
                    modifier = innerModifier
                        .clickable(
                            enabled = !isShowingLyrics && isVideoReady,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (isShowingLyrics) isLyricsFullscreen = false
                            isShowingLyrics = !isShowingLyrics
                        }
                ) {
                    BackgroundVideoView(
                        videoUrl = videoUrl,
                        isPlaying = isPlayingState && !isShowingLyrics && !layoutState.collapsed,
                        onVideoReady = { isVideoReady = it },
                        onVideoSizeChanged = { width, height ->
                            if (width > 0 && height > 0) {
                                isSquareCanvas = (width.toFloat() / height.toFloat() in 0.82f..1.22f)
                            }
                        },
                        onError = {
                            isVideoReady = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val thumbnailContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            Pip(
                numerator = 1,
                denominator = 1,
                modifier = innerModifier
            ) {
                Thumbnail(
                    isShowingLyrics = isShowingLyrics,
                    onShowLyrics = { isShowingLyrics = it },
                    isShowingStatsForNerds = isShowingStatsForNerds,
                    onShowStatsForNerds = { isShowingStatsForNerds = it },
                    onOpenDialog = { isShowingLyricsDialog = true },
                    likedAt = likedAt,
                    setLikedAt = { likedAt = it },
                    squareCanvasUrl = if (isCanvasActive && isSquareCanvas) canvasVideoUrl else null,
                    isPlaying = isPlayingState && !isShowingLyrics && !layoutState.collapsed,
                    modifier = Modifier
                        .nestedScroll(layoutState.preUpPostDownNestedScrollConnection)
                        .pinchToToggle(
                            key = isShowingLyricsDialog,
                            direction = PinchDirection.Out,
                            threshold = 1.05f,
                            onPinch = {
                                if (isShowingLyrics) isShowingLyricsDialog = true
                            }
                        )
                        .pinchToToggle(
                            key = isShowingLyricsDialog,
                            direction = PinchDirection.In,
                            threshold = .95f,
                            onPinch = {
                                pipHandler.enterPictureInPictureMode()
                            }
                        )
                )
            }
        }

        val sampleBitmap = binder.collectProvidedBitmapAsState(mediaItem?.mediaId ?: Unit)

        val controlsContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            Controls(
                media = mediaItem?.toUiMedia(duration),
                binder = binder,
                likedAt = likedAt,
                setLikedAt = { likedAt = it },
                shouldBePlaying = shouldBePlaying,
                position = position,
                isShowingLyrics = isShowingLyrics,
                isLyricsFullscreen = isLyricsFullscreen,
                onToggleLyrics = {
                    if (isShowingLyrics) isLyricsFullscreen = false
                    isShowingLyrics = !isShowingLyrics
                },
                modifier = innerModifier
            )
        }

        val lyricsContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            val currentMedia = mediaItem
            if (currentMedia != null) {
                Lyrics(
                    mediaId = currentMedia.mediaId,
                    isDisplayed = true,
                    onDismiss = {
                        isLyricsFullscreen = false
                        isShowingLyrics = false
                    },
                    ensureSongInserted = { Database.insert(currentMedia) },
                    mediaMetadataProvider = { currentMedia.mediaMetadata },
                    durationProvider = { duration },
                    onOpenDialog = { isShowingLyricsDialog = true },
                    modifier = innerModifier,
                    shouldShowSynchronizedLyrics = isShowingSynchronizedLyrics,
                    setShouldShowSynchronizedLyrics = { isShowingSynchronizedLyrics = it },
                    showControls = true,
                    isFullscreen = isLyricsFullscreen,
                    onToggleFullscreen = { isLyricsFullscreen = !isLyricsFullscreen },
                    onSetFullscreen = { isLyricsFullscreen = it }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            val darkOverlayAlpha by animateFloatAsState(
                targetValue = if (isShowingLyrics) 0.55f else 0.35f,
                label = "coverArtOverlayAlpha"
            )

            when (playerBackgroundStyle) {
                PlayerPreferences.PlayerBackgroundStyle.Liquid -> {
                    LiquidBackground(
                        bitmap = sampleBitmap,
                        isPlaying = isPlayingState,
                        overlayAlpha = { darkOverlayAlpha },
                        fallbackColor = colorPalette.background0,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PlayerPreferences.PlayerBackgroundStyle.Blur -> {
                    BlurredArtworkBackground(
                        bitmap = sampleBitmap,
                        overlayAlpha = { darkOverlayAlpha },
                        fallbackColor = colorPalette.background0,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PlayerPreferences.PlayerBackgroundStyle.LavaLamp -> {
                    LavaLampBackground(
                        bitmap = sampleBitmap,
                        overlayAlpha = { darkOverlayAlpha },
                        fallbackColor = colorPalette.background0,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PlayerPreferences.PlayerBackgroundStyle.Gradient -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.5f to colorPalette.background1,
                                    1f to colorPalette.background0
                                )
                            )
                    )
                }
                PlayerPreferences.PlayerBackgroundStyle.Solid -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorPalette.background0)
                    )
                }
            }

            if (!isLandscape && isCanvasActive && !isSquareCanvas) {
                val canvasAlpha by animateFloatAsState(
                    targetValue = if (!isShowingLyrics) 1f else 0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerCanvasAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = canvasAlpha }
                ) {
                    canvasContent(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.82f)
                            .align(Alignment.TopCenter)
                    )
                }

                // Fading gradient background behind controls to protect contrast and seamlessly blend canvas bottom
                val gradientAlpha by animateFloatAsState(
                    targetValue = if (!isShowingLyrics && !isLyricsFullscreen && isVideoReady) 1f else 0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerCanvasGradientAlpha"
                )
                if (gradientAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = gradientAlpha }
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.25f to colorPalette.background0.copy(alpha = 0.50f),
                                    0.55f to colorPalette.background0.copy(alpha = 0.85f),
                                    0.80f to colorPalette.background0,
                                    1.0f to colorPalette.background0
                                )
                            )
                    )
                }
            }

            if (isLandscape) BoxWithConstraints(
                modifier = containerModifier.fillMaxSize()
            ) {
                val totalWidth = maxWidth
                val landscapeTopPadding by animateDpAsState(
                    targetValue = if (isShowingLyrics && isLyricsFullscreen) 12.dp else 24.dp,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "playerLandscapeTopPadding"
                )
                val maxCoverArtHeight = (maxHeight - landscapeTopPadding - 16.dp).coerceAtLeast(0.dp)
                val coverArtTargetSize = remember(totalWidth, maxCoverArtHeight) {
                    val sizeFromWidth = (totalWidth * (0.66f / 1.66f)) - 32.dp
                    if (maxCoverArtHeight > 0.dp && sizeFromWidth > maxCoverArtHeight) maxCoverArtHeight else sizeFromWidth
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = landscapeTopPadding)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .padding(bottom = if (isShowingLyrics && isLyricsFullscreen) 0.dp else 16.dp)
                    ) {
                        val artworkLandscapeAlpha by animateFloatAsState(
                            targetValue = if (!isShowingLyrics) 1f else 0f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "artworkLandscapeAlpha"
                        )
                        val lyricsLandscapeAlpha by animateFloatAsState(
                            targetValue = if (isShowingLyrics) 1f else 0f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "lyricsLandscapeAlpha"
                        )

                        if (isCanvasActive && !isSquareCanvas && isVideoReady) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = artworkLandscapeAlpha }
                            ) {
                                canvasContent(Modifier.fillMaxSize())
                            }
                        } else {
                            Box(
                                modifier = Modifier.graphicsLayer { alpha = artworkLandscapeAlpha }
                            ) {
                                thumbnailContent(Modifier.size(coverArtTargetSize))
                            }
                        }

                        if (lyricsLandscapeAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = lyricsLandscapeAlpha }
                            ) {
                                lyricsContent(Modifier.fillMaxSize().padding(horizontal = 16.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isLyricsFullscreen,
                        enter = expandHorizontally(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                                fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)),
                        exit = shrinkHorizontally(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                                fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = playerBottomSheetState.collapsedBound)
                    ) {
                        controlsContent(
                            Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxSize()
                        )
                    }
                }
            } else BoxWithConstraints(
                modifier = containerModifier.fillMaxSize()
            ) {
                val totalHeight = maxHeight
                val totalWidth = maxWidth
                val topPadding by animateDpAsState(
                    targetValue = if (isShowingLyrics && isLyricsFullscreen) 16.dp else 24.dp,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "playerTopPadding"
                )
                val availableHeight = (totalHeight - topPadding).coerceAtLeast(0.dp)
                val isExpressive = playerLayout == PlayerPreferences.PlayerLayout.Expressive
                val controlsTargetHeight = remember(availableHeight, isShowingLyrics, isLyricsFullscreen, isExpressive) {
                    when {
                        isLyricsFullscreen && isExpressive -> 92.dp
                        isLyricsFullscreen -> 0.dp
                        isShowingLyrics && isExpressive -> 175.dp
                        isShowingLyrics -> availableHeight * (1f / 2.7f)
                        isExpressive -> 236.dp
                        else -> availableHeight * (1f / 2.5f)
                    }
                }
                val animatedControlsHeight by animateDpAsState(
                    targetValue = controlsTargetHeight,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "playerControlsHeight"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val artworkAlpha by animateFloatAsState(
                            targetValue = if (!isShowingLyrics) 1f else 0f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "ArtworkAlpha"
                        )
                        val lyricsAlpha by animateFloatAsState(
                            targetValue = if (isShowingLyrics) 1f else 0f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "LyricsAlpha"
                        )

                        if (isCanvasActive && !isSquareCanvas && isVideoReady) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = artworkAlpha }
                                    .clickable(
                                        enabled = !isShowingLyrics,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        if (isShowingLyrics) isLyricsFullscreen = false
                                        isShowingLyrics = !isShowingLyrics
                                    }
                            )
                        } else {
                            Box(
                                modifier = Modifier.graphicsLayer { alpha = artworkAlpha }
                            ) {
                                thumbnailContent(Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
                            }
                        }

                        if (lyricsAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = lyricsAlpha }
                            ) {
                                lyricsContent(Modifier.fillMaxSize().padding(horizontal = 16.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isShowingLyrics || !isLyricsFullscreen || isExpressive,
                        enter = expandVertically(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                                fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)),
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                               fadeOut(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
                    ) {
                        controlsContent(
                            Modifier
                                .fillMaxWidth()
                                .height(animatedControlsHeight)
                        )
                    }
                }
            }
        }

        var audioDialogOpen by rememberSaveable { mutableStateOf(false) }

        if (audioDialogOpen) SliderDialog(
            onDismiss = { audioDialogOpen = false },
            title = stringResource(R.string.playback_settings)
        ) {
            SliderDialogBody(
                provideState = { remember(speed) { mutableFloatStateOf(speed) } },
                onSlideComplete = { speed = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_speed)
            )
            SliderDialogBody(
                provideState = { remember(pitch) { mutableFloatStateOf(pitch) } },
                onSlideComplete = { pitch = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_pitch)
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SecondaryTextButton(
                    text = stringResource(R.string.reset),
                    onClick = {
                        speed = 1f
                        pitch = 1f
                    }
                )
            }
        }

        var boostDialogOpen by rememberSaveable { mutableStateOf(false) }

        if (boostDialogOpen) {
            fun submit(state: Float) = transaction {
                mediaItem?.mediaId?.let { mediaId ->
                    Database.setLoudnessBoost(
                        songId = mediaId,
                        loudnessBoost = state.takeUnless { it == 0f }
                    )
                }
            }

            SliderDialog(
                onDismiss = { boostDialogOpen = false },
                title = stringResource(R.string.volume_boost)
            ) {
                SliderDialogBody(
                    provideState = {
                        val state = remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(mediaItem) {
                            mediaItem?.mediaId?.let { mediaId ->
                                Database
                                    .loudnessBoost(mediaId)
                                    .distinctUntilChanged()
                                    .collect { state.floatValue = it ?: 0f }
                            }
                        }

                        state
                    },
                    onSlideComplete = { submit(it) },
                    min = -20f,
                    max = 20f,
                    toDisplay = { stringResource(R.string.format_db, "%.2f".format(it)) }
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.reset),
                        onClick = { submit(0f) }
                    )
                }
            }
        }

        if (binder != null) {
            val queueModifier = if (isLandscape) {
                Modifier
                    .padding(
                        windowInsets
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues()
                    )
                    .fillMaxHeight()
                    .fillMaxWidth(1f / 2.35f)
                    .align(Alignment.BottomEnd)
            } else {
                Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter)
            }

            AnimatedVisibility(
                visible = !isLandscape || !isLyricsFullscreen,
                enter = expandHorizontally(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)),
                exit = shrinkHorizontally(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
                modifier = queueModifier
            ) {
                Queue(
                    layoutState = playerBottomSheetState,
                    binder = binder,
                    beforeContent = {
                        IconButton(
                            icon = R.drawable.lyrics,
                            color = if (isShowingLyrics) colorPalette.accent else colorPalette.text,
                            onClick = {
                                if (isShowingLyrics) isLyricsFullscreen = false
                                isShowingLyrics = !isShowingLyrics
                            },
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(20.dp)
                        )
                        if (playerLayout != PlayerPreferences.PlayerLayout.Classic) {
                            IconButton(
                                onClick = { trackLoopEnabled = !trackLoopEnabled },
                                icon = R.drawable.infinite,
                                enabled = trackLoopEnabled,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .size(20.dp)
                            )
                        }
                    },
                    afterContent = {
                        IconButton(
                            icon = R.drawable.ellipsis_horizontal,
                            color = colorPalette.text,
                            onClick = {
                                mediaItem?.let {
                                    menuState.display {
                                        PlayerMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = it,
                                            binder = binder,
                                            onShowSpeedDialog = { audioDialogOpen = true },
                                            onShowNormalizationDialog = {
                                                boostDialogOpen = true
                                            }.takeIf { volumeNormalization }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(20.dp)
                        )
                    },
                    shape = shape,
                    windowInsets = if (isLandscape) windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom) else windowInsets
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onShowSpeedDialog: (() -> Unit)? = null,
    onShowNormalizationDialog: (() -> Unit)? = null
) {
    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder.player.audioSessionId })

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = launchEqualizer,
        onShowSleepTimer = {},
        onDismiss = onDismiss,
        onShowSpeedDialog = onShowSpeedDialog,
        onShowNormalizationDialog = onShowNormalizationDialog
    )
}

private fun onDismiss(binder: PlayerService.Binder) {
    binder.stopRadio()
    binder.player.clearMediaItems()
}
