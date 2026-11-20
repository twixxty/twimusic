package app.vitune.android.ui.components

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.vitune.android.utils.BitmapBlur
import app.vitune.core.ui.LocalAppearance
import kotlin.math.max
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint

private const val SCRIM_COLOR_1 = 0x80000000.toInt()
private const val SCRIM_COLOR_2 = 0x0DFFFFFF

private class AndroidLiquidBlurCache(
    val width: Int,
    val height: Int,
    val blurRadius: Float,
) {
    private val bitmapA: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val bitmapB: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val imageA: ImageBitmap = bitmapA.asImageBitmap()
    private val imageB: ImageBitmap = bitmapB.asImageBitmap()
    private var useA = true

    private val canvasA: AndroidCanvas = AndroidCanvas(bitmapA)
    private val canvasB: AndroidCanvas = AndroidCanvas(bitmapB)

    private val pixels = IntArray(width * height)

    private val workMatrix = Matrix()

    private val layerPaint =
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG)
    private val scrimPaint =
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG).apply {
            style = AndroidPaint.Style.FILL
        }
    private val colorMatrix = ColorMatrix()
    private var cachedSaturation: Float = -1f

    private val w = width.toFloat()
    private val h = height.toFloat()
    private val maxDim = (max(w, h) * 1.3f).roundToInt().toFloat()
    private val pivot = maxDim / 2f
    private val offsetX = -(maxDim - w) / 2f
    private val offsetY = -(maxDim - h) / 2f

    private var lastResult: ImageBitmap? = null
    private var lastActive: Bitmap? = null
    private var lastPrevious: Bitmap? = null
    private var lastAlpha: Float = -1f
    private var lastRotation: Long = Long.MIN_VALUE

    fun render(
        activeBitmap: Bitmap?,
        previousBitmap: Bitmap?,
        crossfadeAlpha: Float,
        rotationTimeMs: Long,
        saturation: Float,
    ): ImageBitmap {
        val cached = lastResult
        if (cached != null &&
            activeBitmap === lastActive &&
            previousBitmap === lastPrevious &&
            crossfadeAlpha == lastAlpha &&
            rotationTimeMs == lastRotation &&
            saturation == cachedSaturation
        ) {
            return cached
        }
        lastActive = activeBitmap
        lastPrevious = previousBitmap
        lastAlpha = crossfadeAlpha
        lastRotation = rotationTimeMs

        useA = !useA
        val targetBmp = if (useA) bitmapA else bitmapB
        val targetCanvas = if (useA) canvasA else canvasB

        targetBmp.eraseColor(0)

        if (cachedSaturation != saturation) {
            cachedSaturation = saturation
            colorMatrix.setSaturation(saturation)
            layerPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        val curRotM = -360f * (rotationTimeMs % 130_000L) / 130_000f
        val curRotN = 360f * (rotationTimeMs % 90_000L) / 90_000f
        val curRotO = 360f * (rotationTimeMs % 70_000L) / 70_000f

        if (previousBitmap != null && crossfadeAlpha < 1f) {
            drawLayers(targetCanvas, previousBitmap, 1f - crossfadeAlpha, curRotM, curRotN, curRotO)
        }
        drawLayers(targetCanvas, activeBitmap, crossfadeAlpha, curRotM, curRotN, curRotO)

        scrimPaint.color = SCRIM_COLOR_1
        targetCanvas.drawPaint(scrimPaint)
        scrimPaint.color = SCRIM_COLOR_2
        targetCanvas.drawPaint(scrimPaint)

        targetBmp.getPixels(pixels, 0, width, 0, 0, width, height)
        BitmapBlur.blurArgb(pixels, width, height, blurRadius)
        targetBmp.setPixels(pixels, 0, width, 0, 0, width, height)

        val result = if (useA) imageA else imageB
        lastResult = result
        return result
    }

    private fun drawLayers(
        targetCanvas: AndroidCanvas,
        srcBitmap: Bitmap?,
        alpha: Float,
        curRotM: Float,
        curRotN: Float,
        curRotO: Float,
    ) {
        if (alpha <= 0f || srcBitmap == null || srcBitmap.isRecycled) return
        val srcH = srcBitmap.height.toFloat()
        if (srcBitmap.width <= 0 || srcH <= 0f) return

        layerPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)

        val scale = maxDim / srcH

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotM, pivot, pivot)
        workMatrix.postTranslate(offsetX, offsetY)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotN, pivot, pivot)
        workMatrix.postTranslate(offsetX - 0.95f * w, offsetY - 0.7f * h)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotO, pivot, pivot)
        workMatrix.postTranslate(offsetX - 0.5f * w, offsetY + 0.7f * h)
        workMatrix.postRotate(curRotO, w / 2f, h / 2f)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)
    }
}

