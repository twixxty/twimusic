package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.preferences.LyricsPreferences
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object LrcRedLyricsProvider : LyricsProvider {
    override val name = "LrcRed"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    }
                )
            }
            defaultRequest {
                url("https://lrc.red/")
            }
            expectSuccess = true
        }
    }

    override fun isEnabled(context: Context): Boolean = LyricsPreferences.enableLrcRed

    @Serializable
    data class Hit(
        val isrc: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val duration: Double? = null
    )

    @Serializable
    data class Response(
        val hits: List<Hit>? = null
    )

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): Result<String> = runCatching {
        val q = "$title $artist".trim()
        val response = client.get("search.json") {
            parameter("q", q)
        }.body<Response>()

        val hits = response.hits ?: throw IllegalStateException("No results from lrc.red")
        
        val hit = bestHit(hits, title, artist, duration)
            ?: throw IllegalStateException("No matching track found")

        val rawLyrics = if (!hit.isrc.isNullOrBlank()) {
            val isrcRes = client.get("get/${hit.isrc}.lrc")
            isrcRes.bodyAsText()
        } else {
            val titleParam = hit.title ?: title
            val artistParam = hit.artist ?: artist
            val durationParam = hit.duration?.toInt() ?: duration
            client.get("get.lrc") {
                parameter("title", titleParam)
                parameter("artist", artistParam)
                parameter("duration", durationParam)
            }.bodyAsText()
        }

        if (rawLyrics.isBlank()) {
            throw IllegalStateException("Empty lyrics response")
        }

        if (rawLyrics.trimStart().startsWith("<")) {
            parseTtmlToLrc(rawLyrics)
        } else {
            rawLyrics
        }
    }

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit
    ) {
        val q = "$title $artist".trim()
        val hits = runCatching {
            client.get("search.json") {
                parameter("q", q)
            }.body<Response>().hits
        }.getOrNull() ?: return

        for (hit in hits) {
            val lyrics = runCatching {
                val raw = if (!hit.isrc.isNullOrBlank()) {
                    client.get("get/${hit.isrc}.lrc").bodyAsText()
                } else {
                    client.get("get.lrc") {
                        parameter("title", hit.title ?: title)
                        parameter("artist", hit.artist ?: artist)
                        parameter("duration", hit.duration?.toInt() ?: duration)
                    }.bodyAsText()
                }
                if (raw.isNotBlank()) {
                    if (raw.trimStart().startsWith("<")) parseTtmlToLrc(raw) else raw
                } else null
            }.getOrNull()

            if (!lyrics.isNullOrBlank()) {
                callback(lyrics)
            }
        }
    }

    private fun bestHit(
        hits: List<Hit>,
        requestedTitle: String,
        requestedArtist: String,
        durationSeconds: Int
    ): Hit? {
        val wantedCore = coreOf(requestedTitle)
        val wantedVersion = versionOf(requestedTitle)
        val wantedArtists = artistsOf(requestedArtist)

        fun distance(h: Hit): Double =
            if (durationSeconds > 0 && h.duration != null) abs(h.duration - durationSeconds) else 0.0

        return hits
            .filter { hit ->
                val name = hit.title ?: return@filter false
                coreOf(name) == wantedCore &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                    (durationSeconds <= 0 || distance(hit) <= 3.0)
            }
            .minByOrNull(::distance)
    }

    private fun coreOf(title: String): String =
        normalized(title.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ").substringBefore(" - "))
            .ifEmpty { normalized(title) }

    private fun versionOf(title: String): Set<String> {
        val extras = Regex("""[(\[][^)\]]*[)\]]""").findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { 
            it in setOf(
                "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
                "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
                "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended"
            )
        }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(Regex("""\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""", RegexOption.IGNORE_CASE))
            .map(::normalized).filter { it.isNotEmpty() }.toSet()

    private fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun parseTtmlToLrc(rawTtml: String): String {
        val ttml = rawTtml.replace("\uFEFF", "")
        
        val pRegex = Regex("""<p([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val tokenRegex = Regex("""(<[^>]+>)|([^<]+)""")
        val beginRegex = Regex("""begin="([^"]+)"""")
        val roleRegex = Regex("""(?:ttm:role|role)="([^"]+)"""")
        val agentRegex = Regex("""(?:ttm:agent|agent)="([^"]+)"""")
        
        val lines = buildMap<Long, MutableList<String>> {
            for (pMatch in pRegex.findAll(ttml)) {
                val pAttrs = pMatch.groupValues[1]
                val innerHtml = pMatch.groupValues[2]
                
                val pBeginStr = beginRegex.find(pAttrs)?.groupValues?.get(1) ?: continue
                val lineStartMs = timeToMs(pBeginStr) ?: continue
                val agent = agentRegex.find(pAttrs)?.groupValues?.get(1)
                
                val fgPieces = mutableListOf<Pair<Long, String>>()
                val bgPieces = mutableListOf<Pair<Long, String>>()
                val translationBuilder = StringBuilder()
                var currentSpanMs: Long = lineStartMs
                
                val bgStack = mutableListOf<Boolean>()
                val transStack = mutableListOf<Boolean>()
                val skipStack = mutableListOf<Boolean>()
                val timeStack = mutableListOf<Long>()
                
                for (tokenMatch in tokenRegex.findAll(innerHtml)) {
                    val tag = tokenMatch.groupValues[1]
                    val text = tokenMatch.groupValues[2]
                    
                    if (tag.isNotEmpty()) {
                        if (tag.startsWith("</")) {
                            if (bgStack.isNotEmpty()) bgStack.removeLast()
                            if (transStack.isNotEmpty()) transStack.removeLast()
                            if (skipStack.isNotEmpty()) skipStack.removeLast()
                            if (timeStack.isNotEmpty()) {
                                timeStack.removeLast()
                                currentSpanMs = timeStack.lastOrNull() ?: lineStartMs
                            }
                        } else if (tag.startsWith("<span") || tag.startsWith("<s ")) {
                            val role = roleRegex.find(tag)?.groupValues?.get(1)
                            val isTrans = role == "x-translation"
                            val isSkip = role == "x-roman"
                            val isBg = role == "x-bg"
                            
                            bgStack.add(isBg || (bgStack.lastOrNull() ?: false))
                            transStack.add(isTrans || (transStack.lastOrNull() ?: false))
                            skipStack.add(isSkip || isTrans || (skipStack.lastOrNull() ?: false))
                            
                            val tBegin = beginRegex.find(tag)?.groupValues?.get(1)
                            if (tBegin != null) {
                                val tMs = timeToMs(tBegin)
                                if (tMs != null) {
                                    timeStack.add(tMs)
                                    currentSpanMs = tMs
                                    continue
                                }
                            }
                            timeStack.add(currentSpanMs)
                        }
                    } else if (text.isNotEmpty()) {
                        if (transStack.lastOrNull() == true) {
                            translationBuilder.append(text)
                            continue
                        }
                        if (skipStack.lastOrNull() == true) continue
                        val isBackground = bgStack.lastOrNull() == true
                        val cleanText = text.replace(Regex("""\s+"""), " ")
                        if (cleanText.isNotEmpty()) {
                            if (isBackground) bgPieces.add(currentSpanMs to cleanText)
                            else fgPieces.add(currentSpanMs to cleanText)
                        }
                    }
                }
                
                val trText = translationBuilder.toString().replace(Regex("""\s+"""), " ").trim()

                fun buildLine(pieces: List<Pair<Long, String>>, isBg: Boolean) {
                    if (pieces.isEmpty()) return
                    val sb = StringBuilder()
                    
                    if (isBg) sb.append("{bg}")
                    if (agent != null) sb.append("{agent:$agent}")
                    if (trText.isNotEmpty() && !isBg) sb.append("{tr:$trText}")
                    
                    var lastMs = -1L
                    for ((ms, text) in pieces) {
                        if (text.isBlank()) {
                            sb.append(text)
                            continue
                        }
                        if (ms != lastMs) {
                            sb.append("<${formatTime(ms)}>")
                            lastMs = ms
                        }
                        sb.append(text)
                    }
                    val richText = sb.toString().trim()
                    if (richText.isNotEmpty()) {
                        getOrPut(lineStartMs) { mutableListOf() }.add(richText)
                    }
                }
                
                buildLine(fgPieces, false)
                buildLine(bgPieces, true)
            }
        }.entries.sortedBy { it.key }
        
        return lines.joinToString("\n") { (timeMs, list) ->
            list.joinToString("\n") { text ->
                "[${formatTime(timeMs)}]$text"
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = (ms / 60000).toString().padStart(2, '0')
        val seconds = ((ms % 60000) / 1000).toString().padStart(2, '0')
        val millis = ((ms % 1000) / 10).toString().padStart(2, '0')
        return "$minutes:$seconds.$millis"
    }

    private fun timeToMs(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val stripped = raw.removeSuffix("s")
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }
}
