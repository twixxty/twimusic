/*
 * Copyright (C) 2026 twixxty
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package app.vitune.android.canvas

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Shared LRU disk cache for looping canvas MP4s so reopening the player, switching back from
 * lyrics, or replaying tracks serves video frames straight from disk without re-downloading.
 */
@OptIn(UnstableApi::class)
internal object CanvasVideoCache {
    private const val CACHE_DIR_NAME = "canvas_videos"
    private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: run {
                val appContext = context.applicationContext
                val directory = File(appContext.cacheDir, CACHE_DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }
                SimpleCache(
                    directory,
                    LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                    StandaloneDatabaseProvider(appContext)
                ).also { cache = it }
            }
        }
    }

    fun dataSourceFactory(
        context: Context,
        upstreamHttpFactory: DataSource.Factory
    ): DataSource.Factory {
        val defaultUpstream = DefaultDataSource.Factory(context.applicationContext, upstreamHttpFactory)
        return runCatching {
            CacheDataSource.Factory()
                .setCache(get(context))
                .setUpstreamDataSourceFactory(defaultUpstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrElse { defaultUpstream }
    }
}
