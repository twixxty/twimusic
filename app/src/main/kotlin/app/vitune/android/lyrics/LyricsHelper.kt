package app.vitune.android.lyrics

import android.content.Context
import android.util.LruCache
import app.vitune.android.preferences.LyricsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

object LyricsHelper {
    private const val MAX_LYRICS_FETCH_MS = 25000L
    private const val PER_PROVIDER_TIMEOUT_MS = 8000L
    private const val MAX_CACHE_SIZE = 20

    private val cache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)

    fun resolveLyricsProviders(): List<LyricsProvider> {
        val providerOrder = LyricsPreferences.lyricsProviderOrder
        return LyricsProviderRegistry.getOrderedProviders(providerOrder)
    }

    suspend fun getLyrics(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
        album: String? = null
    ): LyricsWithProvider {
        val cached = cache.get(videoId)
        if (cached != null) return cached

        val orderedProviders = resolveLyricsProviders().filter { it.isEnabled(context) }
        val cleanedTitle = LyricsUtils.cleanTitleForSearch(title)

        Timber.tag("LyricsHelper").d("Fetching lyrics for $cleanedTitle by $artist across ${orderedProviders.size} providers")

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            for (provider in orderedProviders) {
                val lyricsResult = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(context, videoId, cleanedTitle, artist, durationSeconds, album)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("LyricsHelper").w(e, "${provider.name} failed")
                    null
                }

                if (lyricsResult != null && lyricsResult.isSuccess) {
                    val raw = lyricsResult.getOrNull()
                    if (!raw.isNullOrBlank()) {
                        val filtered = LyricsUtils.filterLyricsCreditLines(raw)
                        val res = LyricsWithProvider(filtered, provider.name)
                        cache.put(videoId, res)
                        return@withTimeoutOrNull res
                    }
                }
            }
            null
        }

        return result ?: LyricsWithProvider("", "")
    }
}

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
