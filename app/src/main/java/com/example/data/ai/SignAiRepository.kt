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

data class GestureMotionSample(
    val overallScore: Float,
    val upperScore: Float,
    val middleScore: Float,
    val lowerScore: Float,
    val leftScore: Float,
    val rightScore: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

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

    suspend fun interpretCameraGesture(
        frames: List<Bitmap>,
        motionSamples: List<GestureMotionSample>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult = interpretCameraFrame(
        cameraBitmap = frames.lastOrNull(),
        motionScore = motionSamples.maxOfOrNull { it.overallScore } ?: 0f,
        languageCode = languageCode,
        learnedVocabulary = learnedVocabulary,
        isOffline = isOffline
    )

    suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult
}

/**
 * Offline rule-based LSA -> Argentine idiomatic Spanish engine.
 * Works 100% locally without network connectivity using Room vocabulary,
 * spatial camera motion telemetry, and Argentine voseo rules.
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

    fun classifyGestureMotion(
        motionSamples: List<GestureMotionSample>,
        languageCode: String,
        vocabulary: List<LearnedSignEntity>
    ): SignTranslationResult {
        if (motionSamples.isEmpty()) {
            return buildIdiomaticPhrase(emptyList(), languageCode, vocabulary)
        }

        val avgOverall = motionSamples.map { it.overallScore }.average().toFloat()
        val peakOverall = motionSamples.maxOf { it.overallScore }
        val avgUpper = motionSamples.map { it.upperScore }.average().toFloat()
        val avgMiddle = motionSamples.map { it.middleScore }.average().toFloat()
        val avgLower = motionSamples.map { it.lowerScore }.average().toFloat()
        val lateralBalance = kotlin.math.abs(
            motionSamples.map { it.leftScore }.average() - motionSamples.map { it.rightScore }.average()
        ).toFloat()

        // Vertical trend across the gesture (positive = moved upward, negative = moved downward)
        val firstHalfLower = motionSamples.take((motionSamples.size / 2).coerceAtLeast(1))
            .map { it.lowerScore }.average()
        val secondHalfUpper = motionSamples.takeLast((motionSamples.size / 2).coerceAtLeast(1))
            .map { it.upperScore }.average()
        val upwardShift = (secondHalfUpper - firstHalfLower).toFloat()

        val (detectedGloss, motionSummary) = when {
            avgUpper >= avgMiddle && avgUpper >= avgLower && lateralBalance > 0.04f ->
                listOf("HOLA") to "movimiento en zona superior/sien"
            avgUpper >= avgMiddle && peakOverall > 0.55f ->
                listOf("NO_ENTIENDO") to "gesto breve en zona superior"
            upwardShift > 0.06f && peakOverall > 0.42f ->
                listOf("AYUDA") to "elevación progresiva de manos"
            avgMiddle >= avgUpper && avgOverall in 0.12f..0.34f ->
                listOf("MAS_DESPACIO") to "movimiento pausado frente al torso"
            avgMiddle >= avgUpper && avgLower >= avgUpper * 0.85f ->
                listOf("GRACIAS") to "proyección desde mentón/pecho hacia el frente"
            lateralBalance > 0.03f && avgMiddle >= avgLower ->
                listOf("DONDE") to "vaivén lateral en zona media"
            else ->
                listOf("POR_FAVOR") to "movimiento suave en zona central"
        }

        val base = buildIdiomaticPhrase(detectedGloss, languageCode, vocabulary)
        val confidence = ((peakOverall * 45f) + 50f).toInt().coerceIn(58, 89)
        return base.copy(
            culturalNote = "Interpretado localmente ($motionSummary). Podés combinar señas abajo o activar IA visual en 'IA y Voz'.",
            confidencePercent = confidence,
            isAiSampleMode = false,
            sourceLabel = "Sensor de señas local ($languageCode)"
        )
    }

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
                culturalNote = "Tocá 'Traducir seña' y hacé el gesto frente a la cámara, o elegí señas abajo.",
                confidencePercent = 90,
                isAiSampleMode = false,
                sourceLabel = "Motor Local ($languageCode)"
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
            "LSA" -> "Giro idiomático argentino (voseo natural) armado localmente."
            else -> "Adaptación puente desde $languageCode mediante el diccionario local."
        }

        return SignTranslationResult(
            detectedGlosses = glossKeys,
            idiomaticPhrase = localizedPhrase,
            culturalNote = note,
            confidencePercent = 92,
            isAiSampleMode = false,
            sourceLabel = "Motor Local ($languageCode)"
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
 * Uses structured JSON schema output and real camera gesture frames when AI extra is enabled & online,
 * and delegates to [OfflineSignEngine] when local/offline mode is active.
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
                temperature = 0.3f
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
        val frames = if (cameraBitmap != null) listOf(cameraBitmap) else emptyList()
        val samples = if (motionScore > 0f) {
            listOf(
                GestureMotionSample(
                    overallScore = motionScore,
                    upperScore = motionScore,
                    middleScore = motionScore * 0.8f,
                    lowerScore = motionScore * 0.5f,
                    leftScore = motionScore * 0.5f,
                    rightScore = motionScore * 0.5f
                )
            )
        } else {
            emptyList()
        }
        return interpretCameraGesture(frames, samples, languageCode, learnedVocabulary, isOffline)
    }

    override suspend fun interpretCameraGesture(
        frames: List<Bitmap>,
        motionSamples: List<GestureMotionSample>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        if (isOffline) {
            return OfflineSignEngine.classifyGestureMotion(motionSamples, languageCode, learnedVocabulary)
        }

        val profile = SignLanguageRegistry.findByCode(languageCode)
        val frequentGlossHint = learnedVocabulary
            .sortedByDescending { it.usageCount }
            .take(8)
            .joinToString(", ") { "${it.glossKey} (${it.displayTitle})" }
        val peakMotion = ((motionSamples.maxOfOrNull { it.overallScore } ?: 0f) * 100).toInt()

        val prompt = """
            Sos un intérprete experto en ${profile.name} ($languageCode, región: ${profile.region}).
            Analizá esta secuencia de ${frames.size} cuadro(s) capturados mientras el usuario realizaba una seña frente a la cámara (movimiento pico detectado: $peakMotion%).
            Vocabulario frecuente personalizado del usuario: $frequentGlossHint.
            Identificá la seña o intención comunicativa observando manos, postura y expresión facial.
            Para LSA generá español argentino natural con voseo (ej: "¿Cómo andás?", "¿Me ayudás por favor?").
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                if (frames.isNotEmpty()) {
                    val scaledFrames = frames.takeLast(2).map { scaleBitmapIfNeeded(it, 640) }
                    model.generateContent(
                        content {
                            scaledFrames.forEach { image(it) }
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
 * Local deterministic sign repository used when Firebase is not configured in the APK.
 * Uses real CameraX spatial motion telemetry and local LSA grammar without random phrases.
 */
class LocalPlaceholderSignAiRepository : SignAiRepository {
    override val requiresFirebaseNotice: Boolean = true

    override suspend fun interpretCameraFrame(
        cameraBitmap: Bitmap?,
        motionScore: Float,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        val samples = if (motionScore > 0f) {
            listOf(
                GestureMotionSample(
                    overallScore = motionScore,
                    upperScore = motionScore,
                    middleScore = motionScore * 0.8f,
                    lowerScore = motionScore * 0.4f,
                    leftScore = motionScore * 0.6f,
                    rightScore = motionScore * 0.4f
                )
            )
        } else {
            emptyList()
        }
        return OfflineSignEngine.classifyGestureMotion(samples, languageCode, learnedVocabulary)
    }

    override suspend fun interpretCameraGesture(
        frames: List<Bitmap>,
        motionSamples: List<GestureMotionSample>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        return OfflineSignEngine.classifyGestureMotion(motionSamples, languageCode, learnedVocabulary)
    }

    override suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        return OfflineSignEngine.buildIdiomaticPhrase(glossSequence, languageCode, learnedVocabulary)
    }
}
