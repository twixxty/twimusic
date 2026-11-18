package app.vitune.android.ui.screens.settings

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.discord.DiscordDefaults
import app.vitune.android.discord.DiscordRpcManager
import app.vitune.android.listentogether.ListenTogetherConnectionState
import app.vitune.android.listentogether.ListenTogetherEvent
import app.vitune.android.listentogether.ListenTogetherRoomRole
import app.vitune.android.preferences.DiscordPreferences
import app.vitune.android.preferences.ListenTogetherPreferences
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.secondary
import app.vitune.core.ui.LocalAppearance
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
    val (_, typography) = LocalAppearance.current

    SettingsCategoryScreen(title = stringResource(R.string.rich_presence)) {
        // ---------------------------------------------------------------- Discord
        val status by DiscordRpcManager.connectionStatus.collectAsState()
        val user by DiscordRpcManager.currentUser.collectAsState()
        val error by DiscordRpcManager.lastError.collectAsState()
        val token by DiscordRpcManager.accessTokenFlow.collectAsState()
        val loggedIn = token != null || DiscordPreferences.accessToken.isNotBlank()

        SettingsGroup(
            title = stringResource(R.string.discord_rich_presence),
            description = stringResource(R.string.discord_rich_presence_description)
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
                        uriHandler.openUri(session.authorizeUrl)
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
                SettingsDescription(
                    text = if (resId != 0) context.getString(resId) else info.message,
                    important = true
                )
            }

            val enabled = DiscordPreferences.enabled

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
            }

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

            TextSettingsEntry(
                title = stringResource(R.string.discord_custom_app_id),
                value = DiscordPreferences.customAppId,
                hint = stringResource(R.string.discord_custom_app_id_hint),
                onValue = {
                    DiscordPreferences.customAppId = it.filter(Char::isDigit)
                    DiscordRpcManager.logout()
                },
                isEnabled = enabled,
                keyboardType = KeyboardType.Number
            )
            SettingsDescription(text = stringResource(R.string.discord_custom_app_id_description))
        }

        SettingsGroupSpacer()

        // --------------------------------------------------------- Listen Together
        val manager = binder?.listenTogether
        SettingsGroup(
            title = stringResource(R.string.listen_together),
            description = stringResource(R.string.listen_together_description)
        ) {
            SwitchSettingsEntry(
                title = stringResource(R.string.listen_together_enable),
                text = null,
                isChecked = ListenTogetherPreferences.enabled,
                onCheckedChange = {
                    ListenTogetherPreferences.enabled = it
                    manager?.setEnabled(it)
                }
            )

            if (manager == null) {
                SettingsDescription(text = stringResource(R.string.listen_together_service_unavailable))
                return@SettingsGroup
            }

            val enabled = ListenTogetherPreferences.enabled
            val connection by manager.connectionState.collectAsState()
            val room by manager.roomState.collectAsState()
            val role by manager.role.collectAsState()
            val myId by manager.userId.collectAsState()
            val joinRequests by manager.pendingJoinRequests.collectAsState()
            val suggestions by manager.pendingSuggestions.collectAsState()
            val servers by manager.servers.collectAsState()
            val serverUrl by manager.serverUrl.collectAsState()
            val blocked by manager.blockedUsernames.collectAsState()
            val pendingRequest by manager.roomRequestPending.collectAsState()

            LaunchedEffect(manager) {
                manager.events.collect { event ->
                    val message = when (event) {
                        is ListenTogetherEvent.Notice -> when (event.key) {
                            ListenTogetherEvent.Notice.Key.RoomCreated -> R.string.lt_room_created
                            ListenTogetherEvent.Notice.Key.JoinedRoom -> R.string.lt_joined_room
                            ListenTogetherEvent.Notice.Key.Reconnected -> R.string.lt_reconnected
                            ListenTogetherEvent.Notice.Key.BecameHost -> R.string.lt_became_host
                            ListenTogetherEvent.Notice.Key.HostChanged -> R.string.lt_host_changed
                            ListenTogetherEvent.Notice.Key.SuggestionSubmitted -> R.string.lt_suggestion_submitted
                            ListenTogetherEvent.Notice.Key.SuggestionApproved -> R.string.lt_suggestion_approved
                            ListenTogetherEvent.Notice.Key.SuggestionRejected -> R.string.lt_suggestion_rejected
                        }

                        is ListenTogetherEvent.Failure -> when (event.key) {
                            ListenTogetherEvent.Failure.Key.ConnectionFailed -> R.string.lt_connection_failed
                            ListenTogetherEvent.Failure.Key.JoinRejected -> R.string.lt_join_rejected
                            ListenTogetherEvent.Failure.Key.Kicked -> R.string.lt_kicked
                            ListenTogetherEvent.Failure.Key.ServerError -> R.string.lt_server_error
                            ListenTogetherEvent.Failure.Key.QueueTooLarge -> R.string.lt_queue_too_large
                            ListenTogetherEvent.Failure.Key.TrackLoadFailed -> R.string.lt_track_load_failed
                        }

                        is ListenTogetherEvent.Reconnecting -> null
                        is ListenTogetherEvent.SuggestionAutoApproved -> R.string.lt_suggestion_approved
                    }
                    if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            TextSettingsEntry(
                title = stringResource(R.string.lt_username),
                value = ListenTogetherPreferences.username,
                hint = stringResource(R.string.lt_username_hint),
                onValue = { manager.setUsername(it) },
                isEnabled = enabled
            )

            ValueSelectorSettingsEntry(
                title = stringResource(R.string.lt_server),
                selectedValue = serverUrl,
                values = servers.map { it.url }.distinct().toImmutableList(),
                onValueSelect = { manager.setServerUrl(it) },
                valueText = { url -> servers.firstOrNull { it.url == url }?.name ?: url },
                text = servers.firstOrNull { it.url == serverUrl }?.name ?: serverUrl,
                isEnabled = enabled && room == null
            )

            TextSettingsEntry(
                title = stringResource(R.string.lt_add_server),
                value = "",
                hint = "wss://example.com/ws",
                onValue = { if (it.isNotBlank()) manager.addCustomServer(it) },
                isEnabled = enabled,
                keyboardType = KeyboardType.Uri
            )

            servers.filter { it.isCustom }.forEach { server ->
                SettingsEntry(
                    title = server.name,
                    text = stringResource(R.string.lt_remove_server),
                    onClick = { manager.removeCustomServer(server.url) },
                    isEnabled = enabled
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.lt_auto_approve_joins),
                text = null,
                isChecked = ListenTogetherPreferences.autoApproveJoinRequests,
                onCheckedChange = { ListenTogetherPreferences.autoApproveJoinRequests = it },
                isEnabled = enabled
            )
            SwitchSettingsEntry(
                title = stringResource(R.string.lt_auto_approve_suggestions),
                text = null,
                isChecked = ListenTogetherPreferences.autoApproveSongSuggestions,
                onCheckedChange = { ListenTogetherPreferences.autoApproveSongSuggestions = it },
                isEnabled = enabled
            )
            SwitchSettingsEntry(
                title = stringResource(R.string.lt_sync_volume),
                text = stringResource(R.string.lt_sync_volume_description),
                isChecked = ListenTogetherPreferences.syncHostVolume,
                onCheckedChange = { ListenTogetherPreferences.syncHostVolume = it },
                isEnabled = enabled
            )

            blocked.forEach { name ->
                SettingsEntry(
                    title = name,
                    text = stringResource(R.string.lt_unblock),
                    onClick = { manager.unblockUser(name) }
                )
            }

            SettingsGroupSpacer()

            // Session
            val currentRoom = room
            if (currentRoom == null) {
                SettingsDescription(
                    text = when {
                        pendingRequest -> stringResource(R.string.lt_waiting_for_approval)
                        connection == ListenTogetherConnectionState.CONNECTING ->
                            stringResource(R.string.lt_connecting)

                        connection == ListenTogetherConnectionState.ERROR ->
                            stringResource(R.string.lt_connection_failed)

                        else -> stringResource(R.string.lt_not_in_room)
                    }
                )
                if (pendingRequest) SettingsEntry(
                    title = stringResource(R.string.cancel),
                    onClick = { manager.cancelJoinRequest() }
                ) else {
                    SettingsEntry(
                        title = stringResource(R.string.lt_create_room),
                        onClick = { manager.createRoom() },
                        isEnabled = enabled
                    )
                    var joining by remember { mutableStateOf(false) }
                    if (joining) TextFieldDialog(
                        hintText = stringResource(R.string.lt_room_code),
                        onDismiss = { joining = false },
                        onAccept = { manager.joinRoom(it.trim().uppercase()) }
                    )
                    SettingsEntry(
                        title = stringResource(R.string.lt_join_room),
                        onClick = { joining = true },
                        isEnabled = enabled
                    )
                    if (connection == ListenTogetherConnectionState.ERROR) SettingsEntry(
                        title = stringResource(R.string.lt_retry),
                        onClick = { manager.retryConnection() }
                    )
                }
            } else {
                SettingsEntry(
                    title = stringResource(R.string.lt_room_code),
                    text = currentRoom.roomCode + if (role == ListenTogetherRoomRole.HOST)
                        " · " + stringResource(R.string.lt_you_are_host) else "",
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("room", currentRoom.roomCode))
                        Toast.makeText(context, R.string.lt_code_copied, Toast.LENGTH_SHORT).show()
                    }
                )
                manager.roomLink()?.let { link ->
                    SettingsEntry(
                        title = stringResource(R.string.lt_share_link),
                        text = link,
                        onClick = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, link),
                                    null
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
                if (role == ListenTogetherRoomRole.GUEST) SettingsEntry(
                    title = stringResource(R.string.lt_resync),
                    onClick = { manager.requestSync() }
                )

                joinRequests.forEach { request ->
                    SettingsEntry(
                        title = stringResource(R.string.lt_join_request, request.username),
                        text = stringResource(R.string.lt_tap_to_approve),
                        onClick = { manager.approveJoin(request.userId) },
                        trailingContent = {
                            BasicText(
                                text = stringResource(R.string.lt_reject),
                                style = typography.xs.secondary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable { manager.rejectJoin(request.userId) }
                            )
                        }
                    )
                }
                suggestions.forEach { s ->
                    SettingsEntry(
                        title = stringResource(
                            R.string.lt_suggestion_from,
                            s.fromUsername,
                            s.trackInfo.title
                        ),
                        text = stringResource(R.string.lt_tap_to_approve),
                        onClick = { manager.approveSuggestion(s.id) },
                        trailingContent = {
                            BasicText(
                                text = stringResource(R.string.lt_reject),
                                style = typography.xs.secondary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable { manager.rejectSuggestion(s.id) }
                            )
                        }
                    )
                }

                Column {
                    currentRoom.users.forEach { u ->
                        var menu by remember { mutableStateOf(false) }
                        if (menu && role == ListenTogetherRoomRole.HOST && u.userId != myId)
                            app.vitune.android.ui.components.themed.ValueSelectorDialog(
                                onDismiss = { menu = false },
                                title = u.username,
                                selectedValue = "",
                                values = persistentListOf("transfer", "kick", "block"),
                                onValueSelect = {
                                    when (it) {
                                        "transfer" -> manager.transferHost(u.userId)
                                        "kick" -> manager.kickUser(u.userId)
                                        "block" -> {
                                            manager.blockUser(u.username)
                                            manager.kickUser(u.userId)
                                        }
                                    }
                                },
                                valueText = {
                                    when (it) {
                                        "transfer" -> stringResource(R.string.lt_make_host)
                                        "kick" -> stringResource(R.string.lt_kick)
                                        else -> stringResource(R.string.lt_block)
                                    }
                                }
                            )
                        SettingsEntry(
                            title = u.username + if (u.userId == myId) " (" +
                                stringResource(R.string.lt_you) + ")" else "",
                            text = when {
                                u.isHost -> stringResource(R.string.lt_host)
                                !u.isConnected -> stringResource(R.string.lt_disconnected)
                                else -> stringResource(R.string.lt_guest)
                            },
                            onClick = { menu = true },
                            isEnabled = role == ListenTogetherRoomRole.HOST && u.userId != myId
                        )
                    }
                }

                SettingsEntry(
                    title = stringResource(R.string.lt_leave_room),
                    onClick = { manager.leaveRoom() }
                )
            }
        }
    }
}

