package app.vitune.android.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private object AppleMotionartworkLogger {
    fun d(msg: String) = println("AppleMusicMotionartwork: D: $msg")
    fun w(msg: String) = println("AppleMusicMotionartwork: W: $msg")
    fun e(t: Throwable, msg: String) {
        println("AppleMusicMotionartwork: E: $msg")
        t.printStackTrace()
    }
}

/**
 * Fetches Apple Music album motion artwork (HLS / MP4 canvas) for the music player.
 *
 * Strategies:
 * 1. editorialVideo — present on albums with Apple Motion artwork via ?extend=editorialVideo
 * 2. Caches results in memory for 24 hours.
 */
object AppleMusicMotionartworkProvider {

    // Public read-only JWT used by Apple Music web player for unauthenticated catalog reads.
    private const val APPLE_MUSIC_TOKEN =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
        ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzgxMDMyODU1LCJleHAiOjE3ODQw" +
        "NTY4NTUsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
        ".fiMFcJWkfSlxKP9NVA0UW9CbItD1Rge0SISuepz203XcpU762OqdCpU9M-YkmtKkjRmaIWtjsfGgqZPrlMonpA"

    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    private suspend fun getOrFetchToken(): String {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryMs - 60_000) {
            return cachedToken!!
        }

        AppleMotionartworkLogger.d("Fetching fresh developer token dynamically...")
        return try {
            val html = client.get("https://music.apple.com/us/browse") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.body<String>()

            val scriptRegex = Regex("""/assets/index(?:-legacy)?[~-][a-zA-Z0-9_-]+\.js""")
            val scripts = scriptRegex.findAll(html).map { it.value }.distinct().toList()

            var fetchedToken: String? = null
            for (scriptPath in scripts) {
                val scriptUrl = "https://music.apple.com$scriptPath"
                val scriptText = client.get(scriptUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }.body<String>()

                val tokenRegex = Regex("""ey[a-zA-Z0-9_-]+\.ey[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")
                val tokens = tokenRegex.findAll(scriptText).map { it.value }
                for (token in tokens) {
                    try {
                        val body = token.split(".")[1]
                        val decodedBytes = java.util.Base64.getUrlDecoder().decode(body)
                        val decoded = String(decodedBytes, Charsets.UTF_8)
                        if (decoded.contains("iss") && decoded.contains("exp")) {
                            val expIndex = decoded.indexOf("\"exp\":")
                            if (expIndex != -1) {
                                val expValStr = decoded.substring(expIndex + 6).takeWhile { it.isDigit() }
                                val expSeconds = expValStr.toLongOrNull() ?: 0L
                                if (expSeconds * 1000 > now) {
                                    fetchedToken = token
                                    tokenExpiryMs = expSeconds * 1000
                                    break
                                }
                            }
                        }
                    } catch (ignored: Exception) {
                    }
                }
                if (fetchedToken != null) break
            }

            if (fetchedToken != null) {
                cachedToken = fetchedToken
                fetchedToken
            } else {
                APPLE_MUSIC_TOKEN
            }
        } catch (e: Exception) {
            AppleMotionartworkLogger.e(e, "Error occurred during dynamic token fetch. Using fallback token.")
            APPLE_MUSIC_TOKEN
        }
    }

    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: Motionartwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 hours

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): Motionartwork? {
        AppleMotionartworkLogger.d("getByAlbumArtist: album='$album', artist='$artist'")
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): Motionartwork? {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = searchAndFetchMotion(song, artist, album, storefront, "songs")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return result
    }

    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String,
    ): Motionartwork? {
        return runCatching {
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) {
                query = "$query $album"
            }
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            val response = client.get(url) {
                header("Authorization", "Bearer ${getOrFetchToken()}")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", query)
                parameter("types", type)
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
                parameter("include", "albums")
            }
            if (response.status != HttpStatusCode.OK) {
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val results = root["results"]?.jsonObject?.get(type)?.jsonObject?.get("data")?.jsonArray ?: return@runCatching null

            val scoredResults = results.mapNotNull { item ->
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultCollectionName = attributes["albumName"]?.jsonPrimitive?.contentOrNull
                    ?: attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                    ?: ""

                val nameLower = resultName.lowercase(Locale.ROOT)
                val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
                val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                        collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                        nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                        collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                        collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                        nameLower.contains("session") || collectionLower.contains("session")

                if (isBlacklisted) return@mapNotNull null

                val artistMatch = artistMatches(artist, resultArtistName)
                if (!artistMatch) return@mapNotNull null

                var score = 10
                val normTerm = term.normalizeForComparison()
                val normResultName = resultName.normalizeForComparison()
                if (normResultName == normTerm) {
                    score += 15
                } else if (normResultName.contains(normTerm) || normTerm.contains(normResultName)) {
                    score += 7
                } else {
                    score -= 10
                }

                if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
                    val normAlbum = album.normalizeForComparison()
                    val normCollection = resultCollectionName.normalizeForComparison()
                    if (normCollection == normAlbum) {
                        score += 20
                    } else if (normCollection.contains(normAlbum) || normAlbum.contains(normCollection)) {
                        score += 10
                    }
                }

                score to item
            }.sortedByDescending { it.first }

            for ((score, item) in scoredResults) {
                if (score < 12) continue
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""

                var targetAlbumId: String? = null
                val itemType = obj["type"]?.jsonPrimitive?.contentOrNull
                if (itemType == "songs") {
                    val relationships = obj["relationships"]?.jsonObject
                    targetAlbumId = relationships?.get("albums")?.jsonObject?.get("data")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull

                    if (targetAlbumId == null) {
                        val u = attributes["url"]?.jsonPrimitive?.contentOrNull
                        if (u != null) {
                            val albumPart = u.substringAfter("/album/", "").substringBefore("?")
                            val id = albumPart.substringAfterLast("/", "")
                            if (id.isNotBlank() && id.all { it.isDigit() }) {
                                targetAlbumId = id
                            }
                        }
                    }
                } else if (itemType == "albums") {
                    targetAlbumId = obj["id"]?.jsonPrimitive?.contentOrNull
                }

                if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) continue

                val ev = attributes["editorialVideo"]?.jsonObject
                if (ev != null) {
                    val hlsUrl = extractEditorialVideoUrl(ev, preferTall = false)
                    val tallHlsUrl = extractEditorialVideoUrl(ev, preferTall = true)
                    if (!hlsUrl.isNullOrBlank()) {
                        val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                        val resolvedAlbumName = if (itemType == "songs") collName else resultName
                        return@runCatching Motionartwork(
                            name = resultName,
                            artist = resultArtistName,
                            albumId = targetAlbumId,
                            albumName = resolvedAlbumName,
                            animated = hlsUrl,
                            animatedTall = tallHlsUrl
                        )
                    }
                }

                val fetched = fetchMotionArtwork(
                    albumId = targetAlbumId,
                    storefront = storefront,
                    fallbackArtist = resultArtistName,
                    titleOverride = if (itemType == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                    artistOverride = if (itemType == "songs") resultArtistName else null
                )
                if (fetched != null) return@runCatching fetched
            }
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleMotionartworkLogger.e(it, "error in searchAndFetchMotion for $term")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): Motionartwork? {
        if (albumId.startsWith("pl.")) return null
        return runCatching {
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId"
            val response = client.get(url) {
                header("Authorization", "Bearer ${getOrFetchToken()}")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo")
                parameter("include", "tracks")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null

            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist

            val nameLower = albumName.lowercase(Locale.ROOT)
            val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                    nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                    nameLower.contains("mixed") || nameLower.contains("apple music") ||
                    nameLower.contains("today's hits") || nameLower.contains("session")

            if (isBlacklisted) return@runCatching null

            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val u = extractEditorialVideoUrl(ev, preferTall = false)
                val tallUrl = extractEditorialVideoUrl(ev, preferTall = true)
                if (!u.isNullOrBlank()) {
                    return@runCatching Motionartwork(
                        name = finalTitle,
                        artist = finalArtist,
                        albumId = albumId,
                        albumName = albumName,
                        animated = u,
                        animatedTall = tallUrl
                    )
                }
            }
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleMotionartworkLogger.e(it, "error in fetchMotionArtwork for $albumId")
        }.getOrNull()
    }

    private fun extractEditorialVideoUrl(ev: JsonObject, preferTall: Boolean = false): String? {
        val order = if (preferTall) {
            listOf(
                ev["motionDetailTall"]?.jsonObject,
                ev["motionDetailRaw"]?.jsonObject,
                ev["motionDetailSquare"]?.jsonObject,
                ev["motionDetailStatic"]?.jsonObject
            )
        } else {
            listOf(
                ev["motionDetailSquare"]?.jsonObject,
                ev["motionDetailRaw"]?.jsonObject,
                ev["motionDetailTall"]?.jsonObject,
                ev["motionDetailStatic"]?.jsonObject
            )
        }
        val assets = order.filterNotNull()

        for (asset in assets) {
            val video = asset["video"]?.jsonPrimitive?.contentOrNull
                ?: asset["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["url"]?.jsonPrimitive?.contentOrNull

            if (!video.isNullOrBlank()) return video
        }
        return null
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }

    private fun artistMatches(requested: String, returned: String): Boolean {
        val delimiters = Regex("(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)", RegexOption.IGNORE_CASE)
        val requestedList = requested.split(delimiters).map { it.normalizeForComparison() }.filter { it.isNotBlank() }
        val returnedList = returned.split(delimiters).map { it.normalizeForComparison() }.filter { it.isNotBlank() }
        if (requestedList.isEmpty() || returnedList.isEmpty()) return false
        return requestedList.all { req -> returnedList.any { res -> res == req } }
    }
}
