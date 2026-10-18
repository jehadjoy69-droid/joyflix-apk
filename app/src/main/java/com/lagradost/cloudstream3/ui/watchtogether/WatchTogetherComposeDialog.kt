package com.lagradost.cloudstream3.ui.watchtogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.WatchTogetherManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WatchTogetherComposeDialog : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogFullscreenPlayer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFFE50914),
                        surface = Color(0xFF1E1E1E),
                        background = Color(0xFF121212)
                    )
                ) {
                    WatchTogetherScreenContent(
                        onDismiss = { dismissAllowingStateLoss() },
                        onOpenPlayer = { streamUrl, title ->
                            dismissAllowingStateLoss()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "WatchTogetherComposeDialog"

        var mediaContext: WatchTogetherMediaContext? = null
        var onSyncRoomPlayback: ((WatchTogetherManager.RoomData) -> Unit)? = null

        fun show(
            fragmentManager: FragmentManager,
            context: WatchTogetherMediaContext? = null,
            onSyncRoom: ((WatchTogetherManager.RoomData) -> Unit)? = null
        ) {
            mediaContext = context
            onSyncRoomPlayback = onSyncRoom
            val existing = fragmentManager.findFragmentByTag(TAG) as? DialogFragment
            if (existing != null && existing.isAdded) return
            WatchTogetherComposeDialog().show(fragmentManager, TAG)
        }
    }
}

data class WatchTogetherMediaContext(
    val title: String? = null,
    val streamUrl: String? = null,
    val mediaUrl: String? = null,
    val apiName: String? = null,
    val episodeId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val poster: String? = null,
    val tvType: String? = null,
    val currentPos: Long = 0L,
    val isPlaying: Boolean = false,
    val episodeIndex: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchTogetherScreenContent(
    onDismiss: () -> Unit,
    onOpenPlayer: (streamUrl: String?, title: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State polling to keep Compose UI in sync with WatchTogetherManager
    var isInRoom by remember { mutableStateOf(WatchTogetherManager.isInRoom) }
    var currentRoom by remember { mutableStateOf(WatchTogetherManager.currentRoom) }
    var isVoiceActive by remember { mutableStateOf(WatchTogetherManager.isVoiceCallActive) }
    var chatList by remember { mutableStateOf(WatchTogetherManager.chatHistory.toList()) }

    LaunchedEffect(Unit) {
        while (true) {
            isInRoom = WatchTogetherManager.isInRoom
            currentRoom = WatchTogetherManager.currentRoom
            isVoiceActive = WatchTogetherManager.isVoiceCallActive
            chatList = WatchTogetherManager.chatHistory.toList()
            delay(500)
        }
    }

    val savedNick = remember { WatchTogetherManager.getSavedNickname(context).ifBlank { "User" } }
    var nicknameInput by remember { mutableStateOf(savedNick) }
    var roomIdInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val primaryRed = Color(0xFFE50914)
    val cardDark = Color(0xFF1E1E1E)
    val bgDark = Color(0xFF121212)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(16.dp)
    ) {
        // Top Close Button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
        ) {
            Icon(painter = painterResource(R.drawable.ic_baseline_close_24), contentDescription = "Close", tint = Color.White)
        }

        if (!isInRoom) {
            // ================= LOBBY SCREEN (CREATE OR JOIN) =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_people_24),
                    contentDescription = null,
                    tint = primaryRed,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "JoyFlix Watch Together",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Watch movies and series simultaneously with friends with live chat and synchronized playback.",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = {
                        nicknameInput = it
                        WatchTogetherManager.saveNickname(context, it)
                    },
                    label = { Text("Your Nickname") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryRed,
                        focusedLabelColor = primaryRed,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Create Room Button
                Button(
                    onClick = {
                        val nick = nicknameInput.trim().ifBlank { "Host" }
                        WatchTogetherManager.saveNickname(context, nick)
                        isLoading = true
                        statusMessage = "Creating room on JoyCall..."
                        coroutineScope.launch {
                            try {
                                val ctx = WatchTogetherComposeDialog.mediaContext
                                val result = WatchTogetherManager.createRoom(
                                    nickname = nick,
                                    title = ctx?.title,
                                    streamUrl = ctx?.streamUrl,
                                    mediaUrl = ctx?.mediaUrl,
                                    apiName = ctx?.apiName,
                                    episodeId = ctx?.episodeId,
                                    episode = ctx?.episode,
                                    season = ctx?.season,
                                    poster = ctx?.poster,
                                    tvType = ctx?.tvType,
                                    currentPos = ctx?.currentPos ?: 0L,
                                    isPlaying = ctx?.isPlaying ?: false,
                                    episodeIndex = ctx?.episodeIndex
                                )
                                isLoading = false
                                result.onSuccess {
                                    isInRoom = true
                                    currentRoom = WatchTogetherManager.currentRoom
                                    statusMessage = "Room created: $it"
                                }.onFailure { err ->
                                    statusMessage = "Error: ${err.message}"
                                }
                            } catch (e: Throwable) {
                                isLoading = false
                                statusMessage = "Error: ${e.message}"
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_baseline_add_24), contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Watch Party", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                    Text(
                        text = " OR JOIN ",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                }

                // Join Room Input & Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = roomIdInput,
                        onValueChange = { roomIdInput = it.uppercase() },
                        label = { Text("Room Code (e.g. JOY-1234)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryRed,
                            focusedLabelColor = primaryRed,
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val code = roomIdInput.trim()
                            val nick = nicknameInput.trim().ifBlank { "Guest" }
                            if (code.isNotBlank()) {
                                WatchTogetherManager.saveNickname(context, nick)
                                isLoading = true
                                statusMessage = "Joining $code..."
                                coroutineScope.launch {
                                    try {
                                        WatchTogetherManager.joinRoom(
                                            inputCode = code,
                                            nickname = nick,
                                            onRoomLoaded = { loadedRoom ->
                                                isLoading = false
                                                isInRoom = true
                                                currentRoom = WatchTogetherManager.currentRoom
                                                statusMessage = "Connected!"
                                                WatchTogetherComposeDialog.onSyncRoomPlayback?.invoke(loadedRoom)
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                statusMessage = "Error: $err"
                                            }
                                        )
                                    } catch (e: Throwable) {
                                        isLoading = false
                                        statusMessage = "Error: ${e.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_baseline_play_arrow_24), contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Join", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        color = Color.Yellow,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(color = primaryRed, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            // ================= ACTIVE ROOM SCREEN =================
            val room = currentRoom
            val chatScrollState = rememberLazyListState()

            // Scroll chat to bottom on new messages
            LaunchedEffect(chatList.size) {
                if (chatList.isNotEmpty()) {
                    chatScrollState.animateScrollToItem(chatList.size - 1)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Room Details & Member List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Room Info Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("JoyFlix Room", room?.roomId ?: "")
                                        clipboard?.setPrimaryClip(clip)
                                        showToast(R.string.copy_link_toast)
                                    }
                                ) {
                                    Text(
                                        text = "Room Code (Tap to copy)",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = room?.roomId ?: WatchTogetherManager.currentRoomId ?: "",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryRed
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Voice Chat Toggle
                                    FilledIconToggleButton(
                                        checked = isVoiceActive,
                                        onCheckedChange = { active ->
                                            isVoiceActive = active
                                            WatchTogetherManager.setVoiceStatus(active)
                                        },
                                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                                            checkedContainerColor = Color(0xFF2E7D32),
                                            containerColor = Color.DarkGray
                                        )
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isVoiceActive) R.drawable.ic_baseline_mic_24 else R.drawable.ic_baseline_mic_off_24),
                                            contentDescription = "Voice Call",
                                            tint = Color.White
                                        )
                                    }

                                    // Leave Room Button
                                    Button(
                                        onClick = {
                                            WatchTogetherManager.leaveRoom()
                                            isInRoom = false
                                            currentRoom = null
                                            chatList = emptyList()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(painter = painterResource(R.drawable.ic_baseline_close_24), contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Leave", color = Color.White)
                                    }
                                }
                            }

                            if (!room?.title.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Now Playing: ${room?.title}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }

                            if (!room?.apiName.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Provider: ${room?.apiName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64B5F6)
                                )
                            }

                            if (!room?.streamUrl.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        onOpenPlayer(room?.streamUrl, room?.title)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_baseline_play_arrow_24), contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Player & Sync", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Members List Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val members = room?.members ?: emptyList()
                            Text(
                                text = "Members (${members.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(members) { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (member.isHost) primaryRed else Color.Gray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = member.name.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = member.name + if (member.userId == WatchTogetherManager.myUserId) " (You)" else "",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (member.isHost) {
                                                Text(
                                                    text = "HOST",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryRed,
                                                    modifier = Modifier
                                                        .background(primaryRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Icon(
                                                painter = painterResource(if (member.voiceActive) R.drawable.ic_baseline_mic_24 else R.drawable.ic_baseline_mic_off_24),
                                                contentDescription = null,
                                                tint = if (member.voiceActive) Color.Green else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Live Chat
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Room Live Chat",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            state = chatScrollState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chatList) { msg ->
                                val isMe = msg.senderId == WatchTogetherManager.myUserId
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                ) {
                                    Text(
                                        text = msg.senderName,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isMe) primaryRed else Color(0xFF333333),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = msg.text, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = { Text("Type a message...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryRed,
                                    unfocusedTextColor = Color.White,
                                    focusedTextColor = Color.White
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    val text = chatInput.trim()
                                    if (text.isNotBlank()) {
                                        val nick = nicknameInput.trim().ifBlank { "User" }
                                        WatchTogetherManager.sendMessage(text = text, senderNickname = nick)
                                        chatInput = ""
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = primaryRed)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_baseline_send_24), contentDescription = "Send", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
