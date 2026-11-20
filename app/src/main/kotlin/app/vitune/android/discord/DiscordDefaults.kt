package app.vitune.android.discord

fun resolveDiscordButtonLabel(
    configured: String,
    default: String,
    localized: String,
): String = if (configured.isEmpty() || configured == default) localized else configured

object DiscordDefaults {
    const val YOUTUBE_WATCH_URL = "https://music.youtube.com/watch?v="

    const val BUTTON1_LABEL = "Listen on YouTube Music"
    const val BUTTON1_URL_TEMPLATE = "{song.url}"

    const val BUTTON2_LABEL = "Visit TwiMusic"
    const val BUTTON2_URL = "https://github.com/twixxty/twimusic"

    const val STATE_TEMPLATE = "{artist.name}"
    const val DETAILS_TEMPLATE = "{song.name}"

    const val DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize"
    const val DISCORD_OAUTH_TOKEN = "https://discord.com/api/v10/oauth2/token"
    /** Application ID used for Discord OAuth sign-in and Rich Presence display. */
    const val DEFAULT_APP_ID = "1573339712846831666"
    const val OAUTH_CLIENT_ID = "1573339712846831666"
    const val DEFAULT_REDIRECT_URI = "twimusic-auth://discord/callback"
    const val OAUTH_REDIRECT_URI = "twimusic-auth://discord/callback"
    const val CUSTOM_REDIRECT_URI = "twimusic-auth://discord/callback"

    const val DISCORD_SCOPES = "openid sdk.social_layer_presence"
}
