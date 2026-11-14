package app.vitune.android.preferences

import app.vitune.android.GlobalPreferencesHolder

enum class LyricsPriorityMode {
    Quality,
    Fast,
    Custom
}

object LyricsPreferences : GlobalPreferencesHolder() {
    var lyricsPriorityMode by enum(LyricsPriorityMode.Quality)

    var enableMusixmatch by boolean(true)
    var enableYouLyPlus by boolean(true)
    var enablePaxsenix by boolean(true)
    var enableBetterLyrics by boolean(true)
    var enableLrcLib by boolean(true)
    var enableKuGou by boolean(true)
    var enableUnison by boolean(true)
    var enableBiniLyrics by boolean(true)
    var enableSpotify by boolean(false)
    var enableLrcRed by boolean(true)
    var enableYouTubeSubtitle by boolean(true)
    var enableYouTube by boolean(true)
    var enableLyricsPlus by boolean(true)
    var enableZemer by boolean(true)

    var lyricsPrioritizeSyncAccuracy by boolean(true)
    var lyricsProviderOrder by string(
        "BetterLyrics,LrcRed,Paxsenix,BiniLyrics,YouLyPlus,Unison,Musixmatch,LrcLib,KuGou,Spotify,YouTubeSubtitle,YouTube,LyricsPlus,Zemer"
    )

    fun isProviderEnabled(name: String): Boolean = when (name) {
        "Musixmatch" -> enableMusixmatch
        "YouLyPlus" -> enableYouLyPlus
        "Paxsenix" -> enablePaxsenix
        "BetterLyrics" -> enableBetterLyrics
        "LrcLib" -> enableLrcLib
        "KuGou" -> enableKuGou
        "Unison" -> enableUnison
        "BiniLyrics" -> enableBiniLyrics
        "Spotify" -> enableSpotify
        "LrcRed" -> enableLrcRed
        "YouTubeSubtitle" -> enableYouTubeSubtitle
        "YouTube", "YouTubeMusic" -> enableYouTube
        "LyricsPlus" -> enableLyricsPlus
        "Zemer" -> enableZemer
        else -> true
    }

    fun setProviderEnabled(name: String, enabled: Boolean) {
        when (name) {
            "Musixmatch" -> enableMusixmatch = enabled
            "YouLyPlus" -> enableYouLyPlus = enabled
            "Paxsenix" -> enablePaxsenix = enabled
            "BetterLyrics" -> enableBetterLyrics = enabled
            "LrcLib" -> enableLrcLib = enabled
            "KuGou" -> enableKuGou = enabled
            "Unison" -> enableUnison = enabled
            "BiniLyrics" -> enableBiniLyrics = enabled
            "Spotify" -> enableSpotify = enabled
            "LrcRed" -> enableLrcRed = enabled
            "YouTubeSubtitle" -> enableYouTubeSubtitle = enabled
            "YouTube", "YouTubeMusic" -> enableYouTube = enabled
            "LyricsPlus" -> enableLyricsPlus = enabled
            "Zemer" -> enableZemer = enabled
        }
    }
}
