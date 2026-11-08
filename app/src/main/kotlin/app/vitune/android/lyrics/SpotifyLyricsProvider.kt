package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.preferences.LyricsPreferences
import app.vitune.android.spotify.Spotify

object SpotifyLyricsProvider : LyricsProvider {
    override val name = "Spotify"

    override fun isEnabled(context: Context): Boolean =
        LyricsPreferences.enableSpotify

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val query = "$title $artist"
        val trackId = Spotify.searchTrackRest(query)
            ?: throw IllegalStateException("Track not found on Spotify")

        val lyrics = Spotify.getColorLyrics(trackId)
            ?: throw IllegalStateException("Lyrics not available on Spotify")

        if (lyrics.isBlank()) throw IllegalStateException("Empty lyrics")
        lyrics
    }

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        getLyrics(context, id, title, artist, duration, album).onSuccess(callback)
    }
}
