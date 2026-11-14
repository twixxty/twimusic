package app.vitune.android.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.vitune.android.BuildConfig
import app.vitune.core.data.utils.version
import app.vitune.providers.github.GitHub
import app.vitune.providers.github.models.Release
import app.vitune.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException

object AppUpdater {
    val currentVersionName: String = BuildConfig.VERSION_NAME.substringBeforeLast("-")
    const val REPO_OWNER = "twixxty"
    const val REPO_NAME = "twimusic"

    /**
     * Queries GitHub for releases and returns the latest Release if it is newer than the current version.
     */
    suspend fun getNewerVersion(
        repoOwner: String = REPO_OWNER,
        repoName: String = REPO_NAME,
        contentType: String = "application/vnd.android.package-archive"
    ): Result<Release?> = withContext(Dispatchers.IO) {
        val currentVersion = currentVersionName.version
        GitHub.releases(owner = repoOwner, repo = repoName)?.mapCatching { releases ->
            releases
                .sortedByDescending { it.publishedAt }
                .firstOrNull { release ->
                    !release.draft &&
                        !release.preRelease &&
                        release.tag.version > currentVersion &&
                        release.assets.any { asset ->
                            (asset.contentType == contentType || asset.name.endsWith(".apk", ignoreCase = true)) &&
                                asset.state == Release.Asset.State.Uploaded
                        }
                }
        } ?: Result.failure(Exception("Failed to fetch releases from GitHub"))
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** True if the asset's file name ends in "-<abi>" (before ".apk"), so "x86" never matches "x86_64". */
    private fun Release.Asset.isForAbi(abi: String): Boolean {
        val base = name.lowercase().removeSuffix(".apk")
        val target = abi.lowercase()
        return base == target || base.endsWith("-$target") || base.endsWith("_$target")
    }

    /**
     * Finds the best matching APK asset for the current device.
     *
     * Walks the device's ABIs in the order Android prefers them (Build.SUPPORTED_ABIS) and takes the
     * first matching per-ABI APK (arm64-v8a, armeabi-v7a, x86_64), so a 32-bit-only phone gets the
     * armeabi-v7a build. If the release has none for this device, the universal APK is used, then
     * any other .apk.
     */
    fun findBestAsset(release: Release): Release.Asset? {
        val apkAssets = release.assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType == "application/vnd.android.package-archive"
        }
        if (apkAssets.isEmpty()) return null

        // 1. The device's own ABIs, best first.
        for (abi in Build.SUPPORTED_ABIS) {
            apkAssets.find { it.isForAbi(abi) }?.let { return it }
        }

        // 2. Universal APK
        apkAssets.find { it.name.contains("universal", ignoreCase = true) }?.let { return it }

        // 3. Fallback to any APK asset
        return apkAssets.firstOrNull()
    }

    /**
     * Downloads the APK asset with streaming progress reporting and triggers installation.
     *
     * @param onProgress Lambda called with (progress: Float, downloadedBytes: Long, totalBytes: Long)
     *                   progress is in range 0.0f..1.0f (or -1.0f if total length is unknown)
     * @return Result containing the downloaded File on success
     */
    suspend fun downloadAndInstall(
        context: Context,
        asset: Release.Asset,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadUrl = asset.downloadUrl.toString()
            Timber.d("Starting APK download from $downloadUrl (${asset.size} bytes)")

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "TwiMusic")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body from update server")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else asset.size

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "TwiMusic-${asset.name.takeIf { it.isNotBlank() } ?: "update.apk"}")

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
                        withContext(Dispatchers.Main) {
                            onProgress(progress, bytesRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Timber.d("Download complete: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }

            apkFile
        }.onFailure { e ->
            Timber.e(e, "Failed to download and install update")
        }
    }

    /**
     * Prompts the system package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        }.onFailure { e ->
            Timber.e(e, "Failed to launch package installer")
            context.toast("Failed to open installer: ${e.message}")
        }
    }
}
