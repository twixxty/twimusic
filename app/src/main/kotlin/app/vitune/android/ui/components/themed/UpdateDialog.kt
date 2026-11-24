package app.vitune.android.ui.components.themed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vitune.android.R
import app.vitune.android.utils.AppUpdater
import app.vitune.android.utils.bold
import app.vitune.android.utils.center
import app.vitune.android.utils.color
import app.vitune.android.utils.getAppIcon
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.style
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.github.models.Release
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    release: Release,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val bestAsset = remember(release) { AppUpdater.findBestAsset(release) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isVisible by remember { mutableStateOf(false) }

    val handleDismiss: () -> Unit = {
        if (isVisible) {
            isVisible = false
            coroutineScope.launch {
                delay(280)
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    BackHandler(enabled = isVisible) {
        handleDismiss()
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (isVisible) 0.55f else 0f,
            animationSpec = tween(280),
            label = "updaterScrimAlpha"
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Darkened backdrop scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = handleDismiss
                    )
            )

            // Slide-up Bottom Sheet
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(250)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(colorPalette.background1)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle pill
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colorPalette.textSecondary.copy(alpha = 0.35f))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Header Row: Sparkles Icon + Update Available & Tag Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colorPalette.accent.copy(alpha = 0.15f))
                            ) {
                                Image(
                                    painter = painterResource(getAppIcon(R.drawable.sparkles)),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colorPalette.accent),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BasicText(
                                        text = stringResource(R.string.app_out_of_date),
                                        style = typography.m.bold.color(colorPalette.text)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colorPalette.accent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        BasicText(
                                            text = release.tag,
                                            style = typography.xxs.semiBold.color(colorPalette.onAccent)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BasicText(
                                        text = "v${AppUpdater.currentVersionName}",
                                        style = typography.xxs.secondary
                                    )
                                    Image(
                                        painter = painterResource(getAppIcon(R.drawable.arrow_forward)),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    BasicText(
                                        text = release.tag,
                                        style = typography.xxs.semiBold.color(colorPalette.accent)
                                    )
                                }
                            }
                        }

                        // Release title if distinct from tag
                        release.name?.takeIf { it.isNotBlank() && it != release.tag }?.let { releaseName ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorPalette.background2.copy(alpha = 0.6f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                BasicText(
                                    text = releaseName,
                                    style = typography.xs.semiBold.color(colorPalette.text)
                                )
                            }
                        }

                        // Changelog section with Markdown rendering
                        release.markdown?.takeIf { it.isNotBlank() }?.let { markdown ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp, max = 260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colorPalette.background2.copy(alpha = 0.7f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownContent(
                                    markdown = markdown.trim(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Download progress or action buttons
                        if (isDownloading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colorPalette.accent,
                                    trackColor = colorPalette.background2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = if (downloadProgress > 0f) {
                                        "${(downloadProgress * 100).toInt()}% ($downloadedMb)"
                                    } else stringResource(R.string.downloading_update),
                                    style = typography.xs.semiBold.center.color(colorPalette.text)
                                )
                            }
                        } else if (downloadedFile != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(colorPalette.accent)
                                    .clickable {
                                        AppUpdater.installApk(context, downloadedFile!!)
                                    }
                            ) {
                                BasicText(
                                    text = stringResource(R.string.install_update),
                                    style = typography.xs.bold.color(colorPalette.onAccent)
                                )
                            }
                        } else if (bestAsset != null) {
                            val sizeMb = remember(bestAsset.size) {
                                "%.1f MB".format(bestAsset.size / (1024f * 1024f))
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(colorPalette.accent)
                                    .clickable {
                                        isDownloading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val result = AppUpdater.downloadAndInstall(
                                                context = context,
                                                asset = bestAsset,
                                                onProgress = { progress, downloaded, total ->
                                                    downloadProgress = progress
                                                    downloadedMb = "%.1f / %.1f MB".format(
                                                        downloaded / (1024f * 1024f),
                                                        total / (1024f * 1024f)
                                                    )
                                                }
                                            )
                                            isDownloading = false
                                            result.fold(
                                                onSuccess = { file -> downloadedFile = file },
                                                onFailure = { e -> errorMessage = e.message }
                                            )
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(getAppIcon(R.drawable.download)),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.onAccent),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicText(
                                        text = "${stringResource(R.string.download_and_install)} ($sizeMb)",
                                        style = typography.xs.bold.color(colorPalette.onAccent)
                                    )
                                }
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(colorPalette.accent)
                                    .clickable {
                                        uriHandler.openUri(release.frontendUrl.toString())
                                    }
                            ) {
                                BasicText(
                                    text = stringResource(R.string.more_information),
                                    style = typography.xs.bold.color(colorPalette.onAccent)
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BasicText(
                                text = errorMessage ?: "",
                                style = typography.xs.secondary.center
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bottom secondary actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(colorPalette.background2)
                                    .clickable { handleDismiss() }
                            ) {
                                BasicText(
                                    text = stringResource(R.string.dismiss),
                                    style = typography.xs.medium.color(colorPalette.text)
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(colorPalette.background2)
                                    .clickable { uriHandler.openUri(release.frontendUrl.toString()) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(getAppIcon(R.drawable.information)),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.text),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    BasicText(
                                        text = stringResource(R.string.more_information),
                                        style = typography.xs.medium.color(colorPalette.text)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rich Markdown renderer supporting headers, bullet lists, numbered lists, blockquotes, code blocks,
 * horizontal rules, bold, italic, inline code, and clickable URLs.
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val lines = remember(markdown) { markdown.lines() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        var inCodeBlock = false
        val codeBlockLines = remember { mutableListOf<String>() }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    val codeContent = codeBlockLines.joinToString("\n")
                    codeBlockLines.clear()
                    inCodeBlock = false
                    CodeBlock(code = codeContent)
                } else {
                    inCodeBlock = true
                }
                continue
            }
            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            if (trimmed.isBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                continue
            }

            when {
                trimmed.startsWith("#### ") -> {
                    HeadingText(text = trimmed.removePrefix("#### ").trim(), level = 4)
                }
                trimmed.startsWith("### ") -> {
                    HeadingText(text = trimmed.removePrefix("### ").trim(), level = 3)
                }
                trimmed.startsWith("## ") -> {
                    HeadingText(text = trimmed.removePrefix("## ").trim(), level = 2)
                }
                trimmed.startsWith("# ") -> {
                    HeadingText(text = trimmed.removePrefix("# ").trim(), level = 1)
                }
                trimmed.startsWith("> ") -> {
                    BlockQuote(text = trimmed.removePrefix("> ").trim())
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") -> {
                    BulletItem(text = trimmed.substring(2).trim())
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val num = trimmed.substringBefore(".")
                    val content = trimmed.substringAfter(".").trim()
                    NumberedItem(number = num, text = content)
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(1.dp)
                            .background(colorPalette.background0.copy(alpha = 0.5f))
                    )
                }
                else -> {
                    MarkdownParagraph(text = line)
                }
            }
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlock(code = codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
private fun HeadingText(
    text: String,
    level: Int,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val (fontSize, fontWeight, color) = when (level) {
        1 -> Triple(17.sp, FontWeight.Bold, colorPalette.text)
        2 -> Triple(15.sp, FontWeight.Bold, colorPalette.accent)
        3 -> Triple(14.sp, FontWeight.SemiBold, colorPalette.accent)
        else -> Triple(13.sp, FontWeight.SemiBold, colorPalette.text)
    }

    MarkdownParagraph(
        text = text,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            fontFamily = typography.m.fontFamily
        ),
        modifier = modifier.padding(top = if (level <= 2) 4.dp else 2.dp)
    )
}

@Composable
private fun BulletItem(
    text: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(colorPalette.accent)
        )
        MarkdownParagraph(
            text = text,
            style = typography.xxs.color(colorPalette.text),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedItem(
    number: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        BasicText(
            text = "$number.",
            style = typography.xxs.semiBold.color(colorPalette.accent)
        )
        MarkdownParagraph(
            text = text,
            style = typography.xxs.color(colorPalette.text),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlockQuote(
    text: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colorPalette.background0.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(colorPalette.accent)
        )
        MarkdownParagraph(
            text = text,
            style = typography.xxs.style(FontStyle.Italic).color(colorPalette.textSecondary),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colorPalette.background0)
            .padding(8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        BasicText(
            text = code,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = colorPalette.textSecondary
            )
        )
    }
}

@Composable
private fun MarkdownParagraph(
    text: String,
    style: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = remember(text, colorPalette) {
        parseMarkdownSpans(
            text = text,
            accentColor = colorPalette.accent,
            codeBgColor = colorPalette.background0
        )
    }

    BasicText(
        text = annotated,
        style = style.let {
            if (it.color == Color.Unspecified) it.copy(color = colorPalette.text) else it
        },
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layout ->
                    val offset = layout.getOffsetForPosition(pos)
                    annotated.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            runCatching { uriHandler.openUri(annotation.item) }
                        }
                }
            }
        }
    )
}

/**
 * Parses inline markdown tokens: `[label](url)`, `code`, **bold**, *italic*, and raw URLs.
 */
private fun parseMarkdownSpans(
    text: String,
    accentColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""(\[(.+?)\]\((https?://[^\s)]+)\))|(`([^`]+)`)|\*\*([^*]+)\*\*|\*([^*]+)\*|(https?://[^\s)]+)""")
    var currentIndex = 0
    val matches = regex.findAll(text)

    for (match in matches) {
        val range = match.range
        if (range.first > currentIndex) {
            append(text.substring(currentIndex, range.first))
        }

        when {
            // [label](url)
            match.groupValues[1].isNotEmpty() -> {
                val label = match.groupValues[2]
                val url = match.groupValues[3]
                val start = length
                append(label)
                val end = length
                addStyle(
                    SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    end
                )
                addStringAnnotation("URL", url, start, end)
            }
            // `code`
            match.groupValues[4].isNotEmpty() -> {
                val code = match.groupValues[5]
                val start = length
                append(code)
                val end = length
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                        fontSize = 12.sp
                    ),
                    start,
                    end
                )
            }
            // **bold**
            match.groupValues[6].isNotEmpty() -> {
                val boldText = match.groupValues[6]
                val start = length
                append(boldText)
                val end = length
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            // *italic*
            match.groupValues[7].isNotEmpty() -> {
                val italicText = match.groupValues[7]
                val start = length
                append(italicText)
                val end = length
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
            // Raw URL
            match.groupValues[8].isNotEmpty() -> {
                val url = match.groupValues[8]
                val start = length
                append(url)
                val end = length
                addStyle(
                    SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    end
                )
                addStringAnnotation("URL", url, start, end)
            }
        }
        currentIndex = range.last + 1
    }

    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}
