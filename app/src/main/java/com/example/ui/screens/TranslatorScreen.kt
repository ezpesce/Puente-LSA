package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.LearnedSignEntity
import com.example.data.local.UserSettings
import com.example.ui.components.FirebaseAiNoticeCard
import com.example.ui.components.FullscreenInterlocutorDialog
import com.example.ui.components.SignLanguageChip
import com.example.ui.components.SpeakingVisualIndicator
import com.example.ui.components.StatusPill
import com.example.ui.state.TranslationUiState
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun TranslatorScreen(
    settings: UserSettings,
    isEffectiveOffline: Boolean,
    translationState: TranslationUiState,
    selectedGlosses: List<String>,
    learnedVocabulary: List<LearnedSignEntity>,
    cameraMotionScore: Float,
    isAutoCaptureActive: Boolean,
    useFrontCamera: Boolean,
    isTorchOn: Boolean,
    isSpeaking: Boolean,
    requiresFirebaseAiNotice: Boolean,
    onOpenLanguageSheet: () -> Unit,
    onToggleOfflineMode: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onAnalyzeCamera: (android.graphics.Bitmap?) -> Unit,
    onMotionUpdate: (Float) -> Unit,
    onAddGloss: (String) -> Unit,
    onRemoveLastGloss: () -> Unit,
    onClearGlosses: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onSaveAsFrequent: (String, String) -> Unit,
    onTriggerInterlocutorAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showFullscreenCaption by remember { mutableStateOf(false) }
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(isAutoCaptureActive, hasCameraPermission) {
        if (isAutoCaptureActive) {
            while (true) {
                delay(3600)
                onAnalyzeCamera(previewViewRef.value?.bitmap)
            }
        }
    }

    val currentPhrase = when (translationState) {
        is TranslationUiState.Success -> translationState.result.idiomaticPhrase
        is TranslationUiState.Error -> translationState.message
        else -> "Hacé una seña frente a la cámara o tocá tus señas frecuentes."
    }

    if (showFullscreenCaption) {
        FullscreenInterlocutorDialog(
            phrase = currentPhrase,
            languageCode = settings.activeSignLanguageCode,
            onSpeak = { onSpeakText(currentPhrase) },
            onDismiss = { showFullscreenCaption = false }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Minimalist top status bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Puente LSA",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Traductor visual e idiomático",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignLanguageChip(
                        languageCode = settings.activeSignLanguageCode,
                        onClick = onOpenLanguageSheet
                    )
                    StatusPill(
                        label = if (isEffectiveOffline) "Offline" else "Gemini",
                        isOffline = isEffectiveOffline,
                        onClick = onToggleOfflineMode
                    )
                }
            }

            // CameraX Live Viewfinder Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        CameraXLivePreview(
                            useFrontCamera = useFrontCamera,
                            isTorchOn = isTorchOn,
                            onMotionScore = onMotionUpdate,
                            onPreviewViewReady = { previewViewRef.value = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CameraPermissionPlaceholder(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top overlay inside camera frame
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                                )
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAutoCaptureActive) Color(0xFF4ADE80)
                                            else Color(0xFF56CFE1)
                                        )
                                )
                                Text(
                                    text = if (isAutoCaptureActive) "Escucha visual continua" else "Cámara ${settings.activeSignLanguageCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!useFrontCamera) {
                                IconButton(
                                    onClick = onToggleTorch,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .testTag("camera_torch_button")
                                ) {
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Alternar linterna",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleCameraFacing,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .testTag("camera_switch_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Cambiar cámara frontal o trasera",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Bottom overlay inside camera frame: motion bar + capture buttons
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                )
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = null,
                                tint = Color(0xFF8BE8FF),
                                modifier = Modifier.size(16.dp)
                            )
                            LinearProgressIndicator(
                                progress = { cameraMotionScore.coerceIn(0.08f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF56CFE1),
                                trackColor = Color.White.copy(alpha = 0.24f)
                            )
                            Text(
                                text = "${(cameraMotionScore * 100).toInt()}% mov.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onAnalyzeCamera(previewViewRef.value?.bitmap) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("capture_sign_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Traducir seña")
                            }

                            FilledTonalButton(
                                onClick = onToggleAutoCapture,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("toggle_auto_capture_button")
                            ) {
                                Icon(
                                    imageVector = if (isAutoCaptureActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAutoCaptureActive) "Pausar" else "Auto")
                            }
                        }
                    }
                }
            }

            // Live Translated Idiomatic Phrase & Voice Output Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translation_result_card"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (translationState) {
                                    is TranslationUiState.Success -> translationState.result.sourceLabel
                                    is TranslationUiState.Analyzing -> "Interpretando giros idiomáticos..."
                                    else -> "Traducción en tiempo real (${settings.activeSignLanguageCode})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SpeakingVisualIndicator(isSpeaking = isSpeaking)
                    }

                    if (translationState is TranslationUiState.Analyzing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "Armando frase natural argentina...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text(
                            text = currentPhrase,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (translationState is TranslationUiState.Success) {
                        Text(
                            text = translationState.result.culturalNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Active Gloss Sequence chips
                    AnimatedVisibility(visible = selectedGlosses.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Glosas:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(selectedGlosses) { gloss ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = gloss.replace("_", " "),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onRemoveLastGloss,
                                modifier = Modifier.testTag("remove_last_gloss_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Quitar última seña"
                                )
                            }
                        }
                    }

                    // Primary accessible actions: Speak, Fullscreen Card, Save Frequent, Haptic Alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isSpeaking) onStopSpeaking() else onSpeakText(currentPhrase)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("speak_translation_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSpeaking) "Detener voz" else stringResource(R.string.speak_aloud))
                        }

                        FilledTonalIconButton(
                            onClick = { showFullscreenCaption = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fullscreen_caption_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.fullscreen_caption)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val title = selectedGlosses.joinToString(" ").replace("_", " ")
                                    .ifBlank { "Frase LSA" }
                                onSaveAsFrequent(title, currentPhrase.removePrefix("[Muestra IA]").trim())
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("save_translation_frequent_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Guardar en frases frecuentes"
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onTriggerInterlocutorAlert,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("trigger_haptic_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Simular alerta háptica de interlocutor"
                            )
                        }
                    }
                }
            }

            if (requiresFirebaseAiNotice && !isEffectiveOffline) {
                FirebaseAiNoticeCard()
            }

            // Personalized Adaptive Frequent Signs Strip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tus señas más frecuentes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selectedGlosses.isNotEmpty()) {
                        Text(
                            text = "Limpiar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = onClearGlosses)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("clear_glosses_button")
                        )
                    }
                }
                Text(
                    text = "Se ordenan automáticamente según tu uso para combinar frases al instante (funciona offline).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(learnedVocabulary.take(10), key = { it.id }) { sign ->
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onAddGloss(sign.glossKey) }
                                .testTag("quick_sign_${sign.glossKey.lowercase()}"),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = sign.displayTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sign.usageCount} usos · ${sign.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CameraPermissionPlaceholder(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.camera_permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onRequestPermission,
            modifier = Modifier
                .height(48.dp)
                .testTag("grant_camera_permission_button")
        ) {
            Text(stringResource(R.string.camera_permission_grant))
        }
    }
}

