package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.musixmatch.Musixmatch
import app.vitune.android.preferences.LyricsPreferences

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean =
        LyricsPreferences.enableMusixmatch

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        Musixmatch.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit
    ) {
        Musixmatch.getAllLyrics(title, artist, duration, album, callback)
    }
}
