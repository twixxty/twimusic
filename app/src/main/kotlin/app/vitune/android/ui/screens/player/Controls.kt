package app.vitune.android.ui.screens.player

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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.service.PlayerService
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

        PlayerPreferences.PlayerLayout.New -> ModernControls(
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        MediaInfo(media)
        Spacer(modifier = Modifier.weight(1f))
        AnimatedVisibility(
            visible = !isShowingLyrics,
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

            Box(
                modifier = Modifier
                    .clip(playButtonRadius.roundedShape)
                    .clickable {
                        if (shouldBePlaying) binder.player.pause()
                        else {
                            if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                            binder.player.play()
                        }
                    }
                    .background(colorPalette.background2)
                    .size(64.dp)
            ) {
                AnimatedPlayPauseButton(
                    playing = shouldBePlaying,
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
                    icon = R.drawable.musical_notes,
                    color = if (isShowingLyrics) colorPalette.accent else colorPalette.text,
                    onClick = onToggleLyrics,
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                )
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
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
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

        val hasTopPrev = PlayerPreferences.showLike
        val basePlayWeight = if (hasTopPrev) 3f else 4f
        val baseNextWeight = 1f
        val basePrevWeight = 1f

        val targetPlayWeight = when {
            isPlayPressed -> basePlayWeight + (if (hasTopPrev) 0.35f else 0.3f)
            isNextPressed || isTopPrevPressed -> (basePlayWeight - 0.25f).coerceAtLeast(1.5f)
            else -> basePlayWeight
        }

        val targetNextWeight = when {
            isNextPressed -> baseNextWeight + 0.25f
            isPlayPressed -> (baseNextWeight - 0.15f).coerceAtLeast(0.6f)
            else -> baseNextWeight
        }

        val targetPrevWeight = when {
            isTopPrevPressed -> basePrevWeight + 0.25f
            isPlayPressed -> (basePrevWeight - 0.15f).coerceAtLeast(0.6f)
            else -> basePrevWeight
        }

        val playWeight by animateFloatAsState(targetPlayWeight, expressiveSpring, label = "playWeight")
        val nextWeight by animateFloatAsState(targetNextWeight, expressiveSpring, label = "nextWeight")
        val prevWeight by animateFloatAsState(targetPrevWeight, expressiveSpring, label = "prevWeight")

        val playScale by animateFloatAsState(
            targetValue = when {
                isPlayPressed -> 0.98f
                isNextPressed || isTopPrevPressed -> 0.96f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "playScale"
        )

        val nextScale by animateFloatAsState(
            targetValue = when {
                isNextPressed -> 0.98f
                isPlayPressed -> 0.95f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "nextScale"
        )

        val prevScale by animateFloatAsState(
            targetValue = when {
                isTopPrevPressed -> 0.98f
                isPlayPressed -> 0.95f
                else -> 1f
            },
            animationSpec = expressiveSpring,
            label = "prevScale"
        )

        val expressiveSpringDp = spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

        val currentPlayRadius by animateDpAsState(
            targetValue = if (isPlayPressed) (playButtonRadius - 2.dp).coerceAtLeast(14.dp) else playButtonRadius,
            animationSpec = expressiveSpringDp,
            label = "playRadius"
        )
        val currentNextRadius by animateDpAsState(
            targetValue = if (isNextPressed) 28.dp else 32.dp,
            animationSpec = expressiveSpringDp,
            label = "nextRadius"
        )
        val currentPrevRadius by animateDpAsState(
            targetValue = if (isTopPrevPressed) 28.dp else 32.dp,
            animationSpec = expressiveSpringDp,
            label = "prevRadius"
        )

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
        AnimatedVisibility(
            visible = !isShowingLyrics,
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

        Spacer(modifier = Modifier.weight(1f))
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
                scaleY = scale
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
            painter = painterResource(iconId),
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
                scaleY = scale
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
            .background(colorPalette.accent)
    ) {
        AnimatedPlayPauseButton(
            playing = shouldBePlaying,
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp)
        )
    }
}

@Composable
private fun MediaInfo(media: UiMedia) {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = media.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { title ->
            FadingRow(modifier = Modifier.fillMaxWidth(0.75f)) {
                BasicText(
                    text = title,
                    style = typography.l.bold,
                    maxLines = 1
                )
            }
        }

        AnimatedContent(
            targetState = media to artistInfo,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { [media, state] ->
            state?.let { artists ->
                FadingRow(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .heightIn(maxHeight.px.dp),
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
                modifier = Modifier.fillMaxWidth(0.75f),
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
