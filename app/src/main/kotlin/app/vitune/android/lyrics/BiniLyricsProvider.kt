package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.preferences.LyricsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Lyrics provider backed by the Binimum API.
 *
 * Endpoint: `GET https://lyrics-api.binimum.org/getLyrics?track=...&artist=...`
 * Returns a JSON with a TTML URL, which is then parsed into RichSync LRC format.
 */
object BiniLyricsProvider : LyricsProvider {

    override val name = "BiniLyrics"

    private const val BASE_URL = "https://lyrics-api.binimum.org"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun isEnabled(context: Context): Boolean =
        LyricsPreferences.enableBiniLyrics

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/getLyrics".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("track", title)
                ?.addQueryParameter("artist", artist)
                ?.build()
                ?.toString() ?: return@withContext Result.failure(Exception("Invalid URL"))

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("Bini Lyrics API returned ${response.code}"))
            }

            val body = response.body?.string() ?: run {
                response.close()
                return@withContext Result.failure(Exception("Empty API response"))
            }
            response.close()

            val json = JSONObject(body)
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext Result.failure(Exception("Bini Lyrics: no results found"))
            }

            // Get the first result's TTML url
            val firstResult = results.getJSONObject(0)
            val lyricsUrl = firstResult.optString("lyricsUrl").takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(Exception("Bini Lyrics: no lyricsUrl in result"))

            // Fetch the TTML
            val ttmlReq = Request.Builder().url(lyricsUrl).build()
            val ttmlRes = client.newCall(ttmlReq).execute()
            if (!ttmlRes.isSuccessful) {
                ttmlRes.close()
                return@withContext Result.failure(Exception("Failed to fetch TTML"))
            }

            val ttmlBody = ttmlRes.body?.string() ?: run {
                ttmlRes.close()
                return@withContext Result.failure(Exception("Empty TTML response"))
            }
            ttmlRes.close()

            val lrc = ttmlToLrc(ttmlBody)
            if (lrc.isNotBlank()) {
                Result.success(lrc)
            } else {
                Result.failure(Exception("Failed to parse TTML or empty lyrics"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val pRegex = """<p\s+[^>]*?begin=["']([\w:.]+)["'][^>]*>(.*?)</p>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val spanRegex = """<span\s+[^>]*?begin=["']([\w:.]+)["'][^>]*>(.*?)</span>""".toRegex(RegexOption.DOT_MATCHES_ALL)

    internal fun ttmlToLrc(ttml: String): String {
        val lrcBuilder = StringBuilder()

        for (pMatch in pRegex.findAll(ttml)) {
            val pBeginSec = parseTtmlTimeToSeconds(pMatch.groupValues[1]) ?: continue
            val innerHtml = pMatch.groupValues[2]

            val mainTime = formatLrcTime(pBeginSec)
            lrcBuilder.append("[$mainTime]")

            val spanMatches = spanRegex.findAll(innerHtml).toList()
            if (spanMatches.isNotEmpty()) {
                for (spanMatch in spanMatches) {
                    val spanBeginSec = parseTtmlTimeToSeconds(spanMatch.groupValues[1]) ?: continue
                    val word = spanMatch.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                    if (word.isNotEmpty()) {
                        val spanTime = formatLrcTime(spanBeginSec)
                        lrcBuilder.append("<$spanTime> $word ")
                    }
                }
            } else {
                val plainText = innerHtml.replace(Regex("<[^>]*>"), "").trim()
                lrcBuilder.append(plainText)
            }
            lrcBuilder.append("\n")
        }

        return lrcBuilder.toString().trimEnd()
    }

    private fun formatLrcTime(sec: Float): String {
        val mm = (sec / 60).toInt()
        val ss = sec % 60
        val ssStr = String.format(Locale.US, "%05.2f", ss)
        return String.format(Locale.US, "%02d:%s", mm, ssStr)
    }

    private fun parseTtmlTimeToSeconds(timeStr: String): Float? {
        val cleanStr = timeStr.replace(Regex("[a-zA-Z]+$"), "")
        val isMs = timeStr.endsWith("ms", ignoreCase = true)
        val parts = cleanStr.split(":")

        val seconds = when (parts.size) {
            3 -> {
                val h = parts[0].toFloatOrNull() ?: return null
                val m = parts[1].toFloatOrNull() ?: return null
                val s = parts[2].toFloatOrNull() ?: return null
                h * 3600f + m * 60f + s
            }
            2 -> {
                val m = parts[0].toFloatOrNull() ?: return null
                val s = parts[1].toFloatOrNull() ?: return null
                m * 60f + s
            }
            else -> {
                cleanStr.toFloatOrNull() ?: return null
            }
        }
        return if (isMs) seconds / 1000f else seconds
    }
}
