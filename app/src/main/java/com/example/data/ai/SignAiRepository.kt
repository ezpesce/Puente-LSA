package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.LearnedSignEntity
import com.example.data.model.SignLanguageRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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

data class ExternalSourceLink(
    val title: String,
    val uri: String,
    val provider: String
)

data class LearnedSignProposal(
    val glossKey: String,
    val displayTitle: String,
    val idiomaticOutput: String,
    val category: String,
    val handshapeGuide: String,
    val facialCue: String
)

data class SignTranslationResult(
    val detectedGlosses: List<String>,
    val idiomaticPhrase: String,
    val culturalNote: String,
    val confidencePercent: Int,
    val isAiSampleMode: Boolean,
    val sourceLabel: String,
    val externalSources: List<ExternalSourceLink> = emptyList(),
    val searchEntryPointHtml: String? = null,
    val newlyLearnedSign: LearnedSignProposal? = null
)

data class ExternalSignSearchResult(
    val query: String,
    val glossKey: String,
    val displayTitle: String,
    val idiomaticPhrase: String,
    val handshapeGuide: String,
    val facialCue: String,
    val category: String,
    val culturalNote: String,
    val externalSources: List<ExternalSourceLink> = emptyList(),
    val searchEntryPointHtml: String? = null,
    val usedGoogleSearch: Boolean = false,
    val usedExternalDatabases: Boolean = false
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

    suspend fun searchExternalSignKnowledge(
        query: String,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): ExternalSignSearchResult
}

/**
 * Offline rule-based LSA -> Argentine idiomatic Spanish engine.
 * Works 100% locally for composing phrases from the Room dictionary and quick-sign chips.
 * Note: Visual handshape recognition from camera pixels requires Gemini Vision AI online
 * and is never faked locally.
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
                culturalNote = "Tocá 'Traducir seña' con IA activa frente a la cámara, o elegí señas rápidas abajo.",
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
            "LSA" -> "Giro idiomático argentino (voseo natural) armado desde tu diccionario local."
            else -> "Adaptación puente desde $languageCode mediante tu diccionario local."
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
 * Combines:
 * 1) Multi-frame multimodal camera gesture analysis grounded with Google Search (`Tool.googleSearch()`),
 * 2) Function calling (`Tool.functionDeclarations`) querying Room learned vocabulary + real external
 *    open knowledge databases (Wikipedia ES, Wikcionario ES, Wikidata, Wikimedia Commons),
 * 3) Strict structured JSON extraction (`Schema.obj`) that learns and persists new LSA signs.
 */
class GeminiSignAiRepository : SignAiRepository {
    override val requiresFirebaseNotice: Boolean = false

    private val cameraAnalysisSchema = Schema.obj(
        properties = mapOf(
            "signDetected" to Schema.boolean(
                description = "true SOLO si realmente se distinguen manos realizando una seña reconocible en las imágenes; false si no se ven manos o no hay una seña clara"
            ),
            "unrecognizedReason" to Schema.string(
                description = "Si signDetected es false, explicación breve en español de qué se vio o por qué no se pudo reconocer la seña",
                nullable = true
            ),
            "detectedGlosses" to Schema.array(
                items = Schema.string(),
                description = "Lista de glosas detectadas en MAYUSCULAS_CON_GUION (ej. HOLA, BUENOS_DIAS, GRACIAS)"
            ),
            "idiomaticPhrase" to Schema.string(
                description = "Frase natural y fluida en español argentino (voseo) o en la lengua configurada"
            ),
            "culturalNote" to Schema.string(
                description = "Descripción breve de la configuración de mano, movimiento observado en los cuadros y contexto idiomático"
            ),
            "confidencePercent" to Schema.integer(
                description = "Porcentaje de confianza estimado de 0 a 100"
            ),
            "learnedSignTitle" to Schema.string(
                description = "Nombre legible de la seña principal detectada para guardar en el diccionario (ej. 'Buenos días')",
                nullable = true
            ),
            "learnedHandshapeGuide" to Schema.string(
                description = "Guía breve de cómo se realiza la seña observada (configuración manual y trayectoria)",
                nullable = true
            ),
            "learnedFacialCue" to Schema.string(
                description = "Expresión facial o rasgo no manual asociado",
                nullable = true
            ),
            "learnedCategory" to Schema.string(
                description = "Categoría breve (Saludos, Cortesía, Emergencia, Transporte, Cotidiano, Salud, Aprendida IA)",
                nullable = true
            )
        ),
        optionalProperties = listOf(
            "unrecognizedReason",
            "learnedSignTitle",
            "learnedHandshapeGuide",
            "learnedFacialCue",
            "learnedCategory"
        )
    )

