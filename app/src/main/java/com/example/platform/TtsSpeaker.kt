package com.example.platform

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceStatus = MutableStateFlow("Inicializando voz...")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val arLocale = Locale.forLanguageTag("es-AR")
            val result = engine.setLanguage(arLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackResult = engine.setLanguage(Locale.forLanguageTag("es-ES"))
                _voiceStatus.value = if (fallbackResult >= 0) {
                    "Voz lista (Español)"
                } else {
                    "Voz del sistema lista"
                }
            } else {
                _voiceStatus.value = "Voz Argentina (es-AR) lista"
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
            isReady = true
        } else {
            _voiceStatus.value = "Síntesis de voz no disponible en este dispositivo"
            isReady = false
        }
    }

    fun updateLocale(localeTag: String) {
        if (!isReady) return
        val locale = Locale.forLanguageTag(localeTag)
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.forLanguageTag("es-AR"))
        }
    }

    fun speak(rawText: String, speechRate: Float = 1.0f) {
        if (!isReady) return
        val cleanText = rawText
            .removePrefix("[Muestra IA]")
            .trim()
        if (cleanText.isBlank()) return

        val engine = tts ?: return
        engine.setSpeechRate(speechRate.coerceIn(0.6f, 1.5f))
        val params = Bundle()
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
