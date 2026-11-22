package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FrequentPhraseEntity
import com.example.data.local.TranslationRecordEntity
import com.example.ui.components.FullscreenInterlocutorDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhrasesAndHistoryScreen(
    frequentPhrases: List<FrequentPhraseEntity>,
    historyRecords: List<TranslationRecordEntity>,
    activeLanguageCode: String,
    onUsePhrase: (FrequentPhraseEntity) -> Unit,
    onTogglePinPhrase: (FrequentPhraseEntity) -> Unit,
    onDeletePhrase: (Long) -> Unit,
    onAddPhrase: (String, String, String) -> Unit,
    onSpeakHistoryItem: (String) -> Unit,
    onSaveHistoryAsFrequent: (TranslationRecordEntity) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf("Todas") }
    var showAddDialog by remember { mutableStateOf(false) }
    var fullscreenText by remember { mutableStateOf<String?>(null) }

    val categories = remember(frequentPhrases) {
        listOf("Todas") + frequentPhrases.map { it.category }.distinct()
    }
    val filteredPhrases = remember(frequentPhrases, selectedCategory) {
        if (selectedCategory == "Todas") frequentPhrases
        else frequentPhrases.filter { it.category == selectedCategory }
    }

    fullscreenText?.let { text ->
        FullscreenInterlocutorDialog(
            phrase = text,
            languageCode = activeLanguageCode,
            onSpeak = { onSpeakHistoryItem(text) },
            onDismiss = { fullscreenText = null }
        )
    }

    if (showAddDialog) {
        AddFrequentPhraseDialog(
            onConfirm = { title, phrase, cat ->
                onAddPhrase(title, phrase, cat)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Conversaciones",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Acceso rápido e historial de traducciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedTab == 1 && historyRecords.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Text("Vaciar")
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("tab_frequent_phrases")
                ) {
                    Text("Frecuentes (${frequentPhrases.size})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_recent_history")
                ) {
                    Text("Historial (${historyRecords.size})")
                }
            }

            if (selectedTab == 0) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                        )
                    }
                }

                if (filteredPhrases.isEmpty()) {
                    EmptyPhrasesState(
                        title = "Sin frases en esta categoría",
                        subtitle = "Tocá el botón + para guardar una conversación rápida."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredPhrases, key = { it.id }) { phrase ->
                            FrequentPhraseCard(
                                phrase = phrase,
                                onUse = { onUsePhrase(phrase) },
                                onShowFullscreen = { fullscreenText = phrase.phraseText },
                                onTogglePin = { onTogglePinPhrase(phrase) },
                                onDelete = { onDeletePhrase(phrase.id) }
                            )
                        }
                    }
                }
            } else {
                if (historyRecords.isEmpty()) {
                    EmptyPhrasesState(
                        title = "Historial vacío",
                        subtitle = "Cada seña que traduzcas con la cámara aparecerá guardada aquí automáticamente."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(historyRecords, key = { it.id }) { record ->
                            HistoryRecordCard(
                                record = record,
                                onSpeak = { onSpeakHistoryItem(record.naturalPhrase) },
                                onShowFullscreen = { fullscreenText = record.naturalPhrase },
                                onSaveFrequent = { onSaveHistoryAsFrequent(record) },
                                onDelete = { onDeleteHistoryItem(record.id) }
                            )
                        }
                    }
                }
            }
        }

        if (selectedTab == 0) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("add_frequent_phrase_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar frase frecuente"
                )
            }
        }
    }
}

@Composable
private fun FrequentPhraseCard(
    phrase: FrequentPhraseEntity,
    onUse: () -> Unit,
    onShowFullscreen: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onUse)
            .testTag("frequent_phrase_card_${phrase.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (phrase.isPinned) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = phrase.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = phrase.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${phrase.usageCount}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = phrase.phraseText,
                style = MaterialTheme.typography.bodyLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onUse) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Reproducir frase en voz",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onShowFullscreen) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Mostrar en pantalla grande"
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (phrase.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Fijar frase",
                            tint = if (phrase.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar frase"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: TranslationRecordEntity,
    onSpeak: () -> Unit,
    onShowFullscreen: () -> Unit,
    onSaveFrequent: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(record.timestamp) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${record.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = record.languageCode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (record.isOfflineRecord) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Traducido offline",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = record.rawGlosses.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = record.naturalPhrase,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onSpeak) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volver a decir en voz",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onShowFullscreen) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Mostrar en grande"
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onSaveFrequent) {
                        Icon(
                            imageVector = if (record.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Guardar en frases frecuentes",
                            tint = if (record.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Borrar registro"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPhrasesState(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddFrequentPhraseDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var title by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    val presetCategories = listOf("Personal", "Comunicación", "Salud", "Transporte", "Compras")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva frase frecuente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título corto") },
                    placeholder = { Text("Ej: Turno médico") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phrase_title_input")
                )
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Frase para voz y pantalla") },
                    placeholder = { Text("Ej: Hola, tengo turno a las 10 hs.") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phrase_body_input")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetCategories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, phrase, category) },
                enabled = title.isNotBlank() && phrase.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_phrase_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
