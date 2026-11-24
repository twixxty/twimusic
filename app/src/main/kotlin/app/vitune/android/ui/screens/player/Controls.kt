package app.vitune.android.ui.screens.player

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import app.vitune.android.utils.thumbnail
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.Player
import app.vitune.android.Database
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Info
import app.vitune.android.models.ui.UiMedia
import app.vitune.android.preferences.AppearancePreferences
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.service.PlayerService
import app.vitune.android.utils.getAppIcon
import app.vitune.android.ui.components.FadingRow
import app.vitune.android.ui.components.SeekBar
import app.vitune.android.ui.components.themed.BigIconButton
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.screens.artistRoute
import app.vitune.android.utils.bold
import app.vitune.android.utils.forceSeekToNext
import app.vitune.android.utils.forceSeekToPrevious
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.favoritesIcon
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DefaultOffset = 24.dp

@Composable
fun Controls(
    media: UiMedia?,
    binder: PlayerService.Binder?,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    shouldBePlaying: Boolean,
    position: Long,
    modifier: Modifier = Modifier,
    isShowingLyrics: Boolean = false,
    isLyricsFullscreen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    layout: PlayerPreferences.PlayerLayout = PlayerPreferences.playerLayout
) {
    val shouldBePlayingTransition = updateTransition(
        targetState = shouldBePlaying,
        label = "shouldBePlaying"
    )

    val playButtonRadius by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = "playPauseRoundness",
        targetValueByState = { if (it) 16.dp else 32.dp }
    )

    if (media != null && binder != null) when (layout) {
        PlayerPreferences.PlayerLayout.Classic -> ClassicControls(
            media = media,
            binder = binder,
            shouldBePlaying = shouldBePlaying,
            position = position,
            likedAt = likedAt,
            setLikedAt = setLikedAt,
            playButtonRadius = playButtonRadius,
            isShowingLyrics = isShowingLyrics,
            onToggleLyrics = onToggleLyrics,
            modifier = modifier
        )

        PlayerPreferences.PlayerLayout.Modern -> ModernControls(
            media = media,
            binder = binder,
            shouldBePlaying = shouldBePlaying,
            position = position,
            likedAt = likedAt,
            setLikedAt = setLikedAt,
            playButtonRadius = playButtonRadius,
            isShowingLyrics = isShowingLyrics,
            onToggleLyrics = onToggleLyrics,
            modifier = modifier
        )

        PlayerPreferences.PlayerLayout.Expressive -> ExpressiveControls(
            media = media,
            binder = binder,
            shouldBePlaying = shouldBePlaying,
            position = position,
            likedAt = likedAt,
            setLikedAt = setLikedAt,
            playButtonRadius = playButtonRadius,
            isShowingLyrics = isShowingLyrics,
            isLyricsFullscreen = isLyricsFullscreen,
            onToggleLyrics = onToggleLyrics,
            modifier = modifier
        )
    }
}

