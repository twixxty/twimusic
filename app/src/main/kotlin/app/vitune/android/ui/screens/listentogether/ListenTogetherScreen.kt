package app.vitune.android.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.listentogether.ListenTogetherConnectionState
import app.vitune.android.ui.screens.searchRoute
import app.vitune.android.listentogether.ListenTogetherEvent
import app.vitune.android.listentogether.ListenTogetherRoomRole
import app.vitune.android.preferences.ListenTogetherPreferences
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.bold
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import kotlinx.coroutines.launch

private const val ROOM_CODE_LENGTH = 8

@Route
@Composable
fun ListenTogetherScreen(
    onBack: () -> Unit,
    initialRoomCode: String? = null
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val manager = binder?.listenTogether
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var showingSettings by remember { mutableStateOf(false) }

    if (showingSettings) {
        BackHandler { showingSettings = false }
        ListenTogetherSettingsScreen(onBack = { showingSettings = false })
        return
    }

    if (manager == null) {
        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .padding(windowInsets.asPaddingValues())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    icon = R.drawable.chevron_back,
                    color = colorPalette.text
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = stringResource(R.string.listen_together),
                    style = typography.xxl.bold.copy(color = colorPalette.text)
                )
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    text = stringResource(R.string.listen_together_service_unavailable),
                    style = typography.m.secondary
                )
            }
        }
        return
    }

    val enabled = ListenTogetherPreferences.enabled
    val connection by manager.connectionState.collectAsState()
    val room by manager.roomState.collectAsState()
    val role by manager.role.collectAsState()
    val myId by manager.userId.collectAsState()
    val joinRequests by manager.pendingJoinRequests.collectAsState()
    val suggestions by manager.pendingSuggestions.collectAsState()
    val submittedSuggestions by manager.submittedSuggestions.collectAsState()
    val pendingRequest by manager.roomRequestPending.collectAsState()

    var isJoinTab by remember { mutableStateOf(!initialRoomCode.isNullOrBlank()) }
    var roomCodeInput by remember { mutableStateOf(initialRoomCode.orEmpty()) }
    var editingUsername by remember { mutableStateOf(false) }

    // Toast event notifications
    LaunchedEffect(manager) {
        manager.events.collect { event ->
            val message = when (event) {
                is ListenTogetherEvent.Notice -> when (event.key) {
                    ListenTogetherEvent.Notice.Key.RoomCreated -> R.string.lt_room_created
                    ListenTogetherEvent.Notice.Key.JoinedRoom -> R.string.lt_joined_room
                    ListenTogetherEvent.Notice.Key.Reconnected -> R.string.lt_reconnected
                    ListenTogetherEvent.Notice.Key.BecameHost -> R.string.lt_became_host
                    ListenTogetherEvent.Notice.Key.HostChanged -> R.string.lt_host_changed
                    ListenTogetherEvent.Notice.Key.SuggestionSubmitted -> R.string.lt_suggestion_submitted
                    ListenTogetherEvent.Notice.Key.SuggestionApproved -> R.string.lt_suggestion_approved
                    ListenTogetherEvent.Notice.Key.SuggestionRejected -> R.string.lt_suggestion_rejected
                }
                is ListenTogetherEvent.Failure -> when (event.key) {
                    ListenTogetherEvent.Failure.Key.ConnectionFailed -> R.string.lt_connection_failed
                    ListenTogetherEvent.Failure.Key.JoinRejected -> R.string.lt_join_rejected
                    ListenTogetherEvent.Failure.Key.Kicked -> R.string.lt_kicked
                    ListenTogetherEvent.Failure.Key.ServerError -> R.string.lt_server_error
                    ListenTogetherEvent.Failure.Key.QueueTooLarge -> R.string.lt_queue_too_large
                    ListenTogetherEvent.Failure.Key.TrackLoadFailed -> R.string.lt_track_load_failed
                }
                is ListenTogetherEvent.Reconnecting -> null
                is ListenTogetherEvent.SuggestionAutoApproved -> R.string.lt_suggestion_approved
            }
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (editingUsername) {
        TextFieldDialog(
            hintText = stringResource(R.string.lt_username_hint),
            initialTextInput = ListenTogetherPreferences.username,
            onDismiss = { editingUsername = false },
            onAccept = { newName ->
                val trimmed = newName.trim()
                ListenTogetherPreferences.username = trimmed
                manager.setUsername(trimmed)
            },
            isTextInputValid = { it.isNotBlank() }
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(windowInsets.asPaddingValues())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Top app bar back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                icon = R.drawable.chevron_back,
                color = colorPalette.text
            )
        }

        // Header matching Screenshots 2 & 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Listen\nTogether",
                    style = typography.xxl.bold.copy(
                        color = colorPalette.text,
                        fontSize = 32.sp,
                        lineHeight = 36.sp
                    )
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = stringResource(R.string.lt_header_subtitle),
                    style = typography.s.secondary
                )
            }

            // Decorative wavy badge icon on top right
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colorPalette.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.group),
                    contentDescription = null,
                    tint = colorPalette.accent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val currentRoom = room
        if (currentRoom != null) {
            // IN ROOM VIEW
            RoomActiveContent(
                room = currentRoom,
                role = role,
                myId = myId,
                connectionState = connection,
                joinRequests = joinRequests,
                suggestions = suggestions,
                submittedSuggestions = submittedSuggestions,
                manager = manager,
                onNavigateToSettings = { showingSettings = true },
                colorPalette = colorPalette,
                typography = typography,
                context = context
            )
        } else {
            // NOT IN ROOM VIEW: Host / Join tabs
            HostJoinSegmentedPill(
                isJoinTab = isJoinTab,
                onTabSelect = { isJoinTab = it },
                colorPalette = colorPalette,
                typography = typography
            )

            Spacer(Modifier.height(28.dp))

            if (!isJoinTab) {
                // HOST TAB
                HostTabContent(
                    username = ListenTogetherPreferences.username,
                    pendingRequest = pendingRequest,
                    connectionState = connection,
                    onEditUsername = { editingUsername = true },
                    onCreateRoom = { manager.createRoom() },
                    onNavigateToSettings = { showingSettings = true },
                    colorPalette = colorPalette,
                    typography = typography
                )
            } else {
                // JOIN TAB
                JoinTabContent(
                    username = ListenTogetherPreferences.username,
                    roomCode = roomCodeInput,
                    onRoomCodeChange = { code ->
                        roomCodeInput = code.filter { it.isLetterOrDigit() }.take(ROOM_CODE_LENGTH).uppercase()
                    },
                    pendingRequest = pendingRequest,
                    connectionState = connection,
                    onEditUsername = { editingUsername = true },
                    onJoinRoom = {
                        if (roomCodeInput.length == ROOM_CODE_LENGTH) {
                            manager.joinRoom(roomCodeInput)
                        }
                    },
                    onCancelJoin = { manager.cancelJoinRequest() },
                    onNavigateToSettings = { showingSettings = true },
                    colorPalette = colorPalette,
                    typography = typography
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HostJoinSegmentedPill(
    isJoinTab: Boolean,
    onTabSelect: (Boolean) -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colorPalette.background1)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Host pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (!isJoinTab) colorPalette.accent.copy(alpha = 0.35f) else Color.Transparent)
                .clickable { onTabSelect(false) },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.lt_host_tab),
                style = typography.m.semiBold.copy(
                    color = if (!isJoinTab) colorPalette.text else colorPalette.textSecondary
                )
            )
        }

        // Join pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isJoinTab) colorPalette.accent.copy(alpha = 0.35f) else Color.Transparent)
                .clickable { onTabSelect(true) },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.lt_join_tab),
                style = typography.m.semiBold.copy(
                    color = if (isJoinTab) colorPalette.text else colorPalette.textSecondary
                )
            )
        }
    }
}

