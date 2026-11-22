package com.example.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class TranslationCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val languageCode: String = "LSA",
    val rawGlosses: String = "",
    val naturalPhrase: String = "",
    val sourceMode: String = "GEMINI_AI",
    val confidencePercent: Long = 90L,
    val spokenAloud: Boolean = false,
    val offlineRecord: Boolean = false,
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "languageCode" to languageCode.take(12).ifBlank { "LSA" },
        "rawGlosses" to rawGlosses.take(200).ifBlank { "GESTO_LSA" },
        "naturalPhrase" to naturalPhrase.take(500).ifBlank { "Seña LSA" },
        "sourceMode" to sourceMode.take(40).ifBlank { "GEMINI_AI" },
        "confidencePercent" to confidencePercent.coerceIn(0L, 100L),
        "spokenAloud" to spokenAloud,
        "offlineRecord" to offlineRecord,
        "favorite" to favorite,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FrequentPhraseCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val phraseText: String = "",
    val category: String = "Personal",
    val languageCode: String = "LSA",
    val usageCount: Long = 1L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.take(80).ifBlank { "Frase LSA" },
        "phraseText" to phraseText.take(500).ifBlank { "Hola" },
        "category" to category.take(40).ifBlank { "Personal" },
        "languageCode" to languageCode.take(12).ifBlank { "LSA" },
        "usageCount" to usageCount.coerceAtLeast(0L),
        "pinned" to pinned,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class LearnedSignCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val glossKey: String = "",
    val displayTitle: String = "",
    val idiomaticOutput: String = "",
    val category: String = "Personalizada",
    val languageCode: String = "LSA",
    val handshapeGuide: String = "",
    val facialCue: String = "",
    val usageCount: Long = 1L,
    val masteryPercent: Long = 20L,
    val pinned: Boolean = false,
    val userCreated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "glossKey" to glossKey.take(60).ifBlank { "SEÑA" },
        "displayTitle" to displayTitle.take(80).ifBlank { "Seña" },
        "idiomaticOutput" to idiomaticOutput.take(300).ifBlank { "Hola" },
        "category" to category.take(40).ifBlank { "Personalizada" },
        "languageCode" to languageCode.take(12).ifBlank { "LSA" },
        "handshapeGuide" to handshapeGuide.take(400).ifBlank { "Seña guardada por el usuario." },
        "facialCue" to facialCue.take(300).ifBlank { "Expresión acorde al contexto." },
        "usageCount" to usageCount.coerceAtLeast(0L),
        "masteryPercent" to masteryPercent.coerceIn(0L, 100L),
        "pinned" to pinned,
        "userCreated" to userCreated,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
