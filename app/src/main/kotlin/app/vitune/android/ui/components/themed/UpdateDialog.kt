package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.utils.AppUpdater
import app.vitune.android.utils.bold
import app.vitune.android.utils.center
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.github.models.Release
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    release: Release,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val bestAsset = remember(release) { AppUpdater.findBestAsset(release) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        BasicText(
            text = stringResource(R.string.app_out_of_date),
            style = typography.m.bold.center
        )

        Spacer(modifier = Modifier.height(6.dp))

        BasicText(
            text = stringResource(
                R.string.app_out_of_date_description,
                release.tag,
                AppUpdater.currentVersionName
            ),
            style = typography.xs.secondary.center
        )

        release.name?.takeIf { it.isNotBlank() && it != release.tag }?.let { releaseName ->
            Spacer(modifier = Modifier.height(6.dp))
            BasicText(
                text = releaseName,
                style = typography.xs.semiBold.center
            )
        }

        release.markdown?.takeIf { it.isNotBlank() }?.let { markdown ->
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .background(
                        color = colorPalette.background2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicText(
                    text = markdown.trim(),
                    style = typography.xxs.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isDownloading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorPalette.accent,
                    trackColor = colorPalette.background2
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = if (downloadProgress > 0f) {
                        "${(downloadProgress * 100).toInt()}% ($downloadedMb)"
                    } else stringResource(R.string.downloading_update),
                    style = typography.xs.semiBold.center
                )
            }
        } else if (downloadedFile != null) {
            DialogTextButton(
                text = stringResource(R.string.install_update),
                primary = true,
                onClick = { AppUpdater.installApk(context, downloadedFile!!) }
            )
        } else if (bestAsset != null) {
            val sizeMb = remember(bestAsset.size) {
                "%.1f MB".format(bestAsset.size / (1024f * 1024f))
            }
            DialogTextButton(
                text = "${stringResource(R.string.download_and_install)} ($sizeMb)",
                primary = true,
                onClick = {
                    isDownloading = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = AppUpdater.downloadAndInstall(
                            context = context,
                            asset = bestAsset,
                            onProgress = { progress, downloaded, total ->
                                downloadProgress = progress
                                downloadedMb = "%.1f / %.1f MB".format(
                                    downloaded / (1024f * 1024f),
                                    total / (1024f * 1024f)
                                )
                            }
                        )
                        isDownloading = false
                        result.fold(
                            onSuccess = { file -> downloadedFile = file },
                            onFailure = { e -> errorMessage = e.message }
                        )
                    }
                }
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = errorMessage ?: "",
                style = typography.xs.secondary.center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DialogTextButton(
                text = stringResource(R.string.dismiss),
                onClick = onDismiss
            )

            DialogTextButton(
                text = stringResource(R.string.more_information),
                onClick = { uriHandler.openUri(release.frontendUrl.toString()) }
            )
        }
    }
}