@Composable
private fun UserProfileCard(
    username: String,
    onEdit: () -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    val displayInitial = username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colorPalette.background1
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorPalette.accent.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = displayInitial,
                    style = typography.l.bold.copy(color = colorPalette.text)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = username.ifBlank { "Android user" },
                    style = typography.m.semiBold.copy(color = colorPalette.text),
                    maxLines = 1
                )
                BasicText(
                    text = stringResource(R.string.username),
                    style = typography.xs.secondary
                )
            }

            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorPalette.text
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.pencil),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorPalette.text
                )
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = stringResource(R.string.lt_edit),
                    style = typography.xs.semiBold.copy(color = colorPalette.text)
                )
            }
        }
    }
}

@Composable
private fun SettingsCardButton(
    onClick: () -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colorPalette.background1
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.settings),
                contentDescription = null,
                tint = colorPalette.textSecondary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = stringResource(R.string.settings),
                    style = typography.m.semiBold.copy(color = colorPalette.text)
                )
                BasicText(
                    text = stringResource(R.string.lt_setup_settings),
                    style = typography.xs.secondary
                )
            }

            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                tint = colorPalette.textDisabled,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HostTabContent(
    username: String,
    pendingRequest: Boolean,
    connectionState: ListenTogetherConnectionState,
    onEditUsername: () -> Unit,
    onCreateRoom: () -> Unit,
    onNavigateToSettings: () -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                text = stringResource(R.string.lt_host_room),
                style = typography.l.bold.copy(color = colorPalette.text)
            )
            BasicText(
                text = stringResource(R.string.lt_host_room_desc),
                style = typography.s.secondary
            )
        }

        UserProfileCard(
            username = username,
            onEdit = onEditUsername,
            colorPalette = colorPalette,
            typography = typography
        )

        // Create Room primary pill button
        Button(
            onClick = onCreateRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette.accent.copy(alpha = 0.4f),
                contentColor = colorPalette.text
            ),
            enabled = connectionState != ListenTogetherConnectionState.CONNECTING
        ) {
            BasicText(
                text = stringResource(R.string.lt_create_room),
                style = typography.m.semiBold.copy(color = colorPalette.text)
            )
        }

        Spacer(Modifier.height(8.dp))

        SettingsCardButton(
            onClick = onNavigateToSettings,
            colorPalette = colorPalette,
            typography = typography
        )
    }
}

