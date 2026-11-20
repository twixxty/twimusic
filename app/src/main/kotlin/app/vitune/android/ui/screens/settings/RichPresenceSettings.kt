package app.vitune.android.ui.screens.settings

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.discord.DiscordDefaults
import app.vitune.android.discord.DiscordRpcManager
import app.vitune.android.preferences.DiscordPreferences
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.bold
import app.vitune.android.utils.formatAsDuration
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import kotlinx.collections.immutable.persistentListOf

@Composable
private fun TextSettingsEntry(
    title: String,
    value: String,
    hint: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    allowEmpty: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) TextFieldDialog(
        hintText = hint,
        initialTextInput = value,
        onDismiss = { editing = false },
        onAccept = { onValue(it.trim()) },
        isTextInputValid = { allowEmpty || it.isNotBlank() },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
    SettingsEntry(
        modifier = modifier,
        title = title,
        text = value.ifBlank { hint },
        onClick = { editing = true },
        isEnabled = isEnabled
    )
}

@Route
@Composable
fun RichPresenceSettings() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val binder = LocalPlayerServiceBinder.current
    val (colorPalette, typography) = LocalAppearance.current

    val status by DiscordRpcManager.connectionStatus.collectAsState()
    val user by DiscordRpcManager.currentUser.collectAsState()
    val error by DiscordRpcManager.lastError.collectAsState()
    val token by DiscordRpcManager.accessTokenFlow.collectAsState()
    val loggedIn = token != null || DiscordPreferences.accessToken.isNotBlank()
    val enabled = DiscordPreferences.enabled

    SettingsCategoryScreen(title = stringResource(R.string.discord_rich_presence)) {
        // Warning description matching Screenshot 4
        SettingsGroup(
            title = stringResource(R.string.settings),
            description = stringResource(R.string.discord_presence_status_warning)
        ) {
            SwitchSettingsEntry(
                title = stringResource(R.string.discord_enable),
                text = null,
                isChecked = DiscordPreferences.enabled,
                onCheckedChange = {
                    DiscordPreferences.enabled = it
                    DiscordRpcManager.notifySettingsChanged()
                }
            )

            // Rich Presence Live Preview Card matching Screenshot 4
            val currentMediaItem = binder?.player?.currentMediaItem
            val player = binder?.player
            val songTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Counting Stars"
            val artistName = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "OneRepublic"
            val albumTitle = currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: "Native"
            val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri?.toString()
            val durationMs = player?.duration?.takeIf { it > 0 } ?: (4 * 60 + 17) * 1000L
            val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: (28 * 1000L)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = colorPalette.background1
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Artwork with Discord logo badge in bottom-right corner
                        Box(modifier = Modifier.size(96.dp)) {
                            if (!artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = artworkUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colorPalette.background2),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.musical_notes),
                                        contentDescription = null,
                                        tint = colorPalette.textSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Discord Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF5865F2))
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.discord),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Song Details
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            BasicText(
                                text = songTitle,
                                style = typography.m.bold.copy(color = colorPalette.text),
                                maxLines = 1
                            )
                            BasicText(
                                text = albumTitle,
                                style = typography.s.secondary,
                                maxLines = 1
                            )
                            BasicText(
                                text = artistName,
                                style = typography.s.secondary,
                                maxLines = 1
                            )
                            BasicText(
                                text = "${formatAsDuration(positionMs)} / ${formatAsDuration(durationMs)}",
                                style = typography.xs.secondary
                            )
                        }
                    }

                    // Action buttons inside card matching Screenshot 4
                    val btn1Text = DiscordPreferences.button1Text.ifBlank { stringResource(R.string.discord_button1_text_default) }
                    Button(
                        onClick = {
                            val url = "https://music.youtube.com/watch?v=${currentMediaItem?.mediaId.orEmpty()}"
                            runCatching { uriHandler.openUri(url) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette.background2,
                            contentColor = colorPalette.text
                        )
                    ) {
                        BasicText(
                            text = btn1Text,
                            style = typography.xs.semiBold.copy(color = colorPalette.text)
                        )
                    }

                    if (DiscordPreferences.button2Enabled) {
                        val btn2Text = DiscordPreferences.button2Text.ifBlank { stringResource(R.string.discord_button2_text_default) }
                        Button(
                            onClick = {
                                runCatching { uriHandler.openUri(DiscordPreferences.button2Url) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPalette.background2,
                                contentColor = colorPalette.text
                            )
                        ) {
                            BasicText(
                                text = btn2Text,
                                style = typography.xs.semiBold.copy(color = colorPalette.text)
                            )
                        }
                    }
                }
            }

            // Refresh RPC card
            SettingsEntry(
                title = stringResource(R.string.discord_refresh_rpc),
                text = stringResource(R.string.discord_refresh_rpc_description),
                onClick = {
                    DiscordRpcManager.notifySettingsChanged()
                    Toast.makeText(context, R.string.discord_refresh_rpc_description, Toast.LENGTH_SHORT).show()
                },
                isEnabled = enabled
            )

            // Quick toggle button toggle matching user requirement
            SwitchSettingsEntry(
                title = stringResource(R.string.discord_quick_toggle),
                text = stringResource(R.string.discord_quick_toggle_description),
                isChecked = DiscordPreferences.showQuickToggle,
                onCheckedChange = { DiscordPreferences.showQuickToggle = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.discord_show_artist_icon),
                text = stringResource(R.string.discord_show_artist_icon_description),
                isChecked = DiscordPreferences.showArtistIcon,
                onCheckedChange = {
                    DiscordPreferences.showArtistIcon = it
                    DiscordRpcManager.notifySettingsChanged()
                },
                isEnabled = enabled
            )

            ValueSelectorSettingsEntry(
                title = stringResource(R.string.discord_activity_type),
                selectedValue = DiscordPreferences.activityType,
                values = persistentListOf("0", "2", "3", "5"),
                onValueSelect = {
                    DiscordPreferences.activityType = it
                    DiscordRpcManager.notifySettingsChanged()
                },
                valueText = {
                    when (it) {
                        "0" -> stringResource(R.string.discord_type_playing)
                        "3" -> stringResource(R.string.discord_type_watching)
                        "5" -> stringResource(R.string.discord_type_competing)
                        else -> stringResource(R.string.discord_type_listening)
                    }
                },
                isEnabled = enabled
            )
        }

        SettingsGroupSpacer()

        // Account Group
        SettingsGroup(
            title = stringResource(R.string.account)
        ) {
            SettingsEntry(
                title = if (loggedIn) stringResource(R.string.discord_logout)
                else stringResource(R.string.discord_login),
                text = when {
                    DiscordLoginFlow.exchanging -> stringResource(R.string.discord_logging_in)
                    loggedIn -> (user?.name?.takeIf { it.isNotBlank() }
                        ?: DiscordPreferences.name.takeIf { it.isNotBlank() }
                        ?: DiscordPreferences.username).let {
                        "$it · ${status.name}"
                    }
                    else -> stringResource(R.string.discord_login_description)
                },
                onClick = {
                    if (loggedIn) DiscordRpcManager.logout()
                    else runCatching {
                        DiscordRpcManager.init(app.vitune.android.discord.DiscordHttp.client)
                        DiscordRpcManager.clearLastError()
                        val session = DiscordRpcManager.auth.createSession()
                        DiscordLoginFlow.start(session)
                        launchCustomTab(context, session.authorizeUrl)
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.discord_error_token_exchange_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            error?.let { info ->
                val resId = context.resources.getIdentifier(info.type, "string", context.packageName)
                val baseMsg = if (resId != 0) context.getString(resId) else info.type
                val displayMsg = if (info.message.isNotBlank() && info.message != info.type) {
                    "$baseMsg\n${info.message}"
                } else baseMsg
                SettingsDescription(
                    text = displayMsg,
                    important = true
                )
            }
        }

        SettingsGroupSpacer()

        // Advanced Mode Group matching Screenshot 4
        SettingsGroup(
            title = stringResource(R.string.advanced)
        ) {
            SwitchSettingsEntry(
                title = stringResource(R.string.discord_advanced_mode),
                text = stringResource(R.string.discord_advanced_mode_description),
                isChecked = DiscordPreferences.advancedMode,
                onCheckedChange = {
                    DiscordPreferences.advancedMode = it
                    DiscordRpcManager.notifySettingsChanged()
                },
                isEnabled = enabled
            )

            if (DiscordPreferences.advancedMode) {
                TextSettingsEntry(
                    title = stringResource(R.string.discord_activity_name),
                    value = DiscordPreferences.activityName,
                    hint = stringResource(R.string.discord_activity_name_hint),
                    onValue = {
                        DiscordPreferences.activityName = it
                        DiscordRpcManager.notifySettingsChanged()
                    },
                    isEnabled = enabled
                )

                TextSettingsEntry(
                    title = stringResource(R.string.discord_details_template),
                    value = DiscordPreferences.detailsTemplate,
                    hint = "{song.name}",
                    onValue = {
                        DiscordPreferences.detailsTemplate = it
                        DiscordRpcManager.notifySettingsChanged()
                    },
                    isEnabled = enabled
                )
                TextSettingsEntry(
                    title = stringResource(R.string.discord_state_template),
                    value = DiscordPreferences.stateTemplate,
                    hint = "{artist.name}",
                    onValue = {
                        DiscordPreferences.stateTemplate = it
                        DiscordRpcManager.notifySettingsChanged()
                    },
                    isEnabled = enabled
                )
                SettingsDescription(text = stringResource(R.string.discord_placeholders))

                // Buttons
                SwitchSettingsEntry(
                    title = stringResource(R.string.discord_button_1),
                    text = null,
                    isChecked = DiscordPreferences.button1Enabled,
                    onCheckedChange = {
                        DiscordPreferences.button1Enabled = it
                        DiscordRpcManager.notifySettingsChanged()
                    },
                    isEnabled = enabled
                )
                if (DiscordPreferences.button1Enabled) {
                    TextSettingsEntry(
                        title = stringResource(R.string.discord_button_text),
                        value = DiscordPreferences.button1Text,
                        hint = stringResource(R.string.discord_button1_text_default),
                        onValue = {
                            DiscordPreferences.button1Text = it
                            DiscordRpcManager.notifySettingsChanged()
                        },
                        isEnabled = enabled
                    )
                    TextSettingsEntry(
                        title = stringResource(R.string.discord_button_url),
                        value = DiscordPreferences.button1Url,
                        hint = "{song.url}",
                        onValue = {
                            DiscordPreferences.button1Url = it
                            DiscordRpcManager.notifySettingsChanged()
                        },
                        isEnabled = enabled,
                        keyboardType = KeyboardType.Uri
                    )
                }

                SwitchSettingsEntry(
                    title = stringResource(R.string.discord_button_2),
                    text = null,
                    isChecked = DiscordPreferences.button2Enabled,
                    onCheckedChange = {
                        DiscordPreferences.button2Enabled = it
                        DiscordRpcManager.notifySettingsChanged()
                    },
                    isEnabled = enabled
                )
                if (DiscordPreferences.button2Enabled) {
                    TextSettingsEntry(
                        title = stringResource(R.string.discord_button_text),
                        value = DiscordPreferences.button2Text,
                        hint = stringResource(R.string.discord_button2_text_default),
                        onValue = {
                            DiscordPreferences.button2Text = it
                            DiscordRpcManager.notifySettingsChanged()
                        },
                        isEnabled = enabled
                    )
                    TextSettingsEntry(
                        title = stringResource(R.string.discord_button_url),
                        value = DiscordPreferences.button2Url,
                        hint = DiscordDefaults.BUTTON2_URL,
                        onValue = {
                            DiscordPreferences.button2Url = it
                            DiscordRpcManager.notifySettingsChanged()
                        },
                        isEnabled = enabled,
                        keyboardType = KeyboardType.Uri
                    )
                }
            }
        }
    }
}

private fun launchCustomTab(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(url)
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            val extras = android.os.Bundle()
            extras.putBinder("android.support.customtabs.extra.SESSION", null)
            putExtras(extras)
            putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
            putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true)
            putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2)
            putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.packageName)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(fallbackIntent)
    }
}
