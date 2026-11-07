package app.vitune.android.ui.screens.player

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
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
    showControls: Boolean = true
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

    if (picking && shouldShowSynchronizedLyrics) {
        var query by rememberSaveable {
            mutableStateOf(
                currentMediaMetadataProvider().title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                        .substringBeforeLast('.')
                        .trim()
                    else it
                }
            )
        }

        LrcLibSearchDialog(
            query = query,
            setQuery = { query = it },
            onDismiss = { picking = false },
            onPick = {
                runCatching {
                    transaction {
                        Database.upsert(
                            Lyrics(
                                songId = mediaId,
                                fixed = lyrics?.fixed,
                                synced = it.syncedLyrics
                            )
                        )
                    }
                }
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
                delay(30.milliseconds)
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
                    if (isAnimating) return
                    if (parsedEntries.isEmpty()) return
                    val listTargetIndex = (targetIndex + 1).coerceIn(0, parsedEntries.size + 1)
                    isAnimating = true
                    try {
                        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listTargetIndex }
                        if (itemInfo != null) {
                            val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                            val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                            val itemCenter = itemInfo.offset + itemInfo.size / 2
                            val offset = itemCenter - center
                            if (kotlin.math.abs(offset) > 10) {
                                lazyListState.animateScrollBy(
                                    value = offset.toFloat(),
                                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                                )
                            }
                        } else {
                            lazyListState.scrollToItem(listTargetIndex)
                        }
                    } finally {
                        isAnimating = false
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
                            val targetAlpha = when {
                                isActive -> 1.0f
                                distance == 1 -> 0.35f
                                distance == 2 -> 0.22f
                                distance == 3 -> 0.15f
                                else -> 0.08f
                            }.let { if (entry.isBackground && !isActive) 0.08f else it }
                            val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lineAlpha")
                            val animatedScale by animateFloatAsState(if (isActive) 1.03f else 1.0f, tween(300), label = "lineScale")

                            val (horizontalAlignment, textAlign) = when {
                                entry.agent == "v1" -> Alignment.Start to TextAlign.Left
                                entry.agent == "v2" -> Alignment.End to TextAlign.Right
                                entry.isBackground -> Alignment.CenterHorizontally to TextAlign.Center
                                else -> Alignment.CenterHorizontally to TextAlign.Center
                            }

                            if (entry.text.isBlank()) {
                                val noteColor = if (isActive) colorPalette.accent else Color.White.copy(alpha = 0.35f)
                                Box(
                                    contentAlignment = Alignment.Center,
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
                                val effectiveWords = remember(entry.words, entry.isBackground) {
                                    val words = entry.words
                                    if (words.isNullOrEmpty()) null
                                    else {
                                        words.flatMapIndexed { _, word ->
                                            if (word.text.contains('-') && word.text.length > 1 && (!word.hasTrailingSpace || words.size == 1)) {
                                                val segments = mutableListOf<String>()
                                                var start = 0
                                                for (i in 0 until word.text.length) {
                                                    if (word.text[i] == '-') {
                                                        segments.add(word.text.substring(start, i + 1))
                                                        start = i + 1
                                                    }
                                                }
                                                if (start < word.text.length) {
                                                    segments.add(word.text.substring(start))
                                                }
                                                if (segments.size > 1) {
                                                    val totalDuration = word.endTime - word.startTime
                                                    val segmentDuration = totalDuration / segments.size
                                                    segments.mapIndexed { idx, segmentText ->
                                                        WordTimestamp(
                                                            text = segmentText,
                                                            startTime = word.startTime + idx * segmentDuration,
                                                            endTime = word.startTime + (idx + 1) * segmentDuration,
                                                            hasTrailingSpace = if (idx == segments.size - 1) word.hasTrailingSpace else false
                                                        )
                                                    }
                                                } else listOf(word)
                                            } else listOf(word)
                                        }
                                    }
                                }

                                val annotatedString = remember(entry, isActive, currentPositionMs, colorPalette, effectiveWords) {
                                    if (!isActive || effectiveWords.isNullOrEmpty()) {
                                        buildAnnotatedString {
                                            withStyle(
                                                SpanStyle(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontStyle = if (entry.isBackground) FontStyle.Italic else FontStyle.Normal
                                                )
                                            ) {
                                                append(entry.text)
                                            }
                                        }
                                    } else {
                                        buildAnnotatedString {
                                            effectiveWords.forEach { w ->
                                                val wStartMs = (w.startTime * 1000).toLong()
                                                val wEndMs = (w.endTime * 1000).toLong()

                                                val isWordActive = currentPositionMs in wStartMs..wEndMs
                                                val hasWordPassed = currentPositionMs > wEndMs

                                                val wordColor = when {
                                                    isWordActive -> colorPalette.accent
                                                    hasWordPassed -> Color.White
                                                    else -> Color.White.copy(alpha = 0.35f)
                                                }
                                                val wordWeight = when {
                                                    isWordActive -> FontWeight.ExtraBold
                                                    hasWordPassed -> FontWeight.Bold
                                                    else -> FontWeight.Medium
                                                }

                                                withStyle(
                                                    SpanStyle(
                                                        color = wordColor,
                                                        fontWeight = wordWeight,
                                                        fontStyle = if (entry.isBackground) FontStyle.Italic else FontStyle.Normal
                                                    )
                                                ) {
                                                    append(w.text)
                                                    if (w.hasTrailingSpace) append(" ")
                                                }
                                            }
                                        }
                                    }
                                }

                                val lineFontSize = if (entry.isBackground) 17.sp else 24.sp
                                val lineSpacing = if (entry.isBackground) 24.sp else 32.sp

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 24.dp,
                                            vertical = if (entry.isBackground) 2.dp else 10.dp
                                        ),
                                    horizontalAlignment = horizontalAlignment
                                ) {
                                    BasicText(
                                        text = annotatedString,
                                        style = TextStyle(
                                            fontSize = lineFontSize,
                                            lineHeight = lineSpacing,
                                            letterSpacing = (-0.5).sp,
                                            textAlign = textAlign,
                                            fontFamily = typography.m.fontFamily
                                        ),
                                        modifier = Modifier
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
                                            .animateContentSize()
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
                        textAlign = TextAlign.Center,
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

        if (showControls) {
            if (onOpenDialog != null) Image(
                painter = painterResource(R.drawable.expand),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onOpenDialog()
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomStart)
            )

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
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    val errorMsg = stringResource(R.string.no_browser_installed)
                                    MenuEntry(
                                        icon = R.drawable.search,
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

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.download,
                                            text = stringResource(R.string.pick_from_lrclib),
                                            onClick = {
                                                menuState.hide()
                                                picking = true
                                            }
                                        )
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
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
