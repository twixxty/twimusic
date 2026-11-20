/**
 * Client for Metrolist's Listen Together protocol ("Play Together").
 *
 * Wire-compatible with Metrolist: the same protobuf messages, capability negotiation, clock
 * synchronisation, buffer barrier and session reconnection are used, so TwiMusic and Metrolist
 * clients can share a room on the same server. The room logic is a condensed port of Metrolist's
 * (https://github.com/MetrolistGroup/Metrolist, GPL-3.0) manager.
 *
 * All state is touched on the main thread; OkHttp callbacks are forwarded onto [scope].
 */
@file:Suppress("TooManyFunctions", "LargeClass", "CyclomaticComplexMethod", "NestedBlockDepth")

package app.vitune.android.listentogether

import android.os.SystemClock
import app.vitune.android.listentogether.network.ApproveJoinPayload
import app.vitune.android.listentogether.network.ApproveSuggestionPayload
import app.vitune.android.listentogether.network.BufferCompletePayload
import app.vitune.android.listentogether.network.BufferReadyPayload
import app.vitune.android.listentogether.network.ClientCapabilities
import app.vitune.android.listentogether.network.CreateRoomPayload
import app.vitune.android.listentogether.network.ErrorPayload
import app.vitune.android.listentogether.network.HostChangedPayload
import app.vitune.android.listentogether.network.JoinApprovedPayload
import app.vitune.android.listentogether.network.JoinRejectedPayload
import app.vitune.android.listentogether.network.JoinRequestPayload
import app.vitune.android.listentogether.network.JoinRoomPayload
import app.vitune.android.listentogether.network.KickUserPayload
import app.vitune.android.listentogether.network.KickedPayload
import app.vitune.android.listentogether.network.ListenTogetherMessageCodec
import app.vitune.android.listentogether.network.MessageTypes
import app.vitune.android.listentogether.network.PingPayload
import app.vitune.android.listentogether.network.PlaybackActionPayload
import app.vitune.android.listentogether.network.PlaybackActions
import app.vitune.android.listentogether.network.PongPayload
import app.vitune.android.listentogether.network.ProtocolFeatures
import app.vitune.android.listentogether.network.ReconnectPayload
import app.vitune.android.listentogether.network.ReconnectedPayload
import app.vitune.android.listentogether.network.RejectJoinPayload
import app.vitune.android.listentogether.network.RejectSuggestionPayload
import app.vitune.android.listentogether.network.RoomCreatedPayload
import app.vitune.android.listentogether.network.RoomState
import app.vitune.android.listentogether.network.ServerCapabilities
import app.vitune.android.listentogether.network.ServerClock
import app.vitune.android.listentogether.network.ServerRestartingPayload
import app.vitune.android.listentogether.network.SuggestTrackPayload
import app.vitune.android.listentogether.network.SuggestionApprovedPayload
import app.vitune.android.listentogether.network.SuggestionReceivedPayload
import app.vitune.android.listentogether.network.SuggestionRejectedPayload
import app.vitune.android.listentogether.network.SuggestionSubmittedPayload
import app.vitune.android.listentogether.network.SyncStatePayload
import app.vitune.android.listentogether.network.TrackInfo
import app.vitune.android.listentogether.network.TransferHostPayload
import app.vitune.android.listentogether.network.UserDisconnectedPayload
import app.vitune.android.listentogether.network.UserInfo
import app.vitune.android.listentogether.network.UserJoinedPayload
import app.vitune.android.listentogether.network.UserLeftPayload
import app.vitune.android.listentogether.network.UserReconnectedPayload
import app.vitune.android.listentogether.network.createListenTogetherMessageCodec
import app.vitune.android.listentogether.network.decodeServerCapabilities
import app.vitune.android.listentogether.network.roomStateHash
import app.vitune.android.preferences.ListenTogetherPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.math.abs
import kotlin.random.Random

enum class ListenTogetherConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }

enum class ListenTogetherRoomRole { HOST, GUEST, NONE }

sealed interface ListenTogetherEvent {
    data class Notice(val key: Notice.Key) : ListenTogetherEvent {
        enum class Key {
            RoomCreated, JoinedRoom, Reconnected, BecameHost, HostChanged,
            SuggestionSubmitted, SuggestionApproved, SuggestionRejected
        }
    }

    data class Failure(val key: Failure.Key, val reason: String? = null) : ListenTogetherEvent {
        enum class Key {
            ConnectionFailed, JoinRejected, Kicked, ServerError, QueueTooLarge, TrackLoadFailed
        }
    }

    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ListenTogetherEvent
    data class SuggestionAutoApproved(val suggestion: ListenTogetherSuggestion) :
        ListenTogetherEvent
}

data class ListenTogetherJoinRequest(val userId: String, val username: String)

data class ListenTogetherSuggestion(
    val id: String,
    val fromUserId: String,
    val fromUsername: String,
    val trackInfo: TrackInfo
)

data class ListenTogetherServer(
    val name: String,
    val url: String,
    val isCustom: Boolean = false
)

/** Bridge to the ExoPlayer owned by the playback service. Every call happens on the main thread. */
interface ListenTogetherPlayback {
    fun currentTrack(): TrackInfo?
    fun isPlaying(): Boolean
    fun positionMs(): Long
    fun durationMs(): Long
    fun volume(): Float
    fun queueTitle(): String? = null

    /** Tracks after the current one, in playback order. */
    fun upcoming(): List<TrackInfo>

    /** True when [trackId] is the current item and the player has buffered enough to start. */
    fun isReady(trackId: String): Boolean
    fun hasFailed(trackId: String): Boolean

    /** Replace the queue with [current] followed by [upcoming], prepared but paused. */
    fun playQueue(current: TrackInfo, upcoming: List<TrackInfo>, title: String?)
    fun replaceUpcoming(tracks: List<TrackInfo>)
    fun skipNext()
    fun skipPrevious()
    fun setPlaying(playing: Boolean)
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun pauseAndClear()
}

