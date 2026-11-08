package app.vitune.android.lyrics

import android.content.Context
import android.util.LruCache
import app.vitune.android.preferences.LyricsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber

object LyricsHelper {
    private const val MAX_TIMEOUT_MS = 15000L
    private const val MAX_CACHE_SIZE = 20

    private val cache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFetches = mutableMapOf<String, Deferred<LyricsWithProvider>>()
    private val fetchesMutex = Mutex()

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

        val cacheKey = videoId
        val deferred = fetchesMutex.withLock {
            activeFetches.getOrPut(cacheKey) {
                helperScope.async {
                    val prioritizeSync = LyricsPreferences.lyricsPrioritizeSyncAccuracy
                    val channel = Channel<Triple<Int, String, String>>(Channel.UNLIMITED)

                    Timber.tag("LyricsHelper").d(
                        "Concurrently fetching lyrics for '$cleanedTitle' by '$artist' across ${orderedProviders.size} providers (prioritizeSync=$prioritizeSync)"
                    )

                    val jobs = orderedProviders.mapIndexed { index, provider ->
                        launch {
                            try {
                                val result = provider.getLyrics(
                                    context = context,
                                    id = videoId,
                                    title = cleanedTitle,
                                    artist = artist,
                                    duration = durationSeconds,
                                    album = album
                                )
                                result.onSuccess { rawLyrics ->
                                    if (rawLyrics.isNotBlank()) {
                                        val filtered = LyricsUtils.filterLyricsCreditLines(rawLyrics)
                                        if (filtered.isNotBlank()) {
                                            channel.send(Triple(index, provider.name, filtered))
                                        }
                                    }
                                }.onFailure { e ->
                                    Timber.tag("LyricsHelper").d("${provider.name} failed: ${e.message}")
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.tag("LyricsHelper").w(e, "${provider.name} exception")
                            }
                        }
                    }

                    launch {
                        joinAll(*jobs.toTypedArray())
                        channel.close()
                    }

                    var bestLyrics = ""
                    var bestProvider = ""
                    var bestSyncType = LyricsUtils.SyncType.NONE
                    var bestIndex = Int.MAX_VALUE

                    try {
                        withTimeout(MAX_TIMEOUT_MS) {
                            for ((index, providerName, lyrics) in channel) {
                                Timber.tag("LyricsHelper").d("Received lyrics candidate from $providerName (index=$index)")
                                if (!prioritizeSync) {
                                    if (index < bestIndex) {
                                        bestLyrics = lyrics
                                        bestProvider = providerName
                                        bestIndex = index
                                        if (index == 0) break
                                    }
                                } else {
                                    val syncType = try {
                                        LyricsUtils.getSyncType(lyrics)
                                    } catch (e: Exception) {
                                        LyricsUtils.SyncType.NONE
                                    }

                                    Timber.tag("LyricsHelper").d("$providerName syncType: $syncType (current best: $bestSyncType from $bestProvider)")

                                    if (syncType == LyricsUtils.SyncType.WORD) {
                                        if (bestSyncType != LyricsUtils.SyncType.WORD || index < bestIndex) {
                                            bestLyrics = lyrics
                                            bestProvider = providerName
                                            bestSyncType = syncType
                                            bestIndex = index
                                        }
                                        if (index == 0) break
                                    } else if (syncType == LyricsUtils.SyncType.LINE && (bestSyncType == LyricsUtils.SyncType.NONE || (bestSyncType == LyricsUtils.SyncType.LINE && index < bestIndex))) {
                                        bestLyrics = lyrics
                                        bestProvider = providerName
                                        bestSyncType = syncType
                                        bestIndex = index
                                    } else if (bestLyrics.isEmpty() || (syncType == bestSyncType && index < bestIndex)) {
                                        bestLyrics = lyrics
                                        bestProvider = providerName
                                        bestSyncType = syncType
                                        bestIndex = index
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("LyricsHelper").d("Fetch completed or timed out: bestProvider=$bestProvider")
                    } finally {
                        jobs.forEach { it.cancel() }
                        channel.close()
                    }

                    val res = LyricsWithProvider(bestLyrics, bestProvider)
                    if (bestLyrics.isNotBlank()) {
                        cache.put(videoId, res)
                    }
                    res
                }
            }
        }

        return try {
            deferred.await()
        } finally {
            fetchesMutex.withLock {
                activeFetches.remove(cacheKey)
            }
        }
    }

    suspend fun getAllLyrics(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
        album: String? = null,
        callback: (LyricsWithProvider) -> Unit
    ) {
        val orderedProviders = resolveLyricsProviders().filter { it.isEnabled(context) }
        val cleanedTitle = LyricsUtils.cleanTitleForSearch(title)

        orderedProviders.forEach { provider ->
            try {
                provider.getAllLyrics(context, videoId, cleanedTitle, artist, durationSeconds, album) { rawLyrics ->
                    if (rawLyrics.isNotBlank()) {
                        val filtered = LyricsUtils.filterLyricsCreditLines(rawLyrics)
                        if (filtered.isNotBlank()) {
                            callback(LyricsWithProvider(filtered, provider.name))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("LyricsHelper").w(e, "getAllLyrics failed for ${provider.name}")
            }
        }
    }
}

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
