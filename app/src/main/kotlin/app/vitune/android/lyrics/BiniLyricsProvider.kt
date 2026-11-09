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

import app.vitune.android.betterlyrics.TTMLParser
import kotlin.math.abs

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

    private fun parseRawLyrics(body: String, url: String): String? {
        if (body.isBlank()) return null
        if (url.endsWith(".ttml", ignoreCase = true) || body.contains("<tt", ignoreCase = true) || body.contains("<p ", ignoreCase = true)) {
            val parsedLines = TTMLParser.parseTTML(body)
            if (parsedLines.isNotEmpty()) {
                val lrc = TTMLParser.toLRC(parsedLines)
                if (lrc.isNotBlank()) return lrc
            }
        }
        val fallback = ttmlToLrc(body)
        return if (fallback.isNotBlank()) fallback else body
    }

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

            // Pick best result: prioritize word-level timing and closest duration match
            var bestResult = results.getJSONObject(0)
            var bestScore = -100

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val timingType = item.optString("timing_type", "")
                val itemDuration = item.optInt("duration", -1)

                var score = 0
                if (timingType.equals("word", ignoreCase = true)) score += 50
                if (duration > 0 && itemDuration > 0) {
                    val diff = abs(itemDuration - duration)
                    if (diff <= 3) score += 30
                    else if (diff <= 8) score += 15
                    else if (diff > 20) score -= 40
                }
                if (score > bestScore) {
                    bestScore = score
                    bestResult = item
                }
            }

            val lyricsUrl = bestResult.optString("lyricsUrl").takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(Exception("Bini Lyrics: no lyricsUrl in result"))

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

            val parsedLrc = parseRawLyrics(ttmlBody, lyricsUrl)
            if (!parsedLrc.isNullOrBlank()) {
                Result.success(parsedLrc)
            } else {
                Result.failure(Exception("Failed to parse TTML or empty lyrics"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/getLyrics".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("track", title)
                ?.addQueryParameter("artist", artist)
                ?.build()
                ?.toString() ?: return@withContext

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext
            }

            val body = response.body?.string() ?: run {
                response.close()
                return@withContext
            }
            response.close()

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext
            val seen = mutableSetOf<String>()

            // Sort results so word-synced come first
            val items = (0 until results.length()).map { results.getJSONObject(it) }
                .sortedByDescending { it.optString("timing_type", "").equals("word", ignoreCase = true) }

            for (item in items.take(5)) {
                val lyricsUrl = item.optString("lyricsUrl").takeIf { it.isNotBlank() } ?: continue
                if (!seen.add(lyricsUrl)) continue

                val ttmlReq = Request.Builder().url(lyricsUrl).build()
                val ttmlRes = client.newCall(ttmlReq).execute()
                if (!ttmlRes.isSuccessful) {
                    ttmlRes.close()
                    continue
                }
                val ttmlBody = ttmlRes.body?.string()
                ttmlRes.close()
                if (!ttmlBody.isNullOrBlank()) {
                    parseRawLyrics(ttmlBody, lyricsUrl)?.let { lrc ->
                        if (lrc.isNotBlank()) callback(lrc)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored in getAllLyrics
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
