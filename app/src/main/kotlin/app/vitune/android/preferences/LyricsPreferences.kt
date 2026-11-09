package app.vitune.android.preferences

import app.vitune.android.GlobalPreferencesHolder

object LyricsPreferences : GlobalPreferencesHolder() {
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
        "BetterLyrics,Paxsenix,YouLyPlus,Unison,BiniLyrics,Musixmatch,LrcLib,KuGou,Spotify,LrcRed,YouTubeSubtitle,YouTube,LyricsPlus,Zemer"
    )
}