/**
 * Liquid background ported from Metrolist with double-buffered software rendering
 * and lightweight Gaussian blur, keeping CPU and memory overhead minimal (<1MB).
 */
@Composable
fun LiquidBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    overlayAlpha: () -> Float = { 0.40f },
    fallbackColor: Color = Color(0xff121216)
) {
    val (colorPalette) = LocalAppearance.current
    val isLightTheme = colorPalette.background0.luminance() >= 0.5f

    var activeBitmap by remember { mutableStateOf(bitmap) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val crossfade = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap != null && bitmap !== activeBitmap) {
            previousBitmap = activeBitmap
            activeBitmap = bitmap
            crossfade.snapTo(0f)
            crossfade.animateTo(1f, tween(800))
            previousBitmap = null
        } else if (bitmap == null) {
            activeBitmap = null
            previousBitmap = null
        }
    }

    val transition = rememberInfiniteTransition(label = "liquidTransition")
    val liquidTimeProg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(130_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidProgress"
    )

    if (activeBitmap == null && previousBitmap == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(fallbackColor)
        )
        return
    }

    val renderer = remember {
        object {
            var cache: AndroidLiquidBlurCache? = null

            fun draw(
                scope: DrawScope,
                active: Bitmap?,
                previous: Bitmap?,
                alpha: Float,
                timeMs: Long
            ) {
                val width = scope.size.width
                val height = scope.size.height
                if (width <= 0f || height <= 0f) return

                val maxTargetDim = 128f
                val aspect = width / height
                val scaledW: Int
                val scaledH: Int
                if (aspect >= 1f) {
                    scaledW = maxTargetDim.roundToInt()
                    scaledH = (maxTargetDim / aspect).roundToInt().coerceAtLeast(1)
                } else {
                    scaledW = (maxTargetDim * aspect).roundToInt().coerceAtLeast(1)
                    scaledH = maxTargetDim.roundToInt()
                }

                val blurRadius = 14f
                val saturation = 2.5f

                var current = cache
                if (current == null || current.width != scaledW || current.height != scaledH) {
                    current = AndroidLiquidBlurCache(scaledW, scaledH, blurRadius)
                    cache = current
                }

                val renderedBitmap = current.render(
                    activeBitmap = active,
                    previousBitmap = previous,
                    crossfadeAlpha = alpha,
                    rotationTimeMs = timeMs,
                    saturation = saturation
                )

                val bw = (width * 1.3f).roundToInt()
                val bh = (height * 1.3f).roundToInt()
                val tx = -(bw - width.roundToInt()) / 2
                val ty = -(bh - height.roundToInt()) / 2

                scope.drawImage(
                    image = renderedBitmap,
                    dstOffset = IntOffset(tx, ty),
                    dstSize = IntSize(bw, bh),
                    filterQuality = FilterQuality.Low
                )
            }
        }
    }

    val rotationTimeMs = remember(liquidTimeProg, isPlaying) {
        (liquidTimeProg * 130_000).toLong()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (isLightTheme) {
                        drawRect(Color.White.copy(alpha = 0.48f))
                    } else {
                        drawRect(Color.Black.copy(alpha = overlayAlpha()))
                    }
                }
        ) {
            renderer.draw(
                scope = this,
                active = activeBitmap,
                previous = previousBitmap,
                alpha = crossfade.value,
                timeMs = if (isPlaying) rotationTimeMs else 0L
            )
        }
    }
}