@Composable
private fun JoinTabContent(
    username: String,
    roomCode: String,
    onRoomCodeChange: (String) -> Unit,
    pendingRequest: Boolean,
    connectionState: ListenTogetherConnectionState,
    onEditUsername: () -> Unit,
    onJoinRoom: () -> Unit,
    onCancelJoin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                text = stringResource(R.string.lt_join_room_title),
                style = typography.l.bold.copy(color = colorPalette.text)
            )
            BasicText(
                text = stringResource(R.string.lt_join_room_desc),
                style = typography.s.secondary
            )
        }

        UserProfileCard(
            username = username,
            onEdit = onEditUsername,
            colorPalette = colorPalette,
            typography = typography
        )

        // 8 PIN-Style Code Input Boxes matching Screenshot 2
        RoomCodePinBoxes(
            code = roomCode,
            onCodeChange = onRoomCodeChange,
            colorPalette = colorPalette,
            typography = typography
        )

        if (pendingRequest) {
            BasicText(
                text = stringResource(R.string.lt_waiting_for_approval),
                style = typography.s.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Button(
                onClick = onCancelJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.background1,
                    contentColor = colorPalette.text
                )
            ) {
                BasicText(
                    text = stringResource(R.string.cancel),
                    style = typography.m.semiBold.copy(color = colorPalette.text)
                )
            }
        } else {
            Button(
                onClick = onJoinRoom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.accent.copy(alpha = 0.4f),
                    contentColor = colorPalette.text
                ),
                enabled = roomCode.length == ROOM_CODE_LENGTH && connectionState != ListenTogetherConnectionState.CONNECTING
            ) {
                BasicText(
                    text = stringResource(R.string.lt_join_room),
                    style = typography.m.semiBold.copy(color = colorPalette.text)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SettingsCardButton(
            onClick = onNavigateToSettings,
            colorPalette = colorPalette,
            typography = typography
        )
    }
}

