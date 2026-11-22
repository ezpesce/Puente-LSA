package com.example.data.model

enum class ThemePreference(val label: String) {
    SYSTEM("Automático"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

enum class SignLanguageSupportLevel(val badge: String) {
    FULL("Completo"),
    ADAPTIVE_BRIDGE("Puente Universal")
}

data class SignLanguageProfile(
    val code: String,
    val name: String,
    val region: String,
    val spokenLocaleTag: String,
    val supportLevel: SignLanguageSupportLevel,
    val description: String,
    val sampleGreeting: String
)

object SignLanguageRegistry {
    val supportedLanguages = listOf(
        SignLanguageProfile(
            code = "LSA",
            name = "Lengua de Señas Argentina",
            region = "Argentina",
            spokenLocaleTag = "es-AR",
            supportLevel = SignLanguageSupportLevel.FULL,
            description = "Diccionario completo, giros idiomáticos argentinos (voseo) y tutorial interactivo.",
            sampleGreeting = "¡Hola! ¿Cómo andás?"
        ),
        SignLanguageProfile(
            code = "LSE",
            name = "Lengua de Signos Española",
            region = "España",
            spokenLocaleTag = "es-ES",
            supportLevel = SignLanguageSupportLevel.ADAPTIVE_BRIDGE,
            description = "Módulo extensible compatible con glosas universales y adaptación castellana.",
            sampleGreeting = "¡Hola! ¿Qué tal estás?"
        ),
        SignLanguageProfile(
            code = "LSCh",
            name = "Lengua de Señas Chilena",
            region = "Chile",
            spokenLocaleTag = "es-CL",
            supportLevel = SignLanguageSupportLevel.ADAPTIVE_BRIDGE,
            description = "Estructura preparada para vocabulario regional andino e interoperabilidad.",
            sampleGreeting = "¡Hola! ¿Cómo estás?"
        ),
        SignLanguageProfile(
            code = "LIBRAS",
            name = "Língua Brasileira de Sinais",
            region = "Brasil",
            spokenLocaleTag = "pt-BR",
            supportLevel = SignLanguageSupportLevel.ADAPTIVE_BRIDGE,
            description = "Puente bilingüe Mercosur preparado para traducción cruzada portugués-español.",
            sampleGreeting = "Olá! Tudo bem?"
        ),
        SignLanguageProfile(
            code = "ASL",
            name = "American Sign Language",
            region = "Internacional / EE.UU.",
            spokenLocaleTag = "en-US",
            supportLevel = SignLanguageSupportLevel.ADAPTIVE_BRIDGE,
            description = "Perfil universal para comunicación internacional con viajeros e interlocutores.",
            sampleGreeting = "Hello! How are you?"
        )
    )

    fun findByCode(code: String): SignLanguageProfile =
        supportedLanguages.firstOrNull { it.code == code } ?: supportedLanguages.first()
}

data class WearableDeviceInfo(
    val name: String,
    val address: String,
    val isWearableClass: Boolean,
    val isConnected: Boolean
)

data class WearableSyncLogItem(
    val id: Long,
    val message: String,
    val hapticPattern: String,
    val timestamp: Long
)
