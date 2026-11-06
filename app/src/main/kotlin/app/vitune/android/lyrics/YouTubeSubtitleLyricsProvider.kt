package app.vitune.android.lyrics

import android.content.Context
import com.metrolist.innertube.YouTube
import app.vitune.android.preferences.LyricsPreferences

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context): Boolean = LyricsPreferences.enableYouTubeSubtitle

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
