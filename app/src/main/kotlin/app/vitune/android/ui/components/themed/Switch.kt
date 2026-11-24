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
import androidx.compose.material3.Switch as M3Switch
import androidx.compose.material3.SwitchDefaults
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

    if (LocalIsExpressiveTheme.current) {
        M3Switch(
            checked = isChecked,
            onCheckedChange = null,
            interactionSource = actualInteractionSource,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorPalette.onAccent,
                checkedTrackColor = colorPalette.accent,
                checkedBorderColor = colorPalette.accent,
                uncheckedThumbColor = colorPalette.textDisabled,
                uncheckedTrackColor = colorPalette.background2,
                uncheckedBorderColor = colorPalette.textDisabled.copy(alpha = 0.5f)
            )
        )
        return
    }

    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val springDpSpec = spring<Dp>(
        dampingRatio = 0.9f,
        stiffness = 800f
    )
    val springColorSpec = spring<Color>(
        dampingRatio = 0.9f,
        stiffness = 800f
    )

    // Classic Switch (48.dp x 24.dp track)
    val targetBackgroundColor = if (isChecked) colorPalette.accent else colorPalette.background1
    val targetThumbColor = if (isChecked) colorPalette.onAccent else colorPalette.textDisabled
    val targetThumbOffset = if (isChecked) 36.dp else 12.dp
    val targetThumbRadius = if (isPressed) 9.dp else 8.dp

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
                color = Color.Black.copy(alpha = if (isChecked) 0.3f else 0.08f),
                blurRadius = 4.dp.toPx(),
                offset = Offset(x = -1.dp.toPx(), y = 1.dp.toPx())
            )
        )
    }
}
