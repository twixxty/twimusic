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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val processedBitmaps = remember(bitmap) {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            null
        } else {
            try {
                val width = bitmap.width
                val height = bitmap.height
                val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
                }
                Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, paint)

                val qW = (width / 2).coerceAtLeast(1)
                val qH = (height / 2).coerceAtLeast(1)
                val tl = Bitmap.createBitmap(enhanced, 0, 0, qW, qH)
                val br = Bitmap.createBitmap(enhanced, qW, qH, qW, qH)
                Triple(enhanced.asImageBitmap(), tl.asImageBitmap(), br.asImageBitmap())
            } catch (e: Exception) {
                null
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "lavaLamp")
    val rotationBase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lavaLampRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
            .clipToBounds()
    ) {
        if (processedBitmaps != null) {
            val (enhanced, topLeft, bottomRight) = processedBitmaps

            // Blur container for the morphing blobs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.3f)
                    .blur(blurRadius)
            ) {
                // Base background layer
                Image(
                    bitmap = enhanced,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.2f)
                )

                // Rotating frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-rotationBase * 0.6f)
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
                            .rotate(rotationBase * 1.2f)
                            .scale(2.2f)
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
                            .rotate(rotationBase * 0.67f)
                            .scale(2.2f)
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
