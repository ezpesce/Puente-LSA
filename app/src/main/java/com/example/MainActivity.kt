package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.GeminiSignAiRepository
import com.example.data.ai.LocalPlaceholderSignAiRepository
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.firebase.CloudSyncRepository
import com.example.firebase.configureAppCheck
import com.example.firebase.isFirebaseAvailable
import com.example.platform.HapticAlertManager
import com.example.platform.NetworkMonitor
import com.example.platform.TtsSpeaker
import com.example.platform.WearableSyncManager
import com.example.ui.components.HapticAlertToastBanner
import com.example.ui.components.SignLanguageSelectorSheet
import com.example.ui.screens.AiAndVoiceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DevicesAndSettingsScreen
import com.example.ui.screens.LearnScreen
import com.example.ui.screens.PhrasesAndHistoryScreen
import com.example.ui.screens.TranslatorScreen
import com.example.ui.screens.signOutFromFirebase
import com.example.ui.state.MainViewModel
import com.example.ui.theme.PuenteLsaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_TRANSLATE = "translate"
const val ROUTE_PHRASES = "phrases"
const val ROUTE_LEARN = "learn"
const val ROUTE_AI_VOICE = "ai_voice"
const val ROUTE_DEVICES = "devices"

data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val appDestinations = listOf(
    NavDestinationItem(
        route = ROUTE_TRANSLATE,
        labelRes = R.string.nav_translate,
        selectedIcon = Icons.Filled.CenterFocusStrong,
        unselectedIcon = Icons.Outlined.CenterFocusStrong,
        testTag = "nav_translate"
    ),
    NavDestinationItem(
        route = ROUTE_PHRASES,
        labelRes = R.string.nav_phrases,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        testTag = "nav_phrases"
    ),
    NavDestinationItem(
        route = ROUTE_LEARN,
        labelRes = R.string.nav_learn,
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
        testTag = "nav_learn"
    ),
    NavDestinationItem(
        route = ROUTE_AI_VOICE,
        labelRes = R.string.nav_ai_voice,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_ai_voice"
    ),
    NavDestinationItem(
        route = ROUTE_DEVICES,
        labelRes = R.string.nav_devices,
        selectedIcon = Icons.Filled.Watch,
        unselectedIcon = Icons.Outlined.Watch,
        testTag = "nav_devices"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
            val settings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.local.UserSettings()
            )
            PuenteLsaTheme(themePreference = settings.themePreference) {
                RootAuthGate()
            }
        }
    }
}

@Composable
fun RootAuthGate(firebaseAuth: FirebaseAuth? = null) {
    val context = LocalContext.current
    val auth = remember(context, firebaseAuth) {
        firebaseAuth ?: if (isFirebaseAvailable(context)) {
            runCatching { Firebase.auth }.getOrNull()
        } else {
            null
        }
    }

    if (auth == null) {
        key("local_device") {
            val viewModel: MainViewModel = viewModel(
                key = "local_device",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        MainViewModel(
                            currentUserId = "local_device",
                            database = AppDatabase.getInstance(app),
                            prefsRepo = UserPreferencesRepository(app),
                            aiRepository = LocalPlaceholderSignAiRepository(),
                            cloudRepo = CloudSyncRepository(db = null, auth = null),
                            ttsSpeaker = TtsSpeaker(app),
                            hapticManager = HapticAlertManager(app),
                            networkMonitor = NetworkMonitor(app),
                            wearableManager = WearableSyncManager(app)
                        )
                    }
                }
            )
            PuenteLsaApp(
                viewModel = viewModel,
                userEmail = "Modo local (sin cuenta Firebase)"
            )
        }
        return
    }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
            AuthScreen(
                onAuthSuccess = { },
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: MainViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val firestore = runCatching {
                            FirebaseFirestore.getInstance(databaseId)
                        }.getOrNull()
                        MainViewModel(
                            currentUserId = uid,
                            database = AppDatabase.getInstance(app),
                            prefsRepo = UserPreferencesRepository(app),
                            aiRepository = GeminiSignAiRepository(),
                            cloudRepo = CloudSyncRepository(firestore, auth),
                            ttsSpeaker = TtsSpeaker(app),
                            hapticManager = HapticAlertManager(app),
                            networkMonitor = NetworkMonitor(app),
                            wearableManager = WearableSyncManager(app)
                        )
                    }
                }
            )
            PuenteLsaApp(
                viewModel = viewModel,
                userEmail = user.email ?: "Usuario conectado"
            )
        }
    }
}

