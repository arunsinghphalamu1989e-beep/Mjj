package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioStreamer
import com.example.live.LiveSessionManager
import com.example.live.SessionState
import com.example.live.ToolEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val audioStreamer = AudioStreamer(viewModelScope)
    val liveSessionManager = LiveSessionManager(application, viewModelScope, audioStreamer)

    val sessionState: StateFlow<SessionState> = liveSessionManager.sessionState
    val statusMessage: StateFlow<String> = liveSessionManager.statusMessage
    val lastTranscript: StateFlow<String> = liveSessionManager.lastTranscript
    val toolEvents = liveSessionManager.toolEvents
    val isLiveWebSocket = liveSessionManager.isLiveWebSocketActive
    val autoCommandEnabled: StateFlow<Boolean> = liveSessionManager.autoCommandEnabled

    // Amplitude for visualizer: blends input amplitude when listening, output amplitude when speaking
    val orbAmplitude: StateFlow<Float> = combine(
        sessionState,
        audioStreamer.inputAmplitude,
        audioStreamer.outputAmplitude
    ) { state, inAmp, outAmp ->
        when (state) {
            SessionState.LISTENING -> inAmp
            SessionState.SPEAKING -> outAmp
            else -> 0f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _customApiKey = MutableStateFlow(
        try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    )
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _recentTools = MutableStateFlow<List<ToolEvent>>(emptyList())
    val recentTools: StateFlow<List<ToolEvent>> = _recentTools.asStateFlow()

    init {
        viewModelScope.launch {
            toolEvents.collect { event ->
                _recentTools.value = (listOf(event) + _recentTools.value).take(5)
            }
        }
    }

    fun toggleSession() {
        val current = sessionState.value
        if (current == SessionState.DISCONNECTED || current == SessionState.ERROR) {
            val key = _customApiKey.value.ifBlank {
                try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            }
            liveSessionManager.startSession(key)
        } else {
            liveSessionManager.stopSession()
        }
    }

    fun toggleAutoCommand() {
        val next = !autoCommandEnabled.value
        liveSessionManager.setAutoCommandEnabled(next)
    }

    fun updateApiKey(key: String) {
        _customApiKey.value = key
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            audioStreamer.stopRecording()
        } else if (sessionState.value == SessionState.LISTENING) {
            audioStreamer.startRecording { /* mic stream */ }
        }
    }

    fun triggerQuickQuery(query: String) {
        if (sessionState.value == SessionState.DISCONNECTED) {
            toggleSession()
        }
        liveSessionManager.handleUserVoiceQuery(query)
    }

    fun executeAutoCommand(commandName: String, param: String = "") {
        if (sessionState.value == SessionState.DISCONNECTED) {
            toggleSession()
        }
        liveSessionManager.executeDirectCommand(commandName, param)
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamer.release()
        liveSessionManager.release()
    }
}
