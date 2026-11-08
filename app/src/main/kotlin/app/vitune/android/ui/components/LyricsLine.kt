package app.vitune.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.vitune.android.preferences.PlayerPreferences
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.vitune.android.lyrics.LyricsEntry
import app.vitune.android.lyrics.WordTimestamp
import kotlinx.coroutines.isActive
import java.text.BreakIterator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private data class HyphenGroupWord(
    val pos: Int,
    val size: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long
)

private fun String.containsRtl(): Boolean {
    for (c in this) {
        val directionality = Character.getDirectionality(c).toInt()
        if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
        ) {
            return true
        }
    }
    return false
}

private fun String.containsJoinedIndic(): Boolean = any { it in '\u0900'..'\u0DFF' }

private fun String.toGraphemeClusters(): List<String> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val it = BreakIterator.getCharacterInstance()
    it.setText(this)
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        result.add(substring(start, end))
        start = end
        end = it.next()
    }
    return result
}

@Composable
fun MetrolistLyricsLine(
    index: Int,
    item: LyricsEntry,
    isSynced: Boolean,
    isActiveLine: Boolean,
    currentPositionState: Long,
    player: Player?,
    expressiveAccent: Color,
    displayedCurrentLineIndex: Int,
    isAutoScrollEnabled: Boolean,
    onSizeChanged: (Int) -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lyricsTextSize = if (item.isBackground) 17f else 25f
    val lyricsLineSpacing = 1.35f

    val defaultAlignment = when (PlayerPreferences.lyricsTextPosition) {
        PlayerPreferences.LyricsTextPosition.Left -> Alignment.Start
        PlayerPreferences.LyricsTextPosition.Center -> Alignment.CenterHorizontally
        PlayerPreferences.LyricsTextPosition.Right -> Alignment.End
    }

    val defaultTextAlign = when (PlayerPreferences.lyricsTextPosition) {
        PlayerPreferences.LyricsTextPosition.Left -> TextAlign.Left
        PlayerPreferences.LyricsTextPosition.Center -> TextAlign.Center
        PlayerPreferences.LyricsTextPosition.Right -> TextAlign.Right
    }

    val agentAlignment = when {
        item.agent == "v1" -> Alignment.Start
        item.agent == "v2" -> Alignment.End
        item.isBackground -> Alignment.CenterHorizontally
        else -> defaultAlignment
    }

    val agentTextAlign = when {
        item.agent == "v1" -> TextAlign.Left
        item.agent == "v2" -> TextAlign.Right
        item.isBackground -> TextAlign.Center
        else -> defaultTextAlign
    }

    val inactiveAlpha = if (item.isBackground) 0.08f else 0.22f
    val activeAlpha = 1.0f
    val focusedAlpha = if (item.isBackground) 0.45f else 0.35f
    val distance = abs(index - displayedCurrentLineIndex)
    val targetAlpha = if (!isSynced || item.isBackground || isActiveLine) {
        activeAlpha
    } else if (isAutoScrollEnabled && displayedCurrentLineIndex >= 0) {
        when (distance) {
            0 -> focusedAlpha
            1 -> 0.35f
            2 -> 0.22f
            3 -> 0.15f
            else -> 0.08f
        }
    } else inactiveAlpha

    val enableBlur = PlayerPreferences.lyricsLineBlur
    val animatedBlur = if (enableBlur) {
        val targetBlur = if (!isSynced || !isAutoScrollEnabled || isActiveLine) 0.dp else when (distance) {
            0 -> 0.dp
            1 -> 2.dp
            2 -> 4.dp
            else -> 6.dp
        }
        animateDpAsState(targetBlur, tween(300), label = "lyricsLineBlur").value
    } else 0.dp

    val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lyricsLineAlpha")
    val animatedScale by animateFloatAsState(if (isActiveLine) 1.04f else 1.0f, tween(300), label = "lyricsLineScale")

    // Luminous soft white with subtle 15% accent tint instead of overwhelming raw neon color
    val activeTextColor = lerp(Color.White, expressiveAccent, 0.15f)
    val lineColor = Color.White.copy(alpha = if (item.isBackground) focusedAlpha else animatedAlpha)

    val mainTextRaw = item.text
    val mainText = if (item.isBackground) mainTextRaw.removePrefix("(").removeSuffix(")") else mainTextRaw

    val lyricStyle = TextStyle(
        fontSize = lyricsTextSize.sp,
        fontWeight = FontWeight.Bold,
        fontStyle = if (item.isBackground) FontStyle.Italic else FontStyle.Normal,
        lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
        letterSpacing = (-0.5).sp,
        textAlign = agentTextAlign,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    val effectiveWords = if (isActiveLine && item.words?.isNotEmpty() == true) {
        item.words
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onSizeChanged(it.height) }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .then(if (enableBlur && animatedBlur > 0.dp) Modifier.blur(animatedBlur) else Modifier)
            .padding(
                horizontal = 24.dp,
                vertical = if (item.isBackground) 4.dp else 10.dp
            ),
        contentAlignment = when {
            item.agent == "v1" -> Alignment.CenterStart
            item.agent == "v2" -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = agentAlignment
        ) {
            if (isSynced && effectiveWords != null && isActiveLine && mainText.isNotBlank()) {
                WordLevelLyrics(
                    mainText = mainText,
                    words = effectiveWords,
                    isActiveLine = isActiveLine,
                    currentPositionState = currentPositionState,
                    player = player,
                    lyricStyle = lyricStyle,
                    lineColor = lineColor,
                    activeTextColor = activeTextColor,
                    expressiveAccent = expressiveAccent,
                    isBackground = item.isBackground,
                    focusedAlpha = focusedAlpha,
                    alignment = agentTextAlign
                )
            } else {
                Text(
                    text = mainText,
                    style = lyricStyle.copy(
                        color = if (isActiveLine) activeTextColor else lineColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WordLevelLyrics(
    mainText: String,
    words: List<WordTimestamp>,
    isActiveLine: Boolean,
    currentPositionState: Long,
    player: Player?,
    lyricStyle: TextStyle,
    lineColor: Color,
    activeTextColor: Color,
    expressiveAccent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var smoothPosition by remember { mutableLongStateOf(currentPositionState) }

    LaunchedEffect(isActiveLine, player) {
        if (isActiveLine && player != null) {
            var lastPlayerPos = player.currentPosition
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    val playerPos = player.currentPosition
                    if (playerPos != lastPlayerPos) {
                        lastPlayerPos = playerPos
                        lastUpdateTime = now
                    }
                    val elapsed = now - lastUpdateTime
                    smoothPosition = lastPlayerPos + (if (player.isPlaying) elapsed else 0)
                }
            }
        }
    }

    LaunchedEffect(isActiveLine, currentPositionState) {
        if (!isActiveLine) {
            smoothPosition = currentPositionState
        }
    }

    val (effectiveWords, effectiveToOriginalIdx) = remember(words, isBackground) {
        words.flatMapIndexed { originalIdx, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 &&
                (!word.hasTrailingSpace || words.size == 1)
            if (shouldSplit) {
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
                    segments.mapIndexed { index, segmentText ->
                        WordTimestamp(
                            text = segmentText,
                            startTime = word.startTime + index * segmentDuration,
                            endTime = word.startTime + (index + 1) * segmentDuration,
                            hasTrailingSpace = if (index == segments.size - 1) word.hasTrailingSpace else false
                        ) to originalIdx
                    }
                } else listOf(word to originalIdx)
            } else listOf(word to originalIdx)
        }.let { data -> data.map { it.first } to data.map { it.second } }
    }

    val graphemeClusters = remember(mainText) { mainText.toGraphemeClusters() }
    val clusterCount = graphemeClusters.size

    val clusterCharOffsets = remember(mainText) {
        IntArray(clusterCount).also { offsets ->
            var charOffset = 0
            graphemeClusters.forEachIndexed { i, cluster ->
                offsets[i] = charOffset
                charOffset += cluster.length
            }
        }
    }

    val charToWordData = remember(mainText, effectiveWords, isBackground, graphemeClusters, clusterCharOffsets) {
        val wordIdxMap = IntArray(clusterCount) { -1 }
        val charInWordMap = IntArray(clusterCount)
        val wordLenMap = IntArray(clusterCount) { 1 }
        var currentPos = 0
        var clCursor = 0
        effectiveWords.forEachIndexed { wordIdx, word ->
            val rawWordText = word.text.let {
                if (isBackground) {
                    var t = it
                    if (wordIdx == 0) t = t.removePrefix("(")
                    if (wordIdx == effectiveWords.size - 1) t = t.removeSuffix(")")
                    t
                } else it
            }
            val indexInMain = mainText.indexOf(rawWordText, currentPos)
            if (indexInMain != -1) {
                val wordEndInMain = indexInMain + rawWordText.length
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < indexInMain) {
                    clCursor++
                }
                val wordClusterIndices = mutableListOf<Int>()
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < wordEndInMain) {
                    wordClusterIndices.add(clCursor)
                    clCursor++
                }
                val wordClusterLen = wordClusterIndices.size
                wordClusterIndices.forEachIndexed { posInWord, clIdx ->
                    wordIdxMap[clIdx] = wordIdx
                    charInWordMap[clIdx] = posInWord
                    wordLenMap[clIdx] = wordClusterLen
                }
                if (clCursor < clusterCount && clusterCharOffsets[clCursor] == wordEndInMain &&
                    wordEndInMain < mainText.length && mainText[wordEndInMain] == ' ') {
                    val spaceClIdx = clCursor
                    wordIdxMap[spaceClIdx] = wordIdx
                    charInWordMap[spaceClIdx] = wordClusterLen
                    wordLenMap[spaceClIdx] = wordClusterLen + 1
                    clCursor++
                }
                currentPos = wordEndInMain
            }
        }
        Triple(wordIdxMap, charInWordMap, wordLenMap)
    }

    val hyphenGroupData = remember(effectiveWords) {
        val map = mutableMapOf<Int, HyphenGroupWord>()
        var currentGroup = mutableListOf<Int>()
        effectiveWords.forEachIndexed { wordIdx, word ->
            currentGroup.add(wordIdx)
            if (!word.text.endsWith("-")) {
                if (currentGroup.size > 1) {
                    val groupSize = currentGroup.size
                    val groupStartMs = (effectiveWords[currentGroup.first()].startTime * 1000).toLong()
                    val groupEndMs = (word.endTime * 1000).toLong()
                    currentGroup.forEachIndexed { pos, idx ->
                        map[idx] = HyphenGroupWord(pos, groupSize, pos == groupSize - 1, groupStartMs, groupEndMs)
                    }
                }
                currentGroup = mutableListOf()
            }
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true
            )
        }

        val letterLayouts = remember(mainText, lyricStyle) {
            graphemeClusters.map { cluster -> textMeasurer.measure(cluster, lyricStyle) }
        }

        val drawWholeLine = remember(mainText) { mainText.containsRtl() || mainText.containsJoinedIndic() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = layoutResult.size.height,
                            maxHeight = layoutResult.size.height
                        )
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .graphicsLayer(clip = false)
        ) {
            if (mainText.isEmpty() || layoutResult.lineCount == 0 || layoutResult.layoutInput.text.isEmpty()) {
                if (mainText.isNotEmpty()) {
                    drawText(layoutResult, color = if (isActiveLine) activeTextColor else lineColor)
                }
                return@Canvas
            }

            try {
                if (!isActiveLine) {
                    drawText(layoutResult, color = lineColor)
                } else {
                    val maxTextOffset = (layoutResult.layoutInput.text.length - 1).coerceAtLeast(0)

                    if (drawWholeLine) {
                        val (wordIdxMap, _, _) = charToWordData
                        val wordFactors = effectiveWords.map { word ->
                            val wStartMs = (word.startTime * 1000).toLong()
                            val wEndMs = (word.endTime * 1000).toLong()
                            val isWordSung = smoothPosition > wEndMs
                            val isWordActive = smoothPosition in wStartMs..wEndMs
                            val sungFactor = if (isWordSung) 1f
                            else if (isWordActive) ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                            else 0f
                            Triple(sungFactor, isWordSung, isWordActive)
                        }

                        drawText(layoutResult, color = lineColor.copy(alpha = focusedAlpha))

                        effectiveWords.indices.forEach { wIdx ->
                            if (wIdx !in wordFactors.indices) return@forEach
                            val (sungFactor, isWordSung, isWordActive) = wordFactors[wIdx]

                            var left = Float.MAX_VALUE
                            var right = Float.MIN_VALUE
                            var top = Float.MAX_VALUE
                            var bottom = Float.MIN_VALUE
                            var found = false

                            for (i in 0 until clusterCount) {
                                if (i in wordIdxMap.indices && wordIdxMap[i] == wIdx && i in clusterCharOffsets.indices) {
                                    val charOffset = clusterCharOffsets[i].coerceIn(0, maxTextOffset)
                                    val bounds = layoutResult.getBoundingBox(charOffset)
                                    left = minOf(left, bounds.left)
                                    right = maxOf(right, bounds.right)
                                    top = minOf(top, bounds.top)
                                    bottom = maxOf(bottom, bounds.bottom)
                                    found = true
                                }
                            }

                            if (found) {
                                if (isWordSung) {
                                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                                        drawText(layoutResult, color = activeTextColor)
                                    }
                                } else if (isWordActive && sungFactor > 0f) {
                                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                                        drawText(layoutResult, color = activeTextColor.copy(alpha = focusedAlpha + (1f - focusedAlpha) * sungFactor))
                                    }
                                }
                            }
                        }
                        return@Canvas
                    }

                    val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
                    val wordFactors = effectiveWords.map { word ->
                        val wStartMs = (word.startTime * 1000).toLong()
                        val wEndMs = (word.endTime * 1000).toLong()
                        val isWordSung = smoothPosition > wEndMs
                        val isWordActive = smoothPosition in wStartMs..wEndMs
                        val sungFactor = if (isWordSung) 1f
                        else if (isWordActive) ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        else 0f
                        Triple(sungFactor, word, isWordSung)
                    }

                    val wordWobbles = FloatArray(words.size)
                    words.forEachIndexed { wordIdx, word ->
                        val startMs = (word.startTime * 1000).toLong()
                        val timeSinceStart = (smoothPosition - startMs).toFloat()
                        val wobble = if (timeSinceStart in 0f..750f) {
                            if (timeSinceStart < 125f) timeSinceStart / 125f
                            else (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                        } else 0f
                        wordWobbles[wordIdx] = wobble
                    }

                    // Main subpixel gradient wipe and draw
                    for (i in 0 until clusterCount) {
                        if (i !in clusterCharOffsets.indices || i !in letterLayouts.indices) continue
                        val charOffset = clusterCharOffsets[i].coerceIn(0, maxTextOffset)
                        val charBounds = layoutResult.getBoundingBox(charOffset)
                        val wordIdx = if (i in wordIdxMap.indices) wordIdxMap[i] else -1
                        val originalWordIdx = if (wordIdx in effectiveToOriginalIdx.indices) effectiveToOriginalIdx[wordIdx] else -1

                        val (sungFactor, wordItem, isWordSung) = if (wordIdx in wordFactors.indices) wordFactors[wordIdx] else Triple(0f, null, false)
                        val wobble = if (originalWordIdx in wordWobbles.indices) wordWobbles[originalWordIdx] else 0f
                        val wobbleX = wobble * 0.025f
                        val wobbleY = wobble * 0.015f

                        val charLp = if (wordItem != null && i in charInWordMap.indices && i in wordLenMap.indices) {
                            val sMs = wordItem.startTime * 1000
                            val dur = (wordItem.endTime * 1000 - wordItem.startTime * 1000).coerceAtLeast(100.0)
                            val wProg = (smoothPosition.toDouble() - sMs) / dur
                            val cInW = charInWordMap[i].toDouble()
                            val wLen = wordLenMap[i].toDouble()
                            ((wProg - cInW / wLen) * wLen).coerceIn(0.0, 1.0).toFloat()
                        } else 0f

                        val shouldGlow = wordItem != null && !isWordSung && sungFactor > 0.001f

                        var crescendoDeltaX = 0f
                        var crescendoDeltaY = 0f
                        val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                        if (groupWord != null) {
                            val p = sungFactor
                            val timeSinceEnd = (smoothPosition - groupWord.groupEndMs).toFloat()
                            val exitDuration = 600f
                            val pOut = (timeSinceEnd / exitDuration).coerceIn(0f, 1f)
                            val peakScale = 0.06f
                            val decay = 3.5f
                            val freq = 5.0f
                            val baseScalePerSegment = 0.012f
                            if (pOut > 0f) {
                                val baseAtEnd = groupWord.pos * baseScalePerSegment
                                val totalAtEnd = baseAtEnd + peakScale
                                val springOut = totalAtEnd * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                                crescendoDeltaX = springOut
                                crescendoDeltaY = springOut
                            } else if (groupWord.isLast) {
                                val base = groupWord.pos * baseScalePerSegment
                                val springPart = peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                                crescendoDeltaX = base + springPart
                                crescendoDeltaY = base + springPart
                            } else {
                                val boost = if (p > 0f) 0.02f * (1f - p) else 0f
                                val base = (groupWord.pos * baseScalePerSegment) + boost
                                crescendoDeltaX = base
                                crescendoDeltaY = base
                            }
                        }

                        val nudgeStrength = 0.038f
                        val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) {
                            nudgeStrength * sin(charLp * PI.toFloat()) * exp(-3f * charLp)
                        } else 0f

                        val charScaleX = 1f + wobbleX + crescendoDeltaX + nudgeScale * 0.3f
                        val charScaleY = 1f + wobbleY + crescendoDeltaY + nudgeScale

                        withTransform({
                            var waveOffset = 0f
                            if (groupWord != null) {
                                val wallTime = System.currentTimeMillis()
                                val adjSmoothPos = smoothPosition
                                val timeInGroup = (adjSmoothPos - groupWord.groupStartMs).toFloat()
                                val timeToGroupEnd = (groupWord.groupEndMs - adjSmoothPos).toFloat()
                                val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) * (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                                if (waveFade > 0.01f) {
                                    val waveSpeed = 0.006f
                                    val waveHeight = 3.24f
                                    val phaseOffset = i * 0.4f
                                    waveOffset = sin(wallTime * waveSpeed + phaseOffset) * waveHeight * waveFade
                                }
                            }

                            translate(left = charBounds.left, top = charBounds.top + waveOffset)
                            if (wordIdx != -1) {
                                scale(
                                    charScaleX,
                                    charScaleY,
                                    pivot = Offset(charBounds.width / 2f, charBounds.height / 2f)
                                )
                            }
                        }) {
                            if (shouldGlow && wordItem != null) {
                                val sMs = wordItem.startTime * 1000
                                val eMs = wordItem.endTime * 1000
                                val dur = eMs - sMs
                                val wordLenText = wordItem.text.length.coerceAtLeast(1)
                                val impactRatio = dur.toFloat() / wordLenText
                                val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                                val impactFactor = (((impactRatio - 100f) / 250f).coerceIn(0f, 1f) * 0.6f + ((dur.toFloat() - 300f) / 1500f).coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f) * fadeFactor
                                if (impactFactor > 0.01f) {
                                    val glowAlpha = (0.45f * impactFactor).coerceIn(0f, 0.5f)
                                    drawText(
                                        letterLayouts[i],
                                        color = expressiveAccent.copy(alpha = glowAlpha),
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = expressiveAccent.copy(alpha = glowAlpha),
                                            blurRadius = 10f * impactFactor
                                        )
                                    )
                                }
                            }
                            val baseAlpha = if (isWordSung || charLp > 0.99f) 1f else (focusedAlpha + (1f - focusedAlpha) * sungFactor)
                            drawText(letterLayouts[i], color = activeTextColor.copy(alpha = if (wordIdx == -1) focusedAlpha else baseAlpha))
                            if (!isWordSung && charLp > 0f) {
                                val fXL = (charBounds.width * charLp).coerceIn(0f, charBounds.width)
                                if (fXL > 0f) {
                                    clipRect(left = 0f, top = 0f, right = fXL, bottom = charBounds.height) {
                                        drawText(letterLayouts[i], color = activeTextColor)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                drawText(layoutResult, color = if (isActiveLine) activeTextColor else lineColor)
            }
        }
    }
}
