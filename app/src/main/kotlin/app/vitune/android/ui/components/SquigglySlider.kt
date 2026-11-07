package app.vitune.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.35f),
    isPlaying: Boolean = true,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value) }

    val currentValue = if (isDragging) dragPosition else value
    val duration = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val position = (currentValue - valueRange.start).coerceIn(0f, duration)

    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(if (isPlaying) 1f else 0f) }

    val scope = rememberCoroutineScope()

    val waveLength = 70f
    val lineAmplitude = 5f
    val phaseSpeed = 24f

    LaunchedEffect(isPlaying, isDragging) {
        scope.launch {
            val shouldFlatten = !isPlaying || isDragging
            val targetHeight = if (shouldFlatten) 0f else 1f
            val animDuration = if (shouldFlatten) 150 else 200
            val startDelay = if (shouldFlatten) 0L else 30L

            delay(startDelay)

            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(
                    durationMillis = animDuration,
                    easing = LinearEasing,
                ),
            ) {
                heightFraction = this.value
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(valueRange) {
                            detectTapGestures { offset ->
                                val newPosition = (offset.x / size.width) * duration
                                val mappedValue = valueRange.start + newPosition.coerceIn(0f, duration)
                                onValueChange(mappedValue)
                                onValueChangeFinished?.invoke()
                            }
                        }
                        .pointerInput(valueRange) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val newPosition = (offset.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newPosition = (change.position.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                }
                            )
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val strokeWidth = 3.5.dp.toPx()
            val progress = (position / duration).coerceIn(0f, 1f)
            val totalWidth = size.width
            val totalProgressPx = totalWidth * progress
            val centerY = size.height / 2f

            fun computeAmplitude(sign: Float): Float {
                return sign * heightFraction * lineAmplitude
            }

            // Build wavy path for the played segment
            val path = Path()
            val waveStart = -phaseOffset - waveLength / 2f
            val waveEnd = totalProgressPx

            path.moveTo(waveStart, centerY)

            var currentX = waveStart
            var waveSign = 1f
            var currentAmp = computeAmplitude(waveSign)
            val dist = waveLength / 2f

            while (currentX < waveEnd + dist) {
                waveSign = -waveSign
                val nextX = currentX + dist
                val midX = currentX + dist / 2f
                val nextAmp = computeAmplitude(waveSign)

                path.cubicTo(
                    midX,
                    centerY + currentAmp,
                    midX,
                    centerY + nextAmp,
                    nextX,
                    centerY + nextAmp,
                )

                currentAmp = nextAmp
                currentX = nextX
            }

            val clipTop = lineAmplitude + strokeWidth + 2.dp.toPx()

            // 1. Draw played squiggly segment (clipped up to totalProgressPx)
            if (totalProgressPx > 0f) {
                clipRect(
                    left = 0f,
                    top = centerY - clipTop,
                    right = totalProgressPx,
                    bottom = centerY + clipTop,
                ) {
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            // 2. Draw unplayed straight line segment from progress to end
            if (totalProgressPx < totalWidth) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(totalProgressPx, centerY),
                    end = Offset(totalWidth, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // 3. Vertical Bar Thumb `|` matching Metrolist and screenshot
            val barHalfHeight = (lineAmplitude + strokeWidth + 4.dp.toPx())
            val barWidth = 4.dp.toPx()

            drawLine(
                color = activeColor,
                start = Offset(totalProgressPx, centerY - barHalfHeight),
                end = Offset(totalProgressPx, centerY + barHalfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
