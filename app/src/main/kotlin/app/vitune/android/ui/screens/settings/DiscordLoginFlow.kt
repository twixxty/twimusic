package app.vitune.android.ui.screens.settings

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vitune.android.discord.DiscordAuthSession
import app.vitune.android.discord.DiscordAuthSessionStore
import app.vitune.android.discord.DiscordHttp
import app.vitune.android.discord.DiscordRpcManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browser-based Discord OAuth (PKCE). The redirect comes back as a deep link to MainActivity. */
object DiscordLoginFlow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions get() = DiscordAuthSessionStore()

    var exchanging by mutableStateOf(false)
        private set

    val isActive get() = sessions.pending() != null || exchanging

    fun start(session: DiscordAuthSession) = sessions.save(session)

    fun clear() = sessions.clear()

    /** Returns true when [intent] was a Discord OAuth redirect (handled or ignored as stale). */
    fun handleCallback(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "metrolist-auth" && data.scheme != "twimusic-auth") return false
        if (data.host != "discord") return false
        val session = sessions.pending() ?: return true
        val redirect = android.net.Uri.parse(session.redirectUri)
        if (data.scheme != redirect.scheme || data.authority != redirect.authority ||
            data.path != redirect.path
        ) {
            return true
        }
        if (data.getQueryParameter("state") != session.state) return true
        clear()
        val errorParam = data.getQueryParameter("error")
        if (errorParam != null) {
            val errorDesc = data.getQueryParameter("error_description") ?: errorParam
            DiscordRpcManager.setError("discord_error_denied", errorDesc)
            return true
        }
        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            DiscordRpcManager.setError("discord_error_token_exchange_failed")
            return true
        }
        exchanging = true
        scope.launch {
            try {
                // OAuth can return to a fresh process in which presence has not been initialised.
                DiscordRpcManager.init(DiscordHttp.client)
                val result = withContext(Dispatchers.IO) {
                    DiscordRpcManager.auth.exchangeCode(
                        code,
                        session.pkceVerifier,
                        session.state,
                        session.state
                    )
                }
                DiscordRpcManager.storeAndConnect(
                    result.accessToken,
                    result.refreshToken,
                    result.expiresInSec
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DiscordRpcManager.setError("discord_error_token_exchange_failed")
            } finally {
                exchanging = false
            }
        }
        return true
    }
}
