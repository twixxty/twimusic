package app.vitune.android.ui.screens.settings

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.preferences.AccountPreferences
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.Switch
import app.vitune.android.utils.YouTubeSync
import app.vitune.android.utils.bold
import app.vitune.android.utils.color
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountSettings(
    onNavigateToLogin: (isSwitchingChannel: Boolean) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(AccountPreferences.isLoggedIn) }
    var accountName by remember { mutableStateOf(AccountPreferences.accountName) }
    var accountHandle by remember { mutableStateOf(AccountPreferences.accountChannelHandle) }
    var accountEmail by remember { mutableStateOf(AccountPreferences.accountEmail) }
    val isSyncing by YouTubeSync.isSyncing.collectAsState()
    val syncStatus by YouTubeSync.syncStatus.collectAsState()

    var showTokenDialog by rememberSaveable { mutableStateOf(false) }

    fun processTokenLogin(token: String) {
        val cleanToken = token.trim()
        val cookie = if (!cleanToken.contains("=") && !cleanToken.contains(";")) {
            "SAPISID=$cleanToken"
        } else {
            cleanToken
        }
        coroutineScope.launch(Dispatchers.IO) {
            YouTube.cookie = cookie
            app.vitune.providers.innertube.Innertube.cookie = cookie
            val accountInfo = runCatching { YouTube.accountInfo().getOrNull() }.getOrNull()
            withContext(Dispatchers.Main) {
                AccountPreferences.innerTubeCookie = cookie
                if (accountInfo != null) {
                    AccountPreferences.accountName = accountInfo.name
                    AccountPreferences.accountEmail = accountInfo.email.orEmpty()
                    AccountPreferences.accountChannelHandle = accountInfo.channelHandle.orEmpty()
                    accountName = accountInfo.name
                    accountHandle = accountInfo.channelHandle.orEmpty()
                    accountEmail = accountInfo.email.orEmpty()
                } else {
                    val fallbackName = "YouTube Account"
                    AccountPreferences.accountName = fallbackName
                    accountName = fallbackName
                }
                isLoggedIn = true
                Toast.makeText(context, "Account token saved", Toast.LENGTH_SHORT).show()
            }
            YouTubeSync.schedulePeriodicSync(context)
            YouTubeSync.syncAll(context)
        }
    }

    if (showTokenDialog) {
        TextFieldDialog(
            hintText = "Paste YouTube Music cookie or SAPISID token",
            singleLine = false,
            maxLines = 6,
            isTextInputValid = { it.isNotBlank() },
            onDismiss = { showTokenDialog = false },
            onAccept = { token ->
                showTokenDialog = false
                processTokenLogin(token)
            }
        )
    }

    SettingsGroup(title = stringResource(R.string.youtube_account)) {
        if (isLoggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette.surface)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colorPalette.accent)
                    ) {
                        BasicText(
                            text = accountName.firstOrNull()?.uppercase() ?: "Y",
                            style = typography.m.bold.color(colorPalette.onAccent)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = accountName.ifBlank { "YouTube User" },
                            style = typography.m.semiBold.color(colorPalette.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (accountHandle.isNotBlank()) {
                            BasicText(
                                text = accountHandle,
                                style = typography.xs.secondary.color(colorPalette.textSecondary),
                                maxLines = 1
                            )
                        } else if (accountEmail.isNotBlank()) {
                            BasicText(
                                text = accountEmail,
                                style = typography.xs.secondary.color(colorPalette.textSecondary),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SecondaryTextButton(
                    text = if (isSyncing) syncStatus.ifBlank { "Syncing..." } else "Sync Library with YouTube Music",
                    enabled = !isSyncing,
                    onClick = {
                        coroutineScope.launch {
                            YouTubeSync.syncAll(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.switch_account),
                        onClick = { onNavigateToLogin(true) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryTextButton(
                        text = "Token Login",
                        onClick = { showTokenDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryTextButton(
                        text = stringResource(R.string.log_out),
                        onClick = {
                            AccountPreferences.clear()
                            YouTube.cookie = null
                            YouTube.visitorData = null
                            YouTube.dataSyncId = null
                            YouTube.authUser = "0"
                            app.vitune.providers.innertube.Innertube.cookie = null
                            app.vitune.providers.innertube.Innertube.visitorData = null
                            CookieManager.getInstance().removeAllCookies(null)
                            YouTubeSync.cancelPeriodicSync(context)
                            isLoggedIn = false
                            accountName = ""
                            accountHandle = ""
                            accountEmail = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.use_login_for_browse),
                text = stringResource(R.string.use_login_for_browse_description),
                isChecked = AccountPreferences.useLoginForBrowse,
                onCheckedChange = {
                    AccountPreferences.useLoginForBrowse = it
                    YouTube.useLoginForBrowse = it
                }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.report_history),
                text = stringResource(R.string.report_history_description),
                isChecked = AccountPreferences.reportHistory,
                onCheckedChange = { AccountPreferences.reportHistory = it }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette.surface)
                    .padding(16.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.not_logged_in),
                    style = typography.m.semiBold.color(colorPalette.text)
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.use_login_for_browse_description),
                    style = typography.xs.secondary.color(colorPalette.textSecondary)
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryTextButton(
                    text = stringResource(R.string.sign_in_youtube),
                    onClick = { onNavigateToLogin(false) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryTextButton(
                    text = "Add Account by Token / Cookie",
                    onClick = { showTokenDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
