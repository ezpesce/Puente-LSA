package com.example

import com.example.data.ai.LocalPlaceholderSignAiRepository
import com.example.data.ai.OfflineSignEngine
import com.example.data.local.AppDatabase
import com.example.data.model.SignLanguageRegistry
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
    fun placeholderAiMarksSampleResponsesClearly() = runTest {
        val repo = LocalPlaceholderSignAiRepository()
        val vocab = AppDatabase.initialLsaVocabulary()
        val result = repo.interpretCameraFrame(
            cameraBitmap = null,
            motionScore = 0.6f,
            languageCode = "LSA",
            learnedVocabulary = vocab,
            isOffline = false
        )
        assertTrue(result.isAiSampleMode)
        assertTrue(result.idiomaticPhrase.startsWith("[Muestra IA]"))
    }
}
