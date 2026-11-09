package app.vitune.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated Lava Lamp background ported from Accord's BlendView.
 * Uses saturated quarters of the current album artwork rotating in a frame
 * with heavy blur to create fluid, morphing, Apple Music-style dynamic background.
 */
@Composable
fun LavaLampBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 70.dp,
    overlayAlpha: Float = 0.40f,
    fallbackColor: Color = Color(0xff121216)
) {
    val baseImageBitmap = remember(bitmap) {
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            bitmap.asImageBitmap()
        } else null
    }

    val processedBitmaps = remember(bitmap) {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            null
        } else {
            try {
                // Downscale to 128x128 for ultra-fast, lightweight blur processing
                val targetDim = 128
                val maxDim = maxOf(bitmap.width, bitmap.height)
                val scale = if (maxDim > targetDim) targetDim.toFloat() / maxDim else 1f
                val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                } else {
                    bitmap
                }

                val enhanced = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
                }
                Canvas(enhanced).drawBitmap(scaled, 0f, 0f, paint)

                val qW = (scaledW / 2).coerceAtLeast(1)
                val qH = (scaledH / 2).coerceAtLeast(1)
                val tl = Bitmap.createBitmap(enhanced, 0, 0, qW, qH)
                val br = Bitmap.createBitmap(enhanced, qW, qH, qW, qH)
                Triple(enhanced.asImageBitmap(), tl.asImageBitmap(), br.asImageBitmap())
            } catch (e: Exception) {
                null
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "lavaLamp")
    val frameRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 44000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lavaLampFrameRotation"
    )
    val topLeftRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lavaLampTopLeftRotation"
    )
    val bottomRightRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lavaLampBottomRightRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        if (baseImageBitmap != null) {
            Image(
                bitmap = baseImageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(blurRadius)
            )
        }

        if (processedBitmaps != null) {
            val (enhanced, topLeft, bottomRight) = processedBitmaps

            // Blur container for the morphing blobs with Offscreen compositing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .blur(blurRadius)
            ) {
                // Saturated background wash layer
                Image(
                    bitmap = enhanced,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            alpha = 0.55f
                        }
                )

                // Rotating frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = frameRotation.value
                        }
                ) {
                    // Top-left quarter rotating clockwise
                    Image(
                        bitmap = topLeft,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(320.dp)
                            .align(Alignment.TopStart)
                            .offset(x = (-30).dp, y = (-30).dp)
                            .graphicsLayer {
                                rotationZ = topLeftRotation.value
                                scaleX = 2.2f
                                scaleY = 2.2f
                            }
                    )

                    // Bottom-right quarter rotating clockwise at different speed
                    Image(
                        bitmap = bottomRight,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(320.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 30.dp, y = 30.dp)
                            .graphicsLayer {
                                rotationZ = bottomRightRotation.value
                                scaleX = 2.2f
                                scaleY = 2.2f
                            }
                    )
                }
            }
        }

        // Contrast and gradient overlays for razor-sharp lyrics and control readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.55f)
                    )
                )
        )
    }
}

@Composable
fun BlurredArtworkBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 45.dp,
    overlayAlpha: Float = 0.35f,
    fallbackColor: Color = Color(0xff121216)
) {
    val imageBitmap = remember(bitmap) {
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            bitmap.asImageBitmap()
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.30f
                        scaleY = 1.30f
                    }
                    .blur(blurRadius)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.60f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.50f)
                    )
                )
        )
    }
}
