package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.UserSettings
import com.example.ui.components.SpeakingVisualIndicator
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

@Composable
fun AiAndVoiceScreen(
    settings: UserSettings,
    isEffectiveOffline: Boolean,
    requiresFirebaseAiNotice: Boolean,
    isSpeaking: Boolean,
    voiceStatus: String,
    onSetAiExtraEnabled: (Boolean) -> Unit,
    onSetVoiceExtraEnabled: (Boolean) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Funciones Extra: IA y Voz",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Módulos opcionales desactivados por defecto. Habilitalos solo si querés asistencia visual con IA o lectura en voz alta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Optional Generative AI Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Asistente IA de Señas",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (settings.isAiExtraEnabled) "Habilitado" else "Deshabilitado por defecto",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (settings.isAiExtraEnabled) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.isAiExtraEnabled,
                            onCheckedChange = onSetAiExtraEnabled,
                            modifier = Modifier.testTag("ai_extra_switch")
                        )
                    }

                    Text(
                        text = "Cuando está desactivado, la cámara usa únicamente el sensor local de movimiento y tu diccionario LSA sin enviar imágenes a la nube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = settings.isAiExtraEnabled) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (requiresFirebaseAiNotice || isEffectiveOffline) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (requiresFirebaseAiNotice || isEffectiveOffline) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = when {
                                        requiresFirebaseAiNotice ->
                                            "Esta APK se compiló sin Firebase (google-services.json), por lo que la traducción seguirá usando el motor local seguro."
                                        isEffectiveOffline ->
                                            "Modo offline activo: desactivá el modo offline en Ajustes para usar Gemini Vision con internet."
                                        else ->
                                            "Gemini Vision está activo para analizar las capturas de tus señas y refinar giros idiomáticos."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Optional Synthesized Voice Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Voz Sintetizada (TTS)",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (settings.isVoiceExtraEnabled) voiceStatus else "Deshabilitada (modo silencioso)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (settings.isVoiceExtraEnabled) StatusOnline else StatusOffline
                                )
                            }
                        }
                        Switch(
                            checked = settings.isVoiceExtraEnabled,
                            onCheckedChange = onSetVoiceExtraEnabled,
                            modifier = Modifier.testTag("voice_extra_switch")
                        )
                    }

                    Text(
                        text = "Mientras esté deshabilitada, ninguna traducción se reproducirá por parlante automáticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = settings.isVoiceExtraEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "Hablar automáticamente al traducir",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Lee en voz alta cada nueva seña interpretada",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.autoSpeakEnabled,
                                    onCheckedChange = onSetAutoSpeak,
                                    modifier = Modifier.testTag("auto_speak_switch")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Velocidad de voz",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${"%.2f".format(settings.speechRate)}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = settings.speechRate,
                                    onValueChange = onSetSpeechRate,
                                    valueRange = 0.7f..1.4f,
                                    steps = 6,
                                    modifier = Modifier.testTag("speech_rate_slider")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = if (isSpeaking) onStopVoice else onTestVoice,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("test_voice_button")
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSpeaking) "Detener prueba" else "Probar voz")
                                }

                                if (isSpeaking) {
                                    SpeakingVisualIndicator(isSpeaking = true)
                                } else {
                                    OutlinedButton(
                                        onClick = onStopVoice,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Silenciar")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
