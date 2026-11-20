package app.vitune.android.discord

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import app.vitune.android.preferences.DiscordPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Shims for the platform helpers Metrolist's shared Discord code expects. */
internal object Logger {
    fun d(tag: String, message: String, error: Throwable? = null) {
        Log.d(tag, message, error)
    }

    fun i(tag: String, message: String, error: Throwable? = null) {
        Log.i(tag, message, error)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
    }
}

internal object DiscordClock {
    fun nowMs(): Long = System.currentTimeMillis()
}

internal enum class PlaybackEventKind { SYNC }

internal object PlaybackEventLog {
    fun error(kind: PlaybackEventKind, tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, "[$kind] $message", error)
    }
}

internal fun Throwable.hasHttpTransportFailureInCauseChain(): Boolean =
    generateSequence(this) { it.cause }.take(16).any { it is IOException }

internal object DeviceCrypto {
    private val random = SecureRandom()

    fun secureRandomBytes(count: Int) = ByteArray(count).also(random::nextBytes)
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}

internal data class NetworkState(val connected: Boolean)

/** Tracks connectivity so the gateway is not kept alive offline. Call [init] once at startup. */
internal object PlaybackNetworkStateStore {
    private val _state = MutableStateFlow(NetworkState(connected = true))
    val state: StateFlow<NetworkState> = _state.asStateFlow()
    val current: NetworkState get() = _state.value

    private var registered = false

    fun init(context: Context) {
        if (registered) return
        registered = true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return
        fun isOnline() = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _state.value = NetworkState(isOnline())
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _state.value = NetworkState(true)
                }

                override fun onLost(network: Network) {
                    _state.value = NetworkState(isOnline())
                }
            })
        }
    }
}

object DiscordTokenStore {
    fun storeFull(accessToken: String, refreshToken: String, expiresInSec: Long) {
        DiscordPreferences.accessToken = accessToken
        DiscordPreferences.refreshToken = refreshToken
        DiscordPreferences.expiresAt = DiscordClock.nowMs() / 1000L + expiresInSec
    }

    fun storeAccessToken(accessToken: String) {
        DiscordPreferences.accessToken = accessToken
    }

    fun retrieve(): String? = DiscordPreferences.accessToken.takeIf { it.isNotEmpty() }
    fun getRefreshToken(): String? = DiscordPreferences.refreshToken.takeIf { it.isNotEmpty() }
    fun getExpiresAt(): Long = DiscordPreferences.expiresAt

    fun getDeviceVendorId(): String {
        if (DiscordPreferences.deviceVendorId.isEmpty()) {
            DiscordPreferences.deviceVendorId = UUID.randomUUID().toString()
        }
        return DiscordPreferences.deviceVendorId
    }

    fun getClientUuid(): String {
        if (DiscordPreferences.clientUuid.isEmpty()) {
            DiscordPreferences.clientUuid = UUID.randomUUID().toString()
        }
        return DiscordPreferences.clientUuid
    }

    fun clear() {
        DiscordPreferences.accessToken = ""
        DiscordPreferences.refreshToken = ""
        DiscordPreferences.expiresAt = 0L
        DiscordPreferences.deviceVendorId = ""
        DiscordPreferences.clientUuid = ""
    }
}

/** HTTP client with WebSocket support shared by the gateway and the OAuth/profile calls. */
internal object DiscordHttp {
    val client: io.ktor.client.HttpClient by lazy {
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
    }
}

internal fun discordAppId(): String = DiscordDefaults.DEFAULT_APP_ID

internal fun discordRedirectUri(): String = DiscordDefaults.DEFAULT_REDIRECT_URI

@Serializable
private data class PendingDiscordAuth(val session: DiscordAuthSession, val expiresAt: Long)

class DiscordAuthSessionStore(private val now: () -> Long = { System.currentTimeMillis() }) {
    fun save(session: DiscordAuthSession) {
        DiscordPreferences.pendingAuthSession =
            Json.encodeToString(PendingDiscordAuth(session, now() + 120_000L))
    }

    fun pending(): DiscordAuthSession? {
        val raw = DiscordPreferences.pendingAuthSession.takeIf(String::isNotBlank) ?: return null
        val pending = runCatching { Json.decodeFromString<PendingDiscordAuth>(raw) }.getOrNull()
        if (pending == null || now() >= pending.expiresAt) {
            clear()
            return null
        }
        return pending.session
    }

    fun clear() {
        DiscordPreferences.pendingAuthSession = ""
    }
}
