package app.vitune.android.ui.screens.player

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import app.vitune.android.lyrics.LyricsProviderRegistry
import app.vitune.android.preferences.LyricsPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.vitune.android.lyrics.LyricsEntry
import app.vitune.android.lyrics.LyricsUtils
import app.vitune.android.lyrics.WordTimestamp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.vitune.android.Database
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Lyrics
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.query
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.android.transaction
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.MetrolistLyricsLine
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.Menu
import app.vitune.android.ui.components.themed.MenuEntry
import app.vitune.android.ui.components.themed.TextField
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.components.themed.TextPlaceholder
import app.vitune.android.ui.components.themed.ValueSelectorDialogBody
import app.vitune.android.ui.modifiers.verticalFadingEdge
import app.vitune.android.utils.SynchronizedLyrics
import app.vitune.android.utils.SynchronizedLyricsState
import app.vitune.android.utils.center
import app.vitune.android.utils.color
import app.vitune.android.utils.isInPip
import app.vitune.android.utils.medium
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.toast
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.onOverlay
import app.vitune.core.ui.onOverlayShimmer
import app.vitune.core.ui.overlay
import app.vitune.core.ui.utils.dp
import app.vitune.android.lyrics.LyricsHelper
import app.vitune.providers.lrclib.LrcLib
import app.vitune.providers.lrclib.LrcParser
import app.vitune.providers.lrclib.models.Track
import app.vitune.providers.lrclib.toLrcFile
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val UPDATE_DELAY = 50L