class ListenTogetherManager(
    private val playback: ListenTogetherPlayback,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val log: (String) -> Unit = {}
) {
    companion object {
        const val DEFAULT_SERVER_URL = "wss://metroserverx.meowery.eu/ws"
        const val MAX_RECONNECT_ATTEMPTS = 15
        private const val INITIAL_PING_INTERVAL_MS = 2_000L
        private const val STEADY_PING_INTERVAL_MS = 15_000L
        private const val INITIAL_CLOCK_SAMPLES = 5
        private const val CAPABILITY_TIMEOUT_MS = 5_000L
        private const val ROOM_REQUEST_TIMEOUT_MS = 5 * 60_000L
        private const val OBSERVER_INTERVAL_MS = 800L
        private const val READY_TIMEOUT_MS = 30_000L
        private const val BARRIER_TIMEOUT_MS = 25_000L
        private const val SYNC_RETRY_DELAY_MS = 1_100L
        private const val MAX_TRACKED_REQUESTS = 128
        private const val SEPARATOR = "\n"
        private val BACKGROUND_REQUESTS = setOf(
            MessageTypes.PING,
            MessageTypes.REQUEST_SYNC,
            MessageTypes.PLAYBACK_ACTION,
            MessageTypes.BUFFER_READY
        )
        private val ROOM_GONE_CODES = setOf(
            "user_not_found", "session_not_found", "session_expired", "room_not_found", "not_in_room"
        )

        fun isAllowedServerUrl(url: String): Boolean {
            val match = Regex("^wss://([^/:?#]+|\\[[^]]+])(?::([0-9]{1,5}))?(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
                .matchEntire(url.trim()) ?: return false
            return match.groupValues[2].toIntOrNull()?.let { it in 1..65535 } != false
        }

        fun sanitizeRoomCode(value: String) =
            value.filter { it.isLetterOrDigit() }.take(8).uppercase()
    }

    private sealed interface PendingAction {
        data object Create : PendingAction
        data class Join(val roomCode: String) : PendingAction
    }

    private data class PendingBarrier(
        val trackId: String,
        val position: Long,
        val effectiveAt: Long?,
        val playing: Boolean,
        val generation: Long
    )

    private val client = OkHttpClient.Builder().readTimeout(java.time.Duration.ZERO).build()
    private val codec: ListenTogetherMessageCodec = createListenTogetherMessageCodec()
    private val clock = ServerClock { SystemClock.elapsedRealtime() }
    private val revisionFilter = PlaybackRevisionFilter()
    private val encodeMutex = Mutex()

    private val _connectionState = MutableStateFlow(ListenTogetherConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ListenTogetherConnectionState> = _connectionState.asStateFlow()
    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()
    private val _role = MutableStateFlow(ListenTogetherRoomRole.NONE)
    val role: StateFlow<ListenTogetherRoomRole> = _role.asStateFlow()
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()
    private val _isInRoom = MutableStateFlow(false)
    val isInRoom: StateFlow<Boolean> = _isInRoom.asStateFlow()
    private val _roomRequestPending = MutableStateFlow(false)
    val roomRequestPending: StateFlow<Boolean> = _roomRequestPending.asStateFlow()
    private val _pendingJoinRequests = MutableStateFlow<List<ListenTogetherJoinRequest>>(emptyList())
    val pendingJoinRequests: StateFlow<List<ListenTogetherJoinRequest>> = _pendingJoinRequests.asStateFlow()
    private val _pendingSuggestions = MutableStateFlow<List<ListenTogetherSuggestion>>(emptyList())
    val pendingSuggestions: StateFlow<List<ListenTogetherSuggestion>> = _pendingSuggestions.asStateFlow()
    private val _submittedSuggestions = MutableStateFlow<Map<String, TrackInfo>>(emptyMap())
    val submittedSuggestions: StateFlow<Map<String, TrackInfo>> = _submittedSuggestions.asStateFlow()
    private val _failedRoomTrack = MutableStateFlow<TrackInfo?>(null)
    val failedRoomTrack: StateFlow<TrackInfo?> = _failedRoomTrack.asStateFlow()
    private val _events = MutableSharedFlow<ListenTogetherEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()
    private val _serverUrl = MutableStateFlow(
        ListenTogetherPreferences.serverUrl.takeIf(::isAllowedServerUrl) ?: DEFAULT_SERVER_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    private val _servers = MutableStateFlow(buildServers())
    val servers: StateFlow<List<ListenTogetherServer>> = _servers.asStateFlow()
    private val _blocked = MutableStateFlow(parseSet(ListenTogetherPreferences.blockedUsers))
    val blockedUsernames: StateFlow<Set<String>> = _blocked.asStateFlow()

    val isGuestPlaybackLocked: Boolean
        get() = ListenTogetherPreferences.enabled && _isInRoom.value &&
            _role.value == ListenTogetherRoomRole.GUEST

    val username get() = ListenTogetherPreferences.username

    private var socket: WebSocket? = null
    private var connectJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var remoteJob: Job? = null
    private var observerJob: Job? = null
    private var recoveryJob: Job? = null
    private var requestTimeoutJob: Job? = null

    private var closed = false
    private var connectionGeneration = 0L
    private var authenticatedGeneration = -1L
    private var reconnectAttempts = 0
    private var restartDelayMs: Long? = null
    private var serverFeatures: Set<String> = emptySet()

    private var pendingAction: PendingAction? = null
    private var awaitingRoomResponse = false
    private var requestedRoomCode: String? = null
    private var sessionToken: String? = ListenTogetherPreferences.sessionToken.ifBlank { null }

    private var pingSequence = 0L
    private var lastPongSequence = 0L
    private val outstandingPings = LinkedHashMap<Long, Long>()
    private var requestSequence = 0L
    private val requestTypes = LinkedHashMap<Long, String>()
    private val suggestionRequests = HashMap<Long, String>()
    private val decisionRequests = HashMap<Long, String>()
    private val outgoingSuggestions = LinkedHashMap<String, TrackInfo>()
    private val autoApproving = HashMap<String, ListenTogetherSuggestion>()

    // guest state
    private var applyingRemote = false
    private var loadingTrackId: String? = null
    private var barrier: PendingBarrier? = null
    private var playbackGeneration = 0L

    // host publication state
    private var hostSequence = 0L
    private var lastTrackId: String? = null
    private var lastPlaying: Boolean? = null
    private var lastPositionBucket: Long? = null
    private var lastQueue: List<TrackInfo>? = null
    private var lastQueueTitle: String? = null
    private var lastVolume: Float? = null
    private var lastPausedPosition: Long = 0L

    init {
        restoreSession()
    }

    // ---- public API -----------------------------------------------------------------------

    fun setEnabled(enabled: Boolean) {
        ListenTogetherPreferences.enabled = enabled
        if (!enabled) scope.launch { leaveRoomAndWait() }
    }

    fun setUsername(value: String) {
        ListenTogetherPreferences.username = value.trim()
    }

    fun createRoom(): Boolean {
        if (!canStartRoomRequest() || username.isBlank()) return false
        pendingAction = PendingAction.Create
        awaitingRoomResponse = false
        requestedRoomCode = null
        beginRoomRequest()
        return true
    }

    fun joinRoom(roomCode: String): Boolean {
        val code = sanitizeRoomCode(roomCode)
        if (!canStartRoomRequest() || username.isBlank() || code.isBlank()) return false
        pendingAction = PendingAction.Join(code)
        awaitingRoomResponse = false
        requestedRoomCode = code
        beginRoomRequest()
        return true
    }

    fun cancelJoinRequest() {
        if (_isInRoom.value) return
        scope.launch { leaveRoomAndWait() }
    }

    fun retryConnection() {
        reconnectAttempts = 0
        if (socket == null) connect()
    }

    fun leaveRoom() {
        scope.launch { leaveRoomAndWait() }
    }

    suspend fun leaveRoomAndWait() {
        val session = socket
        ++connectionGeneration
        clearRoomRequest()
        if (session != null && authenticatedGeneration >= 0) {
            runCatching { sendOn(session, MessageTypes.LEAVE_ROOM, null) }
        }
        socket = null
        connectJob?.cancel()
        connectJob = null
        session?.close(1000, "User disconnected")
        cleanupRoom(clearSession = true)
        _connectionState.value = ListenTogetherConnectionState.DISCONNECTED
    }

    fun requestSync() {
        if (!_isInRoom.value || _role.value != ListenTogetherRoomRole.GUEST) return
        launchSend(MessageTypes.REQUEST_SYNC)
    }

    fun approveJoin(userId: String) = launchSend(
        MessageTypes.APPROVE_JOIN,
        ApproveJoinPayload(userId)
    ) { _pendingJoinRequests.value = _pendingJoinRequests.value.filterNot { it.userId == userId } }

    fun rejectJoin(userId: String, reason: String? = null) = launchSend(
        MessageTypes.REJECT_JOIN,
        RejectJoinPayload(userId, reason)
    ) { _pendingJoinRequests.value = _pendingJoinRequests.value.filterNot { it.userId == userId } }

    fun kickUser(userId: String, reason: String? = null) =
        launchSend(MessageTypes.KICK_USER, KickUserPayload(userId, reason))

    fun transferHost(userId: String) =
        launchSend(MessageTypes.TRANSFER_HOST, TransferHostPayload(userId))

    fun approveSuggestion(id: String) {
        autoApproving.remove(id)
        dropSuggestion(id)
        launchSend(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(id))
    }

    fun rejectSuggestion(id: String, reason: String? = null) {
        autoApproving.remove(id)
        dropSuggestion(id)
        launchSend(MessageTypes.REJECT_SUGGESTION, RejectSuggestionPayload(id, reason))
    }

    fun suggestTrack(track: TrackInfo) {
        if (_role.value != ListenTogetherRoomRole.GUEST || !_isInRoom.value) return
        if (_submittedSuggestions.value.values.any { it.id == track.id }) return
        outgoingSuggestions.putIfAbsent(track.id, track)
        sendPendingSuggestions()
    }

    fun setServerUrl(value: String) {
        val normalized = value.trim().ifBlank { DEFAULT_SERVER_URL }
        if (!isAllowedServerUrl(normalized) || normalized == _serverUrl.value) return
        // Rooms do not transfer between servers.
        scope.launch {
            leaveRoomAndWait()
            _serverUrl.value = normalized
            ListenTogetherPreferences.serverUrl = normalized.takeIf { it != DEFAULT_SERVER_URL }.orEmpty()
        }
    }

    fun addCustomServer(value: String) {
        val url = value.trim()
        if (!isAllowedServerUrl(url) || url == DEFAULT_SERVER_URL) return
        ListenTogetherPreferences.customServers =
            (parseSet(ListenTogetherPreferences.customServers) + url).sorted().joinToString(SEPARATOR)
        _servers.value = buildServers()
    }

    fun removeCustomServer(value: String) {
        ListenTogetherPreferences.customServers =
            (parseSet(ListenTogetherPreferences.customServers) - value).sorted().joinToString(SEPARATOR)
        _servers.value = buildServers()
        if (_serverUrl.value == value) setServerUrl(DEFAULT_SERVER_URL)
    }

    fun blockUser(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _blocked.value += trimmed.lowercase()
        ListenTogetherPreferences.blockedUsers = _blocked.value.sorted().joinToString(SEPARATOR)
    }

    fun unblockUser(name: String) {
        _blocked.value -= name.lowercase()
        ListenTogetherPreferences.blockedUsers = _blocked.value.sorted().joinToString(SEPARATOR)
    }

    fun roomLink(): String? = _roomState.value?.roomCode?.let { "https://metrolist.cc/listen?code=$it" }

    fun close() {
        closed = true
        ++connectionGeneration
        socket?.cancel()
        socket = null
        connectJob?.cancel()
        reconnectJob?.cancel()
        pingJob?.cancel()
        observerJob?.cancel()
        remoteJob?.cancel()
        client.dispatcher.executorService.shutdown()
    }

    /** Called by the service when a local user action changed the player (host publication). */
    fun onLocalPlaybackChanged() {
        if (_role.value == ListenTogetherRoomRole.HOST && _isInRoom.value && !applyingRemote) {
            scope.launch { publishHostPlayback() }
        }
    }

    // ---- connection -----------------------------------------------------------------------

    private fun canStartRoomRequest() = ListenTogetherPreferences.enabled && !closed &&
        !_isInRoom.value && pendingAction == null && !awaitingRoomResponse

    private fun beginRoomRequest() {
        _roomRequestPending.value = true
        requestTimeoutJob?.cancel()
        requestTimeoutJob = scope.launch {
            delay(ROOM_REQUEST_TIMEOUT_MS)
            if (_roomRequestPending.value) {
                clearRoomRequest()
                emitFailure(ListenTogetherEvent.Failure.Key.ConnectionFailed)
            }
        }
        if (socket != null) scope.launch { sendPendingAction() } else connect()
    }

    fun connect() {
        if (!ListenTogetherPreferences.enabled || closed || connectJob?.isActive == true ||
            socket != null
        ) {
            return
        }
        val generation = ++connectionGeneration
        _connectionState.value =
            if (reconnectAttempts > 0) ListenTogetherConnectionState.RECONNECTING
            else ListenTogetherConnectionState.CONNECTING

        connectJob = scope.launch {
            val incoming = Channel<ByteArray>(Channel.UNLIMITED)
            var transport: WebSocket? = null
            var protocolRejected = false
            try {
                val listener = object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        incoming.trySend(bytes.toByteArray())
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                        incoming.close()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        incoming.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        incoming.close(t)
                    }
                }
                val ws = client.newWebSocket(Request.Builder().url(_serverUrl.value).build(), listener)
                transport = ws
                codec.compressionEnabled = false
                ws.send(
                    encode(
                        MessageTypes.CLIENT_CAPABILITIES,
                        ClientCapabilities(
                            supportsProtobuf = true,
                            supportsCompression = true,
                            clientVersion = appVersion,
                            features = ProtocolFeatures.supported
                        ),
                        0L,
                        false
                    ).toByteString()
                )
                val capabilities = withTimeoutOrNull(CAPABILITY_TIMEOUT_MS) {
                    val data = incoming.receiveCatching().getOrNull() ?: return@withTimeoutOrNull null
                    withContext(Dispatchers.Default) {
                        runCatching { codec.decodeServerCapabilities(data) }.getOrNull()
                    }
                }
                if (closed || generation != connectionGeneration) return@launch
                if (capabilities?.supportsProtobuf != true) {
                    protocolRejected = true
                    clearRoomRequest()
                    _connectionState.value = ListenTogetherConnectionState.ERROR
                    emitFailure(ListenTogetherEvent.Failure.Key.ConnectionFailed)
                    ws.close(1002, "Capability negotiation failed")
                    return@launch
                }
                codec.compressionEnabled = capabilities.supportsCompression
                serverFeatures = capabilities.features.toSet()
                socket = ws
                reconnectJob?.cancel()
                if (sessionToken == null) reconnectAttempts = 0
                _connectionState.value = ListenTogetherConnectionState.CONNECTED
                clock.reset()
                pingSequence = 0L
                lastPongSequence = 0L
                outstandingPings.clear()
                startPing(ws)
                sendPendingAction()
                for (data in incoming) {
                    if (generation != connectionGeneration || socket !== ws) break
                    handleBinary(data, generation)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log("Transport failed: ${error.message}")
            } finally {
                transport?.cancel()
                if (generation == connectionGeneration) {
                    connectJob = null
                    if (!protocolRejected) handleDisconnect(generation)
                }
            }
        }
    }

    private fun handleDisconnect(generation: Long) {
        if (generation != connectionGeneration || closed) return
        socket = null
        authenticatedGeneration = -1L
        pingJob?.cancel()
        cancelRemotePlayback()
        _connectionState.value = ListenTogetherConnectionState.DISCONNECTED
        if (awaitingRoomResponse && requestedRoomCode != null) {
            pendingAction = PendingAction.Join(requestedRoomCode!!)
            awaitingRoomResponse = false
        }
        val shouldReconnect = sessionToken != null || _isInRoom.value || pendingAction != null
        if (!shouldReconnect) {
            clearRoomRequest()
            _connectionState.value = ListenTogetherConnectionState.ERROR
            emitFailure(ListenTogetherEvent.Failure.Key.ConnectionFailed)
            return
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ListenTogetherConnectionState.ERROR
            emitFailure(ListenTogetherEvent.Failure.Key.ConnectionFailed)
            return
        }
        val announced = restartDelayMs
        restartDelayMs = null
        if (announced == null) reconnectAttempts++
        val attempt = reconnectAttempts.coerceAtLeast(1)
        val delayMs = announced ?: reconnectDelayMillis(attempt)
        _connectionState.value = ListenTogetherConnectionState.RECONNECTING
        _events.tryEmit(ListenTogetherEvent.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS))
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun startPing(session: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && socket === session) {
                send(MessageTypes.PING, pingPayload())
                delay(
                    if (clock.sampleCount() < INITIAL_CLOCK_SAMPLES) INITIAL_PING_INTERVAL_MS
                    else STEADY_PING_INTERVAL_MS
                )
            }
        }
    }

    private fun pingPayload(): PingPayload {
        val ping = PingPayload(SystemClock.elapsedRealtime(), ++pingSequence)
        outstandingPings[ping.sequence] = ping.clientTime
        if (outstandingPings.size > MAX_TRACKED_REQUESTS) {
            outstandingPings.remove(outstandingPings.keys.first())
        }
        val state = _roomState.value
        if (_role.value != ListenTogetherRoomRole.GUEST || !_isInRoom.value || state == null) {
            return ping
        }
        var result = ping
        if (ProtocolFeatures.DESYNC_CANARY in serverFeatures) {
            result = result.copy(
                hasStateHash = true,
                stateHash = roomStateHash(playback.currentTrack()?.id, playback.upcoming())
            )
        }
        val serverNow = clock.serverTimeNow()
        val trackId = playback.currentTrack()?.id
        val settled = loadingTrackId == null && barrier == null && trackId != null &&
            trackId == state.currentTrack?.id
        if (ProtocolFeatures.DRIFT_REPORT in serverFeatures && serverNow != null && settled &&
            state.isPlaying
        ) {
            result = result.copy(
                hasPosition = true,
                trackId = trackId,
                position = playback.positionMs(),
                positionCapturedAtServerTime = serverNow
            )
        }
        return result
    }

    private suspend fun sendPendingAction() {
        val action = pendingAction
        val delivered = when {
            action is PendingAction.Create ->
                send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username))

            action is PendingAction.Join ->
                send(MessageTypes.JOIN_ROOM, JoinRoomPayload(action.roomCode, username))

            sessionToken != null -> send(MessageTypes.RECONNECT, ReconnectPayload(sessionToken!!))
            else -> true
        }
        if (delivered && pendingAction == action) {
            pendingAction = null
            awaitingRoomResponse = action != null
        }
    }

    // ---- sending --------------------------------------------------------------------------

    private suspend fun encode(
        type: String,
        payload: Any?,
        requestId: Long,
        compression: Boolean
    ): ByteArray = encodeMutex.withLock {
        withContext(Dispatchers.Default) {
            val previous = codec.compressionEnabled
            codec.compressionEnabled = compression
            try {
                codec.encode(type, payload, requestId)
            } finally {
                codec.compressionEnabled = previous
            }
        }
    }

    private suspend fun send(type: String, payload: Any? = null): Boolean {
        val session = socket ?: return false
        return sendOn(session, type, payload)
    }

    private suspend fun sendOn(session: WebSocket, type: String, payload: Any?): Boolean {
        val requestId = if (ProtocolFeatures.REQUEST_ID in serverFeatures) trackRequest(type) else 0L
        if (requestId != 0L && payload is SuggestTrackPayload) {
            suggestionRequests[requestId] = payload.trackInfo.id
        }
        val decisionId = when (payload) {
            is ApproveSuggestionPayload -> payload.suggestionId
            is RejectSuggestionPayload -> payload.suggestionId
            else -> null
        }
        if (requestId != 0L && decisionId != null) decisionRequests[requestId] = decisionId
        val bytes = try {
            encode(type, payload, requestId, codec.compressionEnabled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log("Encoding $type failed: ${error.message}")
            return false
        }
        return session.send(bytes.toByteString())
    }

    private fun launchSend(type: String, payload: Any? = null, onDelivered: () -> Unit = {}) {
        if (socket == null || authenticatedGeneration != connectionGeneration) return
        val generation = connectionGeneration
        scope.launch {
            if (generation == connectionGeneration && send(type, payload)) onDelivered()
        }
    }

    private fun trackRequest(type: String): Long {
        val id = ++requestSequence
        requestTypes[id] = type
        if (requestTypes.size > MAX_TRACKED_REQUESTS) {
            val oldest = requestTypes.keys.first()
            requestTypes.remove(oldest)
            suggestionRequests.remove(oldest)
            decisionRequests.remove(oldest)
        }
        return id
    }

    private fun sendPendingSuggestions() {
        scope.launch {
            if (_role.value != ListenTogetherRoomRole.GUEST || !_isInRoom.value) return@launch
            for (track in outgoingSuggestions.values.toList()) {
                if (!send(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(track))) return@launch
                if (ProtocolFeatures.SUGGESTION_ACK !in serverFeatures) {
                    outgoingSuggestions.remove(track.id)
                    _submittedSuggestions.value += track.id to track
                    emitNotice(ListenTogetherEvent.Notice.Key.SuggestionSubmitted)
                }
            }
        }
    }

    private suspend fun sendPlaybackAction(payload: PlaybackActionPayload): Boolean {
        if (socket == null || authenticatedGeneration != connectionGeneration) return false
        return send(
            MessageTypes.PLAYBACK_ACTION,
            payload.copy(capturedAtServerTime = clock.serverTimeNow())
        )
    }

    // ---- incoming -------------------------------------------------------------------------

    private suspend fun handleBinary(data: ByteArray, generation: Long) {
        if (!ListenTogetherPreferences.enabled) return
        val envelope = try {
            withContext(Dispatchers.Default) { codec.decodeEnvelope(data) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log("Invalid envelope")
            return
        }
        val type = envelope.type
        val payload = try {
            withContext(Dispatchers.Default) { codec.decodePayload(type, envelope.payload) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log("Invalid payload for $type")
            return
        }
        if (generation != connectionGeneration) return
        val requestType = if (envelope.requestId == 0L) null else requestTypes.remove(envelope.requestId)
        val suggestedTrackId = suggestionRequests.remove(envelope.requestId)
        val decidedSuggestionId = decisionRequests.remove(envelope.requestId)

        when (type) {
            MessageTypes.ACK -> if (decidedSuggestionId != null) {
                dropSuggestion(decidedSuggestionId)
                val automatic = autoApproving.remove(decidedSuggestionId)
                if (automatic != null && requestType == MessageTypes.APPROVE_SUGGESTION &&
                    _role.value == ListenTogetherRoomRole.HOST
                ) {
                    _events.emit(ListenTogetherEvent.SuggestionAutoApproved(automatic))
                }
            }

            MessageTypes.SERVER_RESTARTING -> (payload as? ServerRestartingPayload)?.let {
                val eta = it.etaMs.coerceIn(0L, 60_000L)
                restartDelayMs = eta + Random.nextLong(eta / 2 + 1)
            }

            MessageTypes.SUGGESTION_SUBMITTED -> if (payload is SuggestionSubmittedPayload &&
                _role.value == ListenTogetherRoomRole.GUEST
            ) {
                outgoingSuggestions.remove(payload.trackInfo.id)
                _submittedSuggestions.value += payload.suggestionId to payload.trackInfo
                emitNotice(ListenTogetherEvent.Notice.Key.SuggestionSubmitted)
            }

            MessageTypes.ROOM_CREATED -> handleRoomCreated(payload as? RoomCreatedPayload ?: return)
            MessageTypes.JOIN_REQUEST -> handleJoinRequest(payload as? JoinRequestPayload ?: return)
            MessageTypes.JOIN_APPROVED -> handleJoinApproved(payload as? JoinApprovedPayload ?: return)
            MessageTypes.JOIN_REJECTED -> if (claimRoomRequest()) {
                emitFailure(
                    ListenTogetherEvent.Failure.Key.JoinRejected,
                    (payload as? JoinRejectedPayload)?.reason
                )
            }

            MessageTypes.USER_JOINED -> (payload as? UserJoinedPayload)?.let { joined ->
                _roomState.value = _roomState.value?.let { state ->
                    state.copy(
                        users = state.users.filterNot { it.userId == joined.userId } +
                            UserInfo(joined.userId, joined.username, false)
                    )
                }
                _pendingJoinRequests.value =
                    _pendingJoinRequests.value.filterNot { it.userId == joined.userId }
            }

            MessageTypes.USER_LEFT -> (payload as? UserLeftPayload)?.let { left ->
                _roomState.value = _roomState.value?.let { state ->
                    state.copy(users = state.users.filterNot { it.userId == left.userId })
                }
            }

            MessageTypes.HOST_CHANGED -> handleHostChanged(payload as? HostChangedPayload ?: return)
            MessageTypes.SYNC_PLAYBACK ->
                handlePlaybackAction(payload as? PlaybackActionPayload ?: return)

            MessageTypes.SYNC_STATE -> handleSyncState(payload as? SyncStatePayload ?: return)
            MessageTypes.PONG -> handlePong(payload as? PongPayload ?: return)
            MessageTypes.BUFFER_COMPLETE -> (payload as? BufferCompletePayload)?.let {
                completeBarrier(it.trackId)
            }

            MessageTypes.RECONNECTED -> handleReconnected(payload as? ReconnectedPayload ?: return)
            MessageTypes.USER_RECONNECTED -> (payload as? UserReconnectedPayload)?.let {
                setConnected(it.userId, true)
            }

            MessageTypes.USER_DISCONNECTED -> (payload as? UserDisconnectedPayload)?.let {
                setConnected(it.userId, false)
            }

            MessageTypes.SUGGESTION_RECEIVED -> handleSuggestion(
                payload as? SuggestionReceivedPayload ?: return
            )

            MessageTypes.SUGGESTION_APPROVED -> (payload as? SuggestionApprovedPayload)?.let {
                val auto = autoApproving.remove(it.suggestionId)
                if (auto != null && _role.value == ListenTogetherRoomRole.HOST) {
                    _events.emit(ListenTogetherEvent.SuggestionAutoApproved(auto))
                }
                if (it.suggestionId in _submittedSuggestions.value) {
                    _submittedSuggestions.value -= it.suggestionId
                    emitNotice(ListenTogetherEvent.Notice.Key.SuggestionApproved)
                } else if (_submittedSuggestions.value.isNotEmpty()) {
                    _submittedSuggestions.value = _submittedSuggestions.value.filterKeys { key -> key != it.suggestionId }
                    emitNotice(ListenTogetherEvent.Notice.Key.SuggestionApproved)
                }
                dropSuggestion(it.suggestionId)
            }

            MessageTypes.SUGGESTION_REJECTED -> (payload as? SuggestionRejectedPayload)?.let {
                autoApproving.remove(it.suggestionId)
                if (it.suggestionId in _submittedSuggestions.value) {
                    _submittedSuggestions.value -= it.suggestionId
                    emitNotice(ListenTogetherEvent.Notice.Key.SuggestionRejected)
                } else if (_submittedSuggestions.value.isNotEmpty()) {
                    _submittedSuggestions.value = _submittedSuggestions.value.filterKeys { key -> key != it.suggestionId }
                    emitNotice(ListenTogetherEvent.Notice.Key.SuggestionRejected)
                }
                dropSuggestion(it.suggestionId)
            }

            MessageTypes.KICKED -> {
                cleanupRoom(clearSession = true)
                emitFailure(ListenTogetherEvent.Failure.Key.Kicked, (payload as? KickedPayload)?.reason)
            }

            MessageTypes.ERROR -> handleError(
                payload as? ErrorPayload ?: return,
                requestType,
                suggestedTrackId,
                decidedSuggestionId
            )

            MessageTypes.SERVER_CAPABILITIES -> (payload as? ServerCapabilities)?.let {
                if (it.supportsProtobuf) codec.compressionEnabled = it.supportsCompression
            }
        }
    }

    private suspend fun handleError(
        error: ErrorPayload,
        requestType: String?,
        suggestedTrackId: String?,
        decidedSuggestionId: String?
    ) {
        if (decidedSuggestionId != null) {
            autoApproving.remove(decidedSuggestionId)
            if (error.code == "suggestion_not_found") dropSuggestion(decidedSuggestionId)
        }
        if (suggestedTrackId != null) {
            outgoingSuggestions.remove(suggestedTrackId)
            if (error.code == "duplicate_suggestion") {
                emitNotice(ListenTogetherEvent.Notice.Key.SuggestionSubmitted)
                return
            }
        }
        if (requestType in BACKGROUND_REQUESTS && error.code != "queue_too_large") {
            log("$requestType rejected: ${error.code}")
            if (error.code == "sync_rate_limited") {
                scope.launch {
                    delay(SYNC_RETRY_DELAY_MS)
                    requestSync()
                }
            }
            return
        }
        if (error.code in ROOM_GONE_CODES || error.message == "User not found in room" ||
            error.message == "You are not in a room"
        ) {
            cleanupRoom(clearSession = true)
        } else {
            clearRoomRequest()
        }
        emitFailure(
            if (error.code == "queue_too_large") ListenTogetherEvent.Failure.Key.QueueTooLarge
            else ListenTogetherEvent.Failure.Key.ServerError,
            error.message
        )
    }

    private fun handleRoomCreated(payload: RoomCreatedPayload) {
        if (!claimRoomRequest()) return
        sessionToken = payload.sessionToken
        _userId.value = payload.userId
        _role.value = ListenTogetherRoomRole.HOST
        authenticatedGeneration = connectionGeneration
        resetHostObservation()
        revisionFilter.reset()
        _roomState.value = RoomState(
            roomCode = payload.roomCode,
            hostId = payload.userId,
            users = listOf(UserInfo(payload.userId, username, true)),
            currentTrack = playback.currentTrack(),
            isPlaying = playback.isPlaying(),
            position = playback.positionMs(),
            lastUpdate = clock.serverTimeNow() ?: System.currentTimeMillis(),
            volume = playback.volume(),
            queue = playback.upcoming(),
            queueTitle = playback.queueTitle()
        )
        persistSession(isHost = true, roomCode = payload.roomCode)
        markRoomActive()
        emitNotice(ListenTogetherEvent.Notice.Key.RoomCreated)
    }

    private fun handleJoinRequest(payload: JoinRequestPayload) {
        if (_role.value != ListenTogetherRoomRole.HOST) return
        when {
            payload.username.lowercase() in _blocked.value ->
                rejectJoin(payload.userId, "user_is_blocked")

            ListenTogetherPreferences.autoApproveJoinRequests -> approveJoin(payload.userId)
            else -> _pendingJoinRequests.value =
                _pendingJoinRequests.value.filterNot { it.userId == payload.userId } +
                ListenTogetherJoinRequest(payload.userId, payload.username)
        }
    }

    private suspend fun handleJoinApproved(payload: JoinApprovedPayload) {
        if (!claimRoomRequest()) return
        cancelRemotePlayback()
        sessionToken = payload.sessionToken
        _userId.value = payload.userId
        _role.value = ListenTogetherRoomRole.GUEST
        authenticatedGeneration = connectionGeneration
        revisionFilter.reset(payload.state.revision)
        _roomState.value = payload.state
        persistSession(isHost = false, roomCode = payload.roomCode)
        markRoomActive()
        emitNotice(ListenTogetherEvent.Notice.Key.JoinedRoom)
        applyRoomState(payload.state, useBarrier = false)
    }

    private suspend fun handleHostChanged(payload: HostChangedPayload) {
        val becameHost = _userId.value == payload.newHostId
        if (becameHost) {
            cancelRemotePlayback()
            _failedRoomTrack.value = null
        }
        _roomState.value = _roomState.value?.let { state ->
            state.copy(
                hostId = payload.newHostId,
                users = state.users.map { it.copy(isHost = it.userId == payload.newHostId) }
            )
        }
        if (!becameHost) autoApproving.clear()
        _role.value = if (becameHost) ListenTogetherRoomRole.HOST else ListenTogetherRoomRole.GUEST
        if (becameHost) resetHostObservation()
        persistSession(isHost = becameHost, roomCode = _roomState.value?.roomCode)
        startObservers()
        emitNotice(
            if (becameHost) ListenTogetherEvent.Notice.Key.BecameHost
            else ListenTogetherEvent.Notice.Key.HostChanged
        )
    }

    private suspend fun handleReconnected(payload: ReconnectedPayload) {
        cancelRemotePlayback()
        if (closed) return
        if (!payload.isHost || _roomState.value?.roomCode != payload.roomCode) autoApproving.clear()
        _userId.value = payload.userId
        _role.value =
            if (payload.isHost) ListenTogetherRoomRole.HOST else ListenTogetherRoomRole.GUEST
        authenticatedGeneration = connectionGeneration
        if (payload.isHost) resetHostObservation()
        revisionFilter.reset(payload.state.revision)
        _roomState.value = payload.state
        persistSession(payload.isHost, payload.roomCode)
        markRoomActive()
        reconnectAttempts = 0
        emitNotice(ListenTogetherEvent.Notice.Key.Reconnected)
        if (payload.isHost) {
            // Host keeps local playback; republish it so guests converge on it.
            lastTrackId = null
            publishHostPlayback()
        } else {
            applyRoomState(payload.state, useBarrier = false)
            sendPendingSuggestions()
        }
    }

    private fun handleSuggestion(payload: SuggestionReceivedPayload) {
        if (_role.value != ListenTogetherRoomRole.HOST) return
        val suggestion = ListenTogetherSuggestion(
            payload.suggestionId,
            payload.fromUserId,
            payload.fromUsername,
            payload.trackInfo
        )
        if (ListenTogetherPreferences.autoApproveSongSuggestions) {
            autoApproving[suggestion.id] = suggestion
            launchSend(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestion.id))
        } else {
            _pendingSuggestions.value =
                _pendingSuggestions.value.filterNot { it.id == suggestion.id } + suggestion
        }
    }

    private fun setConnected(userId: String, connected: Boolean) {
        _roomState.value = _roomState.value?.let { state ->
            state.copy(users = state.users.map {
                if (it.userId == userId) it.copy(isConnected = connected) else it
            })
        }
    }

    private fun handlePong(payload: PongPayload) {
        if (payload.sequence <= lastPongSequence) return
        if (outstandingPings[payload.sequence] != payload.clientTime) return
        val before = clock.sampleCount()
        val first = clock.recordSample(
            payload.clientTime,
            payload.serverReceiveTime,
            payload.serverSendTime
        )
        if (clock.sampleCount() == before) return
        lastPongSequence = payload.sequence
        outstandingPings.keys.removeAll { it <= payload.sequence }
        if (payload.driftValid) correctReportedDrift(payload.driftMs)
        if (first && _isInRoom.value && _role.value == ListenTogetherRoomRole.GUEST &&
            authenticatedGeneration == connectionGeneration
        ) {
            requestSync()
        }
    }

    private fun correctReportedDrift(driftMs: Long) {
        val state = _roomState.value ?: return
        if (_role.value != ListenTogetherRoomRole.GUEST || !state.isPlaying) return
        if (loadingTrackId != null || barrier != null || abs(driftMs) < 200L) return
        playback.seekTo((playback.positionMs() - driftMs).coerceAtLeast(0L))
    }

    // ---- guest playback -------------------------------------------------------------------

    private suspend fun handlePlaybackAction(payload: PlaybackActionPayload) {
        val state = _roomState.value
        if (state != null && payload.revision > 0L && payload.revision == state.revision &&
            payload.serverTime != null && payload.serverTime < state.lastUpdate
        ) {
            return
        }
        if (!revisionFilter.accept(payload.revision)) return
        val isQueueAction = payload.action == PlaybackActions.QUEUE_ADD ||
            payload.action == PlaybackActions.QUEUE_REMOVE ||
            payload.action == PlaybackActions.QUEUE_CLEAR ||
            payload.action == PlaybackActions.SYNC_QUEUE
        _roomState.value = _roomState.value?.let { ListenTogetherProtocolReducer.reduce(it, payload) }

        if (isQueueAction) {
            if (_role.value == ListenTogetherRoomRole.HOST) {
                // Apply the canonical queue locally if it differs from current upcoming queue.
                _roomState.value?.queue?.let {
                    playback.replaceUpcoming(it)
                    lastQueue = it
                }
            } else if (_role.value == ListenTogetherRoomRole.GUEST && loadingTrackId == null) {
                _roomState.value?.queue?.let(playback::replaceUpcoming)
            }
            _roomState.value?.queue?.let { queue ->
                val queueIds = queue.map { it.id }.toSet()
                if (_submittedSuggestions.value.isNotEmpty()) {
                    _submittedSuggestions.value = _submittedSuggestions.value.filterNot { it.value.id in queueIds }
                }
            }
            return
        }
        if (_role.value != ListenTogetherRoomRole.GUEST) return

        when (payload.action) {
            PlaybackActions.PLAY -> {
                if (updateBarrier(payload.position, payload.serverTime, true)) return
                if (loadingTrackId == null && hasLoadedRoomTrack()) {
                    payload.position?.let { syncPosition(it, payload.serverTime, true) }
                    playback.setPlaying(true)
                }
            }

            PlaybackActions.PAUSE -> {
                if (updateBarrier(payload.position, payload.serverTime, false)) return
                if (loadingTrackId == null && hasLoadedRoomTrack()) {
                    payload.position?.let(playback::seekTo)
                }
                playback.setPlaying(false)
            }

            PlaybackActions.SEEK -> {
                if (updateBarrier(payload.position, payload.serverTime, null)) return
                if (loadingTrackId == null && hasLoadedRoomTrack()) {
                    payload.position?.let {
                        syncPosition(it, payload.serverTime, _roomState.value?.isPlaying == true)
                    }
                }
            }

            PlaybackActions.CHANGE_TRACK -> payload.trackInfo?.let { track ->
                val sameTrack = (track.id == playback.currentTrack()?.id &&
                    !playback.hasFailed(track.id)) || track.id == loadingTrackId
                if (sameTrack) {
                    if (payload.replaceQueue || payload.revision > 0L) {
                        _roomState.value?.queue?.let(playback::replaceUpcoming)
                    }
                    if (loadingTrackId == null) acknowledgeSameTrack(track, payload)
                } else {
                    startRemotePlayback(
                        track = track,
                        queue = payload.queue.orEmpty()
                            .takeIf { payload.replaceQueue || payload.revision > 0L },
                        position = payload.position ?: 0L,
                        playing = false,
                        effectiveAt = payload.serverTime,
                        useBarrier = true,
                        trackRevision = payload.revision
                    )
                }
            }

            PlaybackActions.SKIP_NEXT -> if (loadingTrackId == null && hasLoadedRoomTrack()) {
                cancelRemotePlayback()
                playback.skipNext()
            }

            PlaybackActions.SKIP_PREV -> if (loadingTrackId == null && hasLoadedRoomTrack()) {
                cancelRemotePlayback()
                playback.skipPrevious()
            }

            PlaybackActions.SET_VOLUME -> applyHostVolume(payload.volume)
        }
    }

    private suspend fun handleSyncState(payload: SyncStatePayload) {
        val state = _roomState.value
        if (state != null && payload.revision > 0L && payload.revision == state.revision &&
            payload.lastUpdate < state.lastUpdate
        ) {
            return
        }
        if (!revisionFilter.accept(payload.revision)) return
        _roomState.value = _roomState.value?.copy(
            currentTrack = payload.currentTrack,
            isPlaying = payload.isPlaying,
            position = payload.position,
            lastUpdate = payload.lastUpdate,
            queue = payload.queue ?: _roomState.value?.queue.orEmpty(),
            volume = payload.volume ?: _roomState.value?.volume ?: 1f,
            revision = maxOf(_roomState.value?.revision ?: 0L, payload.revision),
            queueTitle = payload.queueTitle
        )
        if (_role.value != ListenTogetherRoomRole.GUEST) return
        applyRoomState(_roomState.value ?: return, useBarrier = false)
    }

    private suspend fun applyRoomState(state: RoomState, useBarrier: Boolean) {
        val track = state.currentTrack
        if (track == null) {
            _failedRoomTrack.value = null
            cancelRemotePlayback()
            playback.setPlaying(false)
            applyHostVolume(state.volume)
            return
        }
        if (hasLoadedRoomTrack() || loadingTrackId == track.id) {
            state.queue.let { if (loadingTrackId == null) playback.replaceUpcoming(it) }
            if (loadingTrackId == null) {
                syncPosition(state.position, state.lastUpdate, state.isPlaying)
                playback.setPlaying(state.isPlaying)
            }
        } else {
            startRemotePlayback(
                track,
                state.queue,
                state.position,
                state.isPlaying,
                state.lastUpdate,
                useBarrier
            )
        }
        applyHostVolume(state.volume)
    }

    private fun startRemotePlayback(
        track: TrackInfo,
        queue: List<TrackInfo>?,
        position: Long,
        playing: Boolean,
        effectiveAt: Long?,
        useBarrier: Boolean,
        trackRevision: Long = 0L
    ) {
        remoteJob?.cancel()
        recoveryJob?.cancel()
        val generation = ++playbackGeneration
        barrier = null
        loadingTrackId = track.id
        _failedRoomTrack.value = null
        remoteJob = scope.launch {
            applyingRemote = true
            try {
                playback.setPlaying(false)
                val upcoming = queue
                    ?: playback.upcoming().takeIf { playback.currentTrack()?.id == track.id }
                    ?: emptyList()
                playback.playQueue(track, upcoming, _roomState.value?.queueTitle)
                if (!awaitReady(track.id) || generation != playbackGeneration) {
                    recoverFromLoadFailure(track.id)
                    return@launch
                }
                val latest = _roomState.value?.takeIf { it.currentTrack?.id == track.id }
                val latestPosition = latest?.position ?: position
                val latestTime = latest?.lastUpdate ?: effectiveAt
                val latestPlaying = latest?.isPlaying ?: playing
                if (useBarrier) {
                    barrier = PendingBarrier(
                        track.id,
                        latestPosition,
                        latestTime,
                        latestPlaying,
                        generation
                    )
                    playback.seekTo(adjusted(latestPosition, latestTime, latestPlaying))
                    send(MessageTypes.BUFFER_READY, BufferReadyPayload(track.id, trackRevision))
                    loadingTrackId = null
                    startBarrierTimeout(track.id)
                } else {
                    loadingTrackId = null
                    syncPosition(latestPosition, latestTime, latestPlaying)
                    playback.setPlaying(latestPlaying)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == playbackGeneration) recoverFromLoadFailure(track.id)
            } finally {
                if (generation == playbackGeneration) {
                    applyingRemote = false
                    loadingTrackId = null
                    remoteJob = null
                }
            }
        }
    }

    private fun acknowledgeSameTrack(track: TrackInfo, payload: PlaybackActionPayload) {
        val generation = playbackGeneration
        val room = _roomState.value?.takeIf { it.currentTrack?.id == track.id }
        val position = room?.position ?: payload.position ?: 0L
        val effectiveAt = room?.lastUpdate ?: payload.serverTime
        val playing = room?.isPlaying ?: false
        barrier = PendingBarrier(track.id, position, effectiveAt, playing, generation)
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            if (!awaitReady(track.id)) {
                recoverFromLoadFailure(track.id)
                return@launch
            }
            val pending = barrier?.takeIf { it.trackId == track.id && it.generation == generation }
                ?: return@launch
            playback.seekTo(adjusted(pending.position, pending.effectiveAt, pending.playing))
            playback.setPlaying(false)
            send(MessageTypes.BUFFER_READY, BufferReadyPayload(track.id, payload.revision))
            startBarrierTimeout(track.id)
        }
    }

    private suspend fun awaitReady(trackId: String): Boolean = withTimeoutOrNull(READY_TIMEOUT_MS) {
        while (!playback.isReady(trackId)) {
            if (playback.hasFailed(trackId)) return@withTimeoutOrNull false
            delay(50)
        }
        true
    } == true

    private fun recoverFromLoadFailure(trackId: String) {
        if (loadingTrackId != trackId && barrier?.trackId != trackId) return
        playback.setPlaying(false)
        _failedRoomTrack.value = _roomState.value?.currentTrack?.takeIf { it.id == trackId }
        emitFailure(ListenTogetherEvent.Failure.Key.TrackLoadFailed)
        barrier = null
        loadingTrackId = null
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(1_000)
            if (_role.value == ListenTogetherRoomRole.GUEST && _isInRoom.value) requestSync()
        }
    }

    private fun startBarrierTimeout(trackId: String) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(BARRIER_TIMEOUT_MS)
            if (barrier?.trackId == trackId) {
                barrier = null
                requestSync()
            }
        }
    }

    private fun updateBarrier(position: Long?, effectiveAt: Long?, playing: Boolean?): Boolean {
        val pending = barrier ?: return false
        barrier = pending.copy(
            position = position ?: pending.position,
            effectiveAt = effectiveAt ?: pending.effectiveAt,
            playing = playing ?: pending.playing
        )
        return true
    }

    private fun completeBarrier(trackId: String) {
        val pending = barrier?.takeIf { it.trackId == trackId && it.generation == playbackGeneration }
            ?: return
        recoveryJob?.cancel()
        recoveryJob = null
        barrier = null
        if (pending.playing) syncPosition(pending.position, pending.effectiveAt, true)
        else playback.seekTo(adjusted(pending.position, pending.effectiveAt, false))
        playback.setPlaying(pending.playing)
    }

    private fun syncPosition(position: Long, effectiveAt: Long?, playing: Boolean) {
        val target = adjusted(position, effectiveAt, playing)
        if (needsSeek(playback.positionMs(), target, if (playing) 750L else 100L)) {
            playback.seekTo(target)
        }
    }

    private fun adjusted(position: Long, effectiveAt: Long?, playing: Boolean) =
        clock.positionAt(position, effectiveAt, playing, playback.durationMs())

    private fun applyHostVolume(volume: Float?) {
        if (volume == null || !ListenTogetherPreferences.syncHostVolume) return
        playback.setVolume(volume.coerceIn(0f, 1f))
    }

    private fun hasLoadedRoomTrack(): Boolean {
        val trackId = _roomState.value?.currentTrack?.id ?: return false
        return trackId == playback.currentTrack()?.id && !playback.hasFailed(trackId)
    }

    private fun cancelRemotePlayback() {
        ++playbackGeneration
        remoteJob?.cancel()
        remoteJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        barrier = null
        loadingTrackId = null
        applyingRemote = false
    }

    // ---- host publication -----------------------------------------------------------------

    private fun resetHostObservation() {
        lastTrackId = null
        lastPlaying = null
        lastPositionBucket = null
        lastQueue = null
        lastQueueTitle = null
        lastVolume = null
        lastPausedPosition = 0L
    }

    private val publishMutex = Mutex()

    private suspend fun publishHostPlayback() = publishMutex.withLock {
        if (_role.value != ListenTogetherRoomRole.HOST || !_isInRoom.value || applyingRemote) {
            return@withLock
        }
        if (socket == null || _connectionState.value != ListenTogetherConnectionState.CONNECTED ||
            authenticatedGeneration != connectionGeneration
        ) {
            return@withLock
        }
        val track = playback.currentTrack()
        val trackId = track?.id
        val playing = playback.isPlaying()
        val position = playback.positionMs()
        val upcoming = playback.upcoming()
        val title = playback.queueTitle()
        val sequence = ++hostSequence
        val previousTrackId = lastTrackId ?: _roomState.value?.currentTrack?.id

        _roomState.value = _roomState.value?.copy(
            currentTrack = track,
            isPlaying = playing,
            position = position,
            lastUpdate = clock.serverTimeNow() ?: System.currentTimeMillis(),
            volume = playback.volume(),
            queue = upcoming,
            queueTitle = title
        )

        if (trackId == null && previousTrackId != null) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        PlaybackActions.PAUSE,
                        previousTrackId,
                        position,
                        hostSequence = sequence
                    )
                )
            ) {
                lastTrackId = null
                lastPlaying = false
                lastPositionBucket = null
            }
        } else if (trackId != null && trackId != lastTrackId) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        action = PlaybackActions.CHANGE_TRACK,
                        trackId = trackId,
                        position = position,
                        trackInfo = track,
                        queue = upcoming,
                        queueTitle = title,
                        replaceQueue = true
                    )
                )
            ) {
                lastQueue = upcoming
                lastQueueTitle = title
                lastTrackId = trackId
                lastPlaying = null
                lastPositionBucket = null
            }
        }

        if (upcoming != lastQueue || title != lastQueueTitle) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        PlaybackActions.SYNC_QUEUE,
                        trackId = trackId,
                        queue = upcoming,
                        queueTitle = title
                    )
                )
            ) {
                lastQueue = upcoming
                lastQueueTitle = title
            }
        }

        val volume = playback.volume()
        if (lastVolume == null || abs(lastVolume!! - volume) > 0.01f) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(PlaybackActions.SET_VOLUME, volume = volume)
                )
            ) {
                lastVolume = volume
            }
        }

        val bucket = position / 3_000L
        if (trackId != null && lastPlaying != playing) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        if (playing) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                        trackId,
                        position,
                        hostSequence = sequence
                    )
                )
            ) {
                lastPlaying = playing
                lastPausedPosition = position
            }
        } else if (trackId != null && playing && lastPositionBucket != bucket) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        PlaybackActions.PLAY,
                        trackId,
                        position,
                        hostSequence = sequence
                    )
                )
            ) {
                lastPositionBucket = bucket
            }
        } else if (trackId != null && !playing && abs(position - lastPausedPosition) > 1_000L) {
            if (sendPlaybackAction(
                    PlaybackActionPayload(
                        PlaybackActions.SEEK,
                        trackId,
                        position,
                        hostSequence = sequence
                    )
                )
            ) {
                lastPausedPosition = position
            }
        }
    }

    /** Guests cannot drive playback; undo local changes that diverge from the room. */
    private fun reassertGuestPlayback() {
        val state = _roomState.value ?: return
        if (loadingTrackId != null || barrier != null || applyingRemote) return
        val track = state.currentTrack ?: return
        if (!hasLoadedRoomTrack()) {
            if (playback.currentTrack()?.id != track.id) requestSync()
            return
        }
        if (playback.isPlaying() != state.isPlaying && playback.isReady(track.id)) {
            playback.setPlaying(state.isPlaying)
        }
    }

    private fun startObservers() {
        observerJob?.cancel()
        observerJob = scope.launch {
            while (isActive) {
                delay(OBSERVER_INTERVAL_MS)
                if (!_isInRoom.value) continue
                when (_role.value) {
                    ListenTogetherRoomRole.HOST -> publishHostPlayback()
                    ListenTogetherRoomRole.GUEST -> reassertGuestPlayback()
                    ListenTogetherRoomRole.NONE -> Unit
                }
            }
        }
    }

    // ---- room lifecycle -------------------------------------------------------------------

    private fun markRoomActive() {
        _isInRoom.value = true
        _roomRequestPending.value = false
        _failedRoomTrack.value = null
        reconnectAttempts = 0
        startObservers()
    }

    private fun claimRoomRequest(): Boolean {
        val expected = awaitingRoomResponse || _roomRequestPending.value
        if (!expected) return false
        awaitingRoomResponse = false
        requestTimeoutJob?.cancel()
        _roomRequestPending.value = false
        return true
    }

    private fun clearRoomRequest() {
        pendingAction = null
        awaitingRoomResponse = false
        requestedRoomCode = null
        requestTimeoutJob?.cancel()
        _roomRequestPending.value = false
    }

    private fun cleanupRoom(clearSession: Boolean) {
        cancelRemotePlayback()
        observerJob?.cancel()
        observerJob = null
        clearRoomRequest()
        _isInRoom.value = false
        _role.value = ListenTogetherRoomRole.NONE
        _roomState.value = null
        _userId.value = null
        _pendingJoinRequests.value = emptyList()
        _pendingSuggestions.value = emptyList()
        _submittedSuggestions.value = emptyMap()
        _failedRoomTrack.value = null
        outgoingSuggestions.clear()
        autoApproving.clear()
        resetHostObservation()
        if (clearSession) {
            sessionToken = null
            persistSession(false, null)
        }
    }

    private fun persistSession(isHost: Boolean, roomCode: String?) {
        ListenTogetherPreferences.sessionToken = sessionToken.orEmpty()
        ListenTogetherPreferences.sessionRoomCode = roomCode.orEmpty()
        ListenTogetherPreferences.sessionIsHost = isHost
    }

    private fun restoreSession() {
        if (!ListenTogetherPreferences.enabled || sessionToken == null) return
        // Session tokens are only used to reconnect; the server answers with the room state.
        scope.launch { connect() }
    }

    private fun dropSuggestion(id: String) {
        _pendingSuggestions.value = _pendingSuggestions.value.filterNot { it.id == id }
    }

    private fun emitNotice(key: ListenTogetherEvent.Notice.Key) {
        _events.tryEmit(ListenTogetherEvent.Notice(key))
    }

    private fun emitFailure(key: ListenTogetherEvent.Failure.Key, reason: String? = null) {
        _events.tryEmit(ListenTogetherEvent.Failure(key, reason))
    }

    private fun buildServers(): List<ListenTogetherServer> =
        listOf(ListenTogetherServer("The Meowery", DEFAULT_SERVER_URL)) +
            parseSet(ListenTogetherPreferences.customServers)
                .filterNot { it == DEFAULT_SERVER_URL }
                .map { ListenTogetherServer(it.removePrefix("wss://"), it, isCustom = true) }

    private fun parseSet(raw: String): Set<String> =
        raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
