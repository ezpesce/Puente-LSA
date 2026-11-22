package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.ai.GestureMotionSample
import com.example.data.local.LearnedSignEntity
import com.example.data.local.UserSettings
import com.example.ui.components.ExternalSourcesChipsRow
import com.example.ui.components.FirebaseAiNoticeCard
import com.example.ui.components.FullscreenInterlocutorDialog
import com.example.ui.components.GoogleSearchEntryPointView
import com.example.ui.components.SignLanguageChip
import com.example.ui.components.SpeakingVisualIndicator
import com.example.ui.components.StatusPill
import com.example.ui.state.TranslationUiState
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs

private enum class SignCapturePhase {
    IDLE,
    PREPARING,
    CAPTURING
}

@Composable
fun TranslatorScreen(
    settings: UserSettings,
    isEffectiveOffline: Boolean,
    translationState: TranslationUiState,
    selectedGlosses: List<String>,
    learnedVocabulary: List<LearnedSignEntity>,
    cameraMotionScore: Float,
    useFrontCamera: Boolean,
    isTorchOn: Boolean,
    isSpeaking: Boolean,
    requiresFirebaseAiNotice: Boolean,
    onOpenLanguageSheet: () -> Unit,
    onToggleOfflineMode: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onToggleTorch: () -> Unit,
    onProcessGestureCapture: (List<Bitmap>, List<GestureMotionSample>) -> Unit,
    onMotionUpdate: (Float) -> Unit,
    onAddGloss: (String) -> Unit,
    onRemoveLastGloss: () -> Unit,
    onClearGlosses: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onSaveAsFrequent: (String, String) -> Unit,
    onTriggerInterlocutorAlert: () -> Unit,
    onOpenAiVoiceTab: () -> Unit = {},
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

    var capturePhase by remember { mutableStateOf(SignCapturePhase.IDLE) }
    var captureProgress by remember { mutableFloatStateOf(0f) }
    val latestMotionSampleRef = remember { mutableStateOf<GestureMotionSample?>(null) }
    val currentMotionSamples = remember { mutableListOf<GestureMotionSample>() }
    val currentCapturedFrames = remember { mutableListOf<Bitmap>() }
    val isCapturingFlag = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val wantFrameSnapshotFlag = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val onProcessGestureState by rememberUpdatedState(onProcessGestureCapture)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(capturePhase) {
        when (capturePhase) {
            SignCapturePhase.IDLE -> {
                isCapturingFlag.set(false)
                wantFrameSnapshotFlag.set(false)
                captureProgress = 0f
            }
            SignCapturePhase.PREPARING -> {
                isCapturingFlag.set(false)
                wantFrameSnapshotFlag.set(false)
                synchronized(currentCapturedFrames) { currentCapturedFrames.clear() }
                currentMotionSamples.clear()
                captureProgress = 0f
                val prepSteps = 12
                repeat(prepSteps) { idx ->
                    delay(100L)
                    captureProgress = (idx + 1).toFloat() / prepSteps
                }
                capturePhase = SignCapturePhase.CAPTURING
            }
            SignCapturePhase.CAPTURING -> {
                isCapturingFlag.set(true)
                captureProgress = 0f
                val captureSteps = 16 // 16 * 150ms = 2.4s active sign window
                for (step in 1..captureSteps) {
                    if (step == 3 || step == 9 || step == 15) {
                        wantFrameSnapshotFlag.set(true)
                    }
                    delay(150L)
                    captureProgress = step.toFloat() / captureSteps
                    latestMotionSampleRef.value?.let { currentMotionSamples.add(it) }
                    if (step == 5 || step == 11 || step == captureSteps) {
                        val currentCount = synchronized(currentCapturedFrames) { currentCapturedFrames.size }
                        val expectedTarget = when (step) {
                            5 -> 1
                            11 -> 2
                            else -> 3
                        }
                        if (currentCount < expectedTarget) {
                            previewViewRef.value?.bitmap?.let { bmp ->
                                synchronized(currentCapturedFrames) {
                                    currentCapturedFrames.add(bmp)
                                }
                            }
                        }
                    }
                }
                isCapturingFlag.set(false)
                wantFrameSnapshotFlag.set(false)
                val framesSnapshot = synchronized(currentCapturedFrames) { currentCapturedFrames.toList() }
                val samplesSnapshot = currentMotionSamples.toList()
                capturePhase = SignCapturePhase.IDLE
                onProcessGestureState(framesSnapshot, samplesSnapshot)
            }
        }
    }

    val currentPhrase = when (translationState) {
        is TranslationUiState.Success -> translationState.result.idiomaticPhrase
        is TranslationUiState.Error -> translationState.message
        else -> "Tocá 'Traducir seña', esperá la señal y hacé tu seña frente a la cámara."
    }

    if (showFullscreenCaption) {
        FullscreenInterlocutorDialog(
            phrase = currentPhrase,
            languageCode = settings.activeSignLanguageCode,
            isVoiceEnabled = settings.isVoiceExtraEnabled,
            onSpeak = { onSpeakText(currentPhrase) },
            onDismiss = { showFullscreenCaption = false }
        )
    }

    val statusLabel = when {
        isEffectiveOffline -> "Offline"
        settings.isAiExtraEnabled && !requiresFirebaseAiNotice -> "IA Gemini"
        else -> "Local"
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val cameraHeight = (maxHeight * 0.62f).coerceIn(360.dp, 640.dp)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Puente LSA",
                    style = MaterialTheme.typography.headlineMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignLanguageChip(
                        languageCode = settings.activeSignLanguageCode,
                        onClick = onOpenLanguageSheet
                    )
                    StatusPill(
                        label = statusLabel,
                        isOffline = isEffectiveOffline,
                        onClick = onToggleOfflineMode
                    )
                }
            }

            // Large Dominant CameraX Live Viewfinder (~62% of screen height)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cameraHeight),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                border = BorderStroke(
                    width = if (capturePhase == SignCapturePhase.CAPTURING) 2.5.dp else 1.dp,
                    color = when (capturePhase) {
                        SignCapturePhase.CAPTURING -> Color(0xFF4ADE80)
                        SignCapturePhase.PREPARING -> Color(0xFFFACC15)
                        SignCapturePhase.IDLE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        CameraXLivePreview(
                            useFrontCamera = useFrontCamera,
                            isTorchOn = isTorchOn,
                            wantFrameSnapshotFlag = wantFrameSnapshotFlag,
                            onMotionSample = { sample ->
                                latestMotionSampleRef.value = sample
                                onMotionUpdate(sample.overallScore)
                            },
                            onFrameCaptured = { bitmap ->
                                synchronized(currentCapturedFrames) {
                                    if (currentCapturedFrames.size < 4) {
                                        currentCapturedFrames.add(bitmap)
                                    }
                                }
                            },
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
                                    listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent)
                                )
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.56f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (capturePhase) {
                                                SignCapturePhase.CAPTURING -> Color(0xFF4ADE80)
                                                SignCapturePhase.PREPARING -> Color(0xFFFACC15)
                                                SignCapturePhase.IDLE -> Color(0xFF56CFE1)
                                            }
                                        )
                                )
                                Text(
                                    text = when (capturePhase) {
                                        SignCapturePhase.PREPARING -> "Preparate frente a la cámara..."
                                        SignCapturePhase.CAPTURING -> "Hacé tu seña ahora..."
                                        SignCapturePhase.IDLE -> "Cámara ${settings.activeSignLanguageCode} lista"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
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

                    // Bottom overlay inside camera frame: motion/capture progress + intentional single capture CTA
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (capturePhase != SignCapturePhase.IDLE) {
                            LinearProgressIndicator(
                                progress = { captureProgress.coerceIn(0.02f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (capturePhase == SignCapturePhase.CAPTURING) {
                                    Color(0xFF4ADE80)
                                } else {
                                    Color(0xFFFACC15)
                                },
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }

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
                                progress = { cameraMotionScore.coerceIn(0.06f, 1f) },
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

                        if (capturePhase == SignCapturePhase.IDLE) {
                            Button(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        capturePhase = SignCapturePhase.PREPARING
                                    }
                                },
                                enabled = translationState !is TranslationUiState.Analyzing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("capture_sign_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasCameraPermission) "Traducir seña" else "Activar cámara y traducir",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { capturePhase = SignCapturePhase.IDLE },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("cancel_capture_sign_button"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (capturePhase == SignCapturePhase.PREPARING) {
                                        "Preparate... (Cancelar)"
                                    } else {
                                        "Capturando seña... (Cancelar)"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Live Translated Phrase Card
            val isErrorState = translationState is TranslationUiState.Error
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translation_result_card"),
                shape = RoundedCornerShape(22.dp),
                color = if (isErrorState) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = 3.dp,
                border = BorderStroke(
                    1.dp,
                    if (isErrorState) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                tint = if (isErrorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (translationState) {
                                    is TranslationUiState.Success -> translationState.result.sourceLabel
                                    is TranslationUiState.Analyzing -> "Analizando cuadros y fuentes externas..."
                                    is TranslationUiState.Error -> "Atención de cámara / IA"
                                    else -> "Traducción visual (${settings.activeSignLanguageCode})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isErrorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (settings.isVoiceExtraEnabled) {
                            SpeakingVisualIndicator(isSpeaking = isSpeaking)
                        }
                    }

                    if (translationState is TranslationUiState.Analyzing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "Gemini Vision está analizando tus manos y consultando fuentes externas...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text(
                            text = currentPhrase,
                            style = if (isErrorState) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isErrorState && !settings.isAiExtraEnabled) {
                        OutlinedButton(
                            onClick = onOpenAiVoiceTab,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Ir a 'IA y Voz' para activar IA")
                        }
                    }

                    if (requiresFirebaseAiNotice) {
                        FirebaseAiNoticeCard()
                    }

                    if (translationState is TranslationUiState.Success) {
                        Text(
                            text = translationState.result.culturalNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        translationState.result.newlyLearnedSign?.let { learned ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Seña aprendida y guardada en tu diccionario: ${learned.displayTitle}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        ExternalSourcesChipsRow(sources = translationState.result.externalSources)
                        GoogleSearchEntryPointView(renderedHtml = translationState.result.searchEntryPointHtml)
                    }

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

                    // Action Row: Visual fullscreen card always available; voice button only when Voice extra is enabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.isVoiceExtraEnabled) {
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
                        } else {
                            FilledTonalButton(
                                onClick = { showFullscreenCaption = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("fullscreen_caption_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.fullscreen_caption))
                            }
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
                    text = "Tocá una o más señas para armar frases al instante (100% local).",
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
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.camera_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
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
    wantFrameSnapshotFlag: java.util.concurrent.atomic.AtomicBoolean,
    onMotionSample: (GestureMotionSample) -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
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
        var prevGrid: FloatArray? = null
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
                            if (wantFrameSnapshotFlag.compareAndSet(true, false)) {
                                runCatching {
                                    val rawBmp = imageProxy.toBitmap()
                                    val rotated = rotateAndMirrorIfNeeded(
                                        bitmap = rawBmp,
                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                        mirrorHorizontally = useFrontCamera
                                    )
                                    onFrameCaptured(rotated)
                                }
                            }
                            if (frameCounter % 4 == 0) {
                                val plane = imageProxy.planes.firstOrNull()
                                if (plane != null) {
                                    val buffer = plane.buffer
                                    val rowStride = plane.rowStride
                                    val width = imageProxy.width
                                    val height = imageProxy.height
                                    val stepX = (width / 18).coerceAtLeast(8)
                                    val stepY = (height / 18).coerceAtLeast(8)

                                    val cellSums = LongArray(9)
                                    val cellCounts = IntArray(9)

                                    var y = 0
                                    while (y < height) {
                                        val rowCell = ((y * 3) / height).coerceIn(0, 2)
                                        val rowOffset = y * rowStride
                                        var x = 0
                                        while (x < width) {
                                            val index = rowOffset + x
                                            if (index < buffer.limit()) {
                                                val colCell = ((x * 3) / width).coerceIn(0, 2)
                                                val cellIdx = rowCell * 3 + colCell
                                                cellSums[cellIdx] += (buffer.get(index).toInt() and 0xFF)
                                                cellCounts[cellIdx]++
                                            }
                                            x += stepX
                                        }
                                        y += stepY
                                    }

                                    val currGrid = FloatArray(9) { idx ->
                                        if (cellCounts[idx] > 0) cellSums[idx].toFloat() / cellCounts[idx] else 0f
                                    }

                                    val previous = prevGrid
                                    if (previous != null) {
                                        val deltas = FloatArray(9) { idx ->
                                            (abs(currGrid[idx] - previous[idx]) / 34f).coerceIn(0f, 1f)
                                        }
                                        val overall = (deltas.sum() / 9f).coerceIn(0f, 1f)
                                        val upper = ((deltas[0] + deltas[1] + deltas[2]) / 3f).coerceIn(0f, 1f)
                                        val middle = ((deltas[3] + deltas[4] + deltas[5]) / 3f).coerceIn(0f, 1f)
                                        val lower = ((deltas[6] + deltas[7] + deltas[8]) / 3f).coerceIn(0f, 1f)
                                        val left = ((deltas[0] + deltas[3] + deltas[6]) / 3f).coerceIn(0f, 1f)
                                        val right = ((deltas[2] + deltas[5] + deltas[8]) / 3f).coerceIn(0f, 1f)
                                        onMotionSample(
                                            GestureMotionSample(
                                                overallScore = overall,
                                                upperScore = upper,
                                                middleScore = middle,
                                                lowerScore = lower,
                                                leftScore = left,
                                                rightScore = right
                                            )
                                        )
                                    }
                                    prevGrid = currGrid
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

private fun rotateAndMirrorIfNeeded(
    bitmap: Bitmap,
    rotationDegrees: Int,
    mirrorHorizontally: Boolean
): Bitmap {
    if (rotationDegrees == 0 && !mirrorHorizontally) return bitmap
    val matrix = Matrix().apply {
        if (rotationDegrees != 0) {
            postRotate(rotationDegrees.toFloat())
        }
        if (mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
