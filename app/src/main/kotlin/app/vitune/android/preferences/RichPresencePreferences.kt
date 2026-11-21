package app.vitune.android.preferences

import app.vitune.android.GlobalPreferencesHolder

/** Discord Rich Presence settings. Mirrors Metrolist's customisation options. */
object DiscordPreferences : GlobalPreferencesHolder() {
    var enabled by boolean(false)
    var showArtistIcon by boolean(true)
    var advancedMode by boolean(false)

    /** Optional own Discord application ID; blank uses the bundled default. */
    var customAppId by string("")

    /** Discord activity type code: 0 Playing, 2 Listening, 3 Watching, 5 Competing. */
    var activityType by string("2")
    var activityName by string("")
    var stateTemplate by string("{artist.name}")
    var detailsTemplate by string("{song.name}")
    var button1Enabled by boolean(true)
    var button1Text by string("")
    var button1Url by string("{song.url}")
    var button2Enabled by boolean(true)
    var button2Text by string("")
    var button2Url by string("https://github.com/twixxty/twimusic")

    var username by string("")
    var name by string("")
    var avatar by string("")

    var accessToken by string("")
    var refreshToken by string("")
    var expiresAt by long(0L)
    var authorizedAppId by string("")
    var customRedirectUri by string("")
    var deviceVendorId by string("")
    var clientUuid by string("")
    var pendingAuthSession by string("")
    var showQuickToggle by boolean(false)
}

/** Listen Together (Play Together) settings, compatible with Metrolist rooms. */
object ListenTogetherPreferences : GlobalPreferencesHolder() {
    var enabled by boolean(true)
    var username by string("")
    var serverUrl by string("")
    var customServers by string("")
    var autoApproveJoinRequests by boolean(false)
    var autoApproveSongSuggestions by boolean(false)
    var syncHostVolume by boolean(true)
    var blockedUsers by string("")
    var showQuickAccess by boolean(false)

    var sessionToken by string("")
    var sessionRoomCode by string("")
    var sessionIsHost by boolean(false)
}