@Composable
private fun RoomCodePinBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Hidden transparent text field to capture input and IME
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(0.01f)
                .onFocusChanged { isFocused = it.isFocused },
            cursorBrush = SolidColor(Color.Transparent)
        )

        // 8 visual PIN boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(ROOM_CODE_LENGTH) { index ->
                val char = code.getOrNull(index)?.toString().orEmpty()
                val isCurrentCell = isFocused && index == code.length.coerceAtMost(ROOM_CODE_LENGTH - 1)
                val isFilled = index < code.length

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCurrentCell) colorPalette.background2 else colorPalette.background1)
                        .border(
                            width = if (isCurrentCell) 2.dp else 1.dp,
                            color = when {
                                isCurrentCell -> colorPalette.accent
                                isFilled -> colorPalette.accent.copy(alpha = 0.6f)
                                else -> colorPalette.background2
                            },
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = char,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colorPalette.text
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomActiveContent(
    room: app.vitune.android.listentogether.network.RoomState,
    role: ListenTogetherRoomRole,
    myId: String?,
    connectionState: ListenTogetherConnectionState,
    joinRequests: List<app.vitune.android.listentogether.ListenTogetherJoinRequest>,
    suggestions: List<app.vitune.android.listentogether.ListenTogetherSuggestion>,
    submittedSuggestions: Map<String, app.vitune.android.listentogether.network.TrackInfo>,
    manager: app.vitune.android.listentogether.ListenTogetherManager,
    onNavigateToSettings: () -> Unit,
    colorPalette: app.vitune.core.ui.ColorPalette,
    typography: app.vitune.core.ui.Typography,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Room Info Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = colorPalette.background1
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText(
                            text = stringResource(R.string.lt_room_code),
                            style = typography.xs.secondary
                        )
                        BasicText(
                            text = room.roomCode,
                            style = typography.xxl.bold.copy(
                                fontFamily = FontFamily.Monospace,
                                color = colorPalette.text,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    // Role pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorPalette.accent.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = if (role == ListenTogetherRoomRole.HOST) stringResource(R.string.lt_host)
                            else stringResource(R.string.lt_guest),
                            style = typography.xs.semiBold.copy(color = colorPalette.accent)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", room.roomCode))
                            Toast.makeText(context, R.string.lt_code_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        BasicText(
                            text = stringResource(R.string.copy),
                            style = typography.xs.semiBold.copy(color = colorPalette.text)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join my Listen Together room on TwiMusic: ${room.roomCode}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        BasicText(
                            text = stringResource(R.string.lt_share_link),
                            style = typography.xs.semiBold.copy(color = colorPalette.text)
                        )
                    }
                }

                if (role == ListenTogetherRoomRole.GUEST) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { manager.requestSync() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPalette.accent.copy(alpha = 0.3f),
                                contentColor = colorPalette.text
                            )
                        ) {
                            BasicText(
                                text = stringResource(R.string.lt_resync),
                                style = typography.xs.semiBold.copy(color = colorPalette.text)
                            )
                        }

                        Button(
                            onClick = { searchRoute.global("") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPalette.accent,
                                contentColor = colorPalette.onAccent
                            )
                        ) {
                            BasicText(
                                text = stringResource(R.string.lt_request_song),
                                style = typography.xs.semiBold.copy(color = colorPalette.onAccent)
                            )
                        }
                    }
                }
            }
        }

        // Pending Join Requests (Host)
        if (role == ListenTogetherRoomRole.HOST && joinRequests.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colorPalette.background1
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        text = "Join Requests (${joinRequests.size})",
                        style = typography.m.semiBold.copy(color = colorPalette.text)
                    )
                    joinRequests.forEach { req ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText(
                                text = req.username,
                                style = typography.s.copy(color = colorPalette.text)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { manager.approveJoin(req.userId) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    BasicText("Approve", style = typography.xxs.semiBold)
                                }
                                OutlinedButton(
                                    onClick = { manager.rejectJoin(req.userId) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    BasicText(stringResource(R.string.lt_reject), style = typography.xxs.semiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pending Track Requests (Host)
        if (role == ListenTogetherRoomRole.HOST && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colorPalette.background1
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        text = stringResource(R.string.lt_pending_suggestions) + " (${suggestions.size})",
                        style = typography.m.semiBold.copy(color = colorPalette.text)
                    )
                    suggestions.forEach { sug ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                BasicText(
                                    text = sug.trackInfo.title,
                                    style = typography.s.semiBold.copy(color = colorPalette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BasicText(
                                    text = "${sug.trackInfo.artist} · from ${sug.fromUsername}",
                                    style = typography.xxs.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { manager.approveSuggestion(sug.id) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    BasicText("Approve", style = typography.xxs.semiBold)
                                }
                                OutlinedButton(
                                    onClick = { manager.rejectSuggestion(sug.id) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    BasicText(stringResource(R.string.lt_reject), style = typography.xxs.semiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Submitted Suggestions / Requested Songs (Guest)
        if (role == ListenTogetherRoomRole.GUEST && submittedSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colorPalette.background1
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        text = stringResource(R.string.lt_requested_songs) + " (${submittedSuggestions.size})",
                        style = typography.m.semiBold.copy(color = colorPalette.text)
                    )
                    submittedSuggestions.forEach { (_, track) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = track.title,
                                    style = typography.s.semiBold.copy(color = colorPalette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BasicText(
                                    text = "${track.artist} · ${stringResource(R.string.lt_waiting_approval)}",
                                    style = typography.xxs.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Participants List
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colorPalette.background1
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(
                    text = "Members (${room.users.size})",
                    style = typography.m.semiBold.copy(color = colorPalette.text)
                )

                room.users.forEach { u ->
                    var userMenuOpen by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = role == ListenTogetherRoomRole.HOST && u.userId != myId) {
                                userMenuOpen = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorPalette.accent.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = u.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                style = typography.s.bold.copy(color = colorPalette.text)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = u.username + if (u.userId == myId) " (${stringResource(R.string.lt_you)})" else "",
                                style = typography.s.semiBold.copy(color = colorPalette.text)
                            )
                            BasicText(
                                text = if (u.isHost) stringResource(R.string.lt_host) else stringResource(R.string.lt_guest),
                                style = typography.xxs.secondary
                            )
                        }

                        if (role == ListenTogetherRoomRole.HOST && u.userId != myId) {
                            Box {
                                IconButton(
                                    onClick = { userMenuOpen = true },
                                    icon = R.drawable.ellipsis_horizontal,
                                    color = colorPalette.textSecondary
                                )
                                DropdownMenu(
                                    expanded = userMenuOpen,
                                    onDismissRequest = { userMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lt_make_host)) },
                                        onClick = {
                                            manager.transferHost(u.userId)
                                            userMenuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lt_kick)) },
                                        onClick = {
                                            manager.kickUser(u.userId)
                                            userMenuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lt_block)) },
                                        onClick = {
                                            manager.blockUser(u.username)
                                            userMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Leave Room button
        Button(
            onClick = { manager.leaveRoom() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette.red.copy(alpha = 0.2f),
                contentColor = colorPalette.red
            )
        ) {
            BasicText(
                text = stringResource(R.string.lt_leave_room),
                style = typography.m.semiBold.copy(color = colorPalette.red)
            )
        }

        SettingsCardButton(
            onClick = onNavigateToSettings,
            colorPalette = colorPalette,
            typography = typography
        )
    }
}
