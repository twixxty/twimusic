package app.vitune.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import app.vitune.core.ui.utils.roundedShape

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val (colorPalette) = LocalAppearance.current

    if (LocalIsExpressiveTheme.current) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier.size(56.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = colorPalette.accent,
            contentColor = colorPalette.onAccent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onAccent),
                modifier = Modifier.size(24.dp)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .clip(16.dp.roundedShape)
            .clickable(enabled = enabled, onClick = onClick)
            .background(colorPalette.surface)
            .size(62.dp)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
        )
    }
}
