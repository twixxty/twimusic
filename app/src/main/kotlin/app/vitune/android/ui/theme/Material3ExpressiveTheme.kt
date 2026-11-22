package app.vitune.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.vitune.core.ui.Appearance

val LocalIsExpressiveTheme = staticCompositionLocalOf { false }

@Composable
fun Material3ExpressiveTheme(
    appearance: Appearance,
    isExpressive: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!isExpressive) {
        CompositionLocalProvider(LocalIsExpressiveTheme provides false) {
            content()
        }
        return
    }

    val colorPalette = appearance.colorPalette
    val isDark = colorPalette.background0.luminance() < 0.5f

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = colorPalette.accent,
            onPrimary = colorPalette.onAccent,
            primaryContainer = colorPalette.background2,
            onPrimaryContainer = colorPalette.text,
            secondary = colorPalette.accent,
            onSecondary = colorPalette.onAccent,
            secondaryContainer = colorPalette.background2,
            onSecondaryContainer = colorPalette.text,
            background = colorPalette.background0,
            onBackground = colorPalette.text,
            surface = colorPalette.background1,
            onSurface = colorPalette.text,
            surfaceVariant = colorPalette.background2,
            onSurfaceVariant = colorPalette.textSecondary
        )
    } else {
        lightColorScheme(
            primary = colorPalette.accent,
            onPrimary = colorPalette.onAccent,
            primaryContainer = colorPalette.background2,
            onPrimaryContainer = colorPalette.text,
            secondary = colorPalette.accent,
            onSecondary = colorPalette.onAccent,
            secondaryContainer = colorPalette.background2,
            onSecondaryContainer = colorPalette.text,
            background = colorPalette.background0,
            onBackground = colorPalette.text,
            surface = colorPalette.background1,
            onSurface = colorPalette.text,
            surfaceVariant = colorPalette.background2,
            onSurfaceVariant = colorPalette.textSecondary
        )
    }

    // Material 3 Expressive shapes emphasizing high curvature and rounded pill geometry
    val expressiveShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    CompositionLocalProvider(LocalIsExpressiveTheme provides true) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = expressiveShapes,
            content = content
        )
    }
}
