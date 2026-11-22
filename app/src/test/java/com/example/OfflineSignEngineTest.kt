package com.example

import com.example.data.ai.LocalPlaceholderSignAiRepository
import com.example.data.ai.OfflineSignEngine
import com.example.data.local.AppDatabase
import com.example.data.model.SignLanguageRegistry
import com.example.firebase.CloudSyncRepository
import com.example.firebase.TranslationCloudDoc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSignEngineTest {

    @Test
    fun offlineEngineBuildsArgentineIdiomaticGreeting() {
        val vocab = AppDatabase.initialLsaVocabulary()
        val result = OfflineSignEngine.buildIdiomaticPhrase(
            glossKeys = listOf("HOLA", "COMO_ANDAS"),
            languageCode = "LSA",
            vocabulary = vocab
        )
        assertTrue(result.idiomaticPhrase.contains("¿Cómo andás?"))
        assertFalse(result.isAiSampleMode)
    }

    @Test
    fun universalRegistryAdaptsDialectForSpain() {
        val vocab = AppDatabase.initialLsaVocabulary()
        val result = OfflineSignEngine.buildIdiomaticPhrase(
            glossKeys = listOf("DONDE", "SUBTE_COLECTIVO"),
            languageCode = "LSE",
            vocabulary = vocab
        )
        assertTrue(result.idiomaticPhrase.contains("autobús") || result.idiomaticPhrase.contains("metro"))
        assertEquals("LSE", SignLanguageRegistry.findByCode("LSE").code)
    }

    @Test
    fun localRepoUsesDeterministicMotionAnalysisWithoutRandomSamples() = runTest {
        val repo = LocalPlaceholderSignAiRepository()
        val vocab = AppDatabase.initialLsaVocabulary()
        val first = repo.interpretCameraFrame(
            cameraBitmap = null,
            motionScore = 0.6f,
            languageCode = "LSA",
            learnedVocabulary = vocab,
            isOffline = false
        )
        val second = repo.interpretCameraFrame(
            cameraBitmap = null,
            motionScore = 0.6f,
            languageCode = "LSA",
            learnedVocabulary = vocab,
            isOffline = false
        )
        assertFalse(first.isAiSampleMode)
        assertFalse(first.idiomaticPhrase.startsWith("[Muestra IA]"))
        assertEquals(first.idiomaticPhrase, second.idiomaticPhrase)
    }

    @Test
    fun cloudSyncRepositoryOperatesSafelyWithoutFirebase() = runTest {
        val repo = CloudSyncRepository(db = null, auth = null)
        assertFalse(repo.isEnabled)
        assertTrue(repo.observeTranslations("local_device").first().isEmpty())
        assertTrue(repo.observeFrequentPhrases("local_device").first().isEmpty())
        assertTrue(repo.observeLearnedSigns("local_device").first().isEmpty())
        val saveResult = repo.saveTranslation(TranslationCloudDoc(userId = "local_device"))
        assertTrue(saveResult.isFailure)
    }
}
