package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.SignLanguageProfile
import com.example.data.model.SignLanguageRegistry
import com.example.data.model.SignLanguageSupportLevel
import com.example.ui.state.RealTimeAlertBanner
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

@Composable
fun StatusPill(
    label: String,
    isOffline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (isOffline) StatusOffline.copy(alpha = 0.14f) else StatusOnline.copy(alpha = 0.14f)
    val contentColor = if (isOffline) StatusOffline else StatusOnline
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("offline_status_pill"),
        color = container,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isOffline) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Modo sin conexión activo",
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(contentColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
fun SignLanguageChip(
    languageCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("language_selector_chip"),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Cambiar lengua de señas",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = languageCode,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun FirebaseAiNoticeCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("firebase_ai_notice_banner"),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.ai_firebase_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun SpeakingVisualIndicator(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isSpeaking) return
    val transition = rememberInfiniteTransition(label = "tts_wave")
    val barScale by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_pulse"
    )
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Voz sintetizada hablando",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.7f, 1.0f, 0.55f, 0.9f).forEachIndexed { idx, factor ->
                    val heightDp = (8 + (12 * factor * (if (idx % 2 == 0) barScale else 1.3f - barScale))).dp
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(heightDp.coerceIn(6.dp, 20.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Text(
                text = "Hablando",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun HapticAlertToastBanner(
    alert: RealTimeAlertBanner?,
    wearableSynced: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        alert?.let { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("realtime_haptic_banner"),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Alerta háptica activa",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            if (wearableSynced) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = "Sincronizado con reloj",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                            maxLines = 2
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_haptic_banner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar alerta",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignLanguageSelectorSheet(
    selectedCode: String,
    onSelect: (SignLanguageProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Universalidad en Lengua de Señas",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "LSA es el idioma principal. Podés activar otros perfiles regionales para comunicarte con interlocutores de otros países.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SignLanguageRegistry.supportedLanguages, key = { it.code }) { profile ->
                    val selected = profile.code == selectedCode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelect(profile)
                                onDismiss()
                            }
                            .testTag("select_lang_${profile.code.lowercase()}"),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = if (selected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = profile.code,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Text(
                                    text = "${profile.region} · ${profile.supportLevel.badge}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (profile.supportLevel == SignLanguageSupportLevel.FULL) {
                                        StatusOnline
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seleccionado",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenInterlocutorDialog(
    phrase: String,
    languageCode: String,
    onSpeak: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Lectura rápida · $languageCode",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_fullscreen_caption_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar pantalla grande",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = phrase.removePrefix("[Muestra IA]").trim(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 36.sp,
                        lineHeight = 44.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                FilledTonalButton(
                    onClick = onSpeak,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("fullscreen_speak_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reproducir voz sintetizada", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