@Composable
private fun ClassicControls(
    media: UiMedia,
    binder: PlayerService.Binder,
    shouldBePlaying: Boolean,
    position: Long,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    playButtonRadius: Dp,
    modifier: Modifier = Modifier,
    isShowingLyrics: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null
) = with(PlayerPreferences) {
    val (colorPalette) = LocalAppearance.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isLandscape) 16.dp else 32.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        MediaInfo(media)
        Spacer(modifier = Modifier.weight(1f))
        val isGuest = binder.listenTogether.isGuestPlaybackLocked

        if (isGuest) {
            var isMuted by remember { mutableStateOf(binder.player.volume == 0f) }
            val muteButton: @Composable () -> Unit = {
                IconButton(
                    icon = if (isMuted) R.drawable.volume_mute else R.drawable.volume_up,
                    color = colorPalette.text,
                    onClick = {
                        if (binder.player.volume == 0f) {
                            binder.player.volume = 1f
                            isMuted = false
                        } else {
                            binder.player.volume = 0f
                            isMuted = true
                        }
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            val likeButton: @Composable () -> Unit = {
                IconButton(
                    icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                    color = colorPalette.favoritesIcon,
                    onClick = {
                        setLikedAt(if (likedAt == null) System.currentTimeMillis() else null)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = isLandscape || !isShowingLyrics,
                enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + expandVertically(tween(350, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (PlayerPreferences.showLike) {
                        likeButton()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SeekBar(
                            binder = binder,
                            position = position,
                            media = media,
                            alwaysShowDuration = true
                        )
                    }
                    muteButton()
                }
            }
        } else {
            AnimatedVisibility(
                visible = isLandscape || !isShowingLyrics,
                enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + expandVertically(tween(350, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing))
            ) {
                SeekBar(
                    binder = binder,
                    position = position,
                    media = media,
                    alwaysShowDuration = true
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                    color = colorPalette.favoritesIcon,
                    onClick = {
                        setLikedAt(if (likedAt == null) System.currentTimeMillis() else null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                )

                IconButton(
                    icon = R.drawable.play_skip_back,
                    color = colorPalette.text,
                    onClick = binder.player::forceSeekToPrevious,
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val isExpressiveTheme = app.vitune.android.ui.theme.LocalIsExpressiveTheme.current

                Box(
                    modifier = Modifier
                        .clip(playButtonRadius.roundedShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                if (shouldBePlaying) binder.player.pause()
                                else {
                                    if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                                    binder.player.play()
                                }
                            }
                        )
                        .background(if (isExpressiveTheme) colorPalette.accent else colorPalette.background2)
                        .size(64.dp)
                ) {
                    AnimatedPlayPauseButton(
                        playing = shouldBePlaying,
                        color = if (isExpressiveTheme) colorPalette.onAccent else Color(0xff16171d),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    icon = R.drawable.play_skip_forward,
                    color = colorPalette.text,
                    onClick = binder.player::forceSeekToNext,
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                )

                IconButton(
                    icon = R.drawable.infinite,
                    enabled = trackLoopEnabled,
                    onClick = { trackLoopEnabled = !trackLoopEnabled },
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                )

                if (onToggleLyrics != null) {
                    IconButton(
                        icon = R.drawable.lyrics,
                        color = if (isShowingLyrics) colorPalette.accent else colorPalette.text,
                        onClick = onToggleLyrics,
                        modifier = Modifier
                            .weight(1f)
                            .size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModernControls(
    media: UiMedia,
    binder: PlayerService.Binder,
    shouldBePlaying: Boolean,
    position: Long,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    playButtonRadius: Dp,
    modifier: Modifier = Modifier,
    isShowingLyrics: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    controlHeight: Dp = 64.dp
) {
    val previousButtonContent: @Composable RowScope.() -> Unit = {
        SkipButton(
            iconId = R.drawable.play_skip_back,
            onClick = binder.player::forceSeekToPrevious,
            modifier = Modifier.weight(1f),
            offsetOnPress = -DefaultOffset
        )
    }

    val likeButtonContent: @Composable RowScope.() -> Unit = {
        BigIconButton(
            iconId = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
            onClick = {
                setLikedAt(if (likedAt == null) System.currentTimeMillis() else null)
            },
            modifier = Modifier.weight(1f)
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isLandscape) 16.dp else 32.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        MediaInfo(media)
        Spacer(modifier = Modifier.weight(1f))

        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayPressed by playInteractionSource.collectIsPressedAsState()

        val nextInteractionSource = remember { MutableInteractionSource() }
        val isNextPressed by nextInteractionSource.collectIsPressedAsState()

        val topPrevInteractionSource = remember { MutableInteractionSource() }
        val isTopPrevPressed by topPrevInteractionSource.collectIsPressedAsState()

        val expressiveSpring = spring<Float>(
            dampingRatio = 0.6f,
            stiffness = 500f
        )
        val expressiveSpringDp = spring<Dp>(
            dampingRatio = 0.6f,
            stiffness = 500f
        )

        val hasTopPrev = PlayerPreferences.showLike

        val targetPlayWeight = when {
            isPlayPressed -> if (hasTopPrev) 2.2f else 2.6f
            isNextPressed || isTopPrevPressed -> if (hasTopPrev) 1.3f else 1.8f
            else -> if (hasTopPrev) 1.9f else 2.4f
        }

        val targetNextWeight = when {
            isNextPressed -> 0.9f
            isPlayPressed -> 0.4f
            isTopPrevPressed -> 0.5f
            else -> 0.6f
        }

        val targetPrevWeight = when {
            isTopPrevPressed -> 0.9f
            isPlayPressed -> 0.4f
            isNextPressed -> 0.5f
            else -> 0.6f
        }

        val playWeight by animateFloatAsState(targetPlayWeight, expressiveSpring, label = "playWeight")
        val nextWeight by animateFloatAsState(targetNextWeight, expressiveSpring, label = "nextWeight")
        val prevWeight by animateFloatAsState(targetPrevWeight, expressiveSpring, label = "prevWeight")

        val playScale by animateFloatAsState(
            targetValue = when {
                isPlayPressed -> 0.96f
                isNextPressed || isTopPrevPressed -> 0.98f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "playScale"
        )

        val nextScale by animateFloatAsState(
            targetValue = when {
                isNextPressed -> 0.96f
                isPlayPressed -> 0.98f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "nextScale"
        )

        val prevScale by animateFloatAsState(
            targetValue = when {
                isTopPrevPressed -> 0.96f
                isPlayPressed -> 0.98f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "prevScale"
        )

        val basePlayRadius = if (shouldBePlaying) 20.dp else (controlHeight / 2)
        val currentPlayRadius by animateDpAsState(
            targetValue = if (isPlayPressed) (basePlayRadius - 4.dp).coerceAtLeast(14.dp) else basePlayRadius,
            animationSpec = expressiveSpringDp,
            label = "playRadius"
        )
        val currentNextRadius by animateDpAsState(
            targetValue = if (isNextPressed) 26.dp else (controlHeight / 2),
            animationSpec = expressiveSpringDp,
            label = "nextRadius"
        )
        val currentPrevRadius by animateDpAsState(
            targetValue = if (isTopPrevPressed) 26.dp else (controlHeight / 2),
            animationSpec = expressiveSpringDp,
            label = "prevRadius"
        )

        val isGuest = binder.listenTogether.isGuestPlaybackLocked

        if (isGuest) {
            var isMuted by remember { mutableStateOf(binder.player.volume == 0f) }
            val muteButtonContent: @Composable RowScope.() -> Unit = {
                BigIconButton(
                    iconId = if (isMuted) R.drawable.volume_mute else R.drawable.volume_up,
                    onClick = {
                        if (binder.player.volume == 0f) {
                            binder.player.volume = 1f
                            isMuted = false
                        } else {
                            binder.player.volume = 0f
                            isMuted = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            AnimatedVisibility(
                visible = isLandscape || !isShowingLyrics,
                enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + expandVertically(tween(350, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (PlayerPreferences.showLike) {
                        likeButtonContent()
                        Column(modifier = Modifier.weight(4f)) {
                            SeekBar(
                                binder = binder,
                                position = position,
                                media = media,
                                alwaysShowDuration = true
                            )
                        }
                        muteButtonContent()
                    } else {
                        Column(modifier = Modifier.weight(4f)) {
                            SeekBar(
                                binder = binder,
                                position = position,
                                media = media,
                                alwaysShowDuration = true
                            )
                        }
                        muteButtonContent()
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (hasTopPrev) 4.dp else 8.dp)
            ) {
                if (hasTopPrev) {
                    SkipButton(
                        iconId = R.drawable.play_skip_back,
                        onClick = binder.player::forceSeekToPrevious,
                        modifier = Modifier
                            .height(controlHeight)
                            .weight(prevWeight),
                        interactionSource = topPrevInteractionSource,
                        scale = prevScale,
                        shape = currentPrevRadius.roundedShape
                    )
                }
                PlayButton(
                    radius = currentPlayRadius,
                    shouldBePlaying = shouldBePlaying,
                    modifier = Modifier
                        .height(controlHeight)
                        .weight(playWeight),
                    interactionSource = playInteractionSource,
                    scale = playScale
                )
                SkipButton(
                    iconId = R.drawable.play_skip_forward,
                    onClick = binder.player::forceSeekToNext,
                    modifier = Modifier
                        .height(controlHeight)
                        .weight(nextWeight),
                    interactionSource = nextInteractionSource,
                    scale = nextScale,
                    shape = currentNextRadius.roundedShape
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            AnimatedVisibility(
                visible = isLandscape || !isShowingLyrics,
                enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + expandVertically(tween(350, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (PlayerPreferences.showLike) likeButtonContent() else previousButtonContent()

                    Column(modifier = Modifier.weight(4f)) {
                        SeekBar(
                            binder = binder,
                            position = position,
                            media = media
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExpressiveControls(
    media: UiMedia,
    binder: PlayerService.Binder,
    shouldBePlaying: Boolean,
    position: Long,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    playButtonRadius: Dp,
    modifier: Modifier = Modifier,
    isShowingLyrics: Boolean = false,
    isLyricsFullscreen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    controlHeight: Dp = 64.dp
) {
    val (colorPalette, typography) = LocalAppearance.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isLandscape) 16.dp else 24.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // 1. Song Info Row: Left-aligned Title/Artist & Like Button, with cover art thumbnail sliding in during lyrics
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val thumbnailSpacerWidth by animateDpAsState(
                targetValue = if (isShowingLyrics && !isLandscape) 66.dp else 0.dp,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                label = "thumbnailSpacerWidth"
            )
            if (thumbnailSpacerWidth > 0.dp) {
                Spacer(Modifier.width(thumbnailSpacerWidth))
            }

            MediaInfo(
                media = media,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            )

            if (PlayerPreferences.showLike) {
                Spacer(Modifier.width(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorPalette.background2)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple()
                        ) {
                            setLikedAt(if (likedAt == null) System.currentTimeMillis() else null)
                        }
                ) {
                    Image(
                        painter = painterResource(getAppIcon(if (likedAt == null) R.drawable.heart_outline else R.drawable.heart)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (likedAt != null) colorPalette.favoritesIcon else colorPalette.textSecondary),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. Playback Controls & Seekbar (collapses when fullscreen lyrics, leaving song info visible)
        AnimatedVisibility(
            visible = !isLyricsFullscreen || !isShowingLyrics,
            enter = expandVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350)),
            exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(18.dp))

                // Primary Controls Row (Skip Prev, Center Play/Pause with text, Skip Next)
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()

                val nextInteractionSource = remember { MutableInteractionSource() }
                val isNextPressed by nextInteractionSource.collectIsPressedAsState()

                val topPrevInteractionSource = remember { MutableInteractionSource() }
                val isTopPrevPressed by topPrevInteractionSource.collectIsPressedAsState()

                val expressiveSpring = spring<Float>(
                    dampingRatio = 0.6f,
                    stiffness = 500f
                )
                val expressiveSpringDp = spring<Dp>(
                    dampingRatio = 0.6f,
                    stiffness = 500f
                )

                val targetPlayWeight = when {
                    isPlayPressed -> 2.2f
                    isNextPressed || isTopPrevPressed -> 1.3f
                    else -> 1.9f
                }

                val targetNextWeight = when {
                    isNextPressed -> 0.9f
                    isPlayPressed -> 0.4f
                    isTopPrevPressed -> 0.5f
                    else -> 0.6f
                }

                val targetPrevWeight = when {
                    isTopPrevPressed -> 0.9f
                    isPlayPressed -> 0.4f
                    isNextPressed -> 0.5f
                    else -> 0.6f
                }

                val playWeight by animateFloatAsState(targetPlayWeight, expressiveSpring, label = "playWeight")
                val nextWeight by animateFloatAsState(targetNextWeight, expressiveSpring, label = "nextWeight")
                val prevWeight by animateFloatAsState(targetPrevWeight, expressiveSpring, label = "prevWeight")

                val playScale by animateFloatAsState(
                    targetValue = when {
                        isPlayPressed -> 0.96f
                        isNextPressed || isTopPrevPressed -> 0.98f
                        else -> 1f
                    },
                    animationSpec = expressiveSpring,
                    label = "playScale"
                )

                val nextScale by animateFloatAsState(
                    targetValue = when {
                        isNextPressed -> 0.96f
                        isPlayPressed -> 0.98f
                        else -> 1f
                    },
                    animationSpec = expressiveSpring,
                    label = "nextScale"
                )

                val prevScale by animateFloatAsState(
                    targetValue = when {
                        isTopPrevPressed -> 0.96f
                        isPlayPressed -> 0.98f
                        else -> 1f
                    },
                    animationSpec = expressiveSpring,
                    label = "prevScale"
                )

                val basePlayRadius = if (shouldBePlaying) 20.dp else (controlHeight / 2)
                val currentPlayRadius by animateDpAsState(
                    targetValue = if (isPlayPressed) (basePlayRadius - 4.dp).coerceAtLeast(14.dp) else basePlayRadius,
                    animationSpec = expressiveSpringDp,
                    label = "playRadius"
                )
                val currentNextRadius by animateDpAsState(
                    targetValue = if (isNextPressed) 26.dp else (controlHeight / 2),
                    animationSpec = expressiveSpringDp,
                    label = "nextRadius"
                )
                val currentPrevRadius by animateDpAsState(
                    targetValue = if (isTopPrevPressed) 26.dp else (controlHeight / 2),
                    animationSpec = expressiveSpringDp,
                    label = "prevRadius"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkipButton(
                        iconId = R.drawable.play_skip_back,
                        onClick = binder.player::forceSeekToPrevious,
                        modifier = Modifier
                            .height(controlHeight)
                            .weight(prevWeight),
                        interactionSource = topPrevInteractionSource,
                        scale = prevScale,
                        shape = currentPrevRadius.roundedShape
                    )
                    ExpressivePlayButton(
                        radius = currentPlayRadius,
                        shouldBePlaying = shouldBePlaying,
                        modifier = Modifier
                            .height(controlHeight)
                            .weight(playWeight),
                        interactionSource = playInteractionSource,
                        scale = playScale
                    )
                    SkipButton(
                        iconId = R.drawable.play_skip_forward,
                        onClick = binder.player::forceSeekToNext,
                        modifier = Modifier
                            .height(controlHeight)
                            .weight(nextWeight),
                        interactionSource = nextInteractionSource,
                        scale = nextScale,
                        shape = currentNextRadius.roundedShape
                    )
                }

                // Full-width Seek Bar (BELOW controls) - smoothly hidden when enabling lyrics
                AnimatedVisibility(
                    visible = !isShowingLyrics,
                    enter = expandVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350)),
                    exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SeekBar(
                                binder = binder,
                                position = position,
                                media = media,
                                color = colorPalette.accent,
                                alwaysShowDuration = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressivePlayButton(
    radius: Dp,
    shouldBePlaying: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    scale: Float = 1f
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f
            }
            .clip(radius.roundedShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = {
                    if (shouldBePlaying) binder?.player?.pause() else {
                        if (binder?.player?.playbackState == Player.STATE_IDLE) binder.player.prepare()
                        binder?.player?.play()
                    }
                }
            )
            .background(colorPalette.accent),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = shouldBePlaying,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)))
            },
            contentAlignment = Alignment.Center,
            label = "expressivePlayPauseWithText"
        ) { isPlaying ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Image(
                    painter = painterResource(if (isPlaying) R.drawable.ms_pause else R.drawable.ms_play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.onAccent),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = if (isPlaying) "Pause" else "Play",
                    style = typography.m.bold.copy(color = colorPalette.onAccent)
                )
            }
        }
    }
}

@Composable
private fun SkipButton(
    @DrawableRes iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    scale: Float = 1f,
    shape: Shape = 32.dp.roundedShape,
    offsetOnPress: Dp = 0.dp
) {
    val (colorPalette) = LocalAppearance.current
    val pressed by interactionSource.collectIsPressedAsState()
    val offset by animateDpAsState(
        targetValue = if (pressed) offsetOnPress else 0.dp,
        label = "skipOffset"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f
            }
            .let {
                if (offsetOnPress != 0.dp) {
                    it.offset { IntOffset(x = offset.roundToPx(), y = 0) }
                } else it
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .background(colorPalette.background2)
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(getAppIcon(iconId)),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(colorPalette.text)
        )
    }
}

@Composable
private fun PlayButton(
    radius: Dp,
    shouldBePlaying: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    scale: Float = 1f
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f
            }
            .clip(radius.roundedShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = {
                    if (shouldBePlaying) binder?.player?.pause() else {
                        if (binder?.player?.playbackState == Player.STATE_IDLE) binder.player.prepare()
                        binder?.player?.play()
                    }
                }
            )
            .background(colorPalette.accent),
        contentAlignment = Alignment.Center
    ) {
        AnimatedPlayPauseButton(
            playing = shouldBePlaying,
            color = colorPalette.onAccent,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun MediaInfo(
    media: UiMedia,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    val (colorPalette, typography) = LocalAppearance.current

    var artistInfo: List<Info>? by remember { mutableStateOf(null) }
    var maxHeight by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(media) {
        withContext(Dispatchers.IO) {
            artistInfo = Database
                .songArtistInfo(media.id)
                .takeIf { it.isNotEmpty() }
        }
    }

    val isLeftAligned = horizontalAlignment == Alignment.Start
    val rowModifier = if (isLeftAligned) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.75f)
    val arrangement = if (isLeftAligned) Arrangement.Start else Arrangement.Center
    val contentAlignment = if (isLeftAligned) Alignment.CenterStart else Alignment.Center

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = media.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = contentAlignment,
            label = "mediaTitle"
        ) { title ->
            FadingRow(
                modifier = rowModifier,
                horizontalArrangement = arrangement
            ) {
                BasicText(
                    text = title,
                    style = typography.xl.bold,
                    maxLines = 1
                )
            }
        }

        AnimatedContent(
            targetState = media to artistInfo,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = contentAlignment,
            label = "mediaArtist"
        ) { [media, state] ->
            state?.let { artists ->
                FadingRow(
                    modifier = rowModifier
                        .heightIn(maxHeight.px.dp),
                    horizontalArrangement = arrangement,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    artists.fastForEachIndexed { i, artist ->
                        if (i == artists.lastIndex && artists.size > 1) BasicText(
                            text = " & ",
                            style = typography.s.semiBold.secondary
                        )
                        BasicText(
                            text = artist.name.orEmpty(),
                            style = typography.s.bold.secondary,
                            modifier = Modifier.clickable { artistRoute.global(artist.id) }
                        )
                        if (i != artists.lastIndex && i + 1 != artists.lastIndex) BasicText(
                            text = ", ",
                            style = typography.s.semiBold.secondary
                        )
                    }
                    if (media.explicit) {
                        Spacer(Modifier.width(4.dp))

                        Image(
                            painter = painterResource(R.drawable.explicit),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } ?: FadingRow(
                modifier = rowModifier,
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = media.artist,
                    style = typography.s.semiBold.secondary,
                    maxLines = 1,
                    modifier = Modifier.onGloballyPositioned { maxHeight = it.size.height }
                )
                if (media.explicit) {
                    Spacer(Modifier.width(4.dp))

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
