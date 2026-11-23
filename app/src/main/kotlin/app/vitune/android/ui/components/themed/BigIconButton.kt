package app.vitune.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.getAppIcon
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

@Composable
fun BigIconButton(
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = LocalAppearance.current.colorPalette.background2,
    contentColor: Color = LocalAppearance.current.colorPalette.text,
    shape: Shape = 32.dp.roundedShape
) {
    val isExpressive = LocalIsExpressiveTheme.current
    val effectiveShape = if (isExpressive) RoundedCornerShape(24.dp) else shape
    val effectiveBg = if (isExpressive) LocalAppearance.current.colorPalette.background1 else backgroundColor

    Box(
        modifier
            .clip(effectiveShape)
            .let {
                if (onClick == null) it
                else if (isExpressive) it.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick
                )
                else it.clickable(onClick = onClick)
            }
            .background(effectiveBg)
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(getAppIcon(iconId)),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(contentColor)
        )
    }
}
