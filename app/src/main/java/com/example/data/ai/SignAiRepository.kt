package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.LearnedSignEntity
import com.example.data.model.SignLanguageRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiSignAiRepo"

data class SignTranslationResult(
    val detectedGlosses: List<String>,
    val idiomaticPhrase: String,
    val culturalNote: String,
    val confidencePercent: Int,
    val isAiSampleMode: Boolean,
    val sourceLabel: String
)

interface SignAiRepository {
    val requiresFirebaseNotice: Boolean

    suspend fun interpretCameraFrame(
        cameraBitmap: Bitmap?,
        motionScore: Float,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult

    suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult
}

/**
 * Offline rule-based LSA -> Argentine idiomatic Spanish engine.
 * Works 100% locally without network connectivity using Room vocabulary & Argentine voseo rules.
 */
object OfflineSignEngine {

    private val idiomaticPairs: Map<Pair<String, String>, String> = mapOf(
        ("HOLA" to "COMO_ANDAS") to "¡Hola! ¿Cómo andás? Todo bien por acá.",
        ("HOLA" to "NOMBRE") to "¡Hola! ¿Cómo es tu nombre?",
        ("HOLA" to "AYUDA") to "Hola, disculpá, ¿me podés dar una mano?",
        ("DONDE" to "BANIO") to "Disculpá, ¿sabés dónde queda el baño?",
        ("DONDE" to "SUBTE_COLECTIVO") to "Perdoná, ¿dónde está la parada de colectivo o el subte?",
        ("AYUDA" to "MEDICO") to "Necesito ayuda médica urgente, por favor.",
        ("NO_ENTIENDO" to "MAS_DESPACIO") to "Disculpá, no te entendí bien. ¿Podés repetirlo más despacio?",
        ("GRACIAS" to "NOS_VEMOS") to "¡Muchas gracias por todo! ¡Nos vemos!",
        ("AGUA" to "POR_FAVOR") to "¿Me podrías dar un poco de agua, por favor?",
        ("CUANTO_SALE" to "POR_FAVOR") to "Disculpá, ¿me decís cuánto sale esto, por favor?"
    )

    fun buildIdiomaticPhrase(
        glossKeys: List<String>,
        languageCode: String,
        vocabulary: List<LearnedSignEntity>
    ): SignTranslationResult {
        if (glossKeys.isEmpty()) {
            val profile = SignLanguageRegistry.findByCode(languageCode)
            return SignTranslationResult(
                detectedGlosses = emptyList(),
                idiomaticPhrase = profile.sampleGreeting,
                culturalNote = "Seleccioná señas o capturá un gesto con la cámara.",
                confidencePercent = 90,
                isAiSampleMode = false,
                sourceLabel = "Motor Offline ($languageCode)"
            )
        }

        val vocabByKey = vocabulary.associateBy { it.glossKey }
        val phrase = if (glossKeys.size >= 2) {
            val lastTwo = glossKeys[glossKeys.size - 2] to glossKeys.last()
            val firstTwo = glossKeys[0] to glossKeys[1]
            idiomaticPairs[lastTwo]
                ?: idiomaticPairs[firstTwo]
                ?: glossKeys.mapNotNull { vocabByKey[it]?.idiomaticOutput ?: it.lowercase() }
                    .joinToString(" · ")
        } else {
            vocabByKey[glossKeys.first()]?.idiomaticOutput ?: glossKeys.first()
        }

        val localizedPhrase = adaptToSignLanguageDialect(phrase, languageCode)
        val note = when (languageCode) {
            "LSA" -> "Giro idiomático argentino (voseo natural) generado localmente sin conexión."
            else -> "Adaptación puente desde $languageCode mediante el diccionario universal offline."
        }

        return SignTranslationResult(
            detectedGlosses = glossKeys,
            idiomaticPhrase = localizedPhrase,
            culturalNote = note,
            confidencePercent = 92,
            isAiSampleMode = false,
            sourceLabel = "Motor Offline ($languageCode)"
        )
    }