@Composable
fun Lyrics(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuLaunch: () -> Unit = { },
    onOpenDialog: (() -> Unit)? = null,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    shouldKeepScreenAwake: Boolean = PlayerPreferences.lyricsKeepScreenAwake,
    shouldUpdateLyrics: Boolean = true,
    showControls: Boolean = true,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val currentEnsureSongInserted by rememberUpdatedState(ensureSongInserted)
    val currentMediaMetadataProvider by rememberUpdatedState(mediaMetadataProvider)
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val density = LocalDensity.current
    val view = LocalView.current

    val pip = isInPip()

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }

    val showSynchronizedLyrics = remember(shouldShowSynchronizedLyrics, lyrics) {
        shouldShowSynchronizedLyrics && lyrics?.synced?.isBlank() != true
    }

    var editing by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var picking by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var error by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }

    val text = remember(lyrics, showSynchronizedLyrics) {
        if (showSynchronizedLyrics) lyrics?.synced else lyrics?.fixed
    }
    var invalidLrc by remember(text) { mutableStateOf(false) }

    DisposableEffect(shouldKeepScreenAwake) {
        view.keepScreenOn = shouldKeepScreenAwake

        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(mediaId, shouldShowSynchronizedLyrics) {
        runCatching {
            withContext(Dispatchers.IO) {
                Database
                    .lyrics(mediaId)
                    .distinctUntilChanged()
                    .cancellable()
                    .collect { currentLyrics ->
                        val hasSufficientLyrics = currentLyrics != null && (
                            (!shouldShowSynchronizedLyrics && currentLyrics.fixed?.isNotBlank() == true) ||
                            (shouldShowSynchronizedLyrics && currentLyrics.synced?.isNotBlank() == true)
                        )

                        if (!shouldUpdateLyrics || hasSufficientLyrics) {
                            lyrics = currentLyrics
                        } else {
                            val mediaMetadata = currentMediaMetadataProvider()
                            var duration =
                                withContext(Dispatchers.Main) { currentDurationProvider() }

                            while (duration == C.TIME_UNSET) {
                                delay(100.milliseconds)
                                duration =
                                    withContext(Dispatchers.Main) { currentDurationProvider() }
                            }

                            val album = mediaMetadata.albumTitle?.toString()
                            val artist = mediaMetadata.artist?.toString().orEmpty()
                            val title = mediaMetadata.title?.toString().orEmpty().let {
                                if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                                    .substringBeforeLast('.')
                                    .trim()
                                else it
                            }

                            lyrics = null
                            error = false

                            val fetched = LyricsHelper.getLyrics(
                                context = context,
                                videoId = mediaId,
                                title = title,
                                artist = artist,
                                durationSeconds = (duration / 1000).toInt(),
                                album = album
                            )

                            val rawLyrics = fetched.lyrics
                            val isSynced = rawLyrics.contains(Regex("\\[\\d+:\\d+"))
                            val plainLyrics = if (isSynced) {
                                rawLyrics.lines()
                                    .map { it.replace(Regex("\\[\\d+:\\d+[^]]*\\]|<[^>]+>"), "").trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n")
                            } else rawLyrics

                            val fixed = currentLyrics?.fixed?.takeIf { it.isNotBlank() } ?: plainLyrics.ifBlank { null }
                            val synced = if (isSynced) rawLyrics else currentLyrics?.synced?.takeIf { it.isNotBlank() }

                            val updatedLyrics = Lyrics(
                                songId = mediaId,
                                fixed = fixed.orEmpty(),
                                synced = synced.orEmpty()
                            )
                            lyrics = updatedLyrics
                            this@withContext.ensureActive()

                            transaction {
                                runCatching {
                                    currentEnsureSongInserted()
                                    Database.upsert(updatedLyrics)
                                }
                            }
                        }

                        error =
                            (shouldShowSynchronizedLyrics && lyrics?.synced?.isBlank() == true) ||
                            (!shouldShowSynchronizedLyrics && lyrics?.fixed?.isBlank() == true)
                    }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    if (editing) TextFieldDialog(
        hintText = stringResource(R.string.enter_lyrics),
        initialTextInput = (if (shouldShowSynchronizedLyrics) lyrics?.synced else lyrics?.fixed)
            .orEmpty(),
        singleLine = false,
        maxLines = 10,
        isTextInputValid = { true },
        onDismiss = { editing = false },
        onAccept = {
            transaction {
                runCatching {
                    currentEnsureSongInserted()

                    Database.upsert(
                        if (shouldShowSynchronizedLyrics) Lyrics(
                            songId = mediaId,
                            fixed = lyrics?.fixed,
                            synced = it
                        ) else Lyrics(
                            songId = mediaId,
                            fixed = it,
                            synced = lyrics?.synced
                        )
                    )
                }
            }
        }
    )

    if (picking) {
        val meta = currentMediaMetadataProvider()
        val songTitle = meta.title?.toString().orEmpty().let {
            if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                .substringBeforeLast('.')
                .trim()
            else it
        }
        val songArtist = meta.artist?.toString().orEmpty()
        val durationSecs = (currentDurationProvider() / 1000).toInt()

        MultiProviderSearchDialog(
            initialTitle = songTitle,
            initialArtist = songArtist,
            duration = durationSecs,
            mediaId = mediaId,
            onDismiss = { picking = false },
            onPick = { result ->
                runCatching {
                    transaction {
                        Database.upsert(
                            Lyrics(
                                songId = mediaId,
                                fixed = if (result.isSynced) lyrics?.fixed else result.lyrics,
                                synced = if (result.isSynced) result.lyrics else lyrics?.synced
                            )
                        )
                    }
                }
                if (result.isSynced) {
                    setShouldShowSynchronizedLyrics(true)
                }
                context.toast("Applied lyrics from ${result.provider}")
                picking = false
            }
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val containerMaxHeight = this.maxHeight
        val animatedHeight by animateDpAsState(
            targetValue = containerMaxHeight,
            label = ""
        )

        AnimatedVisibility(
            visible = error,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(
                    if (shouldShowSynchronizedLyrics) R.string.synchronized_lyrics_not_available
                    else R.string.lyrics_not_available
                ),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = !text.isNullOrBlank() && !error && invalidLrc && shouldShowSynchronizedLyrics,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(R.string.invalid_synchronized_lyrics),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        val parsedEntries = remember(lyrics?.synced) {
            val synced = lyrics?.synced
            if (!synced.isNullOrBlank()) {
                LyricsUtils.parseLyrics(synced)
            } else {
                emptyList()
            }
        }

        invalidLrc = showSynchronizedLyrics && !lyrics?.synced.isNullOrBlank() && parsedEntries.isEmpty()

        var currentPositionMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(binder?.player) {
            while (true) {
                val player = binder?.player
                currentPositionMs = (player?.currentPosition ?: 0L) - (lyrics?.startTime ?: 0L)
                delay(100.milliseconds)
            }
        }

        val activeIndex = remember(parsedEntries, currentPositionMs) {
            if (parsedEntries.isEmpty()) -1
            else parsedEntries.indexOfLast { it.time <= currentPositionMs }
        }

        AnimatedContent(
            targetState = showSynchronizedLyrics && parsedEntries.isNotEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { synchronized ->
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            var isAnimating by remember { mutableStateOf(false) }
            var isAutoScrollEnabled by rememberSaveable { mutableStateOf(true) }
            var lastPreviewTime by remember { mutableLongStateOf(0L) }

            if (synchronized) {
                LaunchedEffect(lastPreviewTime) {
                    if (lastPreviewTime != 0L) {
                        delay(4000.milliseconds)
                        isAutoScrollEnabled = true
                        lastPreviewTime = 0L
                    }
                }

                suspend fun performSmoothPageScroll(
                    targetIndex: Int,
                    duration: Int = 800
                ) {
                    if (parsedEntries.isEmpty()) return
                    val listTargetIndex = (targetIndex + 1).coerceIn(0, parsedEntries.size + 1)
                    val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listTargetIndex }
                    val itemHeight = itemInfo?.size ?: (density.density * 50).toInt()
                    val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)

                    if (itemInfo != null) {
                        val itemCenter = itemInfo.offset + itemInfo.size / 2
                        val offset = itemCenter - center
                        if (kotlin.math.abs(offset) > 4) {
                            try {
                                lazyListState.animateScrollBy(
                                    value = offset.toFloat(),
                                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                                )
                            } catch (_: Throwable) {}
                        }
                    } else {
                        try {
                            lazyListState.animateScrollToItem(
                                index = listTargetIndex,
                                scrollOffset = -((viewportHeight / 2) - (itemHeight / 2))
                            )
                        } catch (_: Throwable) {}
                    }
                }

                LaunchedEffect(activeIndex, isAutoScrollEnabled) {
                    if (isAutoScrollEnabled && activeIndex >= 0 && activeIndex < parsedEntries.size) {
                        performSmoothPageScroll(activeIndex, duration = 800)
                    }
                }

                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (source == NestedScrollSource.UserInput) {
                                isAutoScrollEnabled = false
                                lastPreviewTime = System.currentTimeMillis()
                            }
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            isAutoScrollEnabled = false
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = true,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .verticalFadingEdge()
                            .nestedScroll(nestedScrollConnection)
                            .fillMaxSize()
                    ) {
                        item(key = "header") {
                            Spacer(modifier = Modifier.height(containerMaxHeight * 0.35f))
                        }
                        itemsIndexed(
                            items = parsedEntries,
                            key = { index, entry -> "${entry.time}_$index" }
                        ) { index, entry ->
                            val isActive = index == activeIndex
                            val distance = abs(index - activeIndex)

                            val defaultAlignment = when (PlayerPreferences.lyricsTextPosition) {
                                PlayerPreferences.LyricsTextPosition.Left -> Alignment.Start to TextAlign.Left
                                PlayerPreferences.LyricsTextPosition.Center -> Alignment.CenterHorizontally to TextAlign.Center
                                PlayerPreferences.LyricsTextPosition.Right -> Alignment.End to TextAlign.Right
                            }

                            val (horizontalAlignment, textAlign) = when {
                                entry.agent == "v1" -> Alignment.Start to TextAlign.Left
                                entry.agent == "v2" -> Alignment.End to TextAlign.Right
                                entry.isBackground -> Alignment.CenterHorizontally to TextAlign.Center
                                else -> defaultAlignment
                            }

                            if (entry.text.isBlank()) {
                                val targetAlpha = when {
                                    isActive -> 1.0f
                                    distance == 1 -> 0.35f
                                    distance == 2 -> 0.22f
                                    distance == 3 -> 0.15f
                                    else -> 0.08f
                                }.let { if (entry.isBackground && !isActive) 0.08f else it }
                                val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lineAlpha")
                                val animatedScale by animateFloatAsState(if (isActive) 1.03f else 1.0f, tween(300), label = "lineScale")
                                val noteColor = if (isActive) colorPalette.accent else Color.White.copy(alpha = 0.35f)
                                Box(
                                    contentAlignment = when {
                                        entry.agent == "v1" -> Alignment.CenterStart
                                        entry.agent == "v2" -> Alignment.CenterEnd
                                        else -> when (PlayerPreferences.lyricsTextPosition) {
                                            PlayerPreferences.LyricsTextPosition.Left -> Alignment.CenterStart
                                            PlayerPreferences.LyricsTextPosition.Right -> Alignment.CenterEnd
                                            else -> Alignment.Center
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                            alpha = animatedAlpha
                                        }
                                        .clickable(
                                            indication = ripple(bounded = false),
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = {
                                                binder?.player?.seekTo(entry.time)
                                                coroutineScope.launch {
                                                    performSmoothPageScroll(index, duration = 800)
                                                }
                                            }
                                        )
                                        .padding(vertical = 12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.musical_notes),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(noteColor),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                if (PlayerPreferences.lyricsAnimationStyle == PlayerPreferences.LyricsAnimationStyle.Classic) {
                                    val textColor = if (isActive) colorPalette.onOverlay else colorPalette.onOverlay.copy(alpha = 0.5f)
                                    BasicText(
                                        text = entry.text,
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = textColor,
                                            textAlign = textAlign
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                indication = ripple(bounded = false),
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
                                                    binder?.player?.seekTo(entry.time)
                                                    coroutineScope.launch {
                                                        performSmoothPageScroll(index, duration = 800)
                                                    }
                                                }
                                            )
                                            .padding(vertical = 10.dp, horizontal = 24.dp)
                                    )
                                } else {
                                    MetrolistLyricsLine(
                                        index = index,
                                        item = entry,
                                        isSynced = true,
                                        isActiveLine = isActive,
                                        currentPositionState = currentPositionMs,
                                        player = binder?.player,
                                        expressiveAccent = colorPalette.accent,
                                        displayedCurrentLineIndex = activeIndex,
                                        isAutoScrollEnabled = isAutoScrollEnabled,
                                        onClick = {
                                            binder?.player?.seekTo(entry.time)
                                            coroutineScope.launch {
                                                performSmoothPageScroll(index, duration = 800)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        item(key = "footer") {
                            Spacer(modifier = Modifier.height(containerMaxHeight * 0.5f))
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAutoScrollEnabled,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colorPalette.accent)
                                .clickable {
                                    isAutoScrollEnabled = true
                                    lastPreviewTime = 0L
                                    coroutineScope.launch {
                                        performSmoothPageScroll(activeIndex, duration = 800)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.onAccent),
                                modifier = Modifier.size(16.dp)
                            )
                            BasicText(
                                text = "Sync",
                                style = typography.xs.semiBold.color(colorPalette.onAccent)
                            )
                        }
                    }
                }
            } else {
                BasicText(
                    text = lyrics?.fixed.orEmpty(),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp,
                        textAlign = when (PlayerPreferences.lyricsTextPosition) {
                            PlayerPreferences.LyricsTextPosition.Left -> TextAlign.Start
                            PlayerPreferences.LyricsTextPosition.Center -> TextAlign.Center
                            PlayerPreferences.LyricsTextPosition.Right -> TextAlign.End
                        },
                        color = colorPalette.onOverlay,
                        fontFamily = typography.m.fontFamily
                    ),
                    modifier = Modifier
                        .verticalFadingEdge()
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .padding(vertical = containerMaxHeight * 0.3f, horizontal = 24.dp)
                )
            }
        }

        if (text == null && !error) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.shimmer()
        ) {
            repeat(4) {
                TextPlaceholder(
                    color = colorPalette.onOverlayShimmer,
                    modifier = Modifier.alpha(1f - it * 0.2f)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(350, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(300, easing = FastOutSlowInEasing))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val expandAction = onToggleFullscreen ?: onOpenDialog
                if (PlayerPreferences.showLyricsFullscreenButton && expandAction != null) {
                    AnimatedContent(
                        targetState = isFullscreen,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                        label = "FullscreenToggleIcon",
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) { fullscreen ->
                        Image(
                            painter = painterResource(if (fullscreen) R.drawable.contract else R.drawable.expand),
                            contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen lyrics",
                            colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .clickable(
                                    indication = ripple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        expandAction()
                                    }
                                )
                                .padding(all = 8.dp)
                                .size(20.dp)
                        )
                    }
                }

            Image(
                painter = painterResource(R.drawable.ellipsis_horizontal),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onMenuLaunch()
                            menuState.display {
                                Menu {
                                    MenuEntry(
                                        icon = R.drawable.time,
                                        text = stringResource(
                                            if (shouldShowSynchronizedLyrics) R.string.show_unsynchronized_lyrics
                                            else R.string.show_synchronized_lyrics
                                        ),
                                        secondaryText = if (shouldShowSynchronizedLyrics) null
                                        else stringResource(R.string.provided_lyrics_by),
                                        onClick = {
                                            menuState.hide()
                                            setShouldShowSynchronizedLyrics(!shouldShowSynchronizedLyrics)
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.search_lyrics_providers),
                                        onClick = {
                                            menuState.hide()
                                            picking = true
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.copy,
                                        text = stringResource(R.string.copy_lyrics),
                                        enabled = !text.isNullOrBlank(),
                                        onClick = {
                                            menuState.hide()
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val plainText = if (showSynchronizedLyrics && text != null) {
                                                text.lines().map { line ->
                                                    line.replace(Regex("""\[\d+:\d+(\.\d+)?\]"""), "").trim()
                                                }.filter { it.isNotBlank() }.joinToString("\n")
                                            } else {
                                                text.orEmpty()
                                            }
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics", plainText))
                                            context.toast(context.getString(R.string.lyrics_copied))
                                        }
                                    )

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
                                            enabled = lyrics != null,
                                            onClick = {
                                                menuState.hide()
                                                lyrics?.let {
                                                    val startTime = binder?.player?.currentPosition
                                                    query {
                                                        Database.upsert(it.copy(startTime = startTime))
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    val errorMsg = stringResource(R.string.no_browser_installed)
                                    MenuEntry(
                                        icon = R.drawable.globe,
                                        text = stringResource(R.string.search_lyrics_online),
                                        onClick = {
                                            menuState.hide()
                                            val mediaMetadata = currentMediaMetadataProvider()

                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(
                                                            SearchManager.QUERY,
                                                            "${mediaMetadata.title} ${mediaMetadata.artist} lyrics"
                                                        )
                                                    }
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                context.toast(errorMsg)
                                            }
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.sync,
                                        text = stringResource(R.string.refetch_lyrics),
                                        enabled = lyrics != null,
                                        onClick = {
                                            menuState.hide()

                                            transaction {
                                                runCatching {
                                                    currentEnsureSongInserted()

                                                    Database.upsert(
                                                        if (shouldShowSynchronizedLyrics) Lyrics(
                                                            songId = mediaId,
                                                            fixed = lyrics?.fixed,
                                                            synced = null
                                                        ) else Lyrics(
                                                            songId = mediaId,
                                                            fixed = null,
                                                            synced = lyrics?.synced
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
            )
            }
        }
    }
}

@Composable
fun LrcLibSearchDialog(
    query: String,
    setQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val [_, typography] = LocalAppearance.current

    val tracks = remember { mutableStateListOf<Track>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        error = false

        delay(1000.milliseconds)

        LrcLib.lyrics(
            query = query,
            synced = true
        )?.onSuccess { newTracks ->
            tracks.clear()
            tracks.addAll(newTracks.filter { !it.syncedLyrics.isNullOrBlank() })
            loading = false
            error = false
        }?.onFailure {
            loading = false
            error = true
            it.printStackTrace()
        } ?: run { loading = false }
    }

    TextField(
        value = query,
        onValueChange = setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        maxLines = 1,
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        error || tracks.isEmpty() -> BasicText(
            text = stringResource(R.string.no_lyrics_found),
            style = typography.s.semiBold.center,
            modifier = Modifier
                .padding(all = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        else -> ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = stringResource(R.string.choose_lyric_track),
            selectedValue = null,
            values = tracks.toImmutableList(),
            onValueSelect = {
                transaction {
                    onPick(it)
                    onDismiss()
                }
            },
            valueText = {
                "${it.artistName} - ${it.trackName} (${
                    it.duration.seconds.toComponents { minutes, seconds, _ ->
                        "$minutes:${seconds.toString().padStart(2, '0')}"
                    }
                })"
            }
        )
    }
}

data class LyricsSearchResult(
    val provider: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int = -1,
    val lyrics: String,
    val isSynced: Boolean
)

@Composable
fun MultiProviderSearchDialog(
    initialTitle: String,
    initialArtist: String,
    duration: Int,
    mediaId: String,
    onDismiss: () -> Unit,
    onPick: (LyricsSearchResult) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current

    var query by rememberSaveable {
        mutableStateOf(if (initialArtist.isNotBlank()) "$initialTitle $initialArtist" else initialTitle)
    }
    val results = remember { mutableStateListOf<LyricsSearchResult>() }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            results.clear()
            loading = false
            return@LaunchedEffect
        }

        loading = true
        results.clear()
        delay(600.milliseconds)

        val parts = q.split(" - ", limit = 2)
        val (parsedTitle, parsedArtist) = if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            q to initialArtist
        }

        withContext(Dispatchers.IO) {
            // 1. Search LrcLib for tracks
            runCatching {
                LrcLib.lyrics(query = q, synced = true)?.getOrNull()?.let { tracks ->
                    val lrclibResults = tracks.mapNotNull { track ->
                        val synced = track.syncedLyrics
                        val plain = track.plainLyrics
                        if (!synced.isNullOrBlank()) {
                            LyricsSearchResult(
                                provider = "LrcLib",
                                title = track.trackName,
                                artist = track.artistName,
                                durationSeconds = track.duration.toInt(),
                                lyrics = synced,
                                isSynced = true
                            )
                        } else if (!plain.isNullOrBlank()) {
                            LyricsSearchResult(
                                provider = "LrcLib",
                                title = track.trackName,
                                artist = track.artistName,
                                durationSeconds = track.duration.toInt(),
                                lyrics = plain,
                                isSynced = false
                            )
                        } else null
                    }
                    withContext(Dispatchers.Main) {
                        results.addAll(lrclibResults)
                    }
                }
            }

            // 2. Query other registered providers
            val providers = LyricsProviderRegistry.getOrderedProviders(LyricsPreferences.lyricsProviderOrder)
                .filter { it.name != "LrcLib" && it.isEnabled(context) }

            for (provider in providers) {
                runCatching {
                    val lyricText = provider.getLyrics(
                        context = context,
                        id = mediaId,
                        title = parsedTitle,
                        artist = parsedArtist,
                        duration = duration,
                        album = null
                    ).getOrNull()

                    if (!lyricText.isNullOrBlank()) {
                        val isSynced = lyricText.contains(Regex("""\[\d+:\d+(\.\d+)?\]"""))
                        withContext(Dispatchers.Main) {
                            results.add(
                                LyricsSearchResult(
                                    provider = provider.name,
                                    title = parsedTitle,
                                    artist = if (parsedArtist.isNotBlank()) parsedArtist else provider.name,
                                    durationSeconds = duration,
                                    lyrics = lyricText,
                                    isSynced = isSynced
                                )
                            )
                        }
                    }
                }
            }
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        BasicText(
            text = stringResource(R.string.search_lyrics_providers),
            style = typography.m.semiBold.color(colorPalette.text),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            maxLines = 1,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (loading && results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!loading && results.isEmpty()) {
            BasicText(
                text = stringResource(R.string.no_lyrics_found),
                style = typography.s.semiBold.center.color(colorPalette.textSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                items(results.size) { i ->
                    val item = results[i]
                    val previewLines = item.lyrics
                        .lines()
                        .map { it.replace(Regex("""\[\d+:\d+(\.\d+)?\]"""), "").trim() }
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString(" • ")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = ripple(bounded = true),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onPick(item) }
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BasicText(
                                    text = item.provider,
                                    style = typography.xxs.semiBold.color(colorPalette.onAccent),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colorPalette.accent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                if (item.isSynced) {
                                    BasicText(
                                        text = "SYNCED",
                                        style = typography.xxs.semiBold.color(colorPalette.accent),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colorPalette.accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (item.durationSeconds > 0) {
                                    val mins = item.durationSeconds / 60
                                    val secs = item.durationSeconds % 60
                                    BasicText(
                                        text = "%d:%02d".format(mins, secs),
                                        style = typography.xxs.color(colorPalette.textSecondary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            BasicText(
                                text = "${item.artist} - ${item.title}",
                                style = typography.s.semiBold.color(colorPalette.text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (previewLines.isNotBlank()) {
                                BasicText(
                                    text = previewLines,
                                    style = typography.xs.color(colorPalette.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
