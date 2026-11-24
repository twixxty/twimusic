package app.vitune.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.getAppIcon
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.core.ui.LocalAppearance

@Composable
inline fun Menu(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isExpressive = LocalIsExpressiveTheme.current
    val effectiveShape = shape ?: if (isExpressive) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                                 else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(effectiveShape)
            .verticalScroll(rememberScrollState())
            .background(LocalAppearance.current.colorPalette.background1)
            .padding(top = 2.dp)
            .padding(vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        if (isExpressive) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = LocalAppearance.current.colorPalette.textDisabled.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
        content()
    }
}

enum class MenuEntryPosition {
    Top,
    Middle,
    Bottom,
    Single,
    Auto
}

fun getMenuEntryShape(position: MenuEntryPosition, isExpressive: Boolean): Shape {
    if (!isExpressive) return RoundedCornerShape(0.dp)
    return when (position) {
        MenuEntryPosition.Top -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        MenuEntryPosition.Bottom -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        MenuEntryPosition.Single -> RoundedCornerShape(20.dp)
        MenuEntryPosition.Middle -> RoundedCornerShape(4.dp)
        MenuEntryPosition.Auto -> RoundedCornerShape(16.dp)
    }
}

data class MenuEntryData(
    @DrawableRes val icon: Int = 0,
    val text: String = "",
    val onClick: () -> Unit = {},
    val secondaryText: String? = null,
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val customContent: (@Composable (position: MenuEntryPosition) -> Unit)? = null
)

class MenuSectionBuilder {
    internal val items = mutableListOf<MenuEntryData>()

    fun entry(
        @DrawableRes icon: Int,
        text: String,
        onClick: () -> Unit,
        secondaryText: String? = null,
        enabled: Boolean = true,
        onLongClick: (() -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null
    ) {
        items.add(
            MenuEntryData(
                icon = icon,
                text = text,
                onClick = onClick,
                secondaryText = secondaryText,
                enabled = enabled,
                onLongClick = onLongClick,
                trailingContent = trailingContent
            )
        )
    }

    fun item(content: @Composable (position: MenuEntryPosition) -> Unit) {
        items.add(MenuEntryData(customContent = content))
    }
}

@Composable
fun MenuSection(
    modifier: Modifier = Modifier,
    builder: @Composable MenuSectionBuilder.() -> Unit
) {
    val section = MenuSectionBuilder()
    section.builder()
    if (section.items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        section.items.forEachIndexed { index, item ->
            val position = when {
                section.items.size == 1 -> MenuEntryPosition.Single
                index == 0 -> MenuEntryPosition.Top
                index == section.items.lastIndex -> MenuEntryPosition.Bottom
                else -> MenuEntryPosition.Middle
            }
            if (item.customContent != null) {
                item.customContent.invoke(position)
            } else {
                MenuEntry(
                    icon = item.icon,
                    text = item.text,
                    onClick = item.onClick,
                    secondaryText = item.secondaryText,
                    enabled = item.enabled,
                    onLongClick = item.onLongClick,
                    trailingContent = item.trailingContent,
                    position = position
                )
            }
        }
    }
}

@Composable
fun MenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    position: MenuEntryPosition = MenuEntryPosition.Auto,
    shape: Shape? = null
) {
    val (colorPalette, typography) = LocalAppearance.current
    val isExpressive = LocalIsExpressiveTheme.current
    val entryShape = shape ?: getMenuEntryShape(position, isExpressive)

    val entryModifier = if (isExpressive) {
        modifier
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(entryShape)
            .background(colorPalette.background2, entryShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 20.dp, vertical = 2.dp)
    } else {
        modifier
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 24.dp)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = entryModifier
    ) {
        Image(
            painter = painterResource(getAppIcon(icon)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.text),
            modifier = Modifier.size(22.dp)
        )

        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1f)
        ) {
            BasicText(
                text = text,
                style = typography.xs.medium.copy(color = colorPalette.text)
            )

            secondaryText?.let { secondaryText ->
                BasicText(
                    text = secondaryText,
                    style = typography.xxs.medium.secondary
                )
            }
        }

        trailingContent?.invoke()
    }
}
