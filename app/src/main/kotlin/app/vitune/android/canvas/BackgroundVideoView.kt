package app.vitune.android.canvas

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import app.vitune.android.BuildConfig
import app.vitune.android.utils.TrackRecomposition
import kotlinx.coroutines.delay
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout

private class CanvasFrameSampler(
    private val intervalMs: Long = 100L,
    private val maxSide: Int = 48
) {
    private var lastSampleMs: Long? = null

    fun reset() {
        lastSampleMs = null
    }

    fun sampleSize(nowMs: Long, width: Int, height: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        if (lastSampleMs?.let { nowMs - it < intervalMs } == true) return null
        lastSampleMs = nowMs
        val longestSide = maxOf(width, height)
        val sampleSide = minOf(maxSide, longestSide).toLong()
        return (sampleSide * width / longestSide).toInt().coerceAtLeast(1) to
            (sampleSide * height / longestSide).toInt().coerceAtLeast(1)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun BackgroundVideoView(
    videoUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    fadeInDurationMillis: Int = 800,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    onVideoReady: (Boolean) -> Unit = {},
    onError: () -> Unit = {},
    onFrame: ((ImageBitmap?) -> Unit)? = null
) {
    if (videoUrl.isBlank()) return

    if (BuildConfig.DEBUG) {
        TrackRecomposition("BackgroundVideoView")
    }

    val context = LocalContext.current
    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }
    var videoAspectRatio by remember(videoUrl) { mutableFloatStateOf(1f) }
    val latestOnFrame by rememberUpdatedState(onFrame)
    val latestIsVideoReady by rememberUpdatedState(isVideoReady)
    val frameSampler = remember { CanvasFrameSampler() }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(1080, 1920)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    val isHls = remember(videoUrl) { videoUrl.contains("m3u8", ignoreCase = true) }

    val exoPlayer = remember(isHls) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        val dataSourceFactory = if (isHls) {
            DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)
        } else {
            CanvasVideoCache.dataSourceFactory(context, httpDataSourceFactory)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setTargetBufferBytes(8 * 1024 * 1024)
                    .build()
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = isPlaying
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio =
                        (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    onVideoSizeChanged(videoSize.width, videoSize.height)
                }
            }
            override fun onRenderedFirstFrame() {
                isVideoReady = true
                onVideoReady(true)
            }
            override fun onPlayerError(error: PlaybackException) {
                isVideoReady = false
                frameSampler.reset()
                latestOnFrame?.invoke(null)
                onVideoReady(false)
                onError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(videoUrl) {
        isVideoReady = false
        frameSampler.reset()
        latestOnFrame?.invoke(null)
        onVideoReady(false)
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.playbackParameters = PlaybackParameters(1f)
            exoPlayer.playWhenReady = true
        } else {
            val steps = 8
            val durationMs = 250L
            val stepDelay = durationMs / steps
            for (i in 1..steps) {
                delay(stepDelay)
                val factor = 1f - (i.toFloat() / steps)
                if (factor > 0.05f) {
                    exoPlayer.playbackParameters = PlaybackParameters(factor)
                }
            }
            exoPlayer.playWhenReady = false
            exoPlayer.playbackParameters = PlaybackParameters(1f)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            latestOnFrame?.invoke(null)
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = if (fadeInDurationMillis > 0) tween(fadeInDurationMillis) else snap(),
        label = "canvasVideoAlpha"
    )

    AndroidView(
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                this.resizeMode = resizeMode
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isEnabled = false
                isClickable = false
                isFocusable = false

                val textureView = TextureView(viewContext).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            exoPlayer.setVideoTextureView(this@apply)
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            frameSampler.reset()
                            latestOnFrame?.invoke(null)
                            exoPlayer.clearVideoTextureView(this@apply)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            val callback = latestOnFrame ?: return
                            if (!latestIsVideoReady) return
                            val sample = frameSampler.sampleSize(
                                nowMs = SystemClock.uptimeMillis(),
                                width = this@apply.width,
                                height = this@apply.height
                            ) ?: return
                            val sampledBitmap = runCatching {
                                this@apply.getBitmap(sample.first, sample.second)
                            }.getOrNull()
                            if (sampledBitmap != null) {
                                callback(sampledBitmap.asImageBitmap())
                            }
                        }
                    }
                }
                addView(
                    textureView,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)
                )
                exoPlayer.setVideoTextureView(textureView)
            }
        },
        update = { layout ->
            if (layout.resizeMode != resizeMode) {
                layout.resizeMode = resizeMode
            }
            layout.setAspectRatio(videoAspectRatio)
        },
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
    )
}
