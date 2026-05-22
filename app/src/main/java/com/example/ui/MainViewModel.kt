package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatEntry
import com.example.data.ChatRepository
import com.example.data.GeminiServiceClient
import com.example.voice.CommandParser
import com.example.voice.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ChatRepository) : ViewModel() {

    // Chat History standard state
    val allMessages: StateFlow<List<ChatEntry>> = repository.allItemsStateFlow()

    private val _assistantReply = MutableStateFlow("")
    val assistantReply: StateFlow<String> = _assistantReply

    // Pending intent back-channel for Activity resolution
    private val _pendingIntent = MutableStateFlow<Intent?>(null)
    val pendingIntent: StateFlow<Intent?> = _pendingIntent

    // App Preferences / Customizable settings
    val isTamil = MutableStateFlow(false)
    val speechRate = MutableStateFlow(1.0f)
    val speechPitch = MutableStateFlow(1.0f)
    val isDarkTheme = MutableStateFlow(true)

    // Trigger TTS speak event channel
    private val _ttsSpeakTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ttsSpeakTrigger: SharedFlow<String> = _ttsSpeakTrigger

    fun clearPendingIntent() {
        _pendingIntent.value = null
    }

    fun handleIncomingSpeech(context: Context, command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Save User command to local DB
            repository.insertMessage(command, isUser = true)

            // 1. Process via offline Command Parser
            val isTamilMode = isTamil.value
            val result = CommandParser.parse(context, command, isTamilMode)

            when (result) {
                is CommandResult.HandleIntent -> {
                    // Update assistant text & speech reply
                    _assistantReply.value = result.display
                    repository.insertMessage(result.display, isUser = false)
                    _ttsSpeakTrigger.tryEmit(result.speech)
                    // Pipeline back to main thread/Activity to launch
                    _pendingIntent.value = result.intent
                }
                is CommandResult.TextReply -> {
                    // Verify if it's the default unmatched fallback AND we have online access
                    val isUnmatched = result.display.contains("understand this request offline", ignoreCase = true) || 
                                      result.display.contains("மன்னிக்கவும், எனக்கு அது இன்னும் புரியவில்லை", ignoreCase = true)

                    if (isUnmatched) {
                        _assistantReply.value = if (isTamilMode) "சிந்திக்கிறது..." else "Thinking..."
                        
                        // Query online Gemini model intelligence passing context history
                        val currentList = allMessages.value
                        val response = GeminiServiceClient.getResponse(command, currentList, if (isTamilMode) "Tamil" else "English")
                        
                        _assistantReply.value = response
                        repository.insertMessage(response, isUser = false)
                        _ttsSpeakTrigger.tryEmit(response)
                    } else {
                        // Return the offline matches cleanly
                        _assistantReply.value = result.display
                        repository.insertMessage(result.display, isUser = false)
                        _ttsSpeakTrigger.tryEmit(result.speech)
                    }
                }
            }
        }
    }

    fun handleManualText(context: Context, text: String) {
        handleIncomingSpeech(context, text)
    }

    fun triggerTestSpeak() {
        val testPhrase = if (isTamil.value) {
            "வணக்கம், நான் நெபுலா. உங்கள் குரல் கட்டுப்பாட்டு தளம் தயார் நிலையில் உள்ளது."
        } else {
            "Vocal core synchronised. I am Nebula, standing by for commands."
        }
        _ttsSpeakTrigger.tryEmit(testPhrase)
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }
}

// Extension to safely map Flow in Repository cleanly
private fun ChatRepository.allItemsStateFlow(): StateFlow<List<ChatEntry>> {
    // Flow mapper to State
    val flow = this.allMessages
    val sFlow = MutableStateFlow<List<ChatEntry>>(emptyList())
    CoroutineScopeHelper.launch {
        flow.collect {
            sFlow.value = it
        }
    }
    return sFlow
}

// Simple internal helper to launch scope since VM doesn't have repository scope
private object CoroutineScopeHelper {
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

class MainViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
