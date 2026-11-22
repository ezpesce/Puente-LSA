package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ai.ExternalSignSearchResult
import com.example.data.local.UserSettings
import com.example.ui.components.ExternalSourcesChipsRow
import com.example.ui.components.GoogleSearchEntryPointView
import com.example.ui.components.SpeakingVisualIndicator
import com.example.ui.state.ExternalSearchUiState
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

private val suggestedExternalQueries = listOf(
    "Buenos días",
    "Farmacia",
    "Hospital",
    "Gracias",
    "¿Cómo te llamás?",
    "Subte"
)

@Composable
fun AiAndVoiceScreen(
    settings: UserSettings,
    isEffectiveOffline: Boolean,
    requiresFirebaseAiNotice: Boolean,
    isSpeaking: Boolean,
    voiceStatus: String,
    externalSearchState: ExternalSearchUiState,
    onSetAiExtraEnabled: (Boolean) -> Unit,
    onSearchExternalSign: (String) -> Unit,
    onSaveExternalSign: (ExternalSignSearchResult) -> Unit,
    onSetVoiceExtraEnabled: (Boolean) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

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
                    text = "IA Visual, Búsqueda Externa y Voz",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "La visión IA aprende desde tu cámara y fuentes externas. La voz sintetizada permanece desactivada por defecto salvo que elijas habilitarla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Gemini Vision + External Search Card
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
                                    text = if (settings.isAiExtraEnabled) "Habilitado para cámara y búsqueda" else "Deshabilitado",
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
                        text = "Analiza una secuencia de cuadros de tu cámara con Gemini 3.8 Flash, consulta Google Search + Wikipedia/Wikcionario/Wikidata y guarda señas nuevas automáticamente en tu diccionario.",
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
                                            "Esta APK se compiló sin google-services.json. Subí app/google-services.json (o el secret GOOGLE_SERVICES_JSON en GitHub) para usar Gemini Vision y Google Search Grounding."
                                        isEffectiveOffline ->
                                            "Modo offline activo: desactivá el modo offline en Ajustes para usar Gemini Vision y fuentes externas."
                                        else ->
                                            "Gemini Vision + Google Search Grounding están activos para analizar tus señas reales."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // External Database & Web Learning Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Aprender desde fuentes externas",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Busca en Google Search, Wikipedia, Wikcionario y Wikidata para ampliar tu diccionario ${settings.activeSignLanguageCode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Palabra o frase en ${settings.activeSignLanguageCode}") },
                        placeholder = { Text("Ej: Buenos días, Farmacia, Gracias") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("external_sign_search_input")
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(suggestedExternalQueries) { suggestion ->
                            FilterChip(
                                selected = searchQuery.equals(suggestion, ignoreCase = true),
                                onClick = {
                                    searchQuery = suggestion
                                    onSearchExternalSign(suggestion)
                                },
                                label = { Text(suggestion) }
                            )
                        }
                    }

                    Button(
                        onClick = { onSearchExternalSign(searchQuery) },
                        enabled = externalSearchState !is ExternalSearchUiState.Searching && !isEffectiveOffline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("search_external_sign_button")
                    ) {
                        if (externalSearchState is ExternalSearchUiState.Searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consultando IA y bases externas...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscar y enseñar a la app")
                        }
                    }

                    when (externalSearchState) {
                        is ExternalSearchUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = externalSearchState.message,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        is ExternalSearchUiState.Success -> {
                            val res = externalSearchState.result
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("external_sign_result_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${res.displayTitle} (${res.glossKey})",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = res.category,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Frase idiomática: ${res.idiomaticPhrase}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Cómo hacer la seña: ${res.handshapeGuide}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Expresión facial: ${res.facialCue}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = res.culturalNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    ExternalSourcesChipsRow(sources = res.externalSources)
                                    GoogleSearchEntryPointView(renderedHtml = res.searchEntryPointHtml)

                                    Button(
                                        onClick = { onSaveExternalSign(res) },
                                        enabled = !externalSearchState.savedToDictionary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("save_external_sign_button")
                                    ) {
                                        Icon(
                                            imageVector = if (externalSearchState.savedToDictionary) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.BookmarkAdd
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (externalSearchState.savedToDictionary) {
                                                "Guardada en tu diccionario LSA"
                                            } else {
                                                "Guardar en mi diccionario LSA"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Optional Synthesized Voice Card (OFF by default)
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
