package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.LearnedSignEntity
import com.example.data.local.TutorialLessonEntity
import com.example.ui.theme.StatusOnline

@Composable
fun LearnScreen(
    lessons: List<TutorialLessonEntity>,
    vocabulary: List<LearnedSignEntity>,
    isVoiceEnabled: Boolean,
    onCompleteLesson: (TutorialLessonEntity) -> Unit,
    onSpeakExample: (String) -> Unit,
    onToggleSignPinned: (LearnedSignEntity) -> Unit,
    onAddCustomSign: (String, String, String, String) -> Unit,
    onOpenExternalAiSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeLesson by remember { mutableStateOf<TutorialLessonEntity?>(null) }
    var showAddSignDialog by remember { mutableStateOf(false) }

    val completedCount = remember(lessons) { lessons.count { it.isCompleted } }
    val progress = if (lessons.isEmpty()) 0f else completedCount.toFloat() / lessons.size

    activeLesson?.let { lesson ->
        InteractiveLessonDialog(
            lesson = lesson,
            isVoiceEnabled = isVoiceEnabled,
            onSpeak = { onSpeakExample(lesson.idiomaticExample.replace("\"", "")) },
            onMarkMastered = {
                onCompleteLesson(lesson)
                activeLesson = null
            },
            onDismiss = { activeLesson = null }
        )
    }

    if (showAddSignDialog) {
        AddCustomSignDialog(
            onConfirm = { title, phrase, category, guide ->
                onAddCustomSign(title, phrase, category, guide)
                showAddSignDialog = false
            },
            onDismiss = { showAddSignDialog = false }
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
            Text(
                text = "Aprender LSA",
                style = MaterialTheme.typography.headlineMedium
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("learn_tab_tutorial")
                ) {
                    Text("Tutorial Interactivo")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("learn_tab_vocabulary")
                ) {
                    Text("Aprendizaje Propio (${vocabulary.size})")
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        TutorialHeroCard(
                            completedCount = completedCount,
                            totalCount = lessons.size,
                            progress = progress
                        )
                    }
                    items(lessons, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            onPractice = { activeLesson = lesson }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    item {
                        AdaptiveSummaryCard(
                            vocabulary = vocabulary,
                            onOpenExternalAiSearch = onOpenExternalAiSearch
                        )
                    }
                    items(vocabulary, key = { it.id }) { sign ->
                        LearnedSignCard(
                            sign = sign,
                            isVoiceEnabled = isVoiceEnabled,
                            onSpeak = { onSpeakExample(sign.idiomaticOutput) },
                            onTogglePin = { onToggleSignPinned(sign) }
                        )
                    }
                }
            }
        }

        if (selectedTab == 1) {
            FloatingActionButton(
                onClick = { showAddSignDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("add_custom_sign_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Enseñar nueva seña personal"
                )
            }
        }
    }
}

@Composable
private fun TutorialHeroCard(
    completedCount: Int,
    totalCount: Int,
    progress: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_lsa_tutorial_banner_1795318311266),
                    contentDescription = "Ilustración de manos comunicándose en Lengua de Señas Argentina",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Curso Básico de LSA",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "$completedCount de $totalCount lecciones dominadas",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "Practicá configuración de mano, expresión facial y giros idiomáticos reales de Argentina.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: TutorialLessonEntity,
    onPractice: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onPractice)
            .testTag("lesson_card_${lesson.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            1.dp,
            if (lesson.isCompleted) StatusOnline.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (lesson.isCompleted) StatusOnline.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (lesson.isCompleted) Icons.Default.CheckCircle else Icons.Default.School,
                    contentDescription = null,
                    tint = if (lesson.isCompleted) StatusOnline else MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.moduleTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = lesson.signTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Frase: ${lesson.idiomaticExample}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Practicar lección",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun AdaptiveSummaryCard(
    vocabulary: List<LearnedSignEntity>,
    onOpenExternalAiSearch: () -> Unit
) {
    val topUsed = vocabulary.sortedByDescending { it.usageCount }.take(3)
    val avgMastery = if (vocabulary.isEmpty()) 0 else vocabulary.map { it.masteryPercent }.average().toInt()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sistema Adaptativo Personalizado",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Dominio promedio: $avgMastery% · Señas más frecuentes: ${topUsed.joinToString { it.displayTitle }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            OutlinedButton(
                onClick = onOpenExternalAiSearch,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Buscar y aprender señas desde fuentes externas (IA)")
            }
        }
    }
}

@Composable
private fun LearnedSignCard(
    sign: LearnedSignEntity,
    isVoiceEnabled: Boolean,
    onSpeak: () -> Unit,
    onTogglePin: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learned_sign_card_${sign.glossKey.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sign.displayTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${sign.category} · Usada ${sign.usageCount} veces",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    if (isVoiceEnabled) {
                        IconButton(onClick = onSpeak) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Escuchar seña",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (sign.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Priorizar seña",
                            tint = if (sign.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = sign.handshapeGuide,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { sign.masteryPercent / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = "${sign.masteryPercent}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun InteractiveLessonDialog(
    lesson: TutorialLessonEntity,
    isVoiceEnabled: Boolean,
    onSpeak: () -> Unit,
    onMarkMastered: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val transition = rememberInfiniteTransition(label = "gesture_motion")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lesson.signTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar tutorial")
                    }
                }

                // Visual interactive motion guide canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = size.minDimension * 0.42f
                        drawCircle(
                            color = primaryColor.copy(alpha = (1f - pulse) * 0.45f),
                            radius = maxR * (0.35f + 0.65f * pulse),
                            center = center,
                            style = Stroke(width = 4f)
                        )
                        val startX = center.x - size.width * 0.22f
                        val endX = center.x + size.width * 0.22f
                        val currX = startX + (endX - startX) * pulse
                        drawLine(
                            color = secondaryColor.copy(alpha = 0.4f),
                            start = Offset(startX, center.y),
                            end = Offset(endX, center.y),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 16f,
                            center = Offset(currX, center.y)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "Guía visual de movimiento",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = lesson.handshapeSteps,
                    style = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = lesson.facialExpressionNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (isVoiceEnabled) {
                    OutlinedButton(
                        onClick = onSpeak,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("lesson_speak_example_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escuchar: ${lesson.idiomaticExample}")
                    }
                }

                Button(
                    onClick = onMarkMastered,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("complete_lesson_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("¡Practicada! Guardar progreso")
                }
            }
        }
    }
}

@Composable
private fun AddCustomSignDialog(
    onConfirm: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var title by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var guide by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar nueva seña personal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre de la seña") },
                    placeholder = { Text("Ej: Mate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Traducción hablada") },
                    placeholder = { Text("Ej: ¿Querés tomar unos mates?") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guide,
                    onValueChange = { guide = it },
                    label = { Text("Descripción del gesto (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, phrase, "Personalizada", guide) },
                enabled = title.isNotBlank() && phrase.isNotBlank()
            ) {
                Text("Guardar seña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
