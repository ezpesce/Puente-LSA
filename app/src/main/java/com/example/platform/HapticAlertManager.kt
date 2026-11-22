package com.example.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticPattern(val label: String) {
    SIGN_RECOGNIZED("Seña detectada"),
    INTERACTION_ALERT("Nueva interacción"),
    LESSON_MASTERED("Lección completada"),
    WEARABLE_PULSE("Pulso enviado al reloj")
}

class HapticAlertManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun trigger(pattern: HapticPattern, enabled: Boolean) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timings = when (pattern) {
            HapticPattern.SIGN_RECOGNIZED -> longArrayOf(0L, 35L, 50L, 55L)
            HapticPattern.INTERACTION_ALERT -> longArrayOf(0L, 85L, 65L, 85L, 65L, 120L)
            HapticPattern.LESSON_MASTERED -> longArrayOf(0L, 45L, 45L, 95L)
            HapticPattern.WEARABLE_PULSE -> longArrayOf(0L, 60L, 80L, 60L)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = when (pattern) {
                HapticPattern.SIGN_RECOGNIZED -> intArrayOf(0, 140, 0, 210)
                HapticPattern.INTERACTION_ALERT -> intArrayOf(0, 200, 0, 220, 0, 255)
                HapticPattern.LESSON_MASTERED -> intArrayOf(0, 160, 0, 240)
                HapticPattern.WEARABLE_PULSE -> intArrayOf(0, 180, 0, 180)
            }
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings, -1)
        }
    }
}
