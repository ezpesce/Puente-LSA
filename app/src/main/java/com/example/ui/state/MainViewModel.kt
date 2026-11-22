package com.example.ui.state

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SignAiRepository
import com.example.data.ai.SignTranslationResult
import com.example.data.local.AppDatabase
import com.example.data.local.FrequentPhraseEntity
import com.example.data.local.LearnedSignEntity
import com.example.data.local.TranslationRecordEntity
import com.example.data.local.TutorialLessonEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.model.SignLanguageRegistry
import com.example.data.model.ThemePreference
import com.example.data.model.WearableDeviceInfo
import com.example.data.model.WearableSyncLogItem
import com.example.firebase.CloudSyncRepository
import com.example.firebase.FrequentPhraseCloudDoc
import com.example.firebase.LearnedSignCloudDoc
import com.example.firebase.TranslationCloudDoc
import com.example.platform.HapticAlertManager
import com.example.platform.HapticPattern
import com.example.platform.NetworkMonitor
import com.example.platform.TtsSpeaker
import com.example.platform.WearableSyncManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val VM_TAG = "MainViewModel"

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Analyzing : TranslationUiState
    data class Success(val result: SignTranslationResult) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

sealed interface CloudSyncUiState {
    data object LocalOnly : CloudSyncUiState
    data object Syncing : CloudSyncUiState
    data class Active(val syncedCount: Int) : CloudSyncUiState
    data class Error(val message: String) : CloudSyncUiState
}

