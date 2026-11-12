package app.vitune.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.DialogTextButton
import app.vitune.android.ui.components.themed.UpdateDialog
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.AppUpdater
import app.vitune.android.utils.center
import app.vitune.android.utils.medium
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.github.models.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REPO_OWNER = AppUpdater.REPO_OWNER
private const val REPO_NAME = AppUpdater.REPO_NAME

@Route
@Composable
fun About() = SettingsCategoryScreen(
    title = stringResource(R.string.about),
    description = stringResource(
        R.string.format_version_credits,
        AppUpdater.currentVersionName
    )
) {
    val typography = LocalAppearance.current.typography
    val uriHandler = LocalUriHandler.current

    SettingsGroup(title = stringResource(R.string.social)) {
        SettingsEntry(
            title = stringResource(R.string.github),
            text = stringResource(R.string.view_source),
            onClick = {
                uriHandler.openUri("https://github.com/$REPO_OWNER/$REPO_NAME")
            }
        )
    }

    SettingsGroup(title = stringResource(R.string.contact)) {
        SettingsEntry(
            title = stringResource(R.string.report_bug),
            text = stringResource(R.string.report_bug_description),
            onClick = {
                uriHandler.openUri(
                    @Suppress("MaximumLineLength")
                    "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new?assignees=&labels=bug&template=bug_report.yaml"
                )
            }
        )

        SettingsEntry(
            title = stringResource(R.string.request_feature),
            text = stringResource(R.string.redirect_github),
            onClick = {
                uriHandler.openUri(
                    @Suppress("MaximumLineLength")
                    "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new?assignees=&labels=enhancement&template=feature_request.md"
                )
            }
        )
    }

    var newVersionDialogOpened by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(title = stringResource(R.string.version)) {
        SettingsEntry(
            title = stringResource(R.string.check_new_version),
            text = stringResource(R.string.current_version, AppUpdater.currentVersionName),
            onClick = { newVersionDialogOpened = true }
        )

        SwitchSettingsEntry(
            title = stringResource(R.string.check_updates_on_startup),
            text = stringResource(R.string.check_updates_on_startup_description),
            isChecked = DataPreferences.checkUpdatesOnStartup,
            onCheckedChange = { DataPreferences.checkUpdatesOnStartup = it }
        )
    }

    var licensesDialogOpened by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(title = stringResource(R.string.credits_and_licenses)) {
        SettingsEntry(
            title = stringResource(R.string.open_source_licenses),
            text = stringResource(R.string.credits_and_licenses_description),
            onClick = { licensesDialogOpened = true }
        )
    }

    if (licensesDialogOpened) {
        val colorPalette = LocalAppearance.current.colorPalette
        DefaultDialog(
            onDismiss = { licensesDialogOpened = false }
        ) {
            BasicText(
                text = stringResource(R.string.credits_and_licenses),
                style = typography.s.semiBold.center.copy(color = colorPalette.text),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .weight(weight = 1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                LicenseItem(
                    name = "TwiMusic",
                    author = "twixxty",
                    license = "GPL-3.0",
                    url = "https://github.com/twixxty/twimusic",
                    description = "Music player for YouTube Music",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "accompanist-lyrics",
                    author = "6xingyv",
                    license = "Apache-2.0",
                    url = "https://github.com/6xingyv/accompanist-lyrics-ui",
                    description = "Karaoke lyrics UI & timing models",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "lrc.red",
                    author = "lrc.red community",
                    license = "Community API",
                    url = "https://lrc.red",
                    description = "Synced lyrics & translation provider",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "ViTune",
                    author = "25huizengek1",
                    license = "GPL-3.0",
                    url = "https://github.com/25huizengek1/ViTune",
                    description = "Original ViTune application base",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "ViMusic",
                    author = "vfsfitvnm",
                    license = "GPL-3.0",
                    url = "https://github.com/vfsfitvnm/ViMusic",
                    description = "Inspiration, base design & app icon",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "InnerTune",
                    author = "z-huang",
                    license = "GPL-3.0",
                    url = "https://github.com/z-huang/InnerTune",
                    description = "Dynamic colors, equalizer & replaygain",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "Metrolist InnerTube",
                    author = "metrolistgroup",
                    license = "Apache-2.0",
                    url = "https://github.com/metrolistgroup/innertube",
                    description = "YouTube Music InnerTube client",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "LRCLIB",
                    author = "Simon Csaba",
                    license = "Service",
                    url = "https://lrclib.net",
                    description = "Synchronized lyrics database",
                    uriHandler = uriHandler
                )
                LicenseItem(
                    name = "ionicons",
                    author = "ionic-team",
                    license = "MIT",
                    url = "https://github.com/ionic-team/ionicons",
                    description = "Icons",
                    uriHandler = uriHandler
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            DialogTextButton(
                text = stringResource(R.string.dismiss),
                onClick = { licensesDialogOpened = false }
            )
        }
    }

    if (newVersionDialogOpened) {
        var newerVersion: Result<Release?>? by remember { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                newerVersion = AppUpdater.getNewerVersion()
                    .onFailure(Throwable::printStackTrace)
            }
        }

        newerVersion?.getOrNull()?.let { release ->
            UpdateDialog(
                release = release,
                onDismiss = { newVersionDialogOpened = false }
            )
        } ?: DefaultDialog(
            onDismiss = { newVersionDialogOpened = false }
        ) {
            newerVersion?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_github),
                    style = typography.xs.semiBold.center,
                    modifier = Modifier.padding(all = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogTextButton(
                    text = stringResource(R.string.dismiss),
                    onClick = { newVersionDialogOpened = false }
                )
            } ?: if (newerVersion?.isSuccess == true) {
                BasicText(
                    text = stringResource(R.string.up_to_date),
                    style = typography.xs.semiBold.center,
                    modifier = Modifier.padding(all = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogTextButton(
                    text = stringResource(R.string.dismiss),
                    onClick = { newVersionDialogOpened = false }
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun LicenseItem(
    name: String,
    author: String,
    license: String,
    url: String,
    description: String,
    uriHandler: UriHandler
) {
    val typography = LocalAppearance.current.typography
    val colorPalette = LocalAppearance.current.colorPalette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(url) }
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = name,
                style = typography.xs.semiBold.copy(color = colorPalette.text)
            )
            BasicText(
                text = license,
                style = typography.xxs.medium.copy(color = colorPalette.accent)
            )
        }
        BasicText(
            text = "$author • $description",
            style = typography.xxs.medium.copy(color = colorPalette.textSecondary),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
