package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.ChatDatabase
import com.example.data.ChatRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.ChatHistoryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.voice.VoiceController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy { ChatDatabase.getDatabase(applicationContext) }
    private val repository by lazy { ChatRepository(database.chatDao()) }
    
    // Inject ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository)
    }

    private var voiceController: VoiceController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Persistent onboarding state tracker
        val sharedPrefs = getSharedPreferences("nebula_prefs", Context.MODE_PRIVATE)
        var showOnboardingInit = sharedPrefs.getBoolean("show_onboarding", true)

        setContent {
            var isOnboardingCompleted by remember { mutableStateOf(!showOnboardingInit) }
            val darkThemeMode by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = darkThemeMode) {
                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        onFinished = {
                            sharedPrefs.edit().putBoolean("show_onboarding", false).apply()
                            isOnboardingCompleted = true
                            initializeVoiceEngine()
                        }
                    )
                } else {
                    MainScreenContainer()
                }
            }
        }

        // Onboarding bypass check to spin up voice engine instantly if already completed
        if (!showOnboardingInit) {
            initializeVoiceEngine()
        }

        // Observe pending intents from the ViewModel to trigger actions on main Thread safely
        lifecycleScope.launch {
            viewModel.pendingIntent.collect { intent ->
                if (intent != null) {
                    try {
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Failed to execute phone action: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "Error executing intent: ${e.message}")
                    } finally {
                        viewModel.clearPendingIntent()
                    }
                }
            }
        }
    }

    private fun initializeVoiceEngine() {
        if (voiceController != null) return
        
        // Setup speech engine callbacks
        voiceController = VoiceController(
            context = applicationContext,
            onResultsResult = { spokenText ->
                viewModel.handleIncomingSpeech(applicationContext, spokenText)
            }
        )

        // Observe voice controller parameters in ViewModel and apply adjustments
        lifecycleScope.launch {
            launch {
                viewModel.isTamil.collect { isTamil ->
                    applyControllerVocalConfig()
                }
            }
            launch {
                viewModel.speechRate.collect { _ ->
                    applyControllerVocalConfig()
                }
            }
            launch {
                viewModel.speechPitch.collect { _ ->
                    applyControllerVocalConfig()
                }
            }
            // Bind spoken readbacks triggers from ViewModel
            launch {
                viewModel.ttsSpeakTrigger.collectLatest { verbalReply ->
                    voiceController?.speak(verbalReply)
                }
            }
        }
    }

    private fun applyControllerVocalConfig() {
        val rate = viewModel.speechRate.value
        val pitch = viewModel.speechPitch.value
        val locale = if (viewModel.isTamil.value) "ta-IN" else "en-US"
        voiceController?.updateSpeechSettings(rate, pitch, locale)
    }

    @Composable
    private fun MainScreenContainer() {
        var currentTab by remember { mutableStateOf(0) }
        val chatHistory by viewModel.allMessages.collectAsStateWithLifecycle()
        val isListening by voiceController?.isListening?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }
        val partialVox by voiceController?.partialText?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
        val assistantReply by viewModel.assistantReply.collectAsStateWithLifecycle()
        val isTamilVocal by viewModel.isTamil.collectAsStateWithLifecycle()
        val speechRateVal by viewModel.speechRate.collectAsStateWithLifecycle()
        val speechPitchVal by viewModel.speechPitch.collectAsStateWithLifecycle()
        val darkThemeMode by viewModel.isDarkTheme.collectAsStateWithLifecycle()

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier.shadow(16.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        label = { Text("Dashboard") },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Dashboard"
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        label = { Text("History") },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Forum,
                                contentDescription = "Exchange History"
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        label = { Text("Settings") },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                when (currentTab) {
                    0 -> DashboardScreen(
                        isListening = isListening,
                        partialSpeech = partialVox,
                        assistantReply = assistantReply,
                        isTamil = isTamilVocal,
                        onMicClick = {
                            if (isListening) {
                                voiceController?.stopListening()
                            } else {
                                voiceController?.startListening()
                            }
                        },
                        onTamilToggle = { toggle ->
                            viewModel.isTamil.value = toggle
                        },
                        onQuickAction = { command ->
                            viewModel.handleIncomingSpeech(applicationContext, command)
                        }
                    )
                    1 -> ChatHistoryScreen(
                        currentHistory = chatHistory,
                        onSendManualText = { manualPrompt ->
                            viewModel.handleManualText(applicationContext, manualPrompt)
                        },
                        onDeleteMessage = { id ->
                            viewModel.deleteMessage(id)
                        },
                        onClearAllHistory = {
                            viewModel.clearAllHistory()
                        }
                    )
                    2 -> SettingsScreen(
                        speechRate = speechRateVal,
                        speechPitch = speechPitchVal,
                        isTamil = isTamilVocal,
                        isDarkTheme = darkThemeMode,
                        onSpeechRateChange = { rate -> viewModel.speechRate.value = rate },
                        onSpeechPitchChange = { pitch -> viewModel.speechPitch.value = pitch },
                        onLanguageChange = { isTamilSelected -> viewModel.isTamil.value = isTamilSelected },
                        onThemeToggle = { isDark -> viewModel.isDarkTheme.value = isDark },
                        onWipeHistory = { viewModel.clearAllHistory() },
                        onTriggerTestSpeak = { viewModel.triggerTestSpeak() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        voiceController?.destroy()
        voiceController = null
        super.onDestroy()
    }
}
