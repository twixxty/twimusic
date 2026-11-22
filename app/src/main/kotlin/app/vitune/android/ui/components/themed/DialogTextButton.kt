package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.disabled
import app.vitune.android.utils.medium
import app.vitune.android.utils.primary
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val (colorPalette, typography) = LocalAppearance.current

    if (LocalIsExpressiveTheme.current) {
        if (primary) {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.accent,
                    contentColor = colorPalette.onAccent
                ),
                modifier = modifier
            ) {
                BasicText(
                    text = text,
                    style = typography.xs.semiBold.copy(color = colorPalette.onAccent)
                )
            }
        } else {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorPalette.accent
                ),
                modifier = modifier
            ) {
                BasicText(
                    text = text,
                    style = typography.xs.medium.copy(color = colorPalette.accent)
                )
            }
        }
        return
    }

    BasicText(
        text = text,
        style = typography.xs.medium.let {
            when {
                !enabled -> it.disabled
                primary -> it.primary
                else -> it
            }
        },
        modifier = modifier
            .clip(36.dp.roundedShape)
            .background(if (primary) colorPalette.accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    )
}