    private val externalSearchSchema = Schema.obj(
        properties = mapOf(
            "glossKey" to Schema.string(description = "Clave de glosa en MAYUSCULAS_SIN_TILDES (ej. BUENOS_DIAS)"),
            "displayTitle" to Schema.string(description = "Título claro de la seña (ej. 'Buenos días')"),
            "idiomaticPhrase" to Schema.string(description = "Frase natural idiomática en español argentino"),
            "handshapeGuide" to Schema.string(description = "Explicación paso a paso de configuración de mano, ubicación y movimiento"),
            "facialCue" to Schema.string(description = "Expresión facial / componente no manual recomendado"),
            "category" to Schema.string(description = "Categoría temática (Saludos, Salud, Transporte, Emergencia, Cotidiano, Aprendida IA)"),
            "culturalNote" to Schema.string(description = "Resumen de lo encontrado en diccionarios o fuentes externas sobre esta seña")
        )
    )

    private val searchLocalVocabTool = FunctionDeclaration(
        name = "buscarEnVocabularioLocal",
        description = "Busca señas ya aprendidas o guardadas localmente por el usuario en Puente LSA.",
        parameters = mapOf(
            "consulta" to Schema.string(description = "Palabra, frase o glosa a buscar en el diccionario local")
        )
    )

    private val searchExternalDatabasesTool = FunctionDeclaration(
        name = "buscarEnBasesExternas",
        description = "Consulta bases externas abiertas (Wikipedia ES, Wikcionario ES, Wikidata y Wikimedia Commons) para obtener contexto lingüístico y referencias de lengua de señas.",
        parameters = mapOf(
            "termino" to Schema.string(description = "Palabra o concepto en español para consultar en bases externas"),
            "codigoLengua" to Schema.string(description = "Código de lengua de señas, ej. LSA, LSE, LSCh, LIBRAS, ASL")
        )
    )

