package com.example.ui.screens

import android.Manifest
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.detection.EmotionClassifier
import com.example.ui.theme.EmotionStyles
import com.example.ui.theme.EmotionTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.UserViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraDetectionScreen(
    userViewModel: UserViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUser by userViewModel.currentUser.collectAsState()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var detectedEmotion by remember { mutableStateOf("Neutral") }
    var confidence by remember { mutableStateOf(0.92f) }
    var faceBox by remember { mutableStateOf<Rect?>(null) }
    
    // Fallback: manually chosen override
    var manualOverrideSelected by remember { mutableStateOf<String?>(null) }

    // Floating radar grid animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarSweepOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    val activeEmotionName = manualOverrideSelected ?: detectedEmotion
    val activeEmotionTheme = remember(activeEmotionName) { EmotionStyles.getTheme(activeEmotionName) }

    // Start Scanner Setup
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emotion Scanner", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scan_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraPermissionState.status.isGranted) {
                // Live Camera Frame view
                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(480, 640))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor, EmotionClassifier { emotion, rect, conf ->
                                            if (manualOverrideSelected == null) {
                                                detectedEmotion = emotion
                                                faceBox = rect
                                                confidence = conf
                                            }
                                        })
                                    }

                                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalyzer
                                    )
                                } catch (exc: Exception) {
                                    exc.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay 1: Real-time Scanning Radar Line & Green Face Grid Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Draw moving horizontal scanner beam
                        val sweepY = canvasHeight * radarSweepOffset
                        drawLine(
                            color = activeEmotionTheme.glowingColor.copy(alpha = 0.6f),
                            start = androidx.compose.ui.geometry.Offset(0f, sweepY),
                            end = androidx.compose.ui.geometry.Offset(canvasWidth, sweepY),
                            strokeWidth = 6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                        )

                        // If front face detected, sketch face border
                        faceBox?.let { box ->
                            // Normalize rect coords into coordinates matching canvas size
                            // Simple layout centering
                            val left = canvasWidth * 0.2f
                            val top = canvasHeight * 0.25f
                            val right = canvasWidth * 0.8f
                            val bottom = canvasHeight * 0.65f

                            // Draw corners to denote active targeting matrix
                            val strokeW = 4.dp.toPx()
                            val lineL = 40.dp.toPx()

                            drawRoundRect(
                                color = activeEmotionTheme.color,
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                )
                            )

                            // Corner indicators (Top-Left)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + lineL, top), strokeW)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + lineL), strokeW)

                            // Corner indicators (Top-Right)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - lineL, top), strokeW)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + lineL), strokeW)

                            // Corner indicators (Bottom-Left)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left + lineL, bottom), strokeW)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - lineL), strokeW)

                            // Corner indicators (Bottom-Right)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right - lineL, bottom), strokeW)
                            drawLine(activeEmotionTheme.color, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - lineL), strokeW)
                        }
                    }

                    // Content panel positioned over the bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                )
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Current mood badge
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, activeEmotionTheme.color.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeEmotionTheme.emoji,
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Expression: ${activeEmotionTheme.name}",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Confidence: ${(confidence * 100).toInt()}% • TFLite scan",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Override options if camera doesn't grab it
                        Text(
                            text = "Or choose your base mood manually:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            listOf("Happy", "Sad", "Neutral", "Angry", "Surprised").forEach { moodName ->
                                val moodTheme = EmotionStyles.getTheme(moodName)
                                val isSelected = activeEmotionName.lowercase() == moodName.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) moodTheme.color else Color.White
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            manualOverrideSelected = moodName
                                            detectedEmotion = moodName
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${moodTheme.emoji} ${moodTheme.name}",
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Start chatting button
                        Button(
                            onClick = {
                                currentUser?.let { user ->
                                    chatViewModel.startNewSession(
                                        userId = user.id,
                                        emotion = activeEmotionName,
                                        defaultTitle = "Mood Session"
                                    )
                                    onNavigateToChat()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_conversation_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeEmotionTheme.color
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Start ${activeEmotionTheme.name} Chat Session",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Denied State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Camera Required",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We need camera access to analyze facial expressions locally via our TFLite analyzer. Please enable camera permissions in your settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