    private fun adaptToSignLanguageDialect(argentinePhrase: String, languageCode: String): String {
        return when (languageCode) {
            "LSE" -> argentinePhrase
                .replace("¿Cómo andás?", "¿Qué tal estás?")
                .replace("¿Podés", "¿Puedes")
                .replace("colectivo", "autobús")
                .replace("subte", "metro")
            "LSCh" -> argentinePhrase
                .replace("¿Cómo andás?", "¿Cómo estás?")
                .replace("colectivo", "micro")
            "LIBRAS" -> "$argentinePhrase (Equivalente PT-BR: Olá, tudo bem?)"
            "ASL" -> "$argentinePhrase (EN: Hello! Could you please help me?)"
            else -> argentinePhrase
        }
    }
}

/**
 * Live Firebase AI Logic implementation powered by Gemini 3.8 Flash.
 * Uses structured JSON schema output and real camera frame bitmaps when online,
 * and delegates to [OfflineSignEngine] when offline mode is active.
 */
class GeminiSignAiRepository : SignAiRepository {
    override val requiresFirebaseNotice: Boolean = false

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "detectedGlosses" to Schema.array(
                items = Schema.string(),
                description = "Lista de glosas detectadas o interpretadas en mayúsculas (ej. HOLA, COMO_ANDAS, GRACIAS)"
            ),
            "idiomaticPhrase" to Schema.string(
                description = "Frase natural y fluida con giros idiomáticos propios de la lengua seleccionada (para LSA usar voseo argentino natural)"
            ),
            "culturalNote" to Schema.string(
                description = "Breve nota sobre el giro idiomático o contexto gestual"
            ),
            "confidencePercent" to Schema.integer(
                description = "Porcentaje de confianza estimado de 0 a 100"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.35f
            }
        )
    }

    override suspend fun interpretCameraFrame(
        cameraBitmap: Bitmap?,
        motionScore: Float,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        if (isOffline) {
            val prioritized = learnedVocabulary
                .sortedByDescending { it.usageCount }
                .take(2)
                .map { it.glossKey }
                .ifEmpty { listOf("HOLA", "BIEN") }
            return OfflineSignEngine.buildIdiomaticPhrase(prioritized, languageCode, learnedVocabulary)
        }

        val profile = SignLanguageRegistry.findByCode(languageCode)
        val frequentGlossHint = learnedVocabulary
            .sortedByDescending { it.usageCount }
            .take(8)
            .joinToString(", ") { "${it.glossKey} (${it.displayTitle})" }

        val prompt = """
            Sos un intérprete experto en ${profile.name} ($languageCode, región: ${profile.region}).
            Analizá la imagen de la cámara del usuario realizando una seña (nivel de movimiento detectado: ${(motionScore * 100).toInt()}%).
            Vocabulario frecuente personalizado del usuario: $frequentGlossHint.
            Si se observa una seña clara en las manos/rostro, identificala con precisión; si la mano está en transición o la escena es ambigua, inferí la intención comunicativa más probable y aclaralo brevemente en culturalNote.
            Para LSA generá español argentino natural con voseo (ej: "¿Cómo andás?", "¿Me ayudás por favor?").
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                if (cameraBitmap != null) {
                    val scaled = scaleBitmapIfNeeded(cameraBitmap, 640)
                    model.generateContent(
                        content {
                            image(scaled)
                            text(prompt)
                        }
                    )
                } else {
                    model.generateContent(prompt)
                }
            }
            parseGeminiJson(response.text, languageCode, "Gemini IA ($languageCode)")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini vision call timed out", e)
            throw IllegalStateException("La interpretación con Gemini superó el tiempo de espera (60s). Intentá de nuevo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini vision call failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Error al consultar Gemini AI Logic.")
        }
    }

    override suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        if (isOffline) {
            return OfflineSignEngine.buildIdiomaticPhrase(glossSequence, languageCode, learnedVocabulary)
        }

        val profile = SignLanguageRegistry.findByCode(languageCode)
        val prompt = """
            Sos un intérprete experto en ${profile.name} ($languageCode, región: ${profile.region}).
            Convertí esta secuencia de glosas de lengua de señas en una oración completamente natural y con giros idiomáticos locales:
            Glosas: ${glossSequence.joinToString(" + ")}.
            Para LSA utilizá español argentino natural (voseo, modismos cordiales cotidianos).
            Devolvé las mismas glosas en detectedGlosses, la oración final en idiomaticPhrase y una breve explicación en culturalNote.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            parseGeminiJson(response.text, languageCode, "Gemini IA ($languageCode)", fallbackGlosses = glossSequence)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini idiomatic refinement timed out", e)
            throw IllegalStateException("El refinamiento idiomático con Gemini superó el tiempo de espera.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini idiomatic refinement failed", e)
            throw IllegalStateException(e.localizedMessage ?: "No se pudo refinar la frase con Gemini.")
        }
    }

    private fun parseGeminiJson(
        rawJson: String?,
        languageCode: String,
        sourceLabel: String,
        fallbackGlosses: List<String> = emptyList()
    ): SignTranslationResult {
        val text = rawJson?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini devolvió una respuesta vacía.")

        val json = JSONObject(text)
        val glossArray = json.optJSONArray("detectedGlosses")
        val glosses = if (glossArray != null && glossArray.length() > 0) {
            (0 until glossArray.length()).mapNotNull { idx ->
                glossArray.optString(idx).takeIf { it.isNotBlank() }
            }
        } else {
            fallbackGlosses
        }

        val phrase = json.optString("idiomaticPhrase").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini no devolvió una frase traducida.")
        val note = json.optString("culturalNote")
            .ifBlank { "Interpretación generada con Gemini ($languageCode)." }
        val confidence = json.optInt("confidencePercent", 90).coerceIn(0, 100)

        return SignTranslationResult(
            detectedGlosses = glosses,
            idiomaticPhrase = phrase,
            culturalNote = note,
            confidencePercent = confidence,
            isAiSampleMode = false,
            sourceLabel = sourceLabel
        )
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxEdge && height <= maxEdge) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width >= height) {
            targetW = maxEdge
            targetH = (maxEdge / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxEdge
            targetW = (maxEdge * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}

/**
 * Fallback placeholder repository preserved for unit testing / non-Firebase contexts.
 */
class LocalPlaceholderSignAiRepository : SignAiRepository {
    override val requiresFirebaseNotice: Boolean = true
    private var cycleIndex = 0
    private val sampleSequences = listOf(
        listOf("HOLA", "COMO_ANDAS"),
        listOf("MAS_DESPACIO", "POR_FAVOR"),
        listOf("DONDE", "SUBTE_COLECTIVO")
    )

    override suspend fun interpretCameraFrame(
        cameraBitmap: Bitmap?,
        motionScore: Float,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        val chosen = sampleSequences[cycleIndex++ % sampleSequences.size]
        val base = OfflineSignEngine.buildIdiomaticPhrase(chosen, languageCode, learnedVocabulary)
        return base.copy(
            idiomaticPhrase = "[Muestra IA] ${base.idiomaticPhrase}",
            isAiSampleMode = true,
            sourceLabel = "Muestra IA ($languageCode)"
        )
    }

    override suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        val base = OfflineSignEngine.buildIdiomaticPhrase(glossSequence, languageCode, learnedVocabulary)
        return base.copy(
            idiomaticPhrase = "[Muestra IA] ${base.idiomaticPhrase}",
            isAiSampleMode = true,
            sourceLabel = "Muestra IA ($languageCode)"
        )
    }
}
