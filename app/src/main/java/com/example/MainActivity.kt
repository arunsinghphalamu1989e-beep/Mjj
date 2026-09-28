package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.live.SessionState
import com.example.live.ToolEvent
import com.example.ui.MainViewModel
import com.example.ui.OrbVisualizer
import com.example.ui.theme.BrightAzure
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SpaceCard
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceDark
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MjAssistantScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MjAssistantScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val lastTranscript by viewModel.lastTranscript.collectAsState()
    val orbAmplitude by viewModel.orbAmplitude.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isLiveWs by viewModel.isLiveWebSocket.collectAsState()
    val recentTools by viewModel.recentTools.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val autoCommandEnabled by viewModel.autoCommandEnabled.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.toggleSession()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SpaceDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SpaceDark,
                            SpaceBlack,
                            SpaceDark
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Sci-Fi Header with Auto-Command Badge
                TopHeader(
                    sessionState = sessionState,
                    isLiveWs = isLiveWs,
                    autoCommandEnabled = autoCommandEnabled,
                    onToggleAutoCommand = { viewModel.toggleAutoCommand() },
                    onOpenSettings = { showSettingsDialog = true }
                )

                // Status & Sassy Prompt Banner
                StatusBanner(
                    sessionState = sessionState,
                    statusMessage = statusMessage,
                    lastTranscript = lastTranscript
                )

                // Auto Command Executed HUD Notification
                AutoCommandNotification(recentTools = recentTools)

                // Center 3D Glowing Orb Visualizer (matching reference video)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbVisualizer(
                        state = sessionState,
                        amplitude = orbAmplitude,
                        modifier = Modifier.size(310.dp)
                    )
                }

                // Bottom Controls with Auto-Open Action Buttons
                BottomControlPanel(
                    sessionState = sessionState,
                    isMuted = isMuted,
                    amplitude = orbAmplitude,
                    autoCommandEnabled = autoCommandEnabled,
                    onToggleSession = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleSession()
                        }
                    },
                    onToggleMute = { viewModel.toggleMute() },
                    onQuickPrompt = { query ->
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.triggerQuickQuery(query)
                        }
                    },
                    onExecuteAutoCommand = { command, param ->
                        viewModel.executeAutoCommand(command, param)
                    }
                )
            }

            // Settings Dialog
            if (showSettingsDialog) {
                SettingsDialog(
                    currentApiKey = customApiKey,
                    autoCommandEnabled = autoCommandEnabled,
                    onToggleAutoCommand = { viewModel.toggleAutoCommand() },
                    onDismiss = { showSettingsDialog = false },
                    onSave = { newKey ->
                        viewModel.updateApiKey(newKey)
                        showSettingsDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun TopHeader(
    sessionState: SessionState,
    isLiveWs: Boolean,
    autoCommandEnabled: Boolean,
    onToggleAutoCommand: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (sessionState) {
                            SessionState.SPEAKING -> NeonCyan
                            SessionState.LISTENING -> StatusGreen
                            SessionState.CONNECTING -> StatusYellow
                            SessionState.ERROR -> StatusRed
                            SessionState.DISCONNECTED -> TextTertiary
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "MJ AI",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "सस्सी & विट्टी • हिंदी",
                    color = BrightAzure,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Auto-Command Pill & Settings button
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Auto Command Toggle Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (autoCommandEnabled) NeonCyan.copy(alpha = 0.15f) else SpaceCard)
                    .border(
                        1.dp,
                        if (autoCommandEnabled) NeonCyan.copy(alpha = 0.6f) else SpaceCardBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onToggleAutoCommand() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("auto_command_toggle_badge")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Auto Command",
                        tint = if (autoCommandEnabled) NeonCyan else TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (autoCommandEnabled) "AUTO OPEN" else "MANUAL",
                        color = if (autoCommandEnabled) NeonCyan else TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpaceCard)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AutoCommandNotification(recentTools: List<ToolEvent>) {
    AnimatedVisibility(
        visible = recentTools.isNotEmpty(),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        val lastTool = recentTools.firstOrNull()
        if (lastTool != null) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = SpaceCard.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Executed",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "⚡ AUTOMATICALLY OPENED",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = lastTool.description,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBanner(
    sessionState: SessionState,
    statusMessage: String,
    lastTranscript: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when (sessionState) {
                        SessionState.SPEAKING -> NeonCyan.copy(alpha = 0.15f)
                        SessionState.LISTENING -> StatusGreen.copy(alpha = 0.15f)
                        SessionState.CONNECTING -> StatusYellow.copy(alpha = 0.15f)
                        SessionState.ERROR -> StatusRed.copy(alpha = 0.15f)
                        SessionState.DISCONNECTED -> SpaceCard
                    }
                )
                .border(
                    width = 1.dp,
                    color = when (sessionState) {
                        SessionState.SPEAKING -> NeonCyan.copy(alpha = 0.4f)
                        SessionState.LISTENING -> StatusGreen.copy(alpha = 0.4f)
                        SessionState.CONNECTING -> StatusYellow.copy(alpha = 0.4f)
                        SessionState.ERROR -> StatusRed.copy(alpha = 0.4f)
                        SessionState.DISCONNECTED -> SpaceCardBorder
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = when (sessionState) {
                    SessionState.SPEAKING -> "MJ bol rahi hai ⚡"
                    SessionState.LISTENING -> "MJ sun rahi hai 🎙️"
                    SessionState.CONNECTING -> "Connecting..."
                    SessionState.ERROR -> "Connection Failed"
                    SessionState.DISCONNECTED -> "Tap Power to Start"
                },
                color = when (sessionState) {
                    SessionState.SPEAKING -> NeonCyan
                    SessionState.LISTENING -> StatusGreen
                    SessionState.CONNECTING -> StatusYellow
                    SessionState.ERROR -> StatusRed
                    SessionState.DISCONNECTED -> TextSecondary
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message text
        Text(
            text = statusMessage,
            color = TextPrimary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Subtitle / transcript display
        if (lastTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"$lastTranscript\"",
                color = BrightAzure,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class AutoCommandItem(
    val label: String,
    val command: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isAutoAction: Boolean = true
)

@Composable
fun BottomControlPanel(
    sessionState: SessionState,
    isMuted: Boolean,
    amplitude: Float,
    autoCommandEnabled: Boolean,
    onToggleSession: () -> Unit,
    onToggleMute: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onExecuteAutoCommand: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Automatic Open Commands Carousel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "⚡ AUTOMATIC OPEN COMMANDS",
                color = if (autoCommandEnabled) NeonCyan else TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Text(
                text = "बोलो या टैप करो",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val autoCommands = listOf(
            AutoCommandItem("कैमरा", "camera", Icons.Default.CameraAlt),
            AutoCommandItem("यूट्यूब", "youtube", Icons.Default.PlayArrow),
            AutoCommandItem("व्हाट्सएप", "whatsapp", Icons.Default.OpenInBrowser),
            AutoCommandItem("मैप्स", "maps", Icons.Default.Map),
            AutoCommandItem("कैलकुलेटर", "calculator", Icons.Default.Calculate),
            AutoCommandItem("सेटिंग्स", "settings", Icons.Default.Settings),
            AutoCommandItem("डायलर", "dialer", Icons.Default.Phone),
            AutoCommandItem("गूगल सर्च", "google", Icons.Default.Search)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(autoCommands) { cmdItem ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpaceCard)
                        .border(
                            1.dp,
                            if (autoCommandEnabled) NeonCyan.copy(alpha = 0.4f) else SpaceCardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onExecuteAutoCommand(cmdItem.command, "") }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("auto_command_${cmdItem.command}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = cmdItem.icon,
                            contentDescription = cmdItem.label,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cmdItem.label,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Hindi Sassy Conversational chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "हाय MJ! कैसी हो? ✨",
                "कुछ मजेदार सुनाओ 😜",
                "तुम इतनी sassy क्यों हो? 😏",
                "एक प्यारा सा joke सुनाओ 💕"
            )
            items(suggestions) { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SpaceCard.copy(alpha = 0.7f))
                        .border(1.dp, SpaceCardBorder, RoundedCornerShape(14.dp))
                        .clickable { onQuickPrompt(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("quick_prompt_${prompt.take(5)}")
                ) {
                    Text(
                        text = prompt,
                        color = BrightAzure,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Action Bar with Glowing Power/Mic Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute / Unmute Button
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpaceCard)
                    .border(1.dp, SpaceCardBorder, CircleShape)
                    .testTag("mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) StatusRed else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Central Glowing Power/Mic Button
            CentralPowerButton(
                sessionState = sessionState,
                amplitude = amplitude,
                onClick = onToggleSession
            )

            // Audio Mode / Speaker Icon
            IconButton(
                onClick = { /* Status display */ },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpaceCard)
                    .border(1.dp, SpaceCardBorder, CircleShape)
                    .testTag("speaker_status_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaker status",
                    tint = if (sessionState == SessionState.SPEAKING) NeonCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (sessionState) {
                SessionState.DISCONNECTED -> "टैप करें और MJ से बात करें"
                SessionState.CONNECTING -> "जुड़ रही हूँ..."
                SessionState.LISTENING -> "MJ सुन रही है • बोलिए (उदा: \"कैमरा खोलो\")"
                SessionState.SPEAKING -> "टैप करके रोकें (Interrupt)"
                SessionState.ERROR -> "दोबारा कनेक्ट करने के लिए टैप करें"
            },
            color = TextTertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CentralPowerButton(
    sessionState: SessionState,
    amplitude: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ButtonPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val isActive = sessionState == SessionState.LISTENING || sessionState == SessionState.SPEAKING

    Box(
        modifier = Modifier
            .size(86.dp)
            .testTag("power_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo
        if (isActive || sessionState == SessionState.CONNECTING) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .scale(if (isActive) pulseScale + amplitude * 0.3f else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.45f),
                                ElectricBlue.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Inner circular button
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = when (sessionState) {
                            SessionState.SPEAKING -> listOf(NeonCyan, ElectricBlue)
                            SessionState.LISTENING -> listOf(Color(0xFF10B981), Color(0xFF047857))
                            SessionState.CONNECTING -> listOf(StatusYellow, Color(0xFFD97706))
                            SessionState.ERROR -> listOf(StatusRed, Color(0xFF991B1B))
                            SessionState.DISCONNECTED -> listOf(SpaceCard, Color(0xFF1E293B))
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = when (sessionState) {
                        SessionState.SPEAKING -> NeonCyan
                        SessionState.LISTENING -> StatusGreen
                        SessionState.CONNECTING -> StatusYellow
                        else -> NeonCyan.copy(alpha = 0.5f)
                    },
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (sessionState) {
                    SessionState.DISCONNECTED, SessionState.ERROR -> Icons.Default.PowerSettingsNew
                    SessionState.SPEAKING -> Icons.Default.Stop
                    SessionState.CONNECTING -> Icons.Default.PowerSettingsNew
                    SessionState.LISTENING -> Icons.Default.Mic
                },
                contentDescription = "Power or Mic Toggle",
                tint = if (sessionState == SessionState.DISCONNECTED) NeonCyan else Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun SettingsDialog(
    currentApiKey: String,
    autoCommandEnabled: Boolean,
    onToggleAutoCommand: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceCard,
        title = {
            Text(
                text = "MJ AI सेटिंग्स",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                // Auto Command execution toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceDark)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ स्वतः कमांड खोलें",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "YouTube, Camera, WhatsApp आदि को तुरंत खोलें",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoCommandEnabled,
                        onCheckedChange = { onToggleAutoCommand() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = ElectricBlue,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SpaceCardBorder
                        ),
                        modifier = Modifier.testTag("auto_command_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Gemini API Key (वैकल्पिक):",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text("Enter Gemini API key", color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SpaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "समर्थित कमांड: 'कैमरा खोलो', 'यूट्यूब खोलो', 'व्हाट्सएप खोलो', 'मैप्स खोलो', 'कैलकुलेटर खोलो', 'गूगल पर सर्च करो' आदि।",
                    color = BrightAzure,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save", color = SpaceBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
