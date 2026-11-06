package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.betterlyrics.BetterLyrics
import app.vitune.android.preferences.LyricsPreferences

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = LyricsPreferences.enableBetterLyrics

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)
}
