package app.vitune.android.ui.screens.listentogether

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.preferences.ListenTogetherPreferences
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.Switch
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.components.themed.ValueSelectorDialog
import app.vitune.android.ui.screens.Route
import app.vitune.android.ui.screens.settings.EnumValueSelectorSettingsEntry
import app.vitune.android.ui.screens.settings.SettingsDescription
import app.vitune.android.ui.screens.settings.SettingsEntry
import app.vitune.android.ui.screens.settings.SettingsGroup
import app.vitune.android.ui.screens.settings.SettingsGroupSpacer
import app.vitune.android.ui.screens.settings.SwitchSettingsEntry
import app.vitune.android.utils.bold
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import kotlinx.collections.immutable.toImmutableList

@Route
@Composable
fun ListenTogetherSettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val manager = binder?.listenTogether
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val enabled = ListenTogetherPreferences.enabled
    val servers by (manager?.servers?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val serverUrl by (manager?.serverUrl?.collectAsState() ?: remember { mutableStateOf(ListenTogetherPreferences.serverUrl) })
    val room by (manager?.roomState?.collectAsState() ?: remember { mutableStateOf(null) })
    val blocked by (manager?.blockedUsernames?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    var editingUsername by remember { mutableStateOf(false) }
    var addingServer by remember { mutableStateOf(false) }
    var selectingServer by remember { mutableStateOf(false) }

    if (editingUsername) {
        TextFieldDialog(
            hintText = stringResource(R.string.lt_username_hint),
            initialTextInput = ListenTogetherPreferences.username,
            onDismiss = { editingUsername = false },
            onAccept = { newName ->
                val trimmed = newName.trim()
                ListenTogetherPreferences.username = trimmed
                manager?.setUsername(trimmed)
            },
            isTextInputValid = { it.isNotBlank() }
        )
    }

    if (addingServer) {
        TextFieldDialog(
            hintText = "wss://example.com/ws",
            initialTextInput = "",
            onDismiss = { addingServer = false },
            onAccept = { newUrl ->
                val trimmed = newUrl.trim()
                if (trimmed.isNotBlank()) manager?.addCustomServer(trimmed)
            },
            isTextInputValid = { it.isNotBlank() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }

    if (selectingServer) {
        val serverUrls = servers.map { it.url }.distinct().toImmutableList()
        ValueSelectorDialog(
            onDismiss = { selectingServer = false },
            title = stringResource(R.string.lt_server),
            selectedValue = serverUrl,
            values = serverUrls,
            onValueSelect = { selected ->
                manager?.setServerUrl(selected)
                selectingServer = false
            },
            valueText = { url -> servers.firstOrNull { it.url == url }?.name ?: url }
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                windowInsets
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                icon = R.drawable.chevron_back,
                color = colorPalette.text
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(R.string.listen_together),
                style = typography.xxl.bold.copy(color = colorPalette.text)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Master Switch Card matching Screenshot 1
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (enabled) colorPalette.accent.copy(alpha = 0.22f) else colorPalette.background1)
                .clickable {
                    val target = !ListenTogetherPreferences.enabled
                    ListenTogetherPreferences.enabled = target
                    manager?.setEnabled(target)
                }
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = stringResource(R.string.listen_together),
                    style = typography.l.semiBold.copy(color = colorPalette.text)
                )

                Switch(
                    isChecked = ListenTogetherPreferences.enabled
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Account Group
        SettingsGroup(
            title = stringResource(R.string.account)
        ) {
            SettingsEntry(
                title = stringResource(R.string.username),
                text = ListenTogetherPreferences.username.ifBlank { stringResource(R.string.lt_username_hint) },
                onClick = { editingUsername = true },
                isEnabled = enabled
            )

            val currentServerName = servers.firstOrNull { it.url == serverUrl }?.name ?: serverUrl
            SettingsEntry(
                title = stringResource(R.string.lt_server),
                text = currentServerName.ifBlank { "Select server" },
                onClick = { selectingServer = true },
                isEnabled = enabled && room == null
            )
        }

        SettingsGroupSpacer()

        // Settings Group
        SettingsGroup(
            title = stringResource(R.string.settings)
        ) {
            SwitchSettingsEntry(
                title = stringResource(R.string.lt_auto_approve_joins),
                text = null,
                isChecked = ListenTogetherPreferences.autoApproveJoinRequests,
                onCheckedChange = { ListenTogetherPreferences.autoApproveJoinRequests = it },
                isEnabled = enabled
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lt_auto_approve_suggestions),
                text = stringResource(R.string.lt_auto_approve_suggestions_desc),
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

            SwitchSettingsEntry(
                title = stringResource(R.string.lt_quick_access),
                text = stringResource(R.string.lt_quick_access_description),
                isChecked = ListenTogetherPreferences.showQuickAccess,
                onCheckedChange = { ListenTogetherPreferences.showQuickAccess = it }
            )
        }

        SettingsGroupSpacer()

        // Custom Servers
        SettingsGroup(
            title = stringResource(R.string.servers)
        ) {
            SettingsEntry(
                title = stringResource(R.string.lt_add_server),
                text = "Add WebSocket server URL",
                onClick = { addingServer = true },
                isEnabled = enabled
            )

            servers.filter { it.isCustom }.forEach { server ->
                SettingsEntry(
                    title = server.name,
                    text = stringResource(R.string.lt_remove_server),
                    onClick = { manager?.removeCustomServer(server.url) },
                    isEnabled = enabled
                )
            }
        }

        if (blocked.isNotEmpty()) {
            SettingsGroupSpacer()
            SettingsGroup(
                title = stringResource(R.string.blocked_users)
            ) {
                blocked.forEach { name ->
                    SettingsEntry(
                        title = name,
                        text = stringResource(R.string.lt_unblock),
                        onClick = { manager?.unblockUser(name) },
                        isEnabled = enabled
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}