data class RealTimeAlertBanner(
    val title: String,
    val subtitle: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(
    private val currentUserId: String,
    private val database: AppDatabase,
    private val prefsRepo: UserPreferencesRepository,
    private val aiRepository: SignAiRepository,
    private val cloudRepo: CloudSyncRepository,
    val ttsSpeaker: TtsSpeaker,
    private val hapticManager: HapticAlertManager,
    private val networkMonitor: NetworkMonitor,
    val wearableManager: WearableSyncManager
) : ViewModel() {

    private val translationDao = database.translationDao()
    private val learnedSignDao = database.learnedSignDao()
    private val frequentPhraseDao = database.frequentPhraseDao()
    private val tutorialDao = database.tutorialDao()

    val userSettings: StateFlow<UserSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val isSystemOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val isEffectiveOffline: StateFlow<Boolean> = combine(
        userSettings,
        isSystemOnline
    ) { settings, online ->
        settings.isOfflineModeForced || !online
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val translationHistory: StateFlow<List<TranslationRecordEntity>> = translationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learnedVocabulary: StateFlow<List<LearnedSignEntity>> = learnedSignDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val frequentPhrases: StateFlow<List<FrequentPhraseEntity>> = frequentPhraseDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tutorialLessons: StateFlow<List<TutorialLessonEntity>> = tutorialDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Two-tier Firestore flow observation for real-time cloud sync
    val cloudSyncState: StateFlow<CloudSyncUiState> = if (!cloudRepo.isEnabled) {
        MutableStateFlow<CloudSyncUiState>(CloudSyncUiState.LocalOnly).asStateFlow()
    } else {
        combine(
            cloudRepo.observeTranslations(currentUserId),
            cloudRepo.observeFrequentPhrases(currentUserId),
            cloudRepo.observeLearnedSigns(currentUserId)
        ) { cloudTranslations, cloudPhrases, cloudSigns ->
            CloudSyncUiState.Active(cloudTranslations.size + cloudPhrases.size + cloudSigns.size) as CloudSyncUiState
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(VM_TAG, "Error observing Firestore sync", rootError)
            emit(CloudSyncUiState.Error(rootError.localizedMessage ?: "Error al sincronizar con Firestore"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudSyncUiState.Syncing)
    }

    val isSpeaking: StateFlow<Boolean> = ttsSpeaker.isSpeaking
    val voiceStatus: StateFlow<String> = ttsSpeaker.voiceStatus

    val bondedWearables: StateFlow<List<WearableDeviceInfo>> = wearableManager.bondedWearables
    val lastMirroredCaption: StateFlow<String> = wearableManager.lastMirroredCaption
    val wearableLogs: StateFlow<List<WearableSyncLogItem>> = wearableManager.syncLogs
    val bluetoothStatusMessage: StateFlow<String> = wearableManager.bluetoothStatusMessage

    private val _translationState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val translationState: StateFlow<TranslationUiState> = _translationState.asStateFlow()

    private val _selectedGlosses = MutableStateFlow<List<String>>(emptyList())
    val selectedGlosses: StateFlow<List<String>> = _selectedGlosses.asStateFlow()

    private val _cameraMotionScore = MutableStateFlow(0f)
    val cameraMotionScore: StateFlow<Float> = _cameraMotionScore.asStateFlow()

    private val _isAutoCaptureActive = MutableStateFlow(false)
    val isAutoCaptureActive: StateFlow<Boolean> = _isAutoCaptureActive.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _realTimeAlert = MutableStateFlow<RealTimeAlertBanner?>(null)
    val realTimeAlert: StateFlow<RealTimeAlertBanner?> = _realTimeAlert.asStateFlow()

    val requiresFirebaseAiNotice: Boolean = aiRepository.requiresFirebaseNotice

    fun updateCameraMotion(score: Float) {
        _cameraMotionScore.value = score.coerceIn(0f, 1f)
    }

    fun toggleCameraFacing() {
        _useFrontCamera.value = !_useFrontCamera.value
        if (_useFrontCamera.value) {
            _isTorchOn.value = false
        }
    }

    fun toggleTorch() {
        if (!_useFrontCamera.value) {
            _isTorchOn.value = !_isTorchOn.value
        }
    }

    fun toggleAutoCapture() {
        _isAutoCaptureActive.value = !_isAutoCaptureActive.value
        hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, userSettings.value.hapticAlertsEnabled)
    }

    fun analyzeCameraCapture(cameraBitmap: Bitmap? = null) {
        viewModelScope.launch {
            _translationState.value = TranslationUiState.Analyzing
            val settings = userSettings.value
            val offline = isEffectiveOffline.value
            val vocab = learnedVocabulary.value
            try {
                val result = aiRepository.interpretCameraFrame(
                    cameraBitmap = cameraBitmap,
                    motionScore = _cameraMotionScore.value,
                    languageCode = settings.activeSignLanguageCode,
                    learnedVocabulary = vocab,
                    isOffline = offline
                )
                handleTranslationSuccess(
                    result = result,
                    sourceMode = when {
                        offline -> "OFFLINE_ENGINE"
                        requiresFirebaseAiNotice -> "LOCAL_SAMPLE"
                        else -> "GEMINI_AI"
                    },
                    isOffline = offline
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _translationState.value = TranslationUiState.Error(
                    e.localizedMessage ?: "No se pudo procesar la captura."
                )
            }
        }
    }

    fun addGlossToken(glossKey: String) {
        val current = _selectedGlosses.value
        if (current.size < 5) {
            _selectedGlosses.value = current + glossKey
            hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, userSettings.value.hapticAlertsEnabled)
            composeFromSelectedGlosses()
        }
    }

    fun removeLastGlossToken() {
        val current = _selectedGlosses.value
        if (current.isNotEmpty()) {
            _selectedGlosses.value = current.dropLast(1)
            if (_selectedGlosses.value.isEmpty()) {
                _translationState.value = TranslationUiState.Idle
            } else {
                composeFromSelectedGlosses()
            }
        }
    }

    fun clearGlossTokens() {
        _selectedGlosses.value = emptyList()
        _translationState.value = TranslationUiState.Idle
    }

    private fun composeFromSelectedGlosses() {
        val glosses = _selectedGlosses.value
        if (glosses.isEmpty()) return
        viewModelScope.launch {
            _translationState.value = TranslationUiState.Analyzing
            val settings = userSettings.value
            val offline = isEffectiveOffline.value
            try {
                val result = aiRepository.refineIdiomaticPhrase(
                    glossSequence = glosses,
                    languageCode = settings.activeSignLanguageCode,
                    learnedVocabulary = learnedVocabulary.value,
                    isOffline = offline
                )
                handleTranslationSuccess(
                    result = result,
                    sourceMode = when {
                        offline -> "OFFLINE_ENGINE"
                        requiresFirebaseAiNotice -> "LOCAL_SAMPLE"
                        else -> "GEMINI_COMPOSER"
                    },
                    isOffline = offline
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _translationState.value = TranslationUiState.Error(
                    e.localizedMessage ?: "Error al refinar la oración."
                )
            }
        }
    }

    private suspend fun handleTranslationSuccess(
        result: SignTranslationResult,
        sourceMode: String,
        isOffline: Boolean
    ) {
        val settings = userSettings.value
        _translationState.value = TranslationUiState.Success(result)

        // Update personalized adaptive learning metrics locally and in Firestore
        result.detectedGlosses.forEach { gloss ->
            learnedSignDao.findByGloss(gloss)?.let { entity ->
                learnedSignDao.recordUsage(entity.id)
                if (!isOffline && cloudRepo.isEnabled) {
                    cloudRepo.upsertLearnedSign(
                        LearnedSignCloudDoc(
                            userId = currentUserId,
                            glossKey = entity.glossKey,
                            displayTitle = entity.displayTitle,
                            idiomaticOutput = entity.idiomaticOutput,
                            category = entity.category,
                            languageCode = entity.languageCode,
                            handshapeGuide = entity.handshapeGuide,
                            facialCue = entity.facialCue,
                            usageCount = (entity.usageCount + 1).toLong(),
                            masteryPercent = (entity.masteryPercent + 8).coerceAtMost(100).toLong(),
                            pinned = entity.isPinned,
                            userCreated = entity.isUserCreated
                        )
                    )
                }
            }
        }

        val rawGlossString = result.detectedGlosses.joinToString(" + ").ifBlank { "GESTO_LSA" }
        translationDao.insert(
            TranslationRecordEntity(
                languageCode = settings.activeSignLanguageCode,
                rawGlosses = rawGlossString,
                naturalPhrase = result.idiomaticPhrase,
                sourceMode = sourceMode,
                confidencePercent = result.confidencePercent,
                wasSpokenAloud = settings.autoSpeakEnabled,
                isOfflineRecord = isOffline
            )
        )

        if (!isOffline && cloudRepo.isEnabled) {
            cloudRepo.saveTranslation(
                TranslationCloudDoc(
                    userId = currentUserId,
                    languageCode = settings.activeSignLanguageCode,
                    rawGlosses = rawGlossString,
                    naturalPhrase = result.idiomaticPhrase,
                    sourceMode = sourceMode,
                    confidencePercent = result.confidencePercent.toLong(),
                    spokenAloud = settings.autoSpeakEnabled,
                    offlineRecord = false,
                    favorite = false
                )
            )
        }

        hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, settings.hapticAlertsEnabled)
        _realTimeAlert.value = RealTimeAlertBanner(
            title = "Nueva traducción ${settings.activeSignLanguageCode}",
            subtitle = result.idiomaticPhrase
        )

        if (settings.autoSpeakEnabled) {
            ttsSpeaker.speak(result.idiomaticPhrase, settings.speechRate)
        }

        wearableManager.mirrorToWearable(
            caption = result.idiomaticPhrase,
            hapticLabel = HapticPattern.SIGN_RECOGNIZED.label,
            syncEnabled = settings.wearableSyncEnabled,
            mirrorHaptics = settings.wearableMirrorHaptics
        )
    }

    fun speakText(text: String) {
        val settings = userSettings.value
        ttsSpeaker.speak(text, settings.speechRate)
        hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, settings.hapticAlertsEnabled)
        wearableManager.mirrorToWearable(
            caption = text,
            hapticLabel = "Voz sintetizada activa",
            syncEnabled = settings.wearableSyncEnabled,
            mirrorHaptics = settings.wearableMirrorHaptics
        )
    }

    fun stopSpeaking() {
        ttsSpeaker.stop()
    }

    fun simulateInterlocutorAlert(message: String = "El interlocutor te está respondiendo") {
        val settings = userSettings.value
        hapticManager.trigger(HapticPattern.INTERACTION_ALERT, settings.hapticAlertsEnabled)
        _realTimeAlert.value = RealTimeAlertBanner(
            title = "Alerta háptica en tiempo real",
            subtitle = message
        )
        wearableManager.mirrorToWearable(
            caption = message,
            hapticLabel = HapticPattern.INTERACTION_ALERT.label,
            syncEnabled = settings.wearableSyncEnabled,
            mirrorHaptics = settings.wearableMirrorHaptics
        )
    }

    fun dismissAlertBanner() {
        _realTimeAlert.value = null
    }

    fun useFrequentPhrase(phrase: FrequentPhraseEntity) {
        viewModelScope.launch {
            frequentPhraseDao.incrementUsage(phrase.id)
            speakText(phrase.phraseText)
            translationDao.insert(
                TranslationRecordEntity(
                    languageCode = phrase.languageCode,
                    rawGlosses = "FRASE_RAPIDA",
                    naturalPhrase = phrase.phraseText,
                    sourceMode = "OFFLINE_ENGINE",
                    confidencePercent = 100,
                    wasSpokenAloud = true,
                    isOfflineRecord = isEffectiveOffline.value,
                    isFavorite = phrase.isPinned
                )
            )
            if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                cloudRepo.saveFrequentPhrase(
                    FrequentPhraseCloudDoc(
                        id = "phrase_${phrase.id}",
                        userId = currentUserId,
                        title = phrase.title,
                        phraseText = phrase.phraseText,
                        category = phrase.category,
                        languageCode = phrase.languageCode,
                        usageCount = (phrase.usageCount + 1).toLong(),
                        pinned = phrase.isPinned
                    )
                )
            }
        }
    }

    fun saveFrequentPhrase(title: String, phraseText: String, category: String) {
        if (title.isBlank() || phraseText.isBlank()) return
        viewModelScope.launch {
            val lang = userSettings.value.activeSignLanguageCode
            val insertedId = frequentPhraseDao.insert(
                FrequentPhraseEntity(
                    title = title.trim(),
                    phraseText = phraseText.trim(),
                    category = category,
                    languageCode = lang,
                    usageCount = 1,
                    isPinned = true
                )
            )
            if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                cloudRepo.saveFrequentPhrase(
                    FrequentPhraseCloudDoc(
                        id = "phrase_$insertedId",
                        userId = currentUserId,
                        title = title.trim(),
                        phraseText = phraseText.trim(),
                        category = category,
                        languageCode = lang,
                        usageCount = 1L,
                        pinned = true
                    )
                )
            }
            hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, userSettings.value.hapticAlertsEnabled)
        }
    }

    fun saveTranslationAsFrequent(record: TranslationRecordEntity) {
        saveFrequentPhrase(
            title = record.rawGlosses.replace("_", " ").take(28),
            phraseText = record.naturalPhrase,
            category = "Guardadas"
        )
        viewModelScope.launch {
            translationDao.setFavorite(record.id, true)
        }
    }

    fun togglePhrasePinned(phrase: FrequentPhraseEntity) {
        viewModelScope.launch {
            frequentPhraseDao.setPinned(phrase.id, !phrase.isPinned)
        }
    }

    fun deletePhrase(id: Long) {
        viewModelScope.launch {
            frequentPhraseDao.deleteById(id)
            if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                cloudRepo.deleteFrequentPhrase("phrase_$id")
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            translationDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            translationDao.clearAll()
        }
    }

    fun toggleSignPinned(sign: LearnedSignEntity) {
        viewModelScope.launch {
            val newPinned = !sign.isPinned
            learnedSignDao.setPinned(sign.id, newPinned)
            if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                cloudRepo.upsertLearnedSign(
                    LearnedSignCloudDoc(
                        userId = currentUserId,
                        glossKey = sign.glossKey,
                        displayTitle = sign.displayTitle,
                        idiomaticOutput = sign.idiomaticOutput,
                        category = sign.category,
                        languageCode = sign.languageCode,
                        handshapeGuide = sign.handshapeGuide,
                        facialCue = sign.facialCue,
                        usageCount = sign.usageCount.toLong(),
                        masteryPercent = sign.masteryPercent.toLong(),
                        pinned = newPinned,
                        userCreated = sign.isUserCreated
                    )
                )
            }
        }
    }

    fun addCustomLearnedSign(
        title: String,
        idiomaticOutput: String,
        category: String,
        handshapeGuide: String
    ) {
        if (title.isBlank() || idiomaticOutput.isBlank()) return
        viewModelScope.launch {
            val gloss = title.uppercase()
                .replace(" ", "_")
                .replace(Regex("[^A-Z0-9_]"), "")
                .ifBlank { "SENA_PERSONAL" }
            val lang = userSettings.value.activeSignLanguageCode
            val guide = handshapeGuide.ifBlank { "Seña personalizada guardada por el usuario." }
            learnedSignDao.insert(
                LearnedSignEntity(
                    glossKey = gloss,
                    displayTitle = title.trim(),
                    idiomaticOutput = idiomaticOutput.trim(),
                    category = category,
                    languageCode = lang,
                    handshapeGuide = guide,
                    facialCue = "Expresión natural acorde al contexto.",
                    usageCount = 1,
                    masteryPercent = 40,
                    isPinned = true,
                    isUserCreated = true
                )
            )
            if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                cloudRepo.upsertLearnedSign(
                    LearnedSignCloudDoc(
                        userId = currentUserId,
                        glossKey = gloss,
                        displayTitle = title.trim(),
                        idiomaticOutput = idiomaticOutput.trim(),
                        category = category,
                        languageCode = lang,
                        handshapeGuide = guide,
                        facialCue = "Expresión natural acorde al contexto.",
                        usageCount = 1L,
                        masteryPercent = 40L,
                        pinned = true,
                        userCreated = true
                    )
                )
            }
            hapticManager.trigger(HapticPattern.LESSON_MASTERED, userSettings.value.hapticAlertsEnabled)
        }
    }

    fun completeTutorialLesson(lesson: TutorialLessonEntity) {
        viewModelScope.launch {
            tutorialDao.markPracticed(lesson.id, true)
            learnedSignDao.findByGloss(lesson.glossKey)?.let { sign ->
                learnedSignDao.recordUsage(sign.id)
                if (!isEffectiveOffline.value && cloudRepo.isEnabled) {
                    cloudRepo.upsertLearnedSign(
                        LearnedSignCloudDoc(
                            userId = currentUserId,
                            glossKey = sign.glossKey,
                            displayTitle = sign.displayTitle,
                            idiomaticOutput = sign.idiomaticOutput,
                            category = sign.category,
                            languageCode = sign.languageCode,
                            handshapeGuide = sign.handshapeGuide,
                            facialCue = sign.facialCue,
                            usageCount = (sign.usageCount + 1).toLong(),
                            masteryPercent = (sign.masteryPercent + 8).coerceAtMost(100).toLong(),
                            pinned = sign.isPinned,
                            userCreated = sign.isUserCreated
                        )
                    )
                }
            }
            hapticManager.trigger(HapticPattern.LESSON_MASTERED, userSettings.value.hapticAlertsEnabled)
            wearableManager.mirrorToWearable(
                caption = "Seña aprendida: ${lesson.signTitle}",
                hapticLabel = HapticPattern.LESSON_MASTERED.label,
                syncEnabled = userSettings.value.wearableSyncEnabled,
                mirrorHaptics = userSettings.value.wearableMirrorHaptics
            )
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setThemePreference(theme) }
    }

    fun setOfflineModeForced(forced: Boolean) {
        viewModelScope.launch {
            prefsRepo.setOfflineModeForced(forced)
            hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, userSettings.value.hapticAlertsEnabled)
        }
    }

    fun setActiveSignLanguage(code: String) {
        viewModelScope.launch {
            prefsRepo.setActiveSignLanguage(code)
            val profile = SignLanguageRegistry.findByCode(code)
            ttsSpeaker.updateLocale(profile.spokenLocaleTag)
            hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, userSettings.value.hapticAlertsEnabled)
        }
    }

    fun setHapticAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setHapticAlertsEnabled(enabled)
            if (enabled) hapticManager.trigger(HapticPattern.SIGN_RECOGNIZED, true)
        }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoSpeakEnabled(enabled) }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { prefsRepo.setSpeechRate(rate) }
    }

    fun setWearableSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setWearableSyncEnabled(enabled) }
    }

    fun setWearableMirrorHaptics(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setWearableMirrorHaptics(enabled) }
    }

    fun selectWearableDevice(device: WearableDeviceInfo) {
        viewModelScope.launch {
            prefsRepo.setConnectedWearableName(device.name)
            hapticManager.trigger(HapticPattern.WEARABLE_PULSE, userSettings.value.hapticAlertsEnabled)
            wearableManager.mirrorToWearable(
                caption = "Vinculado con ${device.name}",
                hapticLabel = HapticPattern.WEARABLE_PULSE.label,
                syncEnabled = true,
                mirrorHaptics = userSettings.value.wearableMirrorHaptics
            )
        }
    }

    fun sendWearableTestPulse() {
        val settings = userSettings.value
        hapticManager.trigger(HapticPattern.WEARABLE_PULSE, settings.hapticAlertsEnabled)
        wearableManager.mirrorToWearable(
            caption = "Prueba de alerta háptica y subtítulo en reloj",
            hapticLabel = HapticPattern.WEARABLE_PULSE.label,
            syncEnabled = true,
            mirrorHaptics = settings.wearableMirrorHaptics
        )
    }

    override fun onCleared() {
        super.onCleared()
        ttsSpeaker.shutdown()
        networkMonitor.release()
    }
}