@Composable
private fun CameraXLivePreview(
    useFrontCamera: Boolean,
    isTorchOn: Boolean,
    onMotionScore: (Float) -> Unit,
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }.also(onPreviewViewReady)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(boundCamera, isTorchOn, useFrontCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit() && !useFrontCamera) {
            cam.cameraControl.enableTorch(isTorchOn)
        }
    }

    DisposableEffect(useFrontCamera, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var lastAvgLuma = -1f
        var frameCounter = 0

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            frameCounter++
                            if (frameCounter % 5 == 0) {
                                val buffer = imageProxy.planes.firstOrNull()?.buffer
                                if (buffer != null) {
                                    val remaining = buffer.remaining()
                                    val step = (remaining / 120).coerceAtLeast(1)
                                    var sum = 0L
                                    var samples = 0
                                    var idx = 0
                                    while (idx < remaining) {
                                        sum += (buffer.get(idx).toInt() and 0xFF)
                                        samples++
                                        idx += step
                                    }
                                    val avgLuma = if (samples > 0) sum.toFloat() / samples else 0f
                                    if (lastAvgLuma >= 0f) {
                                        val delta = abs(avgLuma - lastAvgLuma) / 28f
                                        onMotionScore(delta.coerceIn(0.05f, 1f))
                                    }
                                    lastAvgLuma = avgLuma
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analyzer
                )
                boundCamera = cam
            } catch (_: Exception) {
                // Gracefully handle devices/emulators without requested camera facing
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
