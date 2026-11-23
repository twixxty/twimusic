@file:Suppress("TooManyFunctions")

package app.vitune.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ripple
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.getAppIcon
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.NumberFieldDialog
import app.vitune.android.ui.components.themed.Scaffold
import app.vitune.android.ui.components.themed.Slider
import app.vitune.android.ui.components.themed.Switch
import app.vitune.android.ui.components.themed.ValueSelectorDialog
import app.vitune.android.ui.screens.GlobalRoutes
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.color
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.RouteHandler
import app.vitune.core.ui.LocalAppearance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Route
@Composable
fun SettingsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()
    val [tabIndex, onTabChanged] = rememberSaveable { mutableIntStateOf(0) }
    var isSearchPageOpen by rememberSaveable { mutableStateOf(false) }

    PersistMapCleanup("settings/")

    RouteHandler {
        GlobalRoutes()

        Content {
            if (isSearchPageOpen) {
                SettingsSearchScreen(
                    onBack = { isSearchPageOpen = false },
                    onNavigateToCategory = { targetTab ->
                        onTabChanged(targetTab)
                        isSearchPageOpen = false
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        key = "settings",
                        topIconButtonId = R.drawable.chevron_back,
                        onTopIconButtonClick = pop,
                        tabIndex = tabIndex,
                        onTabChange = onTabChanged,
                        tabColumnContent = {
                            tab(0, R.string.appearance, R.drawable.color_palette, canHide = false)
                            tab(1, R.string.player, R.drawable.play, canHide = false)
                            tab(2, R.string.lyrics, R.drawable.lyrics, canHide = false)
                            tab(3, R.string.cache, R.drawable.server, canHide = false)
                            tab(4, R.string.database, R.drawable.server, canHide = false)
                            tab(5, R.string.sync, R.drawable.sync, canHide = false)
                            tab(6, R.string.other, R.drawable.shapes, canHide = false)
                            tab(7, R.string.listen_together, R.drawable.group, canHide = false)
                            tab(8, R.string.discord_rich_presence, R.drawable.discord, canHide = false)
                            tab(9, R.string.about, R.drawable.information, canHide = false)
                        }
                    ) { currentTabIndex ->
                        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                            when (currentTabIndex) {
                                0 -> AppearanceSettings()
                                1 -> PlayerSettings()
                                2 -> LyricsSettings()
                                3 -> CacheSettings()
                                4 -> DatabaseSettings()
                                5 -> SyncSettings()
                                6 -> OtherSettings()
                                7 -> app.vitune.android.ui.screens.listentogether.ListenTogetherScreen(onBack = pop)
                                8 -> RichPresenceSettings()
                                9 -> About()
                            }
                        }
                    }

                    // Floating Search Button on main settings page
                    val (colorPalette) = LocalAppearance.current
                    val isExpressive = LocalIsExpressiveTheme.current
                    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 20.dp,
                                bottom = 20.dp + insets.calculateBottomPadding()
                            )
                            .size(56.dp)
                            .clip(RoundedCornerShape(if (isExpressive) 20.dp else 16.dp))
                            .background(colorPalette.accent)
                            .clickable { isSearchPageOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(getAppIcon(R.drawable.search)),
                            contentDescription = stringResource(R.string.search_settings),
                            colorFilter = ColorFilter.tint(colorPalette.onAccent),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumValueSelectorSettingsEntry(
    title: String,
    selectedValue: T,
    noinline onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    noinline valueText: @Composable (T) -> String = { it.name },
    noinline trailingContent: (@Composable () -> Unit)? = null
) = ValueSelectorSettingsEntry(
    title = title,
    selectedValue = selectedValue,
    values = enumValues<T>().toList().toImmutableList(),
    onValueSelect = onValueSelect,
    modifier = modifier,
    isEnabled = isEnabled,
    valueText = valueText,
    trailingContent = trailingContent
)

@Composable
fun <T> ValueSelectorSettingsEntry(
    title: String,
    selectedValue: T,
    values: ImmutableList<T>,
    onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    isEnabled: Boolean = true,
    usePadding: Boolean = true,
    valueText: @Composable (T) -> String = { it.toString() },
    trailingContent: (@Composable () -> Unit)? = null
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    if (isShowingDialog) ValueSelectorDialog(
        onDismiss = { isShowingDialog = false },
        title = title,
        selectedValue = selectedValue,
        values = values,
        onValueSelect = onValueSelect,
        valueText = valueText
    )

    SettingsEntry(
        modifier = modifier,
        title = title,
        text = text ?: valueText(selectedValue),
        onClick = { isShowingDialog = true },
        isEnabled = isEnabled,
        trailingContent = trailingContent,
        usePadding = usePadding
    )
}

@Composable
fun SwitchSettingsEntry(
    title: String,
    text: String?,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    usePadding: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    SettingsEntry(
        modifier = modifier,
        title = title,
        text = text,
        onClick = { onCheckedChange(!isChecked) },
        isEnabled = isEnabled,
        usePadding = usePadding,
        interactionSource = interactionSource
    ) {
        Switch(
            isChecked = isChecked,
            interactionSource = interactionSource
        )
    }
}

val LocalInSettingsGroup = compositionLocalOf { false }

@Composable
fun SliderSettingsEntry(
    title: String,
    text: String,
    state: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onSlide: (Float) -> Unit = { },
    onSlideComplete: () -> Unit = { },
    toDisplay: @Composable (Float) -> String = { it.toString() },
    steps: Int = 0,
    isEnabled: Boolean = true,
    usePadding: Boolean = true,
    showTicks: Boolean = steps != 0
) {
    val isExpressive = LocalIsExpressiveTheme.current
    val inSettingsGroup = LocalInSettingsGroup.current
    val (colorPalette, typography) = LocalAppearance.current

    if (isExpressive) {
        val sliderModifier = if (inSettingsGroup) {
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colorPalette.background1)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        } else {
            modifier
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colorPalette.background1)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        }

        Column(modifier = sliderModifier) {
            BasicText(
                text = title,
                style = typography.xs.semiBold.copy(color = colorPalette.text)
            )
            BasicText(
                text = "$text (${toDisplay(state)})",
                style = typography.xs.semiBold.secondary
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                state = state,
                setState = onSlide,
                onSlideComplete = onSlideComplete,
                range = range,
                steps = steps,
                showTicks = showTicks,
                modifier = Modifier
                    .height(36.dp)
                    .alpha(if (isEnabled) 1f else 0.5f)
                    .fillMaxWidth()
            )
        }
        return
    }

    Column(modifier = modifier) {
        SettingsEntry(
            title = title,
            text = "$text (${toDisplay(state)})",
            onClick = {},
            isEnabled = isEnabled,
            usePadding = usePadding
        )

        Slider(
            state = state,
            setState = onSlide,
            onSlideComplete = onSlideComplete,
            range = range,
            steps = steps,
            showTicks = showTicks,
            modifier = Modifier
                .height(36.dp)
                .alpha(if (isEnabled) 1f else 0.5f)
                .let { if (usePadding) it.padding(start = 32.dp, end = 16.dp) else it }
                .padding(vertical = 16.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
inline fun IntSettingsEntry(
    title: String,
    text: String,
    currentValue: Int,
    crossinline setValue: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    defaultValue: Int = 0,
    isEnabled: Boolean = true,
    usePadding: Boolean = true
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    if (isShowingDialog) NumberFieldDialog(
        onDismiss = { isShowingDialog = false },
        onAccept = {
            setValue(it)
            isShowingDialog = false
        },
        initialValue = currentValue,
        defaultValue = defaultValue,
        convert = { it.toIntOrNull() },
        range = range
    )

    SettingsEntry(
        modifier = modifier,
        title = title,
        text = text,
        onClick = { isShowingDialog = true },
        isEnabled = isEnabled,
        usePadding = usePadding
    )
}

@Composable
fun SettingsEntry(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    isEnabled: Boolean = true,
    usePadding: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val isExpressive = LocalIsExpressiveTheme.current
    val inSettingsGroup = LocalInSettingsGroup.current
    val (colorPalette, typography) = LocalAppearance.current
    val interaction = interactionSource ?: remember { MutableInteractionSource() }

    val entryModifier = if (isExpressive) {
        if (inSettingsGroup) {
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colorPalette.background1)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = isEnabled,
                    onClick = onClick
                )
                .alpha(if (isEnabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        } else {
            modifier
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colorPalette.background1)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = isEnabled,
                    onClick = onClick
                )
                .alpha(if (isEnabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
        }
    } else {
        modifier
            .clip(RoundedCornerShape(25))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = isEnabled,
                onClick = onClick
            )
            .alpha(if (isEnabled) 1f else 0.5f)
            .let { if (usePadding) it.padding(start = 16.dp, end = 16.dp) else it }
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = entryModifier
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography.xs.semiBold.copy(color = colorPalette.text)
            )

            if (text != null) BasicText(
                text = text,
                style = typography.xs.semiBold.secondary
            )
        }

        trailingContent?.invoke()
    }
}

@Composable
fun SettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
    important: Boolean = false
) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = text,
        style = if (important) typography.xxs.semiBold.color(colorPalette.red)
        else typography.xxs.secondary,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp)
            .padding(vertical = 4.dp)
    )
}