    // Model 1: Multimodal + Google Search Grounding (cannot be combined with responseSchema)
    private val visionGroundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch())
        )
    }

    // Model 2: Function Calling against local Room + external Wikimedia/Wikipedia/Wikidata APIs
    private val externalToolModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(
                Tool.functionDeclarations(listOf(searchLocalVocabTool, searchExternalDatabasesTool))
            )
        )
    }

    // Model 3: Strict structured JSON synthesizer
    private val structuredJsonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = cameraAnalysisSchema
                temperature = 0.2f
            }
        )
    }

    private val externalSearchJsonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = externalSearchSchema
                temperature = 0.2f
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
            throw IllegalStateException(
                "Sin conexión a internet: la visión IA de cámara requiere conexión. Podés usar las señas rápidas de abajo sin conexión."
            )
        }
        if (frames.isEmpty()) {
            throw IllegalStateException(
                "No se capturaron cuadros válidos de la cámara. Verificá los permisos e intentá nuevamente."
            )
        }

        val profile = SignLanguageRegistry.findByCode(languageCode)
        val vocabSummary = learnedVocabulary
            .sortedByDescending { it.usageCount }
            .take(18)
            .joinToString("\n") { "- ${it.glossKey} (${it.displayTitle}): ${it.handshapeGuide}" }

        val motionSummary = buildMotionDirectionSummary(motionSamples)
        val scaledFrames = takeChronologicalSample(frames, maxFrames = 3)
            .map { scaleBitmapIfNeeded(it, 768) }

        val groundingPrompt = """
            Sos un intérprete experto en ${profile.name} ($languageCode, región: ${profile.region}).
            Te envío ${scaledFrames.size} cuadros capturados en orden cronológico (inicio -> desarrollo -> final) mientras la persona intentaba hacer una seña o frase en lengua de señas.
            Telemetría de movimiento: $motionSummary.

            Vocabulario ya aprendido por la app:
            $vocabSummary

            INSTRUCCIONES CRÍTICAS DE PRECISIÓN VISUAL:
            1. Observá primero QUÉ aparece realmente en los cuadros: ¿se ven manos, dedos, posición respecto al rostro/pecho y desplazamiento entre el cuadro 1 y el último cuadro?
            2. Si NO se ven manos realizando una seña clara (ej. solo pared/techo, persona quieta sin señar, imagen muy oscura o manos fuera de cuadro), decí explícitamente: "NO_SE_OBSERVA_SENA" y explicá qué viste realmente. NO inventes ni adivines frases como "Por favor".
            3. Si SÍ observás una seña o secuencia gestual, describí con exactitud la configuración manual, ubicación y trayectoria vista entre los cuadros, y si necesitás confirmar su significado en ${profile.name} ($languageCode), buscá en diccionarios o fuentes web de Lengua de Señas Argentina / $languageCode mediante búsqueda web.
            4. Indicá la(s) glosa(s) detectada(s), una frase idiomática natural (en LSA usá voseo argentino) y si conviene aprender esa seña en el diccionario de la app.
        """.trimIndent()

        return try {
            // Step 1: Multimodal Vision + Google Search Grounding
            val visionResponse = withTimeout(60_000L) {
                visionGroundedModel.generateContent(
                    content {
                        scaledFrames.forEachIndexed { idx, bmp ->
                            text("Cuadro ${idx + 1} de ${scaledFrames.size}:")
                            image(bmp)
                        }
                        text(groundingPrompt)
                    }
                )
            }

            val rawObservation = visionResponse.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini Vision no devolvió análisis de los cuadros.")

            val groundingMeta = visionResponse.candidates.firstOrNull()?.groundingMetadata
            val webSources = groundingMeta?.groundingChunks
                ?.mapNotNull { chunk ->
                    val web = chunk.web ?: return@mapNotNull null
                    val uri = web.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                    ExternalSourceLink(title = title, uri = uri, provider = "Google Search")
                }
                ?.distinctBy { it.uri }
                ?.take(5)
                .orEmpty()
            val searchEntryHtml = groundingMeta?.searchEntryPoint?.renderedContent

            // Step 2: Optionally query external open knowledge bases if a candidate concept surfaced
            val externalHits = if (!rawObservation.contains("NO_SE_OBSERVA_SENA", ignoreCase = true)) {
                val hintWords = extractCandidateSearchTerm(rawObservation)
                if (hintWords.isNotBlank()) {
                    ExternalSignKnowledgeClient.lookupSignConcept(hintWords, languageCode)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val combinedSources = (webSources + externalHits.map {
                ExternalSourceLink(title = "${it.source}: ${it.title}", uri = it.url, provider = it.source)
            }).distinctBy { it.uri }.take(6)

            // Step 3: Structure the multimodal + grounded observation into validated JSON
            val structPrompt = """
                Convertí este análisis visual real de ${profile.name} ($languageCode) en JSON estructurado.
                Análisis visual y búsqueda:
                $rawObservation

                Fuentes externas consultadas:
                ${externalHits.joinToString("\n") { "- [${it.source}] ${it.title}: ${it.snippet}" }}

                Reglas estrictas:
                - Si el análisis indica que no se observó una seña o no se ven manos claras ("NO_SE_OBSERVA_SENA"), poné signDetected = false y explicá en unrecognizedReason qué faltó (ej. alejar/acercar manos o mejorar luz).
                - Si signDetected = true, completá detectedGlosses en MAYÚSCULAS, idiomaticPhrase natural argentina, culturalNote explicando qué manos/movimiento se vieron en cámara, confidencePercent, y los campos learned* para enriquecer la base local.
            """.trimIndent()

            val jsonResponse = withTimeout(45_000L) {
                structuredJsonModel.generateContent(structPrompt)
            }

            parseCameraAnalysisJson(
                rawJson = jsonResponse.text,
                languageCode = languageCode,
                sources = combinedSources,
                searchEntryHtml = searchEntryHtml
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini vision call timed out", e)
            throw IllegalStateException("La interpretación visual con Gemini superó el tiempo de espera (60s). Intentá nuevamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini vision call failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Error al consultar Gemini Vision AI Logic."
            )
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
            Devolvé signDetected = true, las glosas en detectedGlosses, la oración final en idiomaticPhrase y una breve explicación en culturalNote.
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                structuredJsonModel.generateContent(prompt)
            }
            parseCameraAnalysisJson(
                rawJson = response.text,
                languageCode = languageCode,
                sources = emptyList(),
                searchEntryHtml = null,
                fallbackGlosses = glossSequence
            )
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

    override suspend fun searchExternalSignKnowledge(
        query: String,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): ExternalSignSearchResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            throw IllegalArgumentException("Escribí una palabra o frase para buscar.")
        }
        if (isOffline) {
            throw IllegalStateException("Necesitás conexión a internet para consultar fuentes externas y aprendizaje con IA.")
        }

        val profile = SignLanguageRegistry.findByCode(languageCode)
        val collectedHits = mutableListOf<ExternalKnowledgeHit>()
        var usedExternalDbTool = false

        return try {
            // Step 1: Gemini Function Calling over local Room vocabulary + external Wikipedia/Wiktionary/Wikidata APIs
            val toolSummaryText = withTimeout(50_000L) {
                val chat = externalToolModel.startChat()
                var response = chat.sendMessage(
                    """
                    El usuario quiere aprender cómo se expresa "$cleanQuery" en ${profile.name} ($languageCode) para enseñárselo a Puente LSA.
                    Usá primero las herramientas disponibles (`buscarEnVocabularioLocal` y `buscarEnBasesExternas`) para revisar qué sabe la app y qué información lingüística o de señas existe en bases abiertas externas.
                    """.trimIndent()
                )

                var rounds = 0
                while (response.functionCalls.isNotEmpty() && rounds < 3) {
                    rounds++
                    val responseParts = response.functionCalls.map { call ->
                        when (call.name) {
                            "buscarEnVocabularioLocal" -> {
                                val term = call.args["consulta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val matches = learnedVocabulary.filter {
                                    it.displayTitle.contains(term, ignoreCase = true) ||
                                        it.glossKey.contains(term, ignoreCase = true) ||
                                        it.idiomaticOutput.contains(term, ignoreCase = true)
                                }.take(5)
                                val jsonPayload = JsonObject(
                                    mapOf(
                                        "encontradas" to JsonPrimitive(matches.size),
                                        "senas" to JsonArray(
                                            matches.map {
                                                JsonObject(
                                                    mapOf(
                                                        "glossKey" to JsonPrimitive(it.glossKey),
                                                        "displayTitle" to JsonPrimitive(it.displayTitle),
                                                        "handshapeGuide" to JsonPrimitive(it.handshapeGuide)
                                                    )
                                                )
                                            }
                                        )
                                    )
                                )
                                FunctionResponsePart(call.name, jsonPayload)
                            }
                            "buscarEnBasesExternas" -> {
                                usedExternalDbTool = true
                                val term = call.args["termino"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() } ?: cleanQuery
                                val lang = call.args["codigoLengua"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() } ?: languageCode
                                val hits = ExternalSignKnowledgeClient.lookupSignConcept(term, lang)
                                collectedHits += hits
                                val jsonPayload = JsonObject(
                                    mapOf(
                                        "totalResultados" to JsonPrimitive(hits.size),
                                        "resultados" to JsonArray(
                                            hits.map {
                                                JsonObject(
                                                    mapOf(
                                                        "fuente" to JsonPrimitive(it.source),
                                                        "titulo" to JsonPrimitive(it.title),
                                                        "resumen" to JsonPrimitive(it.snippet),
                                                        "url" to JsonPrimitive(it.url)
                                                    )
                                                )
                                            }
                                        )
                                    )
                                )
                                FunctionResponsePart(call.name, jsonPayload)
                            }
                            else -> FunctionResponsePart(
                                call.name,
                                JsonObject(mapOf("status" to JsonPrimitive("unsupported")))
                            )
                        }
                    }
                    response = chat.sendMessage(
                        content("user") {
                            responseParts.forEach { part(it) }
                        }
                    )
                }
                response.text.orEmpty()
            }

            // Ensure external lookup ran at least once even if tool call wasn't invoked by model
            if (collectedHits.isEmpty()) {
                collectedHits += ExternalSignKnowledgeClient.lookupSignConcept(cleanQuery, languageCode)
                usedExternalDbTool = collectedHits.isNotEmpty()
            }

            // Step 2: Live Google Search Grounding for authentic LSA dictionary & web references
            val groundedResponse = withTimeout(50_000L) {
                visionGroundedModel.generateContent(
                    """
                    Buscá información verificable sobre cómo se realiza la seña "$cleanQuery" en ${profile.name} ($languageCode, Argentina/región).
                    Indicá: configuración manual (forma de la mano), ubicación espacial, movimiento, expresión facial (rasgo no manual) y una frase idiomática natural.
                    Si el término suele deletrearse en dactilología LSA o combina señas conocidas, aclarálo con honestidad sin inventar señas falsas.
                    """.trimIndent()
                )
            }

            val groundingMeta = groundedResponse.candidates.firstOrNull()?.groundingMetadata
            val webSources = groundingMeta?.groundingChunks
                ?.mapNotNull { chunk ->
                    val web = chunk.web ?: return@mapNotNull null
                    val uri = web.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                    ExternalSourceLink(title = title, uri = uri, provider = "Google Search")
                }
                ?.distinctBy { it.uri }
                ?.take(5)
                .orEmpty()
            val searchEntryHtml = groundingMeta?.searchEntryPoint?.renderedContent

            val combinedSources = (webSources + collectedHits.map {
                ExternalSourceLink(title = "${it.source}: ${it.title}", uri = it.url, provider = it.source)
            }).distinctBy { it.uri }.take(7)

            // Step 3: Synthesize structured JSON for UI display and Room/Firestore persistence
            val synthPrompt = """
                Sintetizá esta búsqueda externa para guardar en el diccionario de ${profile.name} ($languageCode).
                Consulta del usuario: "$cleanQuery"

                Información de Google Search Grounding:
                ${groundedResponse.text.orEmpty()}

                Información de herramientas y bases abiertas (Wikipedia/Wikcionario/Wikidata):
                $toolSummaryText
                ${collectedHits.joinToString("\n") { "- [${it.source}] ${it.title}: ${it.snippet}" }}
            """.trimIndent()

            val structured = withTimeout(45_000L) {
                externalSearchJsonModel.generateContent(synthPrompt)
            }
            val jsonText = structured.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No se pudo estructurar el resultado de búsqueda externa.")
            val json = JSONObject(jsonText)

            val fallbackGloss = cleanQuery.uppercase()
                .replace(" ", "_")
                .replace(Regex("[^A-Z0-9_]"), "")
                .ifBlank { "SENA_EXTERNA" }

            ExternalSignSearchResult(
                query = cleanQuery,
                glossKey = json.optString("glossKey").ifBlank { fallbackGloss },
                displayTitle = json.optString("displayTitle").ifBlank { cleanQuery },
                idiomaticPhrase = json.optString("idiomaticPhrase").ifBlank { cleanQuery },
                handshapeGuide = json.optString("handshapeGuide")
                    .ifBlank { "Referencia consultada externamente para $cleanQuery en $languageCode." },
                facialCue = json.optString("facialCue")
                    .ifBlank { "Expresión facial acorde al contexto comunicativo." },
                category = json.optString("category").ifBlank { "Aprendida IA" },
                culturalNote = json.optString("culturalNote")
                    .ifBlank { "Aprendida mediante búsqueda externa y Google Search Grounding." },
                externalSources = combinedSources,
                searchEntryPointHtml = searchEntryHtml,
                usedGoogleSearch = webSources.isNotEmpty() || !groundedResponse.text.isNullOrBlank(),
                usedExternalDatabases = usedExternalDbTool || collectedHits.isNotEmpty()
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "External AI sign search timed out", e)
            throw IllegalStateException("La búsqueda externa con IA superó el tiempo de espera. Intentá de nuevo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "External AI sign search failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Error al buscar en bases externas con IA.")
        }
    }

    private fun parseCameraAnalysisJson(
        rawJson: String?,
        languageCode: String,
        sources: List<ExternalSourceLink>,
        searchEntryHtml: String?,
        fallbackGlosses: List<String> = emptyList()
    ): SignTranslationResult {
        val text = rawJson?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini devolvió una respuesta vacía.")

        val json = JSONObject(text)
        val signDetected = json.optBoolean("signDetected", true)
        if (!signDetected) {
            val reason = json.optString("unrecognizedReason")
                .ifBlank {
                    "No se distinguieron manos realizando una seña clara frente a la cámara. Probá con buena luz y encuadrando tus manos y rostro."
                }
            throw IllegalStateException(reason)
        }

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
            .ifBlank { "Interpretación visual generada con Gemini ($languageCode)." }
        val confidence = json.optInt("confidencePercent", 85).coerceIn(0, 100)

        val primaryGloss = glosses.firstOrNull()?.takeIf { it.isNotBlank() }
        val learnedTitle = json.optString("learnedSignTitle").takeIf { it.isNotBlank() }
        val learnedGuide = json.optString("learnedHandshapeGuide").takeIf { it.isNotBlank() }
        val learnedFacial = json.optString("learnedFacialCue")
            .ifBlank { "Expresión facial acorde a la intención comunicativa." }
        val learnedCat = json.optString("learnedCategory").ifBlank { "Aprendida IA" }

        val proposal = if (primaryGloss != null && learnedTitle != null && learnedGuide != null && confidence >= 65) {
            LearnedSignProposal(
                glossKey = primaryGloss,
                displayTitle = learnedTitle,
                idiomaticOutput = phrase,
                category = learnedCat,
                handshapeGuide = learnedGuide,
                facialCue = learnedFacial
            )
        } else {
            null
        }

        val sourceLabel = if (sources.isNotEmpty()) {
            "Gemini Vision + Búsqueda externa ($languageCode)"
        } else {
            "Gemini Vision IA ($languageCode)"
        }

        return SignTranslationResult(
            detectedGlosses = glosses,
            idiomaticPhrase = phrase,
            culturalNote = note,
            confidencePercent = confidence,
            isAiSampleMode = false,
            sourceLabel = sourceLabel,
            externalSources = sources,
            searchEntryPointHtml = searchEntryHtml,
            newlyLearnedSign = proposal
        )
    }

    private fun buildMotionDirectionSummary(samples: List<GestureMotionSample>): String {
        if (samples.isEmpty()) return "sin telemetría previa"
        val peak = (samples.maxOf { it.overallScore } * 100).toInt()
        val avgUpper = samples.map { it.upperScore }.average()
        val avgMiddle = samples.map { it.middleScore }.average()
        val avgLower = samples.map { it.lowerScore }.average()
        val dominantZone = when {
            avgUpper >= avgMiddle && avgUpper >= avgLower -> "zona superior (cabeza/hombros)"
            avgMiddle >= avgLower -> "zona media (pecho/torso)"
            else -> "zona inferior"
        }
        return "pico de movimiento $peak%, mayor actividad en $dominantZone (${samples.size} muestras)"
    }

    private fun takeChronologicalSample(frames: List<Bitmap>, maxFrames: Int): List<Bitmap> {
        if (frames.size <= maxFrames) return frames
        if (maxFrames <= 1) return listOf(frames.last())
        val indices = (0 until maxFrames).map { i ->
            (i * (frames.size - 1)) / (maxFrames - 1)
        }.distinct()
        return indices.map { frames[it] }
    }

    private fun extractCandidateSearchTerm(observation: String): String {
        val cleaned = observation
            .lineSequence()
            .firstOrNull { it.contains("glosa", ignoreCase = true) || it.contains("seña", ignoreCase = true) }
            .orEmpty()
            .replace(Regex("[^\\p{L}0-9\\s]"), " ")
            .trim()
        return cleaned.split(Regex("\\s+")).take(4).joinToString(" ")
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
 * Local sign repository used when Firebase (google-services.json) is not present in the APK.
 * Supports local dictionary phrase building via [OfflineSignEngine] and transparently reports
 * when camera AI vision or external AI search requires Firebase configuration.
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
        throw IllegalStateException(
            "Esta APK se compiló sin configuración Firebase (google-services.json), por lo que la cámara no puede consultar Gemini Vision ni bases externas. Usá las señas rápidas de abajo para armar frases localmente."
        )
    }

    override suspend fun interpretCameraGesture(
        frames: List<Bitmap>,
        motionSamples: List<GestureMotionSample>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        throw IllegalStateException(
            "Esta APK se compiló sin configuración Firebase (google-services.json), por lo que la cámara no puede consultar Gemini Vision ni bases externas. Usá las señas rápidas de abajo para armar frases localmente."
        )
    }

    override suspend fun refineIdiomaticPhrase(
        glossSequence: List<String>,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): SignTranslationResult {
        return OfflineSignEngine.buildIdiomaticPhrase(glossSequence, languageCode, learnedVocabulary)
    }

    override suspend fun searchExternalSignKnowledge(
        query: String,
        languageCode: String,
        learnedVocabulary: List<LearnedSignEntity>,
        isOffline: Boolean
    ): ExternalSignSearchResult {
        if (isOffline) {
            throw IllegalStateException("Sin conexión a internet para consultar fuentes externas.")
        }
        val hits = ExternalSignKnowledgeClient.lookupSignConcept(query, languageCode)
        val firstHit = hits.firstOrNull()
        return ExternalSignSearchResult(
            query = query.trim(),
            glossKey = query.trim().uppercase().replace(" ", "_").replace(Regex("[^A-Z0-9_]"), "")
                .ifBlank { "SENA_EXTERNA" },
            displayTitle = query.trim(),
            idiomaticPhrase = query.trim(),
            handshapeGuide = firstHit?.snippet
                ?: "No se encontró descripción directa en bases abiertas. Compilá con google-services.json para síntesis completa con Gemini AI.",
            facialCue = "Expresión facial acorde al contexto.",
            category = "Fuentes externas",
            culturalNote = if (hits.isNotEmpty()) {
                "Resultados directos de Wikipedia/Wikcionario/Wikidata (sin síntesis Gemini porque falta google-services.json)."
            } else {
                "Sin resultados directos en bases abiertas; configurá Firebase para activar Google Search Grounding con Gemini."
            },
            externalSources = hits.map {
                ExternalSourceLink(title = "${it.source}: ${it.title}", uri = it.url, provider = it.source)
            },
            usedGoogleSearch = false,
            usedExternalDatabases = hits.isNotEmpty()
        )
    }
}
