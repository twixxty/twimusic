package app.vitune.android.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

object Spotify {
    @Volatile
    var accessToken: String? = null

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp)
    }

    suspend fun getValidToken(): String? {
        val current = accessToken
        if (!current.isNullOrBlank()) return current
        return runCatching {
            val tokenObj = SpotifyAuth.fetchAccessToken().getOrNull()
            tokenObj?.accessToken?.also { accessToken = it }
        }.getOrNull()
    }

    suspend fun searchTrackRest(query: String): String? = runCatching {
        val token = getValidToken() ?: return@runCatching null
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val response = client.get("https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=1") {
            header("Authorization", "Bearer $token")
        }
        if (response.status.value in 200..299) {
            val body = response.bodyAsText()
            val js = json.parseToJsonElement(body).jsonObject
            val track = js["tracks"]?.jsonObject?.get("items")?.jsonArray?.firstOrNull()?.jsonObject
            return@runCatching track?.get("id")?.jsonPrimitive?.content
        }
        null
    }.getOrNull()

    suspend fun getColorLyrics(trackId: String): String? = runCatching {
        val token = getValidToken() ?: return@runCatching null
        val response = client.get("https://spclient.wg.spotify.com/color-lyrics/v2/track/$trackId?format=json&vocalRemoval=false") {
            header("Authorization", "Bearer $token")
        }
        if (response.status.value in 200..299) {
            val body = response.bodyAsText()
            val js = json.parseToJsonElement(body).jsonObject
            val lyricsData = js["lyrics"]?.jsonObject ?: return@runCatching null
            val lines = lyricsData["lines"]?.jsonArray ?: return@runCatching null

            val sb = StringBuilder()
            for (line in lines) {
                val lineObj = line.jsonObject
                val words = lineObj["words"]?.jsonPrimitive?.content ?: continue
                val startTimeMsStr = lineObj["startTimeMs"]?.jsonPrimitive?.content ?: continue
                val timeMs = startTimeMsStr.toLongOrNull() ?: continue

                val totalSeconds = timeMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val centiseconds = (timeMs % 1000) / 10

                val timeStr = String.format("[%02d:%02d.%02d]", minutes, seconds, centiseconds)
                sb.appendLine("$timeStr $words")
            }
            return@runCatching sb.toString().trim()
        }
        null
    }.getOrNull()
}
