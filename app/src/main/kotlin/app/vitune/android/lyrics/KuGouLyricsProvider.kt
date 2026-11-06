package app.vitune.android.lyrics

import android.content.Context
import app.vitune.android.kugou.KuGou
import app.vitune.android.preferences.LyricsPreferences

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override fun isEnabled(context: Context): Boolean = LyricsPreferences.enableKuGou

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = KuGou.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
    }
}
