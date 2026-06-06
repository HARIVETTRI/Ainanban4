package com.example.ui.voice

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
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "VoiceManager"

    // Text To Speech (TTS) Engine
    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Speech To Text (STT) Engine
    private var speechRecognizer: SpeechRecognizer? = null
    private val _sttState = MutableStateFlow<SttState>(SttState.Idle)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private val _realtimeInputText = MutableStateFlow("")
    val realtimeInputText: StateFlow<String> = _realtimeInputText.asStateFlow()

    sealed interface SttState {
        object Idle : SttState
        object Listening : SttState
        object Processing : SttState
        data class Error(val message: String) : SttState
        data class FinalResult(val text: String) : SttState
    }

    init {
        // Initialize TTS
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to instantiate TTS", e)
        }

        // Initialize SpeechRecognizer
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _sttState.value = SttState.Listening
                    _realtimeInputText.value = ""
                }

                override fun onBeginningOfSpeech() {
                    _sttState.value = SttState.Listening
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Can be used for audio visualization if needed
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _sttState.value = SttState.Processing
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.e(tag, "SpeechRecognizer Error: $errorMessage ($error)")
                    _sttState.value = SttState.Error(errorMessage)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val resultText = matches?.firstOrNull() ?: ""
                    if (resultText.isNotBlank()) {
                        _realtimeInputText.value = resultText
                        _sttState.value = SttState.FinalResult(resultText)
                    } else {
                        _sttState.value = SttState.Idle
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotBlank()) {
                        _realtimeInputText.value = partialText
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Log.w(tag, "Speech Recognition is not available on this device")
            _sttState.value = SttState.Error("Speech recognition not supported")
        }
    }

    // TTS OnInitListener callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(tag, "Language is not supported or missing data")
                _isTtsReady.value = false
            } else {
                _isTtsReady.value = true
                Log.i(tag, "TTS initialized successfully")
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            }
        } else {
            Log.e(tag, "Initialization of TTS failed")
            _isTtsReady.value = false
        }
    }

    // Playback speech
    fun speak(text: String) {
        if (!_isTtsReady.value) {
            Log.w(tag, "TTS is not ready yet")
            return
        }
        stopSpeaking() // interrupt any active speech before starting new
        _isSpeaking.value = true
        // Clean markdown indicators or emojis from speech to sound natural if needed, but simple text is fine.
        val cleanText = sanitizeTextForSpeech(text)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "aura_val")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    // Speech to Text trigger
    fun startListening() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        _sttState.value = SttState.Idle
        _realtimeInputText.value = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
            _sttState.value = SttState.Error("Failed to trigger record engine")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop listening", e)
        }
    }

    fun resetSttState() {
        _sttState.value = SttState.Idle
        _realtimeInputText.value = ""
    }

    // Resource cleanup
    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS", e)
        }

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(tag, "Error destroying SpeechRecognizer", e)
        }
    }

    private fun sanitizeTextForSpeech(text: String): String {
        // Strip out some non-speakable formatting elements like asterisks, emoji icons, backticks
        return text
            .replace(Regex("[*`#_]"), "")
            .replace(Regex("aura:", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
