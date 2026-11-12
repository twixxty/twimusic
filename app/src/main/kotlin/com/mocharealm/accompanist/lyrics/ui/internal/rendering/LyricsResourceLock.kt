package com.mocharealm.accompanist.lyrics.ui.internal.rendering

/** Short cache bookkeeping sections; rasterization never holds this lock. */
internal class LyricsResourceLock {
    private val monitor = Any()
    fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
