@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vitune.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.preferences.AppearancePreferences
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.Switch
import app.vitune.android.ui.theme.LocalIsExpressiveTheme
import app.vitune.android.utils.getAppIcon
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.BuiltInFontFamily
import app.vitune.core.ui.ColorSource
import app.vitune.core.ui.Darkness
import app.vitune.core.ui.LocalAppearance

private data class SearchSettingEntry(
    val title: String,
    val description: String? = null,
    val categoryName: String,
    val tabIndex: Int,
    val onInteract: () -> Unit,
    val trailingContent: @Composable () -> Unit
)

@Composable
fun SettingsSearchScreen(
    onBack: () -> Unit,
    onNavigateToCategory: (Int) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val isExpressive = LocalIsExpressiveTheme.current
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Build the searchable registry using current preference values
    val allSettings = remember(
        AppearancePreferences.themeMode,
        AppearancePreferences.useMaterialSymbols,
        AppearancePreferences.darkness,
        AppearancePreferences.fontFamily,
        AppearancePreferences.colorSource,
        AppearancePreferences.autoPip,
        AppearancePreferences.openPlayer,
        PlayerPreferences.enableCanvas,
        PlayerPreferences.pauseCanvasWithSong,
        PlayerPreferences.canvasStyle,
        PlayerPreferences.persistentQueue,
        PlayerPreferences.resumePlaybackWhenDeviceConnected,
        PlayerPreferences.stopWhenClosed,
        PlayerPreferences.stopOnMinimumVolume,
        PlayerPreferences.skipSilence,
        PlayerPreferences.volumeNormalization,
        PlayerPreferences.bassBoost,
        PlayerPreferences.handleAudioFocus,
        PlayerPreferences.playerLayout,
        PlayerPreferences.seekBarStyle,
        PlayerPreferences.showLike,
        PlayerPreferences.showRemaining,
        PlayerPreferences.horizontalSwipeToClose,
        PlayerPreferences.isShowingSynchronizedLyrics,
        PlayerPreferences.lyricsTextPosition,
        PlayerPreferences.lyricsKeepScreenAwake,
        PlayerPreferences.lyricsLineBlur,
        PlayerPreferences.lyricsShine,
        PlayerPreferences.lyricsFade,
        PlayerPreferences.lyricsGlow,
        PlayerPreferences.lyricsBouncyScroll,
        PlayerPreferences.lyricsShowTranslation,
        PlayerPreferences.hideLyricsFullscreenButton,
        PlayerPreferences.hideLyricsFullscreenButtonInLandscape,
        PlayerPreferences.pauseCache,
        PlayerPreferences.sponsorBlockEnabled,
        PlayerPreferences.isInvincibilityEnabled
    ) {
        listOf(
            // --- Appearance (tab 0) ---
            SearchSettingEntry(
                title = "Material 3 Expressive UI",
                description = "Modern Google Material 3 Expressive design tokens, pill rail, and animations",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = {
                    AppearancePreferences.themeMode = if (AppearancePreferences.themeMode == AppearancePreferences.ThemeMode.Material3Expressive) {
                        AppearancePreferences.ThemeMode.Classic
                    } else {
                        AppearancePreferences.ThemeMode.Material3Expressive
                    }
                },
                trailingContent = { Switch(isChecked = AppearancePreferences.themeMode == AppearancePreferences.ThemeMode.Material3Expressive) }
            ),
            SearchSettingEntry(
                title = "Material Symbols & Icons",
                description = "Use Google Material Symbols icons across player, queue, and menus",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = { AppearancePreferences.useMaterialSymbols = !AppearancePreferences.useMaterialSymbols },
                trailingContent = { Switch(isChecked = AppearancePreferences.useMaterialSymbols) }
            ),
            SearchSettingEntry(
                title = "Pure Black AMOLED Theme",
                description = "Pitch black background for OLED screens in dark mode",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = {
                    AppearancePreferences.darkness = if (AppearancePreferences.darkness == Darkness.PureBlack) {
                        Darkness.Normal
                    } else {
                        Darkness.PureBlack
                    }
                },
                trailingContent = { Switch(isChecked = AppearancePreferences.darkness == Darkness.PureBlack) }
            ),
            SearchSettingEntry(
                title = "Dynamic Colors (Material You)",
                description = "Derive theme accent colors dynamically from wallpaper",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = {
                    AppearancePreferences.colorSource = if (AppearancePreferences.colorSource == ColorSource.Default) {
                        ColorSource.MaterialYou
                    } else {
                        ColorSource.Default
                    }
                },
                trailingContent = { Switch(isChecked = AppearancePreferences.colorSource == ColorSource.MaterialYou || AppearancePreferences.colorSource == ColorSource.Dynamic) }
            ),
            SearchSettingEntry(
                title = "Use System Font",
                description = "Use device default system typography instead of custom font",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = {
                    AppearancePreferences.fontFamily = if (AppearancePreferences.fontFamily == BuiltInFontFamily.System) {
                        BuiltInFontFamily.Poppins
                    } else {
                        BuiltInFontFamily.System
                    }
                },
                trailingContent = { Switch(isChecked = AppearancePreferences.fontFamily == BuiltInFontFamily.System) }
            ),
            SearchSettingEntry(
                title = "Picture-in-Picture (PiP)",
                description = "Automatically enter Picture-in-Picture when leaving app while playing",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = { AppearancePreferences.autoPip = !AppearancePreferences.autoPip },
                trailingContent = { Switch(isChecked = AppearancePreferences.autoPip) }
            ),
            SearchSettingEntry(
                title = "Auto Open Player",
                description = "Expand full player sheet automatically when starting playback",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = { AppearancePreferences.openPlayer = !AppearancePreferences.openPlayer },
                trailingContent = { Switch(isChecked = AppearancePreferences.openPlayer) }
            ),
            SearchSettingEntry(
                title = "Canvas & Motion Artwork",
                description = "Show video canvases and Motion Artwork behind player and cover",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = { PlayerPreferences.enableCanvas = !PlayerPreferences.enableCanvas },
                trailingContent = { Switch(isChecked = PlayerPreferences.enableCanvas) }
            ),
            SearchSettingEntry(
                title = "Pause Canvas with song",
                description = "Slow down and pause video canvas when playback is paused",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = { PlayerPreferences.pauseCanvasWithSong = !PlayerPreferences.pauseCanvasWithSong },
                trailingContent = { Switch(isChecked = PlayerPreferences.pauseCanvasWithSong) }
            ),
            SearchSettingEntry(
                title = "Canvas Display Style",
                description = "Switch between Cover Art and Wide/Fullscreen video presentation",
                categoryName = "Appearance",
                tabIndex = 0,
                onInteract = {
                    PlayerPreferences.canvasStyle = if (PlayerPreferences.canvasStyle == PlayerPreferences.CanvasStyle.Cover) {
                        PlayerPreferences.CanvasStyle.Wide
                    } else {
                        PlayerPreferences.CanvasStyle.Cover
                    }
                },
                trailingContent = {
                    BasicText(
                        text = PlayerPreferences.canvasStyle.name,
                        style = typography.xs.semiBold.copy(color = colorPalette.accent)
                    )
                }
            ),

            // --- Player (tab 1) ---
            SearchSettingEntry(
                title = "Persistent Queue",
                description = "Save queue across app restarts",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.persistentQueue = !PlayerPreferences.persistentQueue },
                trailingContent = { Switch(isChecked = PlayerPreferences.persistentQueue) }
            ),
            SearchSettingEntry(
                title = "Resume playback when device connected",
                description = "Resume audio when Bluetooth headphones or speaker connects",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.resumePlaybackWhenDeviceConnected = !PlayerPreferences.resumePlaybackWhenDeviceConnected },
                trailingContent = { Switch(isChecked = PlayerPreferences.resumePlaybackWhenDeviceConnected) }
            ),
            SearchSettingEntry(
                title = "Stop when closed",
                description = "Terminate playback service when task is swiped away from Recents",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.stopWhenClosed = !PlayerPreferences.stopWhenClosed },
                trailingContent = { Switch(isChecked = PlayerPreferences.stopWhenClosed) }
            ),
            SearchSettingEntry(
                title = "Stop on minimum volume",
                description = "Automatically pause playback when volume reaches zero",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.stopOnMinimumVolume = !PlayerPreferences.stopOnMinimumVolume },
                trailingContent = { Switch(isChecked = PlayerPreferences.stopOnMinimumVolume) }
            ),
            SearchSettingEntry(
                title = "Skip silence",
                description = "Skip silent parts of audio tracks automatically",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.skipSilence = !PlayerPreferences.skipSilence },
                trailingContent = { Switch(isChecked = PlayerPreferences.skipSilence) }
            ),
            SearchSettingEntry(
                title = "Volume Normalization",
                description = "Maintain consistent volume levels across different songs",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.volumeNormalization = !PlayerPreferences.volumeNormalization },
                trailingContent = { Switch(isChecked = PlayerPreferences.volumeNormalization) }
            ),
            SearchSettingEntry(
                title = "Bass Boost",
                description = "Amplify lower audio frequencies for deeper bass",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.bassBoost = !PlayerPreferences.bassBoost },
                trailingContent = { Switch(isChecked = PlayerPreferences.bassBoost) }
            ),
            SearchSettingEntry(
                title = "Audio Focus",
                description = "Manage playback interruptions from notifications and other apps",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.handleAudioFocus = !PlayerPreferences.handleAudioFocus },
                trailingContent = { Switch(isChecked = PlayerPreferences.handleAudioFocus) }
            ),
            SearchSettingEntry(
                title = "Player Layout",
                description = "Choose between Classic and Modern control layouts",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = {
                    PlayerPreferences.playerLayout = if (PlayerPreferences.playerLayout == PlayerPreferences.PlayerLayout.Classic) {
                        PlayerPreferences.PlayerLayout.New
                    } else {
                        PlayerPreferences.PlayerLayout.Classic
                    }
                },
                trailingContent = {
                    BasicText(
                        text = PlayerPreferences.playerLayout.name,
                        style = typography.xs.semiBold.copy(color = colorPalette.accent)
                    )
                }
            ),
            SearchSettingEntry(
                title = "Seekbar Style",
                description = "Choose between TaperedWave, Squiggly, Wavy, and Static seekbars",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = {
                    val styles = PlayerPreferences.SeekBarStyle.entries
                    val next = (PlayerPreferences.seekBarStyle.ordinal + 1) % styles.size
                    PlayerPreferences.seekBarStyle = styles[next]
                },
                trailingContent = {
                    BasicText(
                        text = PlayerPreferences.seekBarStyle.name,
                        style = typography.xs.semiBold.copy(color = colorPalette.accent)
                    )
                }
            ),
            SearchSettingEntry(
                title = "Show Favorite Button in Controls",
                description = "Display like button on the main player controls row",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.showLike = !PlayerPreferences.showLike },
                trailingContent = { Switch(isChecked = PlayerPreferences.showLike) }
            ),
            SearchSettingEntry(
                title = "Show Remaining Duration",
                description = "Display remaining time instead of total duration",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.showRemaining = !PlayerPreferences.showRemaining },
                trailingContent = { Switch(isChecked = PlayerPreferences.showRemaining) }
            ),
            SearchSettingEntry(
                title = "Horizontal swipe to close player",
                description = "Swipe horizontally on collapsed miniplayer to dismiss",
                categoryName = "Player",
                tabIndex = 1,
                onInteract = { PlayerPreferences.horizontalSwipeToClose = !PlayerPreferences.horizontalSwipeToClose },
                trailingContent = { Switch(isChecked = PlayerPreferences.horizontalSwipeToClose) }
            ),

            // --- Lyrics (tab 2) ---
            SearchSettingEntry(
                title = "Synchronized Lyrics",
                description = "Show time-synced lyrics with line-by-line animations",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.isShowingSynchronizedLyrics = !PlayerPreferences.isShowingSynchronizedLyrics },
                trailingContent = { Switch(isChecked = PlayerPreferences.isShowingSynchronizedLyrics) }
            ),
            SearchSettingEntry(
                title = "Lyrics Text Alignment",
                description = "Align lyrics text to Left, Center, or Right (works with Apple Music)",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = {
                    val positions = PlayerPreferences.LyricsTextPosition.entries
                    val next = (PlayerPreferences.lyricsTextPosition.ordinal + 1) % positions.size
                    PlayerPreferences.lyricsTextPosition = positions[next]
                },
                trailingContent = {
                    BasicText(
                        text = PlayerPreferences.lyricsTextPosition.name,
                        style = typography.xs.semiBold.copy(color = colorPalette.accent)
                    )
                }
            ),
            SearchSettingEntry(
                title = "Keep Screen Awake in Lyrics",
                description = "Prevent display from sleeping while viewing synchronized lyrics",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsKeepScreenAwake = !PlayerPreferences.lyricsKeepScreenAwake },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsKeepScreenAwake) }
            ),
            SearchSettingEntry(
                title = "Lyrics Line Blur Effect",
                description = "Blur inactive lyrics lines to emphasize current vocal line",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsLineBlur = !PlayerPreferences.lyricsLineBlur },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsLineBlur) }
            ),
            SearchSettingEntry(
                title = "Lyrics Shine Effect",
                description = "Sweep shimmering light across active lyrics line",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsShine = !PlayerPreferences.lyricsShine },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsShine) }
            ),
            SearchSettingEntry(
                title = "Lyrics Fade Animation",
                description = "Smooth opacity fade on upcoming and passed lines",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsFade = !PlayerPreferences.lyricsFade },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsFade) }
            ),
            SearchSettingEntry(
                title = "Lyrics Glow Animation",
                description = "Atmospheric glow behind active lyric syllables",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsGlow = !PlayerPreferences.lyricsGlow },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsGlow) }
            ),
            SearchSettingEntry(
                title = "Lyrics Bouncy Scroll",
                description = "Fluid spring physics when auto-scrolling lyrics lines",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsBouncyScroll = !PlayerPreferences.lyricsBouncyScroll },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsBouncyScroll) }
            ),
            SearchSettingEntry(
                title = "Show Lyric Translations",
                description = "Display translated text beneath original lyric lines",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.lyricsShowTranslation = !PlayerPreferences.lyricsShowTranslation },
                trailingContent = { Switch(isChecked = PlayerPreferences.lyricsShowTranslation) }
            ),
            SearchSettingEntry(
                title = "Hide Fullscreen Lyrics Button",
                description = "Remove the fullscreen expand toggle button on lyrics sheet",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.hideLyricsFullscreenButton = !PlayerPreferences.hideLyricsFullscreenButton },
                trailingContent = { Switch(isChecked = PlayerPreferences.hideLyricsFullscreenButton) }
            ),
            SearchSettingEntry(
                title = "Hide Fullscreen Button in Landscape",
                description = "Only hide fullscreen button when device orientation is landscape",
                categoryName = "Lyrics",
                tabIndex = 2,
                onInteract = { PlayerPreferences.hideLyricsFullscreenButtonInLandscape = !PlayerPreferences.hideLyricsFullscreenButtonInLandscape },
                trailingContent = { Switch(isChecked = PlayerPreferences.hideLyricsFullscreenButtonInLandscape) }
            ),

            // --- Cache (tab 3) ---
            SearchSettingEntry(
                title = "Pause Caching",
                description = "Prevent caching new songs and media to disk",
                categoryName = "Cache",
                tabIndex = 3,
                onInteract = { PlayerPreferences.pauseCache = !PlayerPreferences.pauseCache },
                trailingContent = { Switch(isChecked = PlayerPreferences.pauseCache) }
            ),

            // --- Database (tab 4) ---
            SearchSettingEntry(
                title = "Backup & Restore Database",
                description = "Export or restore playlists, history, and favorites database",
                categoryName = "Database",
                tabIndex = 4,
                onInteract = { onNavigateToCategory(4) },
                trailingContent = {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.chevron_forward)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                }
            ),

            // --- Sync & Accounts (tab 5) ---
            SearchSettingEntry(
                title = "Account & Piped Sync",
                description = "Synchronize playlists and library with YouTube or Piped instance",
                categoryName = "Sync",
                tabIndex = 5,
                onInteract = { onNavigateToCategory(5) },
                trailingContent = {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.chevron_forward)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                }
            ),

            // --- Other (tab 6) ---
            SearchSettingEntry(
                title = "SponsorBlock",
                description = "Skip non-music segments, intros, and sponsor messages",
                categoryName = "Other",
                tabIndex = 6,
                onInteract = { PlayerPreferences.sponsorBlockEnabled = !PlayerPreferences.sponsorBlockEnabled },
                trailingContent = { Switch(isChecked = PlayerPreferences.sponsorBlockEnabled) }
            ),
            SearchSettingEntry(
                title = "Invincibility Mode",
                description = "Prevent playback stopping on unrecoverable stream errors",
                categoryName = "Other",
                tabIndex = 6,
                onInteract = { PlayerPreferences.isInvincibilityEnabled = !PlayerPreferences.isInvincibilityEnabled },
                trailingContent = { Switch(isChecked = PlayerPreferences.isInvincibilityEnabled) }
            ),

            // --- Listen Together (tab 7) ---
            SearchSettingEntry(
                title = "Listen Together Group Session",
                description = "Broadcast and sync music playback with friends in real time",
                categoryName = "Listen Together",
                tabIndex = 7,
                onInteract = { onNavigateToCategory(7) },
                trailingContent = {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.chevron_forward)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                }
            ),

            // --- Discord (tab 8) ---
            SearchSettingEntry(
                title = "Discord Rich Presence",
                description = "Display currently playing song, album art, and progress on Discord",
                categoryName = "Discord",
                tabIndex = 8,
                onInteract = { onNavigateToCategory(8) },
                trailingContent = {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.chevron_forward)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                }
            ),

            // --- About (tab 9) ---
            SearchSettingEntry(
                title = "About & Credits",
                description = "Application version, contributors, licenses, and GitHub",
                categoryName = "About",
                tabIndex = 9,
                onInteract = { onNavigateToCategory(9) },
                trailingContent = {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.chevron_forward)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        )
    }

    val trimmedQuery = query.trim()
    val filteredSettings = remember(trimmedQuery, allSettings) {
        if (trimmedQuery.isEmpty()) emptyList()
        else {
            val terms = trimmedQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            allSettings.filter { setting ->
                val fullText = "${setting.title} ${setting.description.orEmpty()} ${setting.categoryName}".lowercase()
                terms.all { term -> fullText.contains(term) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette.background0)
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
    ) {
        // Search Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            IconButton(
                icon = R.drawable.chevron_back,
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (isExpressive) 24.dp else 16.dp))
                    .background(colorPalette.background1)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Image(
                    painter = painterResource(getAppIcon(R.drawable.search)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(12.dp))

                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = typography.s.medium.copy(color = colorPalette.text),
                    singleLine = true,
                    cursorBrush = SolidColor(colorPalette.accent),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (query.isEmpty()) {
                                BasicText(
                                    text = stringResource(R.string.search_settings),
                                    style = typography.s.medium.copy(color = colorPalette.textDisabled)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.close)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { query = "" }
                            )
                    )
                }
            }
        }

        // Helper instruction banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "Tap setting to interact • Hold to open location",
                style = typography.xxs.secondary
            )
            if (trimmedQuery.isNotEmpty()) {
                BasicText(
                    text = "${filteredSettings.size} results",
                    style = typography.xxs.semiBold.copy(color = colorPalette.accent)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Search Results List
        if (trimmedQuery.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(getAppIcon(R.drawable.search)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textDisabled.copy(alpha = 0.5f)),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        text = "Type to search settings",
                        style = typography.s.secondary
                    )
                }
            }
        } else if (filteredSettings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = "No matching settings found",
                        style = typography.m.semiBold.copy(color = colorPalette.textSecondary)
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        text = "Try searching with a different keyword",
                        style = typography.xs.secondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredSettings, key = { it.title + it.categoryName }) { item ->
                    Card(
                        shape = RoundedCornerShape(if (isExpressive) 20.dp else 12.dp),
                        colors = CardDefaults.cardColors(containerColor = colorPalette.background1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(if (isExpressive) 20.dp else 12.dp))
                            .combinedClickable(
                                onClick = item.onInteract,
                                onLongClick = { onNavigateToCategory(item.tabIndex) }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = item.categoryName.uppercase(),
                                    style = typography.xxs.semiBold.copy(color = colorPalette.accent)
                                )
                                Spacer(Modifier.height(2.dp))
                                BasicText(
                                    text = item.title,
                                    style = typography.s.semiBold.copy(color = colorPalette.text)
                                )
                                item.description?.let { desc ->
                                    Spacer(Modifier.height(2.dp))
                                    BasicText(
                                        text = desc,
                                        style = typography.xs.secondary
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            item.trailingContent()
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
