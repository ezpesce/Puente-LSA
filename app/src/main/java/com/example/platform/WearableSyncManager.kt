package com.example.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.WearableDeviceInfo
import com.example.data.model.WearableSyncLogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearableSyncManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "puente_lsa_wearable_bridge"
        private const val NOTIFICATION_ID = 4102
    }

    private val _bondedWearables = MutableStateFlow<List<WearableDeviceInfo>>(emptyList())
    val bondedWearables: StateFlow<List<WearableDeviceInfo>> = _bondedWearables.asStateFlow()

    private val _lastMirroredCaption = MutableStateFlow("Listo para sincronizar con tu reloj")
    val lastMirroredCaption: StateFlow<String> = _lastMirroredCaption.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<WearableSyncLogItem>>(emptyList())
    val syncLogs: StateFlow<List<WearableSyncLogItem>> = _syncLogs.asStateFlow()

    private val _bluetoothStatusMessage = MutableStateFlow(
        "Tocá 'Buscar reloj vinculado' para detectar wearables por Bluetooth."
    )
    val bluetoothStatusMessage: StateFlow<String> = _bluetoothStatusMessage.asStateFlow()

    init {
        ensureNotificationChannel()
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun scanBondedDevices() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null) {
            _bluetoothStatusMessage.value =
                "Adaptador Bluetooth no disponible en este entorno. El puente por notificaciones sigue activo."
            _bondedWearables.value = emptyList()
            return
        }
        if (!hasBluetoothPermission()) {
            _bluetoothStatusMessage.value =
                "Se requiere permiso de Bluetooth para leer relojes vinculados."
            return
        }
        if (!adapter.isEnabled) {
            _bluetoothStatusMessage.value =
                "Activá Bluetooth en el dispositivo para detectar wearables vinculados."
            _bondedWearables.value = emptyList()
            return
        }

        val devices = adapter.bondedDevices.orEmpty().map { device ->
            val name = device.name ?: "Dispositivo Bluetooth"
            val majorClass = device.bluetoothClass?.majorDeviceClass
            val isWearable = majorClass == BluetoothClass.Device.Major.WEARABLE ||
                name.contains("watch", ignoreCase = true) ||
                name.contains("band", ignoreCase = true) ||
                name.contains("fit", ignoreCase = true) ||
                name.contains("wear", ignoreCase = true)
            WearableDeviceInfo(
                name = name,
                address = device.address ?: "--:--",
                isWearableClass = isWearable,
                isConnected = isWearable
            )
        }.sortedByDescending { it.isWearableClass }

        _bondedWearables.value = devices
        _bluetoothStatusMessage.value = if (devices.isEmpty()) {
            "No hay dispositivos Bluetooth vinculados actualmente. Podés usar el espejo por notificaciones Wear OS."
        } else {
            "Se encontraron ${devices.size} dispositivo(s) vinculado(s)."
        }
    }

    fun mirrorToWearable(
        caption: String,
        hapticLabel: String,
        syncEnabled: Boolean,
        mirrorHaptics: Boolean
    ) {
        if (!syncEnabled) return
        val cleanCaption = caption.removePrefix("[Muestra IA]").trim()
        _lastMirroredCaption.value = cleanCaption

        val logEntry = WearableSyncLogItem(
            id = System.currentTimeMillis(),
            message = cleanCaption,
            hapticPattern = if (mirrorHaptics) hapticLabel else "Silencioso",
            timestamp = System.currentTimeMillis()
        )
        _syncLogs.value = (listOf(logEntry) + _syncLogs.value).take(12)

        if (hasNotificationPermission()) {
            try {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Puente LSA · Alerta Wearable")
                    .setContentText(cleanCaption)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(cleanCaption))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOnlyAlertOnce(false)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permission revoked dynamically
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronización Wearable LSA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Envía subtítulos en tiempo real y vibraciones hápticas al reloj conectado."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 80L, 60L, 100L)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }
}
