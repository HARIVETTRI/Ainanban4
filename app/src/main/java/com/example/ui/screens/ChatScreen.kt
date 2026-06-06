package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.ChatMessage
import com.example.ui.theme.EmotionStyles
import com.example.ui.theme.EmotionTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.UserViewModel
import com.example.ui.voice.VoiceManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userViewModel: UserViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by userViewModel.currentUser.collectAsState()
    val activeSession by chatViewModel.activeSession.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val apiKeys by userViewModel.apiKeys.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    val companionName = currentUser?.displayName ?: "Companion"
    val companionPersonality = currentUser?.preferredPersonality ?: "Supportive"

    val activeEmotionName by chatViewModel.lastDetectedEmotion.collectAsState()
    val activeEmotionTheme = remember(activeEmotionName) { EmotionStyles.getTheme(activeEmotionName) }

    val context = LocalContext.current
    val voiceManager = remember { VoiceManager(context) }
    
    var isTtsEnabled by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        chatViewModel.speechEvent.collect { text ->
            if (isTtsEnabled) {
                voiceManager.speak(text)
            }
        }
    }

    val sttState by voiceManager.sttState.collectAsState()
    val realtimeText by voiceManager.realtimeInputText.collectAsState()

    LaunchedEffect(sttState) {
        when (val state = sttState) {
            is VoiceManager.SttState.FinalResult -> {
                if (state.text.isNotEmpty()) {
                    messageText = state.text
                    chatViewModel.sendMessage(
                        content = state.text,
                        friendName = companionName,
                        personality = companionPersonality,
                        userDisplayName = currentUser?.displayName ?: "Friend",
                        customKeys = apiKeys
                    )
                    messageText = ""
                    voiceManager.resetSttState()
                }
            }
            else -> {}
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening()
        } else {
            Toast.makeText(context, "Microphone permission is required to speak your message", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    // Scroll to the latest message turn automatically
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(activeEmotionTheme.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val companionAvatarUrl = currentUser?.avatarChoice ?: ""
                            if (companionAvatarUrl.startsWith("http")) {
                                AsyncImage(
                                    model = companionAvatarUrl,
                                    contentDescription = "Companion Custom Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = activeEmotionTheme.emoji,
                                    fontSize = 22.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                companionName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "$companionPersonality • Aura: ${activeEmotionTheme.name}",
                                fontSize = 11.sp,
                                color = activeEmotionTheme.color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("chat_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isTtsEnabled = !isTtsEnabled
                            if (!isTtsEnabled) {
                                voiceManager.stopSpeaking()
                            }
                        },
                        modifier = Modifier.testTag("tts_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle TTS",
                            tint = activeEmotionTheme.color
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = activeEmotionTheme.color.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, activeEmotionTheme.color.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "TFLite Active",
                                color = activeEmotionTheme.color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Screen messages view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    EmptyChatGreeting(companionName, activeEmotionTheme)
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                companionName = companionName,
                                onPlayVoice = { text -> voiceManager.speak(text) }
                            )
                        }

                        if (isGenerating) {
                            item {
                                CompanionTypingBubble(companionName)
                            }
                        }
                    }
                }
            }

            // Input Dock
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isListening = sttState is VoiceManager.SttState.Listening || sttState is VoiceManager.SttState.Processing

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = {
                            if (isListening) {
                                Text(
                                    text = if (realtimeText.isNotEmpty()) realtimeText else "Listening...",
                                    color = activeEmotionTheme.color,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text("Reply back like a friend...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeEmotionTheme.color,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasPermission) {
                                    voiceManager.startListening()
                                } else {
                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFFEF5350) else activeEmotionTheme.color.copy(alpha = 0.15f)
                            )
                            .size(44.dp)
                            .testTag("chat_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Speak Message",
                            tint = if (isListening) Color.White else activeEmotionTheme.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                chatViewModel.sendMessage(
                                    content = messageText,
                                    friendName = companionName,
                                    personality = companionPersonality,
                                    userDisplayName = currentUser?.displayName ?: "Friend",
                                    customKeys = apiKeys
                                )
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(activeEmotionTheme.color)
                            .size(44.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(
    companionName: String,
    theme: EmotionTheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(theme.color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(theme.emoji, fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connected with $companionName",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = theme.description,
            color = theme.color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "\"${theme.baselineQuote}\"",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    companionName: String,
    onPlayVoice: ((String) -> Unit)? = null
) {
    val isUser = message.sender == "user"
    val emotionTheme = EmotionStyles.getTheme(message.detectedEmotion)
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { formatter.format(Date(message.timestamp)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Display Sender and custom API Badge if response turn loaded
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp, start = 6.dp, end = 6.dp)
            ) {
                Text(
                    text = if (isUser) "You" else companionName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = message.apiUsed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (message.apiUsed == "Gemini") MaterialTheme.colorScheme.primary else Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (onPlayVoice != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onPlayVoice(message.content) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Play message audio",
                                tint = emotionTheme.color,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Message Bubble
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFFEADDFF)
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = if (isUser) null else BorderStroke(
                    width = 1.dp,
                    color = emotionTheme.color.copy(alpha = 0.35f)
                ),
                modifier = Modifier.widthIn(max = 280.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else Color(0xFF21005D),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Timestamp
            Text(
                text = "$timeString • ${emotionTheme.emoji} ${emotionTheme.name}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun CompanionTypingBubble(companionName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotAnimation1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = "$companionName is composing thoughts...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp, start = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = dotAnimation1)))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = (dotAnimation1 * 0.7f).coerceIn(0.1f, 1f))))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = (dotAnimation1 * 0.4f).coerceIn(0.1f, 1f))))
                }
            }
        }
    }
}
