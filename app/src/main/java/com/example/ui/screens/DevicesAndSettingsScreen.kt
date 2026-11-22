package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserSettings
import com.example.data.model.SignLanguageRegistry
import com.example.data.model.ThemePreference
import com.example.data.model.WearableDeviceInfo
import com.example.data.model.WearableSyncLogItem
import com.example.ui.state.CloudSyncUiState
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

@Composable
fun DevicesAndSettingsScreen(
    settings: UserSettings,
    isSystemOnline: Boolean,
    isEffectiveOffline: Boolean,
    bondedWearables: List<WearableDeviceInfo>,
    lastMirroredCaption: String,
    wearableLogs: List<WearableSyncLogItem>,
    bluetoothStatusMessage: String,
    hasBluetoothPermission: Boolean,
    hasNotificationPermission: Boolean,
    userEmail: String,
    cloudSyncState: CloudSyncUiState,
    onSignOut: () -> Unit,
    onScanWearables: () -> Unit,
    onSelectWearable: (WearableDeviceInfo) -> Unit,
    onSendWearableTest: () -> Unit,
    onSetWearableSync: (Boolean) -> Unit,
    onSetWearableMirrorHaptics: (Boolean) -> Unit,
    onSetOfflineMode: (Boolean) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onOpenLanguageSheet: () -> Unit,
    onSetHapticAlerts: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onScanWearables()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val activeProfile = SignLanguageRegistry.findByCode(settings.activeSignLanguageCode)

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
            Text(
                text = "Wearables y Ajustes",
                style = MaterialTheme.typography.headlineMedium
            )

            // Firebase Cloud Account & Sync Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val syncText = when (cloudSyncState) {
                                is CloudSyncUiState.LocalOnly -> "Modo local activo · Datos guardados en el dispositivo"
                                is CloudSyncUiState.Syncing -> "Sincronizando con Cloud Firestore..."
                                is CloudSyncUiState.Active -> "Firestore activo · ${cloudSyncState.syncedCount} registros en nube"
                                is CloudSyncUiState.Error -> cloudSyncState.message
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (cloudSyncState is CloudSyncUiState.LocalOnly) {
                                        Icons.Default.CloudOff
                                    } else {
                                        Icons.Default.CloudDone
                                    },
                                    contentDescription = null,
                                    tint = when (cloudSyncState) {
                                        is CloudSyncUiState.Error -> StatusOffline
                                        is CloudSyncUiState.LocalOnly -> MaterialTheme.colorScheme.secondary
                                        else -> StatusOnline
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = syncText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (cloudSyncState !is CloudSyncUiState.LocalOnly) {
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cerrar sesión")
                        }
                    }
                }
            }

            // Wearable Sync Card
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_wearable_sync_1795318312311),
                            contentDescription = "Ilustración de sincronización entre teléfono y reloj inteligente",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sincronización Wearable",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = settings.connectedWearableName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Espeja subtítulos y alertas hápticas en Wear OS y smartwatches.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Wrist Preview Simulation
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0A1419)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF142A35))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Watch,
                                        contentDescription = null,
                                        tint = Color(0xFF56CFE1),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "LSA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8BE8FF)
                                    )
                                    Text(
                                        text = if (settings.wearableSyncEnabled) "SYNC" else "OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Vista en muñeca",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8BE8FF)
                                )
                                Text(
                                    text = lastMirroredCaption,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    SettingSwitchRow(
                        title = "Espejo en reloj inteligente",
                        subtitle = "Envía cada traducción como alerta al reloj",
                        checked = settings.wearableSyncEnabled,
                        onCheckedChange = onSetWearableSync,
                        testTag = "wearable_sync_switch"
                    )

                    SettingSwitchRow(
                        title = "Vibración háptica en wearable",
                        subtitle = "Pulso en muñeca ante nuevas interacciones",
                        checked = settings.wearableMirrorHaptics,
                        onCheckedChange = onSetWearableMirrorHaptics,
                        testTag = "wearable_haptics_switch"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission) {
                                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    onScanWearables()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scan_wearables_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscar reloj")
                        }

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onSendWearableTest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_wearable_pulse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probar pulso")
                        }
                    }

                    Text(
                        text = bluetoothStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (bondedWearables.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bondedWearables.forEach { device ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectWearable(device) },
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (device.isWearableClass) "Wearable detectado · ${device.address}" else device.address,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Text(
                                            text = "Vincular",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (wearableLogs.isNotEmpty()) {
                        Text(
                            text = "Últimos envíos al reloj: ${wearableLogs.first().hapticPattern}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Offline & Sign Language Universality Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isEffectiveOffline) StatusOffline else StatusOnline
                        )
                        Text(
                            text = "Modo Offline y Conectividad",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = if (isSystemOnline) "Red del dispositivo disponible" else "Sin conexión a internet detectada",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSystemOnline) StatusOnline else StatusOffline
                    )
                    SettingSwitchRow(
                        title = "Forzar Modo Offline",
                        subtitle = "Usa el motor local LSA y tu vocabulario guardado sin usar internet",
                        checked = settings.isOfflineModeForced,
                        onCheckedChange = onSetOfflineMode,
                        testTag = "offline_mode_switch"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenLanguageSheet)
                            .testTag("settings_language_card"),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lengua activa: ${activeProfile.code} — ${activeProfile.name}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tocá para explorar idiomas internacionales (LSE, ASL, LIBRAS, LSCh)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Accessibility, Dark Mode & Synthesized Voice Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DarkMode, contentDescription = null)
                        Text(text = "Tema Oscuro", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { option ->
                            FilterChip(
                                selected = settings.themePreference == option,
                                onClick = { onSetTheme(option) },
                                label = { Text(option.label) },
                                modifier = Modifier.testTag("theme_chip_${option.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Vibration, contentDescription = null)
                        Text(text = "Alertas Hápticas", style = MaterialTheme.typography.titleLarge)
                    }

                    SettingSwitchRow(
                        title = "Notificaciones hápticas en tiempo real",
                        subtitle = "Patrones de vibración al traducir o recibir respuestas",
                        checked = settings.hapticAlertsEnabled,
                        onCheckedChange = onSetHapticAlerts,
                        testTag = "haptic_alerts_switch"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
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
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