@Composable
fun SettingsEntryGroupText(
    title: String,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = title,
        style = typography.xs.semiBold.copy(colorPalette.accent),
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp)
            .padding(vertical = 4.dp)
            .semantics { text = AnnotatedString(text = title) }
    )
}

@Composable
fun SettingsGroupSpacer(modifier: Modifier = Modifier) = Spacer(modifier = modifier.height(16.dp))

@Composable
fun SettingsCategoryScreen(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .let { if (scrollState != null) it.verticalScroll(state = scrollState) else it }
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
            )
    ) {
        Header(title = title)

        description?.let { desc ->
            BasicText(
                text = desc,
                style = typography.s.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SettingsGroupSpacer()
        }

        content()
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    important: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isExpressive = LocalIsExpressiveTheme.current
    val (colorPalette, typography) = LocalAppearance.current

    if (isExpressive) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = title,
                        style = typography.xs.semiBold.copy(colorPalette.accent),
                        modifier = Modifier.semantics { text = AnnotatedString(text = title) }
                    )

                    description?.let { description ->
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = description,
                            style = if (important) typography.xxs.semiBold.color(colorPalette.red)
                            else typography.xxs.secondary
                        )
                    }
                }

                trailingContent?.let {
                    it()
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                CompositionLocalProvider(LocalInSettingsGroup provides true) {
                    content()
                }
            }

            SettingsGroupSpacer()
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Column {
                SettingsEntryGroupText(title = title)

                description?.let { description ->
                    SettingsDescription(
                        text = description,
                        important = important
                    )
                }
            }

            trailingContent?.let {
                Spacer(Modifier.weight(1f))
                it()
            }
        }

        content()

        SettingsGroupSpacer()
    }
}
