package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.center
import app.vitune.android.utils.disabled
import app.vitune.android.utils.medium
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import app.vitune.core.ui.utils.roundedShape

@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alternative: Boolean = false
) {
    val (colorPalette, typography) = LocalAppearance.current

    if (LocalIsExpressiveTheme.current) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (alternative) colorPalette.background0 else colorPalette.background2,
                contentColor = colorPalette.text
            ),
            modifier = modifier
        ) {
            BasicText(
                text = text,
                style = typography.xxs.medium.center.copy(color = colorPalette.text)
            )
        }
        return
    }

    BasicText(
        text = text,
        style = typography.xxs.medium.center.let { if (enabled) it else it.disabled },
        modifier = modifier
            .clip(16.dp.roundedShape)
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (alternative) colorPalette.background0 else colorPalette.surface)
            .padding(all = 8.dp)
            .padding(horizontal = 8.dp)
    )
}
