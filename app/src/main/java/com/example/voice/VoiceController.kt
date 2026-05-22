package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class VoiceController(
    private val context: Context,
    private val onResultsResult: (String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady

    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var selectedLanguageCode = "en-US"

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceController)
                }
            } catch (e: Exception) {
                Log.e("VoiceController", "Failed to create SpeechRecognizer: ${e.message}")
            }
        } else {
            Log.w("VoiceController", "Speech recognition not available on this device")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _isTtsReady.value = true
                applyTtsSettings()
                Log.d("VoiceController", "TTS Initialized successfully")
            } else {
                Log.e("VoiceController", "TTS Initialization failed")
            }
        }
    }

    fun updateSpeechSettings(rate: Float, pitch: Float, languageCode: String) {
        this.speechRate = rate
        this.speechPitch = pitch
        this.selectedLanguageCode = languageCode
        applyTtsSettings()
    }

    private fun applyTtsSettings() {
        textToSpeech?.let { tts ->
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)
            val locale = if (selectedLanguageCode.contains("ta", ignoreCase = true)) {
                Locale("ta", "IN")
            } else {
                Locale.US
            }
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceController", "Language $selectedLanguageCode is not supported")
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            val locale = if (selectedLanguageCode.contains("ta", ignoreCase = true)) {
                "ta-IN"
            } else {
                "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            stopSpeaking() // Stop any ongoing speech before listening
            _partialText.value = ""
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("VoiceController", "Error starting listening: ${e.message}")
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("VoiceController", "Error stopping listening: ${e.message}")
        }
        _isListening.value = false
    }

    fun speak(text: String) {
        if (!_isTtsReady.value || textToSpeech == null) {
            Log.w("VoiceController", "TTS is not ready or null")
            return
        }
        textToSpeech?.let { tts ->
            // Use QUEUE_FLUSH to interrupts previous speech
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NebulaSpeakID")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("VoiceController", "Error destroying SpeechRecognizer: ${e.message}")
        }

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e("VoiceController", "Error shutting down TTS: ${e.message}")
        }
    }

    // --- RecognitionListener Implementation ---

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        _partialText.value = "Listening..."
    }

    override fun onBeginningOfSpeech() {
        _partialText.value = "Hearing speech..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Can be used to drive animations if desired
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Server-side error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
            else -> "Unknown speech error"
        }
        _partialText.value = "$message. Tap to retry."
        Log.e("VoiceController", "Speech error code $error: $message")
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _partialText.value = text
            onResultsResult(text)
        } else {
            _partialText.value = "Sorry, I didn't catch that."
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _partialText.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
