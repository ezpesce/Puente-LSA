package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TranslationRecordEntity::class,
        LearnedSignEntity::class,
        FrequentPhraseEntity::class,
        TutorialLessonEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun learnedSignDao(): LearnedSignDao
    abstract fun frequentPhraseDao(): FrequentPhraseDao
    abstract fun tutorialDao(): TutorialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            lateinit var db: AppDatabase
            db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "puente_lsa.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(supportDb: SupportSQLiteDatabase) {
                        super.onCreate(supportDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(db)
                        }
                    }
                })
                .build()
            return db
        }

        private suspend fun seedInitialData(db: AppDatabase) {
            db.learnedSignDao().insertAll(initialLsaVocabulary())
            db.frequentPhraseDao().insertAll(initialFrequentPhrases())
            db.tutorialDao().insertAll(initialTutorialLessons())
            db.translationDao().insert(
                TranslationRecordEntity(
                    languageCode = "LSA",
                    rawGlosses = "HOLA + COMO_ANDAS",
                    naturalPhrase = "¡Hola! ¿Cómo andás? Qué alegría verte.",
                    sourceMode = "OFFLINE_ENGINE",
                    confidencePercent = 96,
                    wasSpokenAloud = true,
                    isOfflineRecord = true,
                    isFavorite = true
                )
            )
        }

        fun initialLsaVocabulary(): List<LearnedSignEntity> = listOf(
            LearnedSignEntity(
                glossKey = "HOLA",
                displayTitle = "Hola",
                idiomaticOutput = "¡Hola!",
                category = "Saludos",
                handshapeGuide = "Mano abierta cerca de la sien derecha, movimiento suave hacia afuera.",
                facialCue = "Sonrisa abierta y contacto visual directo.",
                usageCount = 12,
                masteryPercent = 85,
                isPinned = true
            ),
            LearnedSignEntity(
                glossKey = "COMO_ANDAS",
                displayTitle = "¿Cómo andás?",
                idiomaticOutput = "¿Cómo andás?",
                category = "Saludos",
                handshapeGuide = "Ambas manos cerradas giran hacia arriba abriendo los dedos.",
                facialCue = "Cejas levemente elevadas indicando pregunta.",
                usageCount = 9,
                masteryPercent = 72,
                isPinned = true
            ),
            LearnedSignEntity(
                glossKey = "GRACIAS",
                displayTitle = "Gracias",
                idiomaticOutput = "¡Muchas gracias!",
                category = "Cortesía",
                handshapeGuide = "Yemas de los dedos sobre el mentón desplazándose hacia el frente.",
                facialCue = "Asentimiento suave con expresión cordial.",
                usageCount = 14,
                masteryPercent = 90,
                isPinned = true
            ),
            LearnedSignEntity(
                glossKey = "POR_FAVOR",
                displayTitle = "Por favor",
                idiomaticOutput = "Por favor",
                category = "Cortesía",
                handshapeGuide = "Palma abierta sobre el centro del pecho en movimiento circular suave.",
                facialCue = "Expresión amable e inclinación leve de cabeza.",
                usageCount = 8,
                masteryPercent = 70,
                isPinned = false
            ),
            LearnedSignEntity(
                glossKey = "BIEN",
                displayTitle = "Bien",
                idiomaticOutput = "Todo bien, por suerte",
                category = "Saludos",
                handshapeGuide = "Pulgar extendido hacia arriba saliendo desde el pecho.",
                facialCue = "Sonrisa natural.",
                usageCount = 7,
                masteryPercent = 75
            ),
            LearnedSignEntity(
                glossKey = "AYUDA",
                displayTitle = "Ayuda",
                idiomaticOutput = "Necesito ayuda, por favor",
                category = "Emergencia",
                handshapeGuide = "Puño cerrado sobre la palma abierta, elevando ambas manos juntas.",
                facialCue = "Mirada firme y expresiva según urgencia.",
                usageCount = 5,
                masteryPercent = 64,
                isPinned = true
            ),
            LearnedSignEntity(
                glossKey = "MEDICO",
                displayTitle = "Médico",
                idiomaticOutput = "Necesito atención médica",
                category = "Salud",
                handshapeGuide = "Índice y mayor tocan suavemente el pulso de la muñeca contraria.",
                facialCue = "Expresión seria y clara.",
                usageCount = 3,
                masteryPercent = 50
            ),
            LearnedSignEntity(
                glossKey = "MAS_DESPACIO",
                displayTitle = "Más despacio",
                idiomaticOutput = "¿Podés hablar más despacio, por favor?",
                category = "Comunicación",
                handshapeGuide = "Ambas palmas hacia abajo bajan lentamente frente al torso.",
                facialCue = "Cejas levemente juntas en tono de pedido.",
                usageCount = 6,
                masteryPercent = 68,
                isPinned = true
            ),
            LearnedSignEntity(
                glossKey = "NO_ENTIENDO",
                displayTitle = "No entiendo",
                idiomaticOutput = "Disculpá, no te llegué a entender",
                category = "Comunicación",
                handshapeGuide = "Dedo índice cerca de la sien con movimiento negativo breve.",
                facialCue = "Leve negación con la cabeza.",
                usageCount = 4,
                masteryPercent = 55
            ),
            LearnedSignEntity(
                glossKey = "SUBTE_COLECTIVO",
                displayTitle = "Colectivo / Subte",
                idiomaticOutput = "¿Qué colectivo o subte me deja cerca?",
                category = "Transporte",
                handshapeGuide = "Manos simulando volante en giro o barra de sujeción.",
                facialCue = "Cejas levantadas para marcar pregunta abierta.",
                usageCount = 4,
                masteryPercent = 52
            ),
            LearnedSignEntity(
                glossKey = "DONDE",
                displayTitle = "¿Dónde?",
                idiomaticOutput = "¿Dónde queda?",
                category = "Preguntas",
                handshapeGuide = "Índice hacia arriba realizando un corto vaivén lateral.",
                facialCue = "Cejas arriba y boca ligeramente abierta en pregunta.",
                usageCount = 5,
                masteryPercent = 60
            ),
            LearnedSignEntity(
                glossKey = "BANIO",
                displayTitle = "Baño",
                idiomaticOutput = "¿Dónde está el baño?",
                category = "Cotidiano",
                handshapeGuide = "Mano cerrada con pulgar asomado entre índice y mayor, suave giro.",
                facialCue = "Expresión interrogativa.",
                usageCount = 3,
                masteryPercent = 48
            ),
            LearnedSignEntity(
                glossKey = "CUANTO_SALE",
                displayTitle = "¿Cuánto sale?",
                idiomaticOutput = "Disculpá, ¿cuánto sale esto?",
                category = "Compras",
                handshapeGuide = "Yemas de pulgar e índice frotándose con mano extendida.",
                facialCue = "Cejas elevadas marcando consulta.",
                usageCount = 5,
                masteryPercent = 62
            ),
            LearnedSignEntity(
                glossKey = "AGUA",
                displayTitle = "Agua",
                idiomaticOutput = "¿Me das un vaso de agua?",
                category = "Cotidiano",
                handshapeGuide = "Dedo índice toca suavemente la comisura del labio.",
                facialCue = "Expresión neutra o amable.",
                usageCount = 3,
                masteryPercent = 50
            ),
            LearnedSignEntity(
                glossKey = "NOMBRE",
                displayTitle = "Nombre",
                idiomaticOutput = "¿Cuál es tu nombre?",
                category = "Saludos",
                handshapeGuide = "Índice y mayor de cada mano se cruzan tocándose dos veces.",
                facialCue = "Cejas levantadas si es pregunta.",
                usageCount = 4,
                masteryPercent = 58
            ),
            LearnedSignEntity(
                glossKey = "NOS_VEMOS",
                displayTitle = "Nos vemos",
                idiomaticOutput = "¡Nos vemos luego, cuidate!",
                category = "Saludos",
                handshapeGuide = "Índice y mayor en V señalan hacia los ojos y avanzan hacia el frente.",
                facialCue = "Sonrisa de despedida.",
                usageCount = 4,
                masteryPercent = 60
            )
        )

        fun initialFrequentPhrases(): List<FrequentPhraseEntity> = listOf(
            FrequentPhraseEntity(
                title = "Presentación accesible",
                phraseText = "¡Hola! Soy una persona sorda y uso Lengua de Señas Argentina. Podés hablar despacio de frente o escribirme acá.",
                category = "Comunicación",
                usageCount = 15,
                isPinned = true
            ),
            FrequentPhraseEntity(
                title = "Pedir que repitan",
                phraseText = "Disculpá, ¿me podrías repetir eso un poco más despacio?",
                category = "Comunicación",
                usageCount = 10,
                isPinned = true
            ),
            FrequentPhraseEntity(
                title = "Consulta de transporte",
                phraseText = "Buen día, ¿sabés qué colectivo o línea de subte me deja cerca del centro?",
                category = "Transporte",
                usageCount = 6,
                isPinned = false
            ),
            FrequentPhraseEntity(
                title = "Asistencia médica",
                phraseText = "Necesito atención médica. Por favor, comuniquémonos por texto o señas claras.",
                category = "Salud",
                usageCount = 4,
                isPinned = true
            ),
            FrequentPhraseEntity(
                title = "Consulta en comercio",
                phraseText = "Hola, ¿cuánto sale esto? ¿Aceptan pago con QR o tarjeta?",
                category = "Compras",
                usageCount = 7,
                isPinned = false
            ),
            FrequentPhraseEntity(
                title = "Agradecimiento",
                phraseText = "¡Muchas gracias por tu paciencia y tu ayuda!",
                category = "Cortesía",
                usageCount = 11,
                isPinned = false
            )
        )

        fun initialTutorialLessons(): List<TutorialLessonEntity> = listOf(
            TutorialLessonEntity(
                id = "lsa_01",
                orderIndex = 1,
                moduleTitle = "Primeros Pasos",
                signTitle = "Hola — Saludo inicial",
                glossKey = "HOLA",
                idiomaticExample = "\"¡Hola! ¿Cómo andás?\"",
                handshapeSteps = "1. Extendé los cinco dedos con la palma abierta.\n2. Ubicá el pulgar cerca de tu sien derecha.\n3. Desplazá la mano hacia adelante con un giro amable.",
                facialExpressionNote = "En LSA la expresión facial es parte de la gramática: acompañá con una sonrisa y mirada atenta.",
                practicePrompt = "Mostrá la palma abierta frente a la cámara o tocá el botón de verificación.",
                isCompleted = true,
                practiceAttempts = 1
            ),
            TutorialLessonEntity(
                id = "lsa_02",
                orderIndex = 2,
                moduleTitle = "Primeros Pasos",
                signTitle = "Gracias y Cortesía",
                glossKey = "GRACIAS",
                idiomaticExample = "\"¡Muchas gracias, muy amable!\"",
                handshapeSteps = "1. Juntá los dedos con la palma hacia tu rostro.\n2. Tocá suavemente el mentón con las yemas.\n3. Proyectá la mano hacia el interlocutor bajándola levemente.",
                facialExpressionNote = "Sumá un leve asentimiento de cabeza para reforzar la calidez del agradecimiento.",
                practicePrompt = "Practicá el movimiento desde el mentón hacia adelante."
            ),
            TutorialLessonEntity(
                id = "lsa_03",
                orderIndex = 3,
                moduleTitle = "Comunicación Accesible",
                signTitle = "Más despacio, por favor",
                glossKey = "MAS_DESPACIO",
                idiomaticExample = "\"¿Podés hablar más despacio?\"",
                handshapeSteps = "1. Colocá ambas manos abiertas con las palmas mirando hacia abajo.\n2. Descendé las manos lentamente a la altura del pecho.\n3. Repetí el descenso una vez más con ritmo pausado.",
                facialExpressionNote = "Levantá levemente las cejas para indicar que es una solicitud cordial.",
                practicePrompt = "Realizá el descenso lento con ambas manos."
            ),
            TutorialLessonEntity(
                id = "lsa_04",
                orderIndex = 4,
                moduleTitle = "Movilidad y Ciudad",
                signTitle = "¿Dónde queda el transporte?",
                glossKey = "DONDE",
                idiomaticExample = "\"Disculpá, ¿dónde está la parada?\"",
                handshapeSteps = "1. Levantá el dedo índice con el resto de la mano cerrada.\n2. Realizá un movimiento corto de lado a lado.\n3. Combiná con la seña de SUBTE o COLECTIVO.",
                facialExpressionNote = "En LSA, las preguntas abiertas se marcan frunciendo o elevando levemente las cejas al final de la frase.",
                practicePrompt = "Combiná mirada interrogativa con el movimiento lateral del índice."
            ),
            TutorialLessonEntity(
                id = "lsa_05",
                orderIndex = 5,
                moduleTitle = "Salud y Emergencias",
                signTitle = "Necesito ayuda / Médico",
                glossKey = "AYUDA",
                idiomaticExample = "\"Necesito ayuda urgente, por favor\"",
                handshapeSteps = "1. Colocá tu mano no dominante abierta con la palma hacia arriba.\n2. Apoyá el puño de la mano dominante encima.\n3. Elevá ambas manos juntas hacia adelante con firmeza.",
                facialExpressionNote = "La intensidad del gesto y las cejas expresan el nivel de urgencia.",
                practicePrompt = "Elevá ambas manos unidas frente al pecho."
            ),
            TutorialLessonEntity(
                id = "lsa_06",
                orderIndex = 6,
                moduleTitle = "Conversación Fluida",
                signTitle = "Mi nombre y despedida",
                glossKey = "NOMBRE",
                idiomaticExample = "\"¿Cuál es tu nombre? ¡Nos vemos!\"",
                handshapeSteps = "1. Extendé índice y mayor en cada mano formando una 'V' cerrada.\n2. Cruzá los dedos de una mano sobre la otra con dos toques suaves.\n3. Para despedirte, señalá hacia adelante con sonrisa.",
                facialExpressionNote = "Mantené el contacto visual al deletrear tu nombre en dactilología LSA.",
                practicePrompt = "Realizá el doble toque en cruz a la altura del pecho."
            )
        )
    }
}
