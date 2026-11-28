/*
 * Copyright (C) 2026 twixxty
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package app.vitune.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.vitune.android.preferences.PlayerPreferences

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

    val waveLength = 70f
    val lineAmplitude = 5f
    val phaseSpeed = 24f

    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phaseOffset = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = waveLength,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = ((waveLength / phaseSpeed) * 1000).toInt(),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "phaseOffset"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val heightFraction by animateFloatAsState(
        targetValue = if (!isPlaying || isDragging) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "heightFraction"
    )

    val cachedPath = remember { Path() }

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
            cachedPath.reset()
            val waveStart = -phaseOffset.value - waveLength / 2f
            val waveEnd = totalProgressPx

            cachedPath.moveTo(waveStart, centerY)

            var currentX = waveStart
            var waveSign = 1f
            var currentAmp = computeAmplitude(waveSign)
            val dist = waveLength / 2f

            while (currentX < waveEnd + dist) {
                waveSign = -waveSign
                val nextX = currentX + dist
                val midX = currentX + dist / 2f
                val nextAmp = computeAmplitude(waveSign)

                cachedPath.cubicTo(
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
                        path = cachedPath,
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

@Composable
fun TaperedWaveSlider(
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

    val waveLength = 64f
    val maxAmplitude = 9.dp
    val phaseSpeed = 22f

    val infiniteTransition = rememberInfiniteTransition(label = "taperedWave")
    val twoPi = (2f * Math.PI).toFloat()
    val phaseOffset = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2400,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "taperedPhaseOffset"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val heightFraction by animateFloatAsState(
        targetValue = if (!isPlaying || isDragging) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "taperedHeightFraction"
    )

    val cachedPath = remember { Path() }

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
            val strokeWidth = 3.2.dp.toPx()
            val progress = (position / duration).coerceIn(0f, 1f)
            val totalWidth = size.width
            val totalProgressPx = totalWidth * progress
            val centerY = size.height / 2f
            val maxAmpPx = maxAmplitude.toPx()

            // 1. Draw played tapered wave segment
            if (totalProgressPx > 0f) {
                cachedPath.reset()

                val freqMultiplier = PlayerPreferences.taperedWaveFrequency.multiplier
                val wavelengthPx = (32.dp.toPx() / (freqMultiplier / 2.5f)).coerceAtLeast(14.dp.toPx())
                val waveLengthPref = PlayerPreferences.taperedWaveLength
                val waveReach = if (waveLengthPref.cycles.isInfinite()) {
                    totalProgressPx.coerceAtLeast(1f)
                } else {
                    (wavelengthPx * waveLengthPref.cycles).coerceAtLeast(waveLengthPref.minDp.dp.toPx())
                }

                // Gentle ramp-in right at 0:00 so very small progress never spikes vertically
                val progressScale = (totalProgressPx / 24.dp.toPx()).coerceIn(0f, 1f)
                val smoothScale = progressScale * progressScale * (3f - 2f * progressScale)

                val step = (wavelengthPx / 20f).coerceIn(4f, 10f)
                val k = (2f * Math.PI.toFloat()) / wavelengthPx
                val minEnvelope = 0.08f

                fun waveY(xPos: Float): Float {
                    val distanceFromScrubber = (totalProgressPx - xPos).coerceAtLeast(0f)
                    val taperFraction = (distanceFromScrubber / waveReach).coerceIn(0f, 1f)
                    val decay = 0.5f * (1f + kotlin.math.cos(taperFraction * Math.PI.toFloat()))
                    val envelope = minEnvelope + (1f - minEnvelope) * decay
                    val angle = ((xPos - totalProgressPx) * k) - phaseOffset.value
                    val amp = maxAmpPx * envelope * smoothScale * heightFraction
                    return centerY + kotlin.math.sin(angle) * amp
                }

                cachedPath.moveTo(0f, waveY(0f))
                var x = step
                while (x < totalProgressPx) {
                    cachedPath.lineTo(x, waveY(x))
                    x += step
                }
                cachedPath.lineTo(totalProgressPx, waveY(totalProgressPx))

                drawPath(
                    path = cachedPath,
                    color = activeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
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
                // Terminal subtle dot at the end matching screenshot
                drawCircle(
                    color = inactiveColor,
                    radius = strokeWidth * 0.9f,
                    center = Offset(totalWidth - strokeWidth / 2f, centerY)
                )
            }

            // 3. Vertical Bar Thumb `|` matching Metrolist and screenshot
            val barHalfHeight = 11.dp.toPx()
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
