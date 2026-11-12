package com.mocharealm.accompanist.lyrics.ui.internal.diagnostics

import android.os.Build

internal fun lyricsTraceEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.os.Trace.isEnabled() else false

internal fun beginLyricsTrace(name: String) = android.os.Trace.beginSection(name)

internal fun endLyricsTrace() = android.os.Trace.endSection()

internal fun setLyricsTraceCounter(name: String, value: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.os.Trace.setCounter(name, value)
    }
}

internal inline fun <T> traceLyrics(name: String, block: () -> T): T {
    if (!lyricsTraceEnabled()) return block()
    beginLyricsTrace(name)
    try {
        return block()
    } finally {
        endLyricsTrace()
    }
}
