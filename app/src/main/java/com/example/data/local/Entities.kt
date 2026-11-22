package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class TranslationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val languageCode: String,
    val rawGlosses: String,
    val naturalPhrase: String,
    val sourceMode: String, // "CAMERA_AI_SAMPLE", "OFFLINE_ENGINE", "MANUAL_COMPOSER"
    val confidencePercent: Int,
    val wasSpokenAloud: Boolean,
    val isOfflineRecord: Boolean,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_signs")
data class LearnedSignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val glossKey: String,
    val displayTitle: String,
    val idiomaticOutput: String,
    val category: String,
    val languageCode: String = "LSA",
    val handshapeGuide: String,
    val facialCue: String,
    val usageCount: Int = 0,
    val masteryPercent: Int = 20,
    val isPinned: Boolean = false,
    val isUserCreated: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "frequent_phrases")
data class FrequentPhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val phraseText: String,
    val category: String,
    val languageCode: String = "LSA",
    val usageCount: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tutorial_lessons")
data class TutorialLessonEntity(
    @PrimaryKey val id: String,
    val orderIndex: Int,
    val moduleTitle: String,
    val signTitle: String,
    val glossKey: String,
    val idiomaticExample: String,
    val handshapeSteps: String,
    val facialExpressionNote: String,
    val practicePrompt: String,
    val isCompleted: Boolean = false,
    val practiceAttempts: Int = 0
)
