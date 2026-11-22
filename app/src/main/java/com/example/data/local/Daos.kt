package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TranslationRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TranslationRecordEntity): Long

    @Query("UPDATE translation_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translation_history")
    suspend fun clearAll()
}

@Dao
interface LearnedSignDao {
    @Query("SELECT * FROM learned_signs ORDER BY isPinned DESC, usageCount DESC, displayTitle ASC")
    fun observeAll(): Flow<List<LearnedSignEntity>>

    @Query("SELECT * FROM learned_signs WHERE languageCode = :languageCode ORDER BY isPinned DESC, usageCount DESC, displayTitle ASC")
    fun observeByLanguage(languageCode: String): Flow<List<LearnedSignEntity>>

    @Query("SELECT * FROM learned_signs WHERE glossKey = :glossKey LIMIT 1")
    suspend fun findByGloss(glossKey: String): LearnedSignEntity?

    @Query("SELECT * FROM learned_signs")
    suspend fun getAllSnapshot(): List<LearnedSignEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(signs: List<LearnedSignEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sign: LearnedSignEntity): Long

    @Query("UPDATE learned_signs SET usageCount = usageCount + 1, masteryPercent = MIN(100, masteryPercent + 8), lastUsedAt = :timestamp WHERE id = :id")
    suspend fun recordUsage(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE learned_signs SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}

@Dao
interface FrequentPhraseDao {
    @Query("SELECT * FROM frequent_phrases ORDER BY isPinned DESC, usageCount DESC, createdAt DESC")
    fun observeAll(): Flow<List<FrequentPhraseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(phrases: List<FrequentPhraseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phrase: FrequentPhraseEntity): Long

    @Query("UPDATE frequent_phrases SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("UPDATE frequent_phrases SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM frequent_phrases WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TutorialDao {
    @Query("SELECT * FROM tutorial_lessons ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<TutorialLessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessons: List<TutorialLessonEntity>)

    @Query("UPDATE tutorial_lessons SET isCompleted = :completed, practiceAttempts = practiceAttempts + 1 WHERE id = :id")
    suspend fun markPracticed(id: String, completed: Boolean)
}
