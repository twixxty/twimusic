package app.vitune.android.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.utils.AppUpdater
import app.vitune.android.utils.secondary
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vitune.android.BuildConfig
import app.vitune.android.R
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.service.ServiceNotifications
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.bold
import app.vitune.android.utils.center
import app.vitune.android.utils.hasPermission
import app.vitune.android.utils.pendingIntent
import app.vitune.android.utils.semiBold
import app.vitune.core.data.utils.Version
import app.vitune.core.data.utils.version
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isAtLeastAndroid13
import app.vitune.core.ui.utils.isCompositionLaunched
import app.vitune.providers.github.GitHub
import app.vitune.providers.github.models.Release
import app.vitune.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private val VERSION_NAME = BuildConfig.VERSION_NAME.substringBeforeLast("-")
private const val REPO_OWNER = "twixxty"
private const val REPO_NAME = "twimusic"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val permission = Manifest.permission.POST_NOTIFICATIONS

class VersionCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_TAG = "version_check_worker"

        fun upsert(context: Context, period: Duration?) = runCatching {
            val workManager = WorkManager.getInstance(context)

            if (period == null) {
                workManager.cancelAllWorkByTag(WORK_TAG)
                return@runCatching
            }

            val request = PeriodicWorkRequestBuilder<VersionCheckWorker>(period.toJavaDuration())
                .addTag(WORK_TAG)
                .setConstraints(
                    Constraints(
                        requiredNetworkType = NetworkType.CONNECTED,
                        requiresBatteryNotLow = true
                    )
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                /* uniqueWorkName = */
                WORK_TAG,
                /* existingPeriodicWorkPolicy = */
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                /* periodicWork = */
                request
            )

            Unit
        }.also { it.exceptionOrNull()?.printStackTrace() }
    }

    override suspend fun doWork(): Result = with(applicationContext) {
        if (isAtLeastAndroid13 && !hasPermission(permission)) return Result.retry()

        val result = withContext(Dispatchers.IO) {
            VERSION_NAME.version
                .getNewerVersion()
                .also { it?.exceptionOrNull()?.printStackTrace() }
        }

        result?.getOrNull()?.let { release ->
            ServiceNotifications.version.sendNotification(applicationContext) {
                this
                    .setSmallIcon(R.drawable.download)
                    .setContentTitle(getString(R.string.new_version_available))
                    .setContentText(getString(R.string.redirect_github))
                    .also {
                        runCatching { release.frontendUrl.toString().toUri() }.getOrNull()
                            ?.let { url ->
                                it.setContentIntent(pendingIntent(Intent(Intent.ACTION_VIEW, url)))
                            }
                        it.setStyle(
                            NotificationCompat
                                .BigTextStyle(it)
                                .bigText(getString(R.string.new_version_available))
                        )
                    }
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
            }
        }

        return when {
            result == null || result.isFailure -> Result.retry()
            result.isSuccess -> Result.success()
            else -> Result.failure() // Unreachable
        }
    }
}

private suspend fun Version.getNewerVersion(
    repoOwner: String = REPO_OWNER,
    repoName: String = REPO_NAME,
    contentType: String = "application/vnd.android.package-archive"
) = GitHub.releases(
    owner = repoOwner,
    repo = repoName
)?.mapCatching { releases ->
    releases
        .sortedByDescending { it.publishedAt }
        .firstOrNull { release ->
            !release.draft &&
                !release.preRelease &&
                release.tag.version > this &&
                release.assets.any {
                    it.contentType == contentType && it.state == Release.Asset.State.Uploaded
                }
        }
}

@Route
@Composable
fun About() = SettingsCategoryScreen(
    title = stringResource(R.string.about),
    description = stringResource(
        R.string.format_version_credits,
        VERSION_NAME
    )
) {
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var hasPermission by remember(isCompositionLaunched()) {
        mutableStateOf(
            !isAtLeastAndroid13 || context.applicationContext.hasPermission(permission)
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

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
            text = stringResource(R.string.current_version, VERSION_NAME),
            onClick = { newVersionDialogOpened = true }
        )

        EnumValueSelectorSettingsEntry(
            title = stringResource(R.string.version_check),
            selectedValue = DataPreferences.versionCheckPeriod,
            onValueSelect = onSelect@{
                DataPreferences.versionCheckPeriod = it
                if (isAtLeastAndroid13 && it.period != null && !hasPermission) {
                    launcher.launch(permission)
                }

                VersionCheckWorker.upsert(context.applicationContext, it.period)
            },
            valueText = { it.displayName() }
        )
    }

    if (newVersionDialogOpened) {
        DefaultDialog(
            onDismiss = { newVersionDialogOpened = false }
        ) {
            var newerVersion: Result<Release?>? by remember { mutableStateOf(null) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    newerVersion = VERSION_NAME.version
                        .getNewerVersion()
                        ?.onFailure(Throwable::printStackTrace)
                }
            }

            newerVersion?.getOrNull()?.let { release ->
                val bestAsset = remember(release) { AppUpdater.findBestAsset(release) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }
                var downloadedMb by remember { mutableStateOf("") }
                var downloadedFile by remember { mutableStateOf<File?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()

                BasicText(
                    text = stringResource(R.string.new_version_available),
                    style = typography.xs.semiBold.center
                )

                Spacer(modifier = Modifier.height(8.dp))

                BasicText(
                    text = release.name ?: release.tag,
                    style = typography.m.bold.center
                )

                release.markdown?.takeIf { it.isNotBlank() }?.let { markdown ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .background(
                                color = colorPalette.background1,
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
                            } else "Downloading update...",
                            style = typography.xs.semiBold.center
                        )
                    }
                } else if (downloadedFile != null) {
                    SecondaryTextButton(
                        text = "Install Update",
                        onClick = { AppUpdater.installApk(context, downloadedFile!!) }
                    )
                } else if (bestAsset != null) {
                    val sizeMb = remember(bestAsset.size) {
                        "%.1f MB".format(bestAsset.size / (1024f * 1024f))
                    }
                    SecondaryTextButton(
                        text = "Download & Install ($sizeMb)",
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

                SecondaryTextButton(
                    text = stringResource(R.string.more_information),
                    onClick = { uriHandler.openUri(release.frontendUrl.toString()) }
                )
            } ?: newerVersion?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_github),
                    style = typography.xs.semiBold.center,
                    modifier = Modifier.padding(all = 24.dp)
                )
            } ?: if (newerVersion?.isSuccess == true) {
                BasicText(
                    text = stringResource(R.string.up_to_date),
                    style = typography.xs.semiBold.center
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
