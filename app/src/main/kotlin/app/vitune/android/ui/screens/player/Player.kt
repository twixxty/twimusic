package app.vitune.android.ui.screens.player

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowInsetsCompat
import app.vitune.android.utils.FullScreenState
import app.vitune.android.ui.components.LavaLampBackground
import app.vitune.android.ui.components.BlurredArtworkBackground
import app.vitune.android.ui.components.LiquidBackground
import app.vitune.android.utils.collectProvidedBitmapAsState
import app.vitune.android.BuildConfig
import app.vitune.android.utils.LagDiagnostics
import app.vitune.android.utils.TrackRecomposition
import app.vitune.android.canvas.BackgroundVideoView
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import app.vitune.android.preferences.AppearancePreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
import app.vitune.android.service.LoginRequiredException
import app.vitune.android.service.PlayableFormatNotFoundException
import app.vitune.android.service.PlayerService
import app.vitune.android.service.PrecacheService
import app.vitune.android.service.RestrictedVideoException
import app.vitune.android.service.UnplayableException
import app.vitune.android.service.VideoIdMismatchException
import app.vitune.android.service.isLocal
import app.vitune.android.transaction
import app.vitune.android.ui.components.BottomSheet
import app.vitune.android.ui.components.BottomSheetState
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.rememberBottomSheetState
import app.vitune.android.ui.components.themed.BaseMediaItemMenu
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.SleepTimerDialog
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
import app.vitune.android.utils.shuffleQueue
import app.vitune.android.utils.bold
import app.vitune.android.utils.getAppIcon
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.shouldBePlaying
import app.vitune.android.ui.components.PlayerScrubState
import app.vitune.android.utils.thumbnail
import app.vitune.android.utils.windowState
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
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.IntSize

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
    var isPlayingState by remember(binder) { mutableStateOf(binder?.player?.isPlaying == true) }

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
                shouldBePlaying = player.shouldBePlaying
                isPlayingState = player.isPlaying
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaItem = player.currentMediaItem
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                mediaItem = player.currentMediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = player.shouldBePlaying
                isPlayingState = player.isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = player.shouldBePlaying
                isPlayingState = player.isPlaying
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
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
                    .background(
                        if (colorPalette.isDark) {
                            androidx.compose.ui.graphics.lerp(colorPalette.background0, colorPalette.background1, 0.40f)
                        } else {
                            androidx.compose.ui.graphics.lerp(colorPalette.background0, colorPalette.background1, 0.60f)
                        }
                    )
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
        var openLyricsMenu by remember { mutableStateOf<(() -> Unit)?>(null) }

        BackHandler(enabled = isShowingLyrics) {
            isLyricsFullscreen = false
            isShowingLyrics = false
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

        val isFullscreenArtworkStyle = PlayerPreferences.canvasStyle == PlayerPreferences.CanvasStyle.FullscreenArtwork
        val allowCanvas = PlayerPreferences.enableCanvas
        val isMobileBlocked = PlayerPreferences.canvasLoadOnlyWifi && context.isMobileData()
        val preferCover = PlayerPreferences.canvasStyle == PlayerPreferences.CanvasStyle.Cover
        val preferSquareCanvas = preferCover || (isFullscreenArtworkStyle && isLandscape)

        val artworkSquareVideo = canvasArtwork?.animated ?: canvasArtwork?.videoUrl
        val artworkTallVideo = canvasArtwork?.animatedTall

        val rawCanvasVideoUrl = if (isMobileBlocked || !allowCanvas) null else {
            if (preferSquareCanvas) {
                artworkSquareVideo ?: artworkTallVideo ?: spotifyCanvasUrl
            } else {
                artworkTallVideo ?: artworkSquareVideo ?: spotifyCanvasUrl
            }
        }

        var canvasFailed by remember(rawCanvasVideoUrl) { mutableStateOf(false) }
        val canvasVideoUrl = if (canvasFailed) null else rawCanvasVideoUrl

        val isCanvasActive = !canvasVideoUrl.isNullOrBlank()
        var isVideoReady by remember(canvasVideoUrl) { mutableStateOf(false) }

        // Wide / Bleed also show covers that have no canvas in their own style, unless the user
        // turned that off: then those covers use the normal cover box.
        val staticUsesCanvasStyle = PlayerPreferences.canvasStyleForStaticCovers
        val isBleedActive = isFullscreenArtworkStyle && (isCanvasActive || staticUsesCanvasStyle)
        // When static covers don't use Bleed style, keep the normal cover box visible until the
        // Bleed canvas video is actually ready, while keeping FullscreenThumbnailBackground composed.
        val isBleedVisible = isBleedActive && (staticUsesCanvasStyle || isVideoReady)
        val isWideStyle = PlayerPreferences.canvasStyle == PlayerPreferences.CanvasStyle.Wide

        // Wide + "style for covers without canvas": every canvas (also square ones, e.g. when a track
        // only has a square loop) is shown in the wide style instead of inside the cover box.
        val wideShowsEveryCanvas = isWideStyle && staticUsesCanvasStyle
        val shouldShowInCover = !isFullscreenArtworkStyle && !wideShowsEveryCanvas && (
            preferCover || (!artworkSquareVideo.isNullOrBlank() && artworkTallVideo.isNullOrBlank() && canvasVideoUrl == artworkSquareVideo)
        )

        var isSquareCanvas by remember(canvasVideoUrl, shouldShowInCover, wideShowsEveryCanvas) {
            mutableStateOf(shouldShowInCover)
        }
        var isTallCanvasVideo by remember(canvasVideoUrl, artworkTallVideo, spotifyCanvasUrl) {
            mutableStateOf(
                !canvasVideoUrl.isNullOrBlank() &&
                    (canvasVideoUrl == artworkTallVideo || canvasVideoUrl == spotifyCanvasUrl)
            )
        }

        // Shared artwork morph: cover <-> wide canvas <-> lyrics header (see PlayerHero.kt). Both
        // progress values are only ever read in the draw phase, so animating them is free of
        // recomposition and layout work.
        val hero = remember { CoverHeroState() }
        // First frame of the wide canvas: the blur under the controls is built from it.
        var wideCanvasFrame by remember(canvasVideoUrl) { mutableStateOf<ImageBitmap?>(null) }
        val isWideCanvasPresent = !isBleedActive && !isLandscape && isCanvasActive && !isSquareCanvas
        val isWideCanvasActive = !isBleedActive && isCanvasActive && !isSquareCanvas && isVideoReady
        val isSquareCanvasActive = !isBleedActive && isCanvasActive && isSquareCanvas
        // Wide + "canvas style for covers without canvas": the wide layer also exists for static
        // covers (and sits under a canvas that is still loading, so nothing shrinks back and forth).
        val isWideStaticLayer = isWideStyle && staticUsesCanvasStyle && !isLandscape &&
            (!isCanvasActive || !isSquareCanvas)
        val isWideLayerPresent = isWideCanvasPresent || isWideStaticLayer
        val isWideReady = if (isWideStaticLayer) true else isWideCanvasPresent && isVideoReady
        val wideCanvasFadeMillis = if (isWideStaticLayer) 400 else 0

        // The box cover shrinks whenever playback isn't actually running: paused, buffering /
        // loading, or while the position is being scrubbed.
        val isCoverPlaybackStalled = !isPlayingState || PlayerScrubState.isScrubbing
        val shouldShrinkStaticCover = !isBleedVisible &&
            isCoverPlaybackStalled &&
            !isShowingLyrics &&
            !isShowingStatsForNerds &&
            !isWideReady &&
            !isWideCanvasActive
        val staticCoverScale by animateFloatAsState(
            targetValue = if (shouldShrinkStaticCover) 0.9f else 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "staticCoverScale"
        )
        val lyricsProgress = animateFloatAsState(
            targetValue = if (isShowingLyrics) 1f else 0f,
            animationSpec = tween(HeroLyricsDurationMillis, easing = HeroEasing),
            label = "heroLyricsProgress"
        )
        // Grows the cover into the canvas once its first frame is ready. If the canvas goes away
        // (e.g. next track without one) there is nothing left to animate, so snap back.
        // Portrait Bleed uses the very same morph: the cover box grows into the Bleed window once the
        // canvas is ready (or is already there when static covers use the Bleed style too).
        val isBleedHero = isFullscreenArtworkStyle && !isLandscape
        val canvasProgress = animateFloatAsState(
            targetValue = if (isWideReady || (isBleedHero && isBleedVisible)) 1f else 0f,
            animationSpec = if (isWideLayerPresent || (isBleedHero && isBleedActive)) {
                tween(HeroCanvasDurationMillis, easing = HeroEasing)
            } else {
                snap()
            },
            label = "heroCanvasProgress"
        )
        // Bleed and the portrait Wide canvas share the translucent controls + bottom bar styling.
        val usesTranslucentUi = isBleedVisible || (!isLandscape && isWideReady)
        SideEffect { hero.lyricsProgress = { lyricsProgress.value } }
        val isCoverInteractive by remember { derivedStateOf { canvasProgress.value < 0.5f } }
        val heroCoverRadius = with(LocalDensity.current) { thumbnailCornerSize.toPx() }
        val heroHeaderRadius = with(LocalDensity.current) { 12.dp.toPx() }

        var playerRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var playerRootHeightPx by remember { mutableFloatStateOf(0f) }
        var songTitleTopPx by remember { mutableFloatStateOf(Float.NaN) }
        var artistBottomPx by remember { mutableFloatStateOf(Float.NaN) }
        val fullscreenTransitionBounds by remember {
            derivedStateOf {
                if (playerRootHeightPx > 0f && !songTitleTopPx.isNaN() && !artistBottomPx.isNaN()) {
                    val start =
                        ((songTitleTopPx - playerRootHeightPx * FULLSCREEN_TITLE_TRANSITION_OFFSET_FRACTION) / playerRootHeightPx)
                            .coerceIn(0f, 0.85f)
                    val end =
                        (artistBottomPx / playerRootHeightPx)
                            .coerceAtLeast(start + FULLSCREEN_MIN_FADE_SPAN_FRACTION)
                            .coerceIn(start, 1f)
                    FullscreenTransitionBounds(
                        bottomTransitionStartFraction = start,
                        bottomTransitionEndFraction = end
                    )
                } else {
                    null
                }
            }
        }

        if (BuildConfig.DEBUG) {
            TrackRecomposition("Player")
            SideEffect {
                LagDiagnostics.updateContext(
                    screen = if (layoutState.expanded) "Player(Expanded)" else "Player(Collapsed)",
                    isCanvasActive = isCanvasActive && !isSquareCanvas,
                    isLyricsVisible = isShowingLyrics,
                    isPlaying = isPlayingState
                )
            }
        }

        val containerModifier = Modifier
            .padding(
                windowInsets
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
            .let {
                if (isLandscape) it
                else it.padding(bottom = playerBottomSheetState.collapsedBound)
            }

        val canvasContent: @Composable (modifier: Modifier, onFrame: ((ImageBitmap?) -> Unit)?) -> Unit = { innerModifier, onFrame ->
            val videoUrl = canvasVideoUrl
            if (!videoUrl.isNullOrBlank()) {
                Box(
                    modifier = innerModifier
                        .fillMaxSize()
                        .clickable(
                            enabled = !isShowingLyrics && !isBleedActive,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            isShowingLyrics = !isShowingLyrics
                        }
                ) {
                    BackgroundVideoView(
                        videoUrl = videoUrl,
                        isPlaying = !layoutState.collapsed && !layoutState.dismissed && !isShowingLyrics && (!PlayerPreferences.pauseCanvasWithSong || isPlayingState),
                        // In portrait the cover -> canvas crossfade is driven by the hero morph / FullscreenThumbnailBackground,
                        // and in landscape it is driven by canvasLandscapeProgress / FullscreenThumbnailBackground.
                        fadeInDurationMillis = wideCanvasFadeMillis,
                        onVideoReady = { isVideoReady = it },
                        onVideoSizeChanged = { width, height ->
                            if (width > 0 && height > 0) {
                                val aspectRatio = width.toFloat() / height.toFloat()
                                // Cover style always keeps the canvas inside the cover square (it is cropped
                                // to fill it, whatever the video's proportions are).
                                isSquareCanvas = when {
                                    isFullscreenArtworkStyle || wideShowsEveryCanvas -> false
                                    preferCover -> true
                                    else -> aspectRatio in 0.82f..1.22f
                                }
                                isTallCanvasVideo = aspectRatio < 0.85f
                            }
                        },
                        onError = {
                            isVideoReady = false
                            canvasFailed = true
                        },
                        onFrame = onFrame,
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
                    isPlaying = !layoutState.collapsed && !layoutState.dismissed && !isShowingLyrics && (!PlayerPreferences.pauseCanvasWithSong || isPlayingState),
                    squareCanvasAlpha = { hero.squareCanvasAlpha() },
                    coverScale = { staticCoverScale },
                    isCoverInteractive = isCoverInteractive,
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
        val appearance = LocalAppearance.current
        // Bleed and the portrait wide canvas use translucent white controls; they cross-fade
        // to / from the normal palette (e.g. when lyrics open) instead of switching abruptly.
        val useWhiteFullscreenControls = usesTranslucentUi && !isShowingLyrics
        val whiteControlsProgress by animateFloatAsState(
            targetValue = if (useWhiteFullscreenControls) 1f else 0f,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "whiteControlsProgress"
        )
        val effectiveControlsAppearance = remember(appearance, whiteControlsProgress) {
            val t = whiteControlsProgress
            if (t <= 0.001f) {
                appearance
            } else {
                val palette = appearance.colorPalette
                appearance.copy(
                    colorPalette = palette.copy(
                        background1 = lerpColor(palette.background1, Color.White.copy(alpha = 0.16f), t),
                        background2 = lerpColor(palette.background2, Color.White.copy(alpha = 0.20f), t),
                        text = lerpColor(palette.text, Color.White, t),
                        textSecondary = lerpColor(palette.textSecondary, Color.White.copy(alpha = 0.84f), t),
                        textDisabled = lerpColor(palette.textDisabled, Color.White.copy(alpha = 0.50f), t),
                        isDark = if (t > 0.5f) true else palette.isDark
                    ),
                    typography = appearance.typography.copy(
                        color = lerpColor(appearance.typography.s.color, Color.White, t)
                    )
                )
            }
        }
        val mediaInfoBoundsModifier =
            if (isBleedVisible && !isLandscape && !isShowingLyrics) {
                Modifier.onGloballyPositioned { coordinates ->
                    val root = playerRootCoordinates
                    if (root != null && root.isAttached && coordinates.isAttached) {
                        val top = root.localPositionOf(coordinates, Offset.Zero).y
                        songTitleTopPx = top
                        artistBottomPx = top + coordinates.size.height
                    }
                }
            } else {
                Modifier
            }

        val controlsContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            CompositionLocalProvider(LocalAppearance provides effectiveControlsAppearance) {
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
                        if (isShowingLyrics) {
                            isLyricsFullscreen = false
                            isShowingLyrics = false
                        } else {
                            isShowingLyrics = true
                        }
                    },
                    hero = hero,
                    onOpenLyricsMenu = openLyricsMenu,
                    mediaInfoModifier = mediaInfoBoundsModifier,
                    isBleedMode = usesTranslucentUi,
                    modifier = innerModifier
                )
            }
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
                    onSetFullscreen = { isLyricsFullscreen = it },
                    onMenuActionReady = { openLyricsMenu = it },
                    isActive = isShowingLyrics && layoutState.expanded
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .heroRoot(hero)
                .onPlaced { playerRootCoordinates = it }
                .onSizeChanged { playerRootHeightPx = it.height.toFloat() }
        ) {
            val darkOverlayAlpha by animateFloatAsState(
                targetValue = if (isShowingLyrics) 0.55f else 0.35f,
                label = "coverArtOverlayAlpha"
            )

            // In Bleed mode, opening lyrics cleanly fades the Bleed artwork/canvas layer out and pauses
            // the composed BackgroundVideoView rather than running a hero transition to the lyrics header.
            val bleedPresence = animateFloatAsState(
                targetValue = if (isBleedVisible && !isShowingLyrics) 1f else 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "fullscreenThumbnailBgAlpha"
            )
            // Portrait Bleed: the blurred backdrop follows the hero morph and is gone before the
            // artwork has landed in the lyrics header. Landscape keeps the plain fade.
            val fullscreenBgAlpha: () -> Float = {
                if (isBleedHero) {
                    // Leaves quickly when the lyrics open, but only returns once the artwork has
                    // (almost) landed again, so no second cover shows where it is flying to.
                    val span = if (isShowingLyrics) 0.6f else 0.12f
                    canvasProgress.value * (1f - (lyricsProgress.value / span).coerceIn(0f, 1f))
                } else {
                    bleedPresence.value
                }
            }
            val canvasHiddenByLyrics by remember { derivedStateOf { isShowingLyrics } }

            // The Bleed background is fully opaque, so while it is shown the (animated, shader
            // based) player background underneath would only burn GPU time: hide it, but keep it
            // composed so there is no hitch when the lyrics open.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Alpha 0 layers are skipped by the renderer, so this costs no GPU time at all.
                        alpha = if (isBleedVisible && fullscreenBgAlpha() >= 0.999f) 0f else 1f
                    }
            ) {
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
            }

            // Always keep FullscreenThumbnailBackground composed when bleed mode is active so the
            // ExoPlayer inside BackgroundVideoView is never released — it only pauses instead.
            // This prevents the "canvas unloads when opening lyrics / collapsing player" bug.
            if (isBleedActive && mediaItem != null) {
                val currentArtworkUri = mediaItem?.mediaMetadata?.artworkUri
                    ?: binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri
                    ?: binder?.player?.mediaMetadata?.artworkUri
                FullscreenThumbnailBackground(
                    artworkUri = currentArtworkUri,
                    fallbackBackgroundColor = colorPalette.background0,
                    alpha = fullscreenBgAlpha,
                    transitionBounds = fullscreenTransitionBounds,
                    isLandscape = isLandscape,
                    canvasVideoUrl = canvasVideoUrl,
                    isVideoReady = isVideoReady,
                    canvasTall = isTallCanvasVideo,
                    canvasSuppressed = canvasHiddenByLyrics,
                    showStaticBleed = staticUsesCanvasStyle,
                    hero = if (isBleedHero) hero else null,
                    lyricsProgress = { lyricsProgress.value },
                    heroProgress = { canvasProgress.value },
                    isBlurredBackgroundStyle = playerBackgroundStyle == PlayerPreferences.PlayerBackgroundStyle.Blur,
                    canvasContent =
                        if (isCanvasActive) {
                            { videoModifier, onFrame -> canvasContent(videoModifier, onFrame) }
                        } else {
                            null
                        }
                )
            }

            if (isWideLayerPresent) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // The slot itself never moves, only the layer inside it does: that keeps the
                    // measured "natural" bounds stable while the video is morphing.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .align(Alignment.TopCenter)
                            .heroCanvasSlot(hero)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    hero.applyCanvasLayer(
                                        scope = this,
                                        lyricsProgress = lyricsProgress.value,
                                        canvasProgress = canvasProgress.value,
                                        coverRadius = heroCoverRadius,
                                        headerRadius = heroHeaderRadius
                                    )
                                }
                        ) {
                            if (isWideStaticLayer) {
                                // Static cover in the wide style; a canvas fades in over it.
                                val wideArtworkUri = mediaItem?.mediaMetadata?.artworkUri
                                Crossfade(
                                    targetState = wideArtworkUri,
                                    animationSpec = tween(350),
                                    label = "wideStaticArtwork",
                                    modifier = Modifier.fillMaxSize()
                                ) { uri ->
                                    AsyncImage(
                                        model = uri?.thumbnail(1280),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            if (isCanvasActive) canvasContent(
                                Modifier.fillMaxSize(),
                                if (wideCanvasFrame == null) {
                                    { frame: ImageBitmap? ->
                                        if (frame != null && wideCanvasFrame == null) wideCanvasFrame = frame
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }

                // Bleed background behind the controls: the cover's blurred extension fills the lower
                // part of the screen, and the canvas melts into it. Nothing is pushed under the canvas.
                val wideBgAlpha by animateFloatAsState(
                    targetValue = if (!isShowingLyrics && !isLyricsFullscreen && isWideReady) 1f else 0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerWideBleedAlpha"
                )
                if (wideBgAlpha > 0.001f && mediaItem != null) {
                    FullscreenThumbnailBackground(
                        artworkUri = mediaItem?.mediaMetadata?.artworkUri,
                        fallbackBackgroundColor = colorPalette.background0,
                        alpha = { wideBgAlpha },
                        transitionBounds = null,
                        isLandscape = false,
                        isBlurredBackgroundStyle = true,
                        overlayMode = true,
                        blurFrame = wideCanvasFrame
                    )
                }
                if (wideBgAlpha > 0.001f) {
                    // Protective dark gradient so white controls remain crisp over bright Wide covers/canvases.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = wideBgAlpha }
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.22f to Color.Black.copy(alpha = 0.16f),
                                    0.42f to Color.Black.copy(alpha = 0.44f),
                                    0.64f to Color.Black.copy(alpha = 0.68f),
                                    0.84f to Color.Black.copy(alpha = 0.80f),
                                    1.0f to Color.Black.copy(alpha = 0.88f)
                                )
                            )
                    )
                }
            }

            if (PlayerPreferences.hideStatusBarOnCanvas && isCanvasActive && !isSquareCanvas && !isShowingLyrics && !layoutState.collapsed && !layoutState.dismissed) {
                FullScreenState(
                    shown = false,
                    type = WindowInsetsCompat.Type.statusBars()
                )
            }

            if (isLandscape) BoxWithConstraints(
                modifier = Modifier
                    .padding(windowInsets.only(WindowInsetsSides.Horizontal).asPaddingValues())
                    .fillMaxSize()
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

                val leftWeight by animateFloatAsState(
                    targetValue = if (isLyricsFullscreen) 1f else 1.35f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerLandscapeLeftWeight"
                )
                val rightWeight by animateFloatAsState(
                    targetValue = if (isLyricsFullscreen) 0.0001f else 1f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerLandscapeRightWeight"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(leftWeight)
                            .fillMaxHeight()
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
                        val isWideLandscapeLayer = isWideStyle && staticUsesCanvasStyle
                        val canvasLandscapeProgress by animateFloatAsState(
                            targetValue = if (isCanvasActive && !isSquareCanvas && isVideoReady) 1f else 0f,
                            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                            label = "canvasLandscapeProgress"
                        )

                        // Wide + "style for covers without canvas": the static cover already sits in the
                        // canvas' place, so a canvas just fades in over it.
                        val coverScale = 1f + 0.12f * canvasLandscapeProgress
                        val coverAlpha = artworkLandscapeAlpha * (1f - canvasLandscapeProgress).coerceIn(0f, 1f)
                        // The right-hand fade comes in together with the canvas instead of popping in
                        // (or is there from the start when the static cover already uses the wide layout).
                        val landscapeMaskStrength: () -> Float = {
                            if (isWideLandscapeLayer) 1f else canvasLandscapeProgress
                        }

                        if (isBleedVisible) {
                            if (artworkLandscapeAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = artworkLandscapeAlpha }
                                ) {
                                    FullscreenArtworkOverlay(
                                        binder = binder,
                                        isShowingLyrics = isShowingLyrics,
                                        onShowLyrics = { isShowingLyrics = it },
                                        isShowingStatsForNerds = isShowingStatsForNerds,
                                        onShowStatsForNerds = { isShowingStatsForNerds = it },
                                        likedAt = likedAt,
                                        setLikedAt = { likedAt = it },
                                        nestedScrollConnection = layoutState.preUpPostDownNestedScrollConnection,
                                        isShowingLyricsDialog = isShowingLyricsDialog,
                                        onShowLyricsDialog = { isShowingLyricsDialog = true },
                                        onEnterPip = { pipHandler.enterPictureInPictureMode() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            if (isWideLandscapeLayer) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = artworkLandscapeAlpha
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    0.55f to Color.Black,
                                                    1.0f to Color.Black.copy(alpha = 1f - landscapeMaskStrength())
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                        .clickable(
                                            enabled = !isShowingLyrics,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { isShowingLyrics = true }
                                ) {
                                    Crossfade(
                                        targetState = mediaItem?.mediaMetadata?.artworkUri,
                                        animationSpec = tween(350),
                                        label = "wideLandscapeStaticArtwork",
                                        modifier = Modifier.fillMaxSize()
                                    ) { uri ->
                                        AsyncImage(
                                            model = uri?.thumbnail(1280),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else if (coverAlpha > 0f) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        alpha = coverAlpha
                                        scaleX = coverScale
                                        scaleY = coverScale
                                    }
                                ) {
                                    thumbnailContent(Modifier.size(coverArtTargetSize))
                                }
                            }

                            if (isCanvasActive && !isSquareCanvas) {
                                val canvasAlpha = artworkLandscapeAlpha * canvasLandscapeProgress
                                val canvasScale = 1.10f - 0.10f * canvasLandscapeProgress
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = canvasAlpha
                                            scaleX = canvasScale
                                            scaleY = canvasScale
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    0.55f to Color.Black,
                                                    1.0f to Color.Black.copy(alpha = 1f - landscapeMaskStrength())
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                ) {
                                    canvasContent(Modifier.fillMaxSize(), null)
                                }
                            }
                        }

                        if (lyricsLandscapeAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = landscapeTopPadding)
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
                            .weight(rightWeight)
                            .fillMaxHeight()
                            .padding(top = landscapeTopPadding, bottom = playerBottomSheetState.collapsedBound)
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
                        isLyricsFullscreen && isExpressive -> 96.dp
                        isLyricsFullscreen -> 0.dp
                        isShowingLyrics && isExpressive -> 180.dp
                        isShowingLyrics -> availableHeight * (1f / 2.7f)
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
                        // Lyrics wait for the artwork to clear the way before fading in; they are
                        // composed right away though, so the (heavy) first composition happens
                        // before the morph starts to move instead of in the middle of it.
                        val lyricsAlpha by animateFloatAsState(
                            targetValue = if (isShowingLyrics) 1f else 0f,
                            animationSpec = if (isShowingLyrics) {
                                tween(300, delayMillis = 140, easing = FastOutSlowInEasing)
                            } else {
                                tween(200, easing = FastOutSlowInEasing)
                            },
                            label = "LyricsAlpha"
                        )

                        // The cover box stays composed in Bleed too (unless static covers already use the
                        // Bleed style): it is what grows into the Bleed window and what flies to the
                        // lyrics header, exactly like in Wide.
                        if (!isBleedVisible || (isBleedHero && !staticUsesCanvasStyle)) {
                            // The cover is always composed (so its image, swipe handling and square
                            // canvas stay alive) and is moved / faded purely in the graphics layer.
                            // This slot is measured un-transformed, the layer inside it is the
                            // thing that morphs towards the canvas or the lyrics header.
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 32.dp, vertical = 8.dp)
                                    .heroCoverSlot(hero)
                            ) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        hero.applyCoverLayer(
                                            scope = this,
                                            lyricsProgress = lyricsProgress.value,
                                            canvasProgress = canvasProgress.value,
                                            coverRadius = heroCoverRadius,
                                            headerRadius = heroHeaderRadius
                                        )
                                    }
                                ) {
                                    thumbnailContent(Modifier)
                                }
                            }
                        }

                        if (isBleedVisible && !isShowingLyrics) {
                            // Gesture surface above the (now invisible) cover box.
                            FullscreenArtworkOverlay(
                                binder = binder,
                                isShowingLyrics = isShowingLyrics,
                                onShowLyrics = { isShowingLyrics = it },
                                isShowingStatsForNerds = isShowingStatsForNerds,
                                onShowStatsForNerds = { isShowingStatsForNerds = it },
                                likedAt = likedAt,
                                setLikedAt = { likedAt = it },
                                nestedScrollConnection = layoutState.preUpPostDownNestedScrollConnection,
                                isShowingLyricsDialog = isShowingLyricsDialog,
                                onShowLyricsDialog = { isShowingLyricsDialog = true },
                                onEnterPip = { pipHandler.enterPictureInPictureMode() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (isShowingLyrics || lyricsAlpha > 0f) {
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
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                               fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    ) {
                        controlsContent(
                            Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth()
                                .height(animatedControlsHeight)
                        )
                    }
                }
            }

            if (PlayerPreferences.showStatusBarGradient) {
                val topInset = windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
                val scrimHeight = (topInset + 36.dp).coerceAtLeast(64.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scrimHeight)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.60f),
                                0.40f to Color.Black.copy(alpha = 0.30f),
                                1.0f to Color.Transparent
                            )
                        )
                )
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

        var sleepTimerDialogOpen by rememberSaveable { mutableStateOf(false) }
        var sleepTimerMillisLeft by remember { mutableLongStateOf(0L) }

        LaunchedEffect(binder, binder?.sleepTimerMillisLeft) {
            binder?.sleepTimerMillisLeft?.collectLatest {
                sleepTimerMillisLeft = it ?: 0L
            } ?: run { sleepTimerMillisLeft = 0L }
        }

        if (sleepTimerDialogOpen) {
            SleepTimerDialog(
                binder = binder,
                sleepTimerMillisLeft = sleepTimerMillisLeft,
                onDismiss = { sleepTimerDialogOpen = false }
            )
        }

        if (binder != null) {
            val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder.player.audioSessionId })
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
                val activeActions = remember(playerBottomBarActions) {
                    parsePlayerBottomActions(playerBottomBarActions)
                }
                val isBleedBarTranslucent = usesTranslucentUi && !isShowingLyrics
                val barBaseColor = lerpColor(colorPalette.text, Color.White, whiteControlsProgress)
                val landscapeMode = isLandscape

                // Build a custom collapsed bar row from the user's configured action list
                val customActionsContent: @Composable RowScope.() -> Unit = {
                    activeActions.forEach { bottomAction ->
                        val iconColor = when (bottomAction) {
                            PlayerPreferences.PlayerBottomAction.Lyrics ->
                                if (isShowingLyrics) colorPalette.accent else barBaseColor
                            PlayerPreferences.PlayerBottomAction.Repeat ->
                                if (trackLoopEnabled) colorPalette.accent else barBaseColor
                            PlayerPreferences.PlayerBottomAction.Like ->
                                if (likedAt != null) colorPalette.favoritesIcon else barBaseColor
                            PlayerPreferences.PlayerBottomAction.SleepTimer ->
                                if (sleepTimerMillisLeft > 0L) colorPalette.accent else barBaseColor
                            PlayerPreferences.PlayerBottomAction.StatsForNerds ->
                                if (isShowingStatsForNerds) colorPalette.accent else barBaseColor
                            PlayerPreferences.PlayerBottomAction.CanvasToggle ->
                                if (enableCanvas) colorPalette.accent else barBaseColor
                            else -> barBaseColor
                        }
                        val iconRes = when (bottomAction) {
                            PlayerPreferences.PlayerBottomAction.Like ->
                                if (likedAt != null) R.drawable.heart else R.drawable.heart_outline
                            else -> bottomAction.icon
                        }
                        IconButton(
                            icon = iconRes,
                            color = iconColor,
                            onClick = {
                                when (bottomAction) {
                                    PlayerPreferences.PlayerBottomAction.Lyrics -> {
                                        if (isShowingLyrics) {
                                            isLyricsFullscreen = false
                                            isShowingLyrics = false
                                        } else {
                                            isShowingLyrics = true
                                        }
                                    }
                                    PlayerPreferences.PlayerBottomAction.Repeat ->
                                        trackLoopEnabled = !trackLoopEnabled
                                    PlayerPreferences.PlayerBottomAction.Queue ->
                                        playerBottomSheetState.expandSoft()
                                    PlayerPreferences.PlayerBottomAction.Shuffle ->
                                        binder.player.shuffleQueue()
                                    PlayerPreferences.PlayerBottomAction.Like ->
                                        likedAt = if (likedAt == null) System.currentTimeMillis() else null
                                    PlayerPreferences.PlayerBottomAction.SleepTimer ->
                                        sleepTimerDialogOpen = true
                                    PlayerPreferences.PlayerBottomAction.AudioSettings ->
                                        audioDialogOpen = true
                                    PlayerPreferences.PlayerBottomAction.Equalizer ->
                                        launchEqualizer()
                                    PlayerPreferences.PlayerBottomAction.Radio -> {
                                        mediaItem?.let { item ->
                                            binder.stopRadio()
                                            binder.player.seamlessPlay(item)
                                            binder.setupRadio(
                                                NavigationEndpoint.Endpoint.Watch(videoId = item.mediaId)
                                            )
                                        }
                                    }
                                    PlayerPreferences.PlayerBottomAction.Share -> {
                                        mediaItem?.let { item ->
                                            val sendIntent = Intent().apply {
                                                this.action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "https://music.youtube.com/watch?v=${item.mediaId}"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, null))
                                        }
                                    }
                                    PlayerPreferences.PlayerBottomAction.PreCache -> {
                                        mediaItem?.let { item ->
                                            runCatching {
                                                PrecacheService.scheduleCache(
                                                    context = context.applicationContext,
                                                    mediaItem = item
                                                )
                                            }
                                        }
                                    }
                                    PlayerPreferences.PlayerBottomAction.StatsForNerds -> {
                                        if (!landscapeMode && isShowingLyrics && !isShowingStatsForNerds) {
                                            isLyricsFullscreen = false
                                            isShowingLyrics = false
                                            isShowingStatsForNerds = true
                                        } else {
                                            isShowingStatsForNerds = !isShowingStatsForNerds
                                        }
                                    }
                                    PlayerPreferences.PlayerBottomAction.VolumeBoost ->
                                        boostDialogOpen = true
                                    PlayerPreferences.PlayerBottomAction.CanvasToggle ->
                                        enableCanvas = !enableCanvas
                                    PlayerPreferences.PlayerBottomAction.MoreOptions -> {
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
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(20.dp)
                        )
                    }
                }

                Queue(
                    layoutState = playerBottomSheetState,
                    binder = binder,
                    beforeContent = {},
                    afterContent = {},
                    customActionsContent = customActionsContent,
                    isTranslucentBar = isBleedBarTranslucent,
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

@Composable
private fun FullscreenArtworkOverlay(
    binder: PlayerService.Binder?,
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingStatsForNerds: Boolean,
    onShowStatsForNerds: (Boolean) -> Unit,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    isShowingLyricsDialog: Boolean,
    onShowLyricsDialog: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val (window, error) = windowState()
    val coroutineScope = rememberCoroutineScope()
    val transitionState = remember { SeekableTransitionState(false) }
    val transition = rememberTransition(transitionState, label = "fullscreenHeart")
    val opacity by transition.animateFloat(label = "fullscreenHeartOpacity") { if (it) 1f else 0f }
    val scale by transition.animateFloat(
        label = "fullscreenHeartScale",
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioLowBouncy) }
    ) { if (it) 1f else 0f }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .nestedScroll(nestedScrollConnection)
            .pinchToToggle(
                key = isShowingLyricsDialog,
                direction = PinchDirection.Out,
                threshold = 1.05f,
                onPinch = {
                    if (isShowingLyrics) onShowLyricsDialog()
                }
            )
            .pinchToToggle(
                key = isShowingLyricsDialog,
                direction = PinchDirection.In,
                threshold = 0.95f,
                onPinch = { onEnterPip() }
            )
            .onSwipe(
                onSwipeLeft = { binder?.player?.forceSeekToNext() },
                onSwipeRight = { binder?.player?.forceSeekToPrevious(seekToStart = false) }
            )
            .pointerInput(isShowingLyrics, isShowingStatsForNerds) {
                if (!isShowingLyrics) {
                    detectTapGestures(
                        onTap = { onShowLyrics(true) },
                        onLongPress = { onShowStatsForNerds(!isShowingStatsForNerds) },
                        onDoubleTap = {
                            if (likedAt == null) setLikedAt(System.currentTimeMillis())
                            coroutineScope.launch {
                                val spec = tween<Float>(durationMillis = 500)
                                transitionState.animateTo(true, spec)
                                transitionState.animateTo(false, spec)
                            }
                        }
                    )
                }
            }
    ) {
        val currentWindow = window
        if (currentWindow != null) {
            StatsForNerds(
                mediaId = currentWindow.mediaItem.mediaId,
                isDisplayed = isShowingStatsForNerds && error == null,
                onDismiss = { onShowStatsForNerds(false) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )

            Image(
                painter = painterResource(getAppIcon(R.drawable.heart)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.accent),
                modifier = Modifier
                    .fillMaxSize(0.38f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        alpha = opacity,
                        shadowElevation = 8.dp.px.toFloat()
                    )
            )

            PlaybackError(
                isDisplayed = error != null,
                messageProvider = {
                    if (currentWindow.mediaItem.isLocal) stringResource(R.string.error_local_music_deleted)
                    else when (error?.cause?.cause) {
                        is UnresolvedAddressException, is UnknownHostException ->
                            stringResource(R.string.error_network)

                        is PlayableFormatNotFoundException -> stringResource(R.string.error_unplayable)

                        is UnplayableException -> stringResource(R.string.error_source_deleted)

                        is LoginRequiredException, is RestrictedVideoException ->
                            stringResource(R.string.error_server_restrictions)

                        is VideoIdMismatchException -> stringResource(R.string.error_id_mismatch)

                        else -> stringResource(R.string.error_unknown_playback)
                    }
                },
                onDismiss = { binder?.player?.prepare() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
