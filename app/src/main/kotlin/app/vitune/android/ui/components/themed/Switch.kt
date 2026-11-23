package app.vitune.android.ui.components.themed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.drawCircle
import app.vitune.core.ui.LocalAppearance

@Composable
fun Switch(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null
) {
    val (colorPalette) = LocalAppearance.current
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()

    val springDpSpec = spring<Dp>(
        dampingRatio = 0.75f,
        stiffness = 400f
    )
    val springColorSpec = spring<Color>(
        dampingRatio = 0.75f,
        stiffness = 400f
    )

    if (LocalIsExpressiveTheme.current) {
        // Material 3 Expressive Switch (52.dp x 32.dp track)
        val targetTrackColor = if (isChecked) colorPalette.accent else colorPalette.background2
        val targetBorderColor = if (isChecked) colorPalette.accent else colorPalette.textDisabled.copy(alpha = 0.5f)
        val targetThumbColor = if (isChecked) colorPalette.onAccent else {
            if (isPressed) colorPalette.text else colorPalette.textDisabled
        }

        // Thumb target size: expands on hold/press
        // Unchecked: 16.dp -> expands to 28.dp on hold
        // Checked: 24.dp -> expands to 28.dp on hold
        val targetThumbSize = if (isPressed) 28.dp else if (isChecked) 24.dp else 16.dp
        val targetThumbOffset = if (isChecked) 36.dp else 16.dp

        val animatedTrackColor by animateColorAsState(targetTrackColor, springColorSpec, label = "m3TrackColor")
        val animatedBorderColor by animateColorAsState(targetBorderColor, springColorSpec, label = "m3BorderColor")
        val animatedThumbColor by animateColorAsState(targetThumbColor, springColorSpec, label = "m3ThumbColor")
        val animatedThumbSize by animateDpAsState(targetThumbSize, springDpSpec, label = "m3ThumbSize")
        val animatedThumbOffset by animateDpAsState(targetThumbOffset, springDpSpec, label = "m3ThumbOffset")

        Canvas(modifier = modifier.size(width = 52.dp, height = 32.dp)) {
            val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

            // Track background
            drawRoundRect(
                color = animatedTrackColor,
                cornerRadius = cornerRadius
            )

            // Track border when unchecked or transitioning
            if (!isChecked || animatedTrackColor != colorPalette.accent) {
                drawRoundRect(
                    color = animatedBorderColor,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Expanding thumb
            val radius = animatedThumbSize.toPx() / 2f
            drawCircle(
                color = animatedThumbColor,
                radius = radius,
                center = size.center.copy(x = animatedThumbOffset.toPx()),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = if (isChecked) 0.35f else 0.15f),
                    blurRadius = 6.dp.toPx(),
                    offset = Offset(x = 0f, y = 1.dp.toPx())
                )
            )
        }
        return
    }

    // Classic Switch (48.dp x 24.dp track)
    val targetBackgroundColor = if (isChecked) colorPalette.accent else colorPalette.background1
    val targetThumbColor = if (isChecked) colorPalette.onAccent else colorPalette.textDisabled
    val targetThumbOffset = if (isChecked) 36.dp else 12.dp
    val targetThumbRadius = if (isPressed) 10.dp else 8.dp

    val animatedBackgroundColor by animateColorAsState(targetBackgroundColor, springColorSpec, label = "classicBgColor")
    val animatedThumbColor by animateColorAsState(targetThumbColor, springColorSpec, label = "classicThumbColor")
    val animatedThumbOffset by animateDpAsState(targetThumbOffset, springDpSpec, label = "classicThumbOffset")
    val animatedThumbRadius by animateDpAsState(targetThumbRadius, springDpSpec, label = "classicThumbRadius")

    Canvas(modifier = modifier.size(width = 48.dp, height = 24.dp)) {
        drawRoundRect(
            color = animatedBackgroundColor,
            cornerRadius = CornerRadius(x = 12.dp.toPx(), y = 12.dp.toPx())
        )

        drawCircle(
            color = animatedThumbColor,
            radius = animatedThumbRadius.toPx(),
            center = size.center.copy(x = animatedThumbOffset.toPx()),
            shadow = Shadow(
                color = Color.Black.copy(alpha = if (isChecked) 0.4f else 0.1f),
                blurRadius = 8.dp.toPx(),
                offset = Offset(x = -1.dp.toPx(), y = 1.dp.toPx())
            )
        )
    }
}
