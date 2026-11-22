package app.vitune.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val (colorPalette) = LocalAppearance.current

    if (LocalIsExpressiveTheme.current) {
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = colorPalette.background1,
            contentColor = colorPalette.text,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.text),
                modifier = Modifier.size(20.dp)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .background(colorPalette.surface)
            .size(48.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(18.dp)
        )
    }
}