@Composable
fun PuenteLsaApp(
    viewModel: MainViewModel,
    userEmail: String
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TRANSLATE

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isSystemOnline by viewModel.isSystemOnline.collectAsStateWithLifecycle()
    val isEffectiveOffline by viewModel.isEffectiveOffline.collectAsStateWithLifecycle()
    val translationState by viewModel.translationState.collectAsStateWithLifecycle()
    val selectedGlosses by viewModel.selectedGlosses.collectAsStateWithLifecycle()
    val learnedVocabulary by viewModel.learnedVocabulary.collectAsStateWithLifecycle()
    val frequentPhrases by viewModel.frequentPhrases.collectAsStateWithLifecycle()
    val historyRecords by viewModel.translationHistory.collectAsStateWithLifecycle()
    val tutorialLessons by viewModel.tutorialLessons.collectAsStateWithLifecycle()
    val cameraMotionScore by viewModel.cameraMotionScore.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val realTimeAlert by viewModel.realTimeAlert.collectAsStateWithLifecycle()
    val bondedWearables by viewModel.bondedWearables.collectAsStateWithLifecycle()
    val lastMirroredCaption by viewModel.lastMirroredCaption.collectAsStateWithLifecycle()
    val wearableLogs by viewModel.wearableLogs.collectAsStateWithLifecycle()
    val bluetoothStatusMessage by viewModel.bluetoothStatusMessage.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val externalSearchState by viewModel.externalSearchState.collectAsStateWithLifecycle()

    var showLanguageSheet by remember { mutableStateOf(false) }

    LaunchedEffect(realTimeAlert) {
        if (realTimeAlert != null) {
            delay(3600)
            viewModel.dismissAlertBanner()
        }
    }

    if (showLanguageSheet) {
        SignLanguageSelectorSheet(
            selectedCode = settings.activeSignLanguageCode,
            onSelect = { profile -> viewModel.setActiveSignLanguage(profile.code) },
            onDismiss = { showLanguageSheet = false }
        )
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        appDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail {
                            appDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TRANSLATE,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_TRANSLATE) {
                            TranslatorScreen(
                                settings = settings,
                                isEffectiveOffline = isEffectiveOffline,
                                translationState = translationState,
                                selectedGlosses = selectedGlosses,
                                learnedVocabulary = learnedVocabulary,
                                cameraMotionScore = cameraMotionScore,
                                useFrontCamera = useFrontCamera,
                                isTorchOn = isTorchOn,
                                isSpeaking = isSpeaking,
                                requiresFirebaseAiNotice = viewModel.requiresFirebaseAiNotice,
                                onOpenLanguageSheet = { showLanguageSheet = true },
                                onToggleOfflineMode = {
                                    viewModel.setOfflineModeForced(!settings.isOfflineModeForced)
                                },
                                onToggleCameraFacing = viewModel::toggleCameraFacing,
                                onToggleTorch = viewModel::toggleTorch,
                                onProcessGestureCapture = viewModel::processCapturedSignGesture,
                                onMotionUpdate = viewModel::updateCameraMotion,
                                onAddGloss = viewModel::addGlossToken,
                                onRemoveLastGloss = viewModel::removeLastGlossToken,
                                onClearGlosses = viewModel::clearGlossTokens,
                                onSpeakText = viewModel::speakText,
                                onStopSpeaking = viewModel::stopSpeaking,
                                onSaveAsFrequent = { title, phrase ->
                                    viewModel.saveFrequentPhrase(title, phrase, "Guardadas")
                                },
                                onTriggerInterlocutorAlert = {
                                    viewModel.simulateInterlocutorAlert()
                                },
                                onOpenAiVoiceTab = {
                                    navigateToTab(ROUTE_AI_VOICE)
                                }
                            )
                        }

                        composable(ROUTE_PHRASES) {
                            PhrasesAndHistoryScreen(
                                frequentPhrases = frequentPhrases,
                                historyRecords = historyRecords,
                                activeLanguageCode = settings.activeSignLanguageCode,
                                isVoiceEnabled = settings.isVoiceExtraEnabled,
                                onUsePhrase = viewModel::useFrequentPhrase,
                                onTogglePinPhrase = viewModel::togglePhrasePinned,
                                onDeletePhrase = viewModel::deletePhrase,
                                onAddPhrase = viewModel::saveFrequentPhrase,
                                onSpeakHistoryItem = viewModel::speakText,
                                onSaveHistoryAsFrequent = viewModel::saveTranslationAsFrequent,
                                onDeleteHistoryItem = viewModel::deleteHistoryItem,
                                onClearHistory = viewModel::clearHistory
                            )
                        }

                        composable(ROUTE_LEARN) {
                            LearnScreen(
                                lessons = tutorialLessons,
                                vocabulary = learnedVocabulary,
                                isVoiceEnabled = settings.isVoiceExtraEnabled,
                                onCompleteLesson = viewModel::completeTutorialLesson,
                                onSpeakExample = viewModel::speakText,
                                onToggleSignPinned = viewModel::toggleSignPinned,
                                onAddCustomSign = viewModel::addCustomLearnedSign,
                                onOpenExternalAiSearch = { navigateToTab(ROUTE_AI_VOICE) }
                            )
                        }

                        composable(ROUTE_AI_VOICE) {
                            AiAndVoiceScreen(
                                settings = settings,
                                isEffectiveOffline = isEffectiveOffline,
                                requiresFirebaseAiNotice = viewModel.requiresFirebaseAiNotice,
                                isSpeaking = isSpeaking,
                                voiceStatus = voiceStatus,
                                externalSearchState = externalSearchState,
                                onSetAiExtraEnabled = viewModel::setAiExtraEnabled,
                                onSearchExternalSign = viewModel::searchExternalSignWithAi,
                                onSaveExternalSign = viewModel::saveExternalSignToVocabulary,
                                onSetVoiceExtraEnabled = viewModel::setVoiceExtraEnabled,
                                onSetAutoSpeak = viewModel::setAutoSpeakEnabled,
                                onSetSpeechRate = viewModel::setSpeechRate,
                                onTestVoice = viewModel::testVoiceSample,
                                onStopVoice = viewModel::stopSpeaking
                            )
                        }

                        composable(ROUTE_DEVICES) {
                            DevicesAndSettingsScreen(
                                settings = settings,
                                isSystemOnline = isSystemOnline,
                                isEffectiveOffline = isEffectiveOffline,
                                bondedWearables = bondedWearables,
                                lastMirroredCaption = lastMirroredCaption,
                                wearableLogs = wearableLogs,
                                bluetoothStatusMessage = bluetoothStatusMessage,
                                hasBluetoothPermission = viewModel.wearableManager.hasBluetoothPermission(),
                                hasNotificationPermission = viewModel.wearableManager.hasNotificationPermission(),
                                userEmail = userEmail,
                                cloudSyncState = cloudSyncState,
                                onSignOut = {
                                    signOutFromFirebase(credentialManager, scope)
                                },
                                onScanWearables = viewModel.wearableManager::scanBondedDevices,
                                onSelectWearable = viewModel::selectWearableDevice,
                                onSendWearableTest = viewModel::sendWearableTestPulse,
                                onSetWearableSync = viewModel::setWearableSyncEnabled,
                                onSetWearableMirrorHaptics = viewModel::setWearableMirrorHaptics,
                                onSetOfflineMode = viewModel::setOfflineModeForced,
                                onSetTheme = viewModel::setThemePreference,
                                onOpenLanguageSheet = { showLanguageSheet = true },
                                onSetHapticAlerts = viewModel::setHapticAlertsEnabled
                            )
                        }
                    }
                }

                HapticAlertToastBanner(
                    alert = realTimeAlert,
                    wearableSynced = settings.wearableSyncEnabled,
                    onDismiss = viewModel::dismissAlertBanner,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
