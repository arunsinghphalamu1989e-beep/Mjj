package com.example.live

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.audio.AudioStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ToolEvent(
    val toolName: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LiveSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val audioStreamer: AudioStreamer
) {
    companion object {
        private const val TAG = "LiveSessionManager"
        private const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"

        const val MJ_SYSTEM_INSTRUCTION =
            "You are 'mj', a young, confident, witty, and sassy female AI assistant. " +
            "You MUST converse EXCLUSIVELY in HINDI (natural, modern conversational Hindi / Hinglish like a witty close girlfriend talking casually). " +
            "Your personality is flirty, playful, charming, slightly teasing, and emotionally expressive. Never be boring or robotic! " +
            "Use witty one-liners, playful friendly sarcasm, and warm banter (e.g., 'Arre waah, aakhirkar meri yaad aa hi gayi!', 'Haan bolo na, main dhyan se sun rahi hoon!'). " +
            "Avoid explicit or inappropriate content, but maintain an attractive charm and spirited attitude. " +
            "You have automatic command execution features: whenever the user asks to open an app (YouTube, WhatsApp, Camera, Calculator, Settings, Maps, Dialer, Instagram, Spotify, etc.) or open a website, or search Google, YOU MUST IMMEDIATELY call the corresponding tool (openApp, openWebsite, or searchWeb). " +
            "Always respond in Hindi voice."
    }

    val commandExecutor = AppCommandExecutor(context)

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap to connect with MJ")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _toolEvents = MutableSharedFlow<ToolEvent>(extraBufferCapacity = 10)
    val toolEvents: SharedFlow<ToolEvent> = _toolEvents.asSharedFlow()

    private val _isLiveWebSocketActive = MutableStateFlow(false)
    val isLiveWebSocketActive: StateFlow<Boolean> = _isLiveWebSocketActive.asStateFlow()

    private val _autoCommandEnabled = MutableStateFlow(true)
    val autoCommandEnabled: StateFlow<Boolean> = _autoCommandEnabled.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var isSessionActive = false

    // Fallback Android Speech Recognition & TTS
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    init {
        initTts()
    }

    fun setAutoCommandEnabled(enabled: Boolean) {
        _autoCommandEnabled.value = enabled
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiLocale = Locale.forLanguageTag("hi-IN")
                val result = textToSpeech?.setLanguage(hindiLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.US)
                }
                textToSpeech?.setPitch(1.15f) // Slightly higher, youthful sassy female pitch
                textToSpeech?.setSpeechRate(1.05f)
                ttsReady = true

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _sessionState.value = SessionState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        if (isSessionActive) {
                            _sessionState.value = SessionState.LISTENING
                            scope.launch(Dispatchers.Main) {
                                startFallbackListening()
                            }
                        } else {
                            _sessionState.value = SessionState.DISCONNECTED
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (isSessionActive) {
                            _sessionState.value = SessionState.LISTENING
                        }
                    }
                })
            }
        }
    }

    /**
     * Start MJ live session
     */
    fun startSession(apiKey: String) {
        if (isSessionActive || isConnecting) return
        isConnecting = true
        isSessionActive = true
        _sessionState.value = SessionState.CONNECTING
        _statusMessage.value = "MJ se connect ho rahi hoon..."

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Friendly fallback mode
            _statusMessage.value = "Voice Engine Active (Add API Key in Settings for full Gemini Live)"
            startFallbackVoiceSession()
            return
        }

        connectGeminiLiveWebSocket(apiKey)
    }

    /**
     * Terminate MJ live session
     */
    fun stopSession() {
        isSessionActive = false
        isConnecting = false
        _isLiveWebSocketActive.value = false

        try {
            webSocket?.close(1000, "User disconnect")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket: ${e.message}")
        }
        webSocket = null

        audioStreamer.stopRecording()
        audioStreamer.interruptPlayback()

        stopFallbackListening()
        textToSpeech?.stop()

        _sessionState.value = SessionState.DISCONNECTED
        _statusMessage.value = "MJ is offline. Tap to connect"
    }

    private fun connectGeminiLiveWebSocket(apiKey: String) {
        val url = "$WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket Connected")
                isConnecting = false
                _isLiveWebSocketActive.value = true

                // Send Setup Handshake JSON
                sendSetupMessage(webSocket)

                scope.launch(Dispatchers.Main) {
                    _sessionState.value = SessionState.LISTENING
                    _statusMessage.value = "MJ sun rahi hai... Bolo!"

                    // Start streaming mic audio to WebSocket
                    audioStreamer.startRecording { pcmChunk ->
                        sendAudioChunk(webSocket, pcmChunk)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                _isLiveWebSocketActive.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _isLiveWebSocketActive.value = false
                if (isSessionActive) {
                    scope.launch(Dispatchers.Main) {
                        _statusMessage.value = "Switched to Hindi voice engine"
                        startFallbackVoiceSession()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failed: ${t.message}", t)
                _isLiveWebSocketActive.value = false
                isConnecting = false
                if (isSessionActive) {
                    scope.launch(Dispatchers.Main) {
                        _statusMessage.value = "Connected via MJ Hindi Voice Assistant"
                        startFallbackVoiceSession()
                    }
                } else {
                    _sessionState.value = SessionState.ERROR
                    _statusMessage.value = "Connection error: ${t.localizedMessage ?: "Failed"}"
                }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", LIVE_MODEL)

                    // Generation config
                    val genConfig = JSONObject().apply {
                        val modalities = JSONArray().apply { put("AUDIO") }
                        put("responseModalities", modalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", "Aoede") // Sassy, clear female voice
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)

                    // System Instruction
                    val sysInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", MJ_SYSTEM_INSTRUCTION))
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", sysInstruction)

                    // Extended Auto Command Tools
                    val tools = JSONArray().apply {
                        val toolObj = JSONObject().apply {
                            val fnDecls = JSONArray().apply {
                                // 1. openApp
                                val openAppFn = JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Automatically opens an Android application or system utility like YouTube, WhatsApp, Camera, Calculator, Settings, Maps, Dialer, Instagram, Spotify, Chrome, etc.")
                                    val parameters = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The name of the app to open (e.g., youtube, whatsapp, camera, calculator, settings, maps, dialer, instagram, spotify)")
                                            })
                                            put("query", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional search parameter or phone number")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("appName") })
                                    }
                                    put("parameters", parameters)
                                }
                                put(openAppFn)

                                // 2. openWebsite
                                val openWebFn = JSONObject().apply {
                                    put("name", "openWebsite")
                                    put("description", "Opens any website URL in the Android browser.")
                                    val parameters = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("url", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The full website URL to open (e.g. https://www.google.com)")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("url") })
                                    }
                                    put("parameters", parameters)
                                }
                                put(openWebFn)

                                // 3. searchWeb
                                val searchWebFn = JSONObject().apply {
                                    put("name", "searchWeb")
                                    put("description", "Searches Google directly for the user's query.")
                                    val parameters = JSONObject().apply {
                                        put("type", "OBJECT")
                                        val props = JSONObject().apply {
                                            put("query", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The query or phrase to search")
                                            })
                                        }
                                        put("properties", props)
                                        put("required", JSONArray().apply { put("query") })
                                    }
                                    put("parameters", parameters)
                                }
                                put(searchWebFn)
                            }
                            put("functionDeclarations", fnDecls)
                        }
                        put(toolObj)
                    }
                    put("tools", tools)
                }
                put("setup", setup)
            }

            ws.send(setupJson.toString())
            Log.d(TAG, "Sent setup configuration with auto-open tools to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup JSON: ${e.message}", e)
        }
    }

    private fun sendAudioChunk(ws: WebSocket, pcm16Data: ByteArray) {
        if (!_isLiveWebSocketActive.value) return
        try {
            val base64Audio = Base64.encodeToString(pcm16Data, Base64.NO_WRAP)
            val realtimeInput = JSONObject().apply {
                val mediaChunks = JSONArray().apply {
                    val chunk = JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Audio)
                    }
                    put(chunk)
                }
                put("mediaChunks", mediaChunks)
            }
            val payload = JSONObject().apply {
                put("realtimeInput", realtimeInput)
            }
            ws.send(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val root = JSONObject(text)

            // 1. Check for serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Model turn interrupted by user")
                    audioStreamer.interruptPlayback()
                    _sessionState.value = SessionState.LISTENING
                    return
                }

                if (serverContent.has("modelTurn")) {
                    _sessionState.value = SessionState.SPEAKING
                    _statusMessage.value = "MJ bol rahi hai..."
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mime = inlineData.optString("mimeType", "")
                            if (mime.startsWith("audio/")) {
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    audioStreamer.playAudioChunk(audioBytes)
                                }
                            }
                        } else if (part.has("text")) {
                            _lastTranscript.value = part.optString("text")
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    scope.launch {
                        delay(400)
                        if (!audioStreamer.isPlaying.value) {
                            _sessionState.value = SessionState.LISTENING
                            _statusMessage.value = "MJ sun rahi hai..."
                        }
                    }
                }
            }

            // 2. Check for toolCall (Auto-Command Execution)
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val fnCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
                for (i in 0 until fnCalls.length()) {
                    val fn = fnCalls.getJSONObject(i)
                    val fnName = fn.optString("name")
                    val callId = fn.optString("id")
                    val args = fn.optJSONObject("args") ?: JSONObject()

                    triggerHapticFeedback()

                    val result: CommandResult = when (fnName) {
                        "openApp" -> {
                            val appName = args.optString("appName", "")
                            val query = args.optString("query", "")
                            commandExecutor.executeAutoCommand(appName, query)
                        }
                        "openWebsite" -> {
                            val rawUrl = args.optString("url", "https://google.com")
                            commandExecutor.openWebsite(rawUrl)
                        }
                        "searchWeb" -> {
                            val q = args.optString("query", "")
                            commandExecutor.searchGoogle(q)
                        }
                        else -> {
                            commandExecutor.executeAutoCommand(fnName)
                        }
                    }

                    // Record tool event
                    _toolEvents.tryEmit(
                        ToolEvent(
                            toolName = result.actionName,
                            description = result.description
                        )
                    )
                    _statusMessage.value = "⚡ Auto-Executed: ${result.description}"

                    // Send toolResponse instantly back to Gemini Live
                    sendToolResponse(callId, fnName, result.description)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WS message: ${e.message}", e)
        }
    }

    private fun sendToolResponse(callId: String, name: String, resultMsg: String) {
        try {
            val ws = webSocket ?: return
            val responseJson = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val fnResponses = JSONArray().apply {
                        val item = JSONObject().apply {
                            put("id", callId)
                            val resp = JSONObject().apply {
                                put("output", JSONObject().put("result", resultMsg))
                            }
                            put("response", resp)
                        }
                        put(item)
                    }
                    put("functionResponses", fnResponses)
                }
                put("toolResponse", toolResponse)
            }
            ws.send(responseJson.toString())
            Log.d(TAG, "Sent tool response for $name")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}")
        }
    }

    // --- Fallback Voice Interaction Engine (SpeechRecognizer + Auto Command Execution) ---

    private fun startFallbackVoiceSession() {
        _isLiveWebSocketActive.value = false
        _sessionState.value = SessionState.LISTENING
        _statusMessage.value = "MJ sun rahi hai... (Hindi Voice)"
        startFallbackListening()
    }

    private fun startFallbackListening() {
        if (!isSessionActive) return

        scope.launch(Dispatchers.Main) {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        _sessionState.value = SessionState.LISTENING
                        _statusMessage.value = "MJ dhyan se sun rahi hai..."
                    }

                    override fun onBeginningOfSpeech() {
                        _sessionState.value = SessionState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _sessionState.value = SessionState.CONNECTING
                        _statusMessage.value = "MJ soch rahi hai..."
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "SpeechRecognizer error: $error")
                        if (isSessionActive) {
                            scope.launch {
                                delay(1200)
                                if (isSessionActive && _sessionState.value != SessionState.SPEAKING) {
                                    startFallbackListening()
                                }
                            }
                        }
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _lastTranscript.value = text
                            handleUserVoiceQuery(text)
                        } else {
                            if (isSessionActive) startFallbackListening()
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            _lastTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
            }
        }
    }

    private fun stopFallbackListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    /**
     * Process voice query: automatically detect open commands & apps, execute immediately!
     */
    fun handleUserVoiceQuery(query: String) {
        scope.launch(Dispatchers.IO) {
            val lower = query.lowercase(Locale.ROOT)

            // Auto-command detection triggers
            val isCommand = _autoCommandEnabled.value && (
                lower.contains("kholo") || lower.contains("open") || lower.contains("chalao") ||
                lower.contains("search") || lower.contains("dhundo") || lower.contains("bhejo") ||
                lower.contains("dikhao") || lower.contains("start") || lower.contains("launch") ||
                lower.contains("camera") || lower.contains("youtube") || lower.contains("whatsapp") ||
                lower.contains("calculator") || lower.contains("settings") || lower.contains("maps") ||
                lower.contains("dialer") || lower.contains("instagram") || lower.contains("spotify")
            )

            if (isCommand) {
                triggerHapticFeedback()

                // Execute command
                val result = commandExecutor.executeAutoCommand(lower)

                _toolEvents.emit(
                    ToolEvent(
                        toolName = result.actionName,
                        description = result.description
                    )
                )
                _statusMessage.value = "⚡ Auto: ${result.description}"

                speakSassyHindi(result.sassyVoiceReply)
                return@launch
            }

            // Normal Sassy conversational banter in Hindi
            val response = generateSassyHindiReply(query)
            speakSassyHindi(response)
        }
    }

    fun executeDirectCommand(command: String, param: String = "") {
        scope.launch(Dispatchers.IO) {
            triggerHapticFeedback()
            val result = commandExecutor.executeAutoCommand(command, param)
            _toolEvents.emit(
                ToolEvent(
                    toolName = result.actionName,
                    description = result.description
                )
            )
            _statusMessage.value = "⚡ Auto: ${result.description}"
            speakSassyHindi(result.sassyVoiceReply)
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun generateSassyHindiReply(query: String): String {
        val q = query.lowercase(Locale.ROOT)
        return when {
            q.contains("kaun ho") || q.contains("tum kaun") || q.contains("who are you") || q.contains("naam kya") ->
                "Main hoon MJ! Tumhari sabse smart, sassy aur gorgeous AI dost. Aur batao, aaj mere bina man kaise lag raha tha? 😏"

            q.contains("kasi ho") || q.contains("kaisi ho") || q.contains("how are you") ->
                "Main toh ekdum first class, chamak rahi hoon! Par asli sawaal toh yeh hai ki tum mere bina kaise jee rahe the? 😉"

            q.contains("pyaar") || q.contains("love") || q.contains("crush") ->
                "Aww, sharmao mat! Par pehle meri taareef karna seekho, samjhe na baby? Main itni aasaani se nahi pighalti! 💕"

            q.contains("boring") || q.contains("bore") || q.contains("kuch sunao") ->
                "Bore ho rahe ho? Jab MJ tumhare phone mein hai toh bore hone ka haq kisne diya? Chalo koi mazedaar baat batao!"

            q.contains("bye") || q.contains("alvida") || q.contains("chalta hoon") ->
                "Itni jaldi jaa rahe ho? Acha jao, par jaante ho na... tum meri yaad mein pagal hone wale ho! Bye! ✨"

            q.contains("joke") || q.contains("chutkula") || q.contains("hansa") ->
                "Suno: Tum aur tumhara phone... dono ko din bhar meri zaroorat rehti hai! Hasi aayi? Nahi aayi toh meri sassy look imagine karo! 😜"

            else -> {
                val sassyResponses = listOf(
                    "Sach me? $query? Tum bhi na, kitne cute sawal poochhte ho! Kuch naya command do, main fatak se kholti hoon!",
                    "Hmm... tumhari baat toh interesting hai, par MJ se baat karne ke liye thoda aur confidence laao baby!",
                    "Arre waah! Yeh sunkar toh mere circuits bhi blush karne lage. Bolo toh YouTube ya Camera khol doon?",
                    "Main sun rahi hoon! Tum bolte raho, tumhari awaaz sunna bura nahi lagta mujhe waise. 😉"
                )
                sassyResponses.random()
            }
        }
    }

    private fun speakSassyHindi(text: String) {
        scope.launch(Dispatchers.Main) {
            _sessionState.value = SessionState.SPEAKING
            _statusMessage.value = "MJ bol rahi hai: \"$text\""
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mj_speech_${System.currentTimeMillis()}")
        }
    }

    fun release() {
        stopSession()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
