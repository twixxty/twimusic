package app.vitune.android.preferences

import app.vitune.android.GlobalPreferencesHolder

object LyricsPreferences : GlobalPreferencesHolder() {
    var enableBetterLyrics by boolean(true)
    var enableLrcLib by boolean(true)
    var enableKuGou by boolean(true)
    var enablePaxsenix by boolean(true)
    var enableLyricsPlus by boolean(true)
    var enableZemer by boolean(true)
    var enableYouTubeSubtitle by boolean(true)
    var enableYouTube by boolean(true)
    var lyricsProviderOrder by string("BetterLyrics,LrcLib,KuGou,Paxsenix,LyricsPlus,Zemer,YouTubeSubtitle,YouTube")
}
