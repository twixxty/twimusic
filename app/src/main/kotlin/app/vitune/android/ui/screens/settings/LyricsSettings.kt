package app.vitune.android.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.lyrics.LyricsProviderRegistry
import app.vitune.android.preferences.LyricsPreferences
import app.vitune.android.preferences.LyricsPriorityMode
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.ReorderHandle
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.Switch
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.compose.reordering.animateItemPlacement
import app.vitune.compose.reordering.draggedItem
import app.vitune.compose.reordering.rememberReorderingState
import app.vitune.core.ui.LocalAppearance

@Route
@Composable
fun LyricsSettings() = with(PlayerPreferences) {
    val (colorPalette, typography) = LocalAppearance.current

    SettingsCategoryScreen(title = stringResource(R.string.lyrics)) {
        SettingsGroup(title = stringResource(R.string.lyrics_priority_mode)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.lyrics_priority_mode),
                selectedValue = LyricsPreferences.lyricsPriorityMode,
                onValueSelect = { LyricsPreferences.lyricsPriorityMode = it },
                valueText = { mode ->
                    when (mode) {
                        LyricsPriorityMode.Quality -> stringResource(R.string.lyrics_priority_quality)
                        LyricsPriorityMode.Fast -> stringResource(R.string.lyrics_priority_fast)
                        LyricsPriorityMode.Custom -> stringResource(R.string.lyrics_priority_custom)
                    }
                }
            )

            BasicText(
                text = when (LyricsPreferences.lyricsPriorityMode) {
                    LyricsPriorityMode.Quality -> stringResource(R.string.lyrics_priority_quality_description)
                    LyricsPriorityMode.Fast -> stringResource(R.string.lyrics_priority_fast_description)
                    LyricsPriorityMode.Custom -> stringResource(R.string.lyrics_priority_custom_description)
                },
                style = typography.xxs.secondary.medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        SettingsGroup(
            title = stringResource(R.string.lyrics_providers),
            description = stringResource(R.string.lyrics_providers_description)
        ) {
            var currentOrder by remember(LyricsPreferences.lyricsProviderOrder) {
                mutableStateOf(LyricsProviderRegistry.deserializeProviderOrder(LyricsPreferences.lyricsProviderOrder))
            }

            var enabledVersion by remember { mutableStateOf(0) }

            val lazyListState = rememberLazyListState()
            val reorderingState = rememberReorderingState(
                lazyListState = lazyListState,
                key = currentOrder,
                onDragEnd = { from, to ->
                    val mutable = currentOrder.toMutableList()
                    val moved = mutable.removeAt(from)
                    mutable.add(to, moved)
                    currentOrder = mutable
                    LyricsPreferences.lyricsProviderOrder = LyricsProviderRegistry.serializeProviderOrder(mutable)
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentOrder.forEachIndexed { index, providerName ->
                    val isEnabled = remember(providerName, enabledVersion) {
                        LyricsPreferences.isProviderEnabled(providerName)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorPalette.background1)
                            .animateItemPlacement(reorderingState)
                            .draggedItem(reorderingState, index)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReorderHandle(
                            reorderingState = reorderingState,
                            index = index
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = providerName,
                                style = typography.xs.semiBold
                            )
                            BasicText(
                                text = "#${index + 1} Priority",
                                style = typography.xxs.secondary.medium
                            )
                        }

                        Switch(
                            isChecked = isEnabled,
                            onCheckedChange = { checked ->
                                LyricsPreferences.setProviderEnabled(providerName, checked)
                                enabledVersion++
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.reset),
                        onClick = {
                            val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
                            currentOrder = defaultOrder
                            LyricsPreferences.lyricsProviderOrder = LyricsProviderRegistry.serializeProviderOrder(defaultOrder)
                        }
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.appearance)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.lyrics_animation_style),
                selectedValue = lyricsAnimationStyle,
                onValueSelect = { lyricsAnimationStyle = it },
                valueText = { it.displayName() }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_line_blur),
                text = stringResource(R.string.lyrics_line_blur_description),
                isChecked = lyricsLineBlur,
                onCheckedChange = { lyricsLineBlur = it }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchSettingsEntry(
                    title = stringResource(R.string.lyrics_optimized_android_12),
                    text = stringResource(R.string.lyrics_optimized_android_12_description),
                    isChecked = lyricsOptimizedAndroid12,
                    onCheckedChange = { lyricsOptimizedAndroid12 = it }
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_shine),
                text = stringResource(R.string.lyrics_shine_description),
                isChecked = lyricsShine,
                onCheckedChange = { lyricsShine = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_fade),
                text = stringResource(R.string.lyrics_fade_description),
                isChecked = lyricsFade,
                onCheckedChange = { lyricsFade = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_use_app_font),
                text = stringResource(R.string.lyrics_use_app_font_description),
                isChecked = lyricsUseAppFont,
                onCheckedChange = { lyricsUseAppFont = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_glow),
                text = stringResource(R.string.lyrics_glow_description),
                isChecked = lyricsGlow,
                onCheckedChange = { lyricsGlow = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_bouncy_scroll),
                text = stringResource(R.string.lyrics_bouncy_scroll_description),
                isChecked = lyricsBouncyScroll,
                onCheckedChange = { lyricsBouncyScroll = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_translation),
                text = stringResource(R.string.lyrics_translation_description),
                isChecked = lyricsShowTranslation,
                onCheckedChange = { lyricsShowTranslation = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.hide_lyrics_fullscreen_button),
                text = stringResource(R.string.hide_lyrics_fullscreen_button_description),
                isChecked = hideLyricsFullscreenButton,
                onCheckedChange = { hideLyricsFullscreenButton = it }
            )

            AnimatedVisibility(visible = hideLyricsFullscreenButton) {
                SwitchSettingsEntry(
                    title = stringResource(R.string.hide_lyrics_fullscreen_button_in_landscape),
                    text = stringResource(R.string.hide_lyrics_fullscreen_button_in_landscape_description),
                    isChecked = hideLyricsFullscreenButtonInLandscape,
                    onCheckedChange = { hideLyricsFullscreenButtonInLandscape = it },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
