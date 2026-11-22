package com.example.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore?,
    private val auth: FirebaseAuth?
) {
    constructor(context: Context) : this(
        db = runCatching {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        }.getOrNull(),
        auth = runCatching { Firebase.auth }.getOrNull()
    )

    val isEnabled: Boolean
        get() = db != null && auth != null

    private fun requireDb(): FirebaseFirestore {
        return db ?: throw IllegalStateException("Cloud Firestore no está configurado en este compilado.")
    }

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(120)
            .ifBlank { "doc_${System.currentTimeMillis()}" }
    }

    fun observeTranslations(userId: String): Flow<List<TranslationCloudDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Session user mismatch" }
            val path = "users/$uid/translations"
            emitAll(
                firestore.collection("users").document(uid).collection("translations")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            TranslationCloudDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeFrequentPhrases(userId: String): Flow<List<FrequentPhraseCloudDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Session user mismatch" }
            val path = "users/$uid/frequent_phrases"
            emitAll(
                firestore.collection("users").document(uid).collection("frequent_phrases")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            FrequentPhraseCloudDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeLearnedSigns(userId: String): Flow<List<LearnedSignCloudDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Session user mismatch" }
            val path = "users/$uid/learned_signs"
            emitAll(
                firestore.collection("users").document(uid).collection("learned_signs")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            LearnedSignCloudDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    suspend fun saveTranslation(doc: TranslationCloudDoc): Result<String> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/translations"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val docRef = firestore.collection("users").document(uid).collection("translations").document()
            docPath = docRef.path
            docRef.set(doc.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTranslation(docId: String): Result<Unit> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/translations/$docId"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val cleanId = sanitizeDocId(docId)
            val docRef = firestore.collection("users").document(uid)
                .collection("translations").document(cleanId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveFrequentPhrase(doc: FrequentPhraseCloudDoc): Result<String> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/frequent_phrases"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val docRef = if (doc.id.isNotBlank()) {
                firestore.collection("users").document(uid)
                    .collection("frequent_phrases").document(sanitizeDocId(doc.id))
            } else {
                firestore.collection("users").document(uid)
                    .collection("frequent_phrases").document()
            }
            docPath = docRef.path
            docRef.set(doc.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateFrequentPhraseUsage(docId: String, newCount: Long, pinned: Boolean): Result<Unit> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/frequent_phrases/$docId"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val docRef = firestore.collection("users").document(uid)
                .collection("frequent_phrases").document(sanitizeDocId(docId))
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "usageCount" to newCount.coerceAtLeast(0L),
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFrequentPhrase(docId: String): Result<Unit> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/frequent_phrases/$docId"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val docRef = firestore.collection("users").document(uid)
                .collection("frequent_phrases").document(sanitizeDocId(docId))
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertLearnedSign(doc: LearnedSignCloudDoc): Result<Unit> {
        if (!isEnabled) return Result.failure(IllegalStateException("Firebase no configurado."))
        var docPath = "users/unknown/learned_signs"
        return try {
            val firestore = requireDb()
            val uid = requireUserId()
            val docId = sanitizeDocId("sign_${doc.languageCode}_${doc.glossKey}")
            val docRef = firestore.collection("users").document(uid)
                .collection("learned_signs").document(docId)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "usageCount" to doc.usageCount.coerceAtLeast(0L),
                        "masteryPercent" to doc.masteryPercent.coerceIn(0L, 100L),
                        "pinned" to doc.pinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(doc.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
