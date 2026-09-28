package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Handles real-time PCM audio streaming:
 * - 16kHz PCM16 Mono mic input for Gemini Live API
 * - 24kHz PCM16 Mono AudioTrack for Gemini Live voice response playback
 * - Real-time RMS amplitude extraction for UI visualizer
 */
class AudioStreamer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var isRecording = false

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(onAudioChunk: (ByteArray) -> Unit): Boolean {
        if (isRecording) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, 2048)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readCount > 0) {
                        // Calculate RMS amplitude
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            val sample = shortBuffer[i]
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readCount)
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        _inputAmplitude.value = normalized

                        // Pack into little-endian PCM16 bytes
                        byteBuffer.clear()
                        for (i in 0 until readCount) {
                            byteBuffer.putShort(shortBuffer[i])
                        }
                        val chunk = ByteArray(readCount * 2)
                        byteBuffer.position(0)
                        byteBuffer.get(chunk, 0, chunk.size)

                        onAudioChunk(chunk)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record: ${e.message}")
        }
        audioRecord = null
        _inputAmplitude.value = 0f
    }

    private fun initAudioTrackIfNeeded() {
        if (audioTrack == null) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, 8192)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        }
    }

    /**
     * Plays a 24kHz PCM16 audio chunk received from Gemini Live API
     */
    fun playAudioChunk(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        try {
            initAudioTrackIfNeeded()
            _isPlaying.value = true

            // Calculate output amplitude
            var sum = 0.0
            val samplesCount = pcmData.size / 2
            val shortBuffer = ShortArray(samplesCount)
            ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)

            for (sample in shortBuffer) {
                sum += sample * sample
            }
            val rms = sqrt(sum / maxOf(1, samplesCount))
            _outputAmplitude.value = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

            audioTrack?.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio chunk: ${e.message}", e)
        }
    }

    /**
     * Handle model interruption: stop playback and clear pending buffers instantly
     */
    fun interruptPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting playback: ${e.message}")
        }
        _outputAmplitude.value = 0f
        _isPlaying.value = false
    }

    fun release() {
        stopRecording()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track: ${e.message}")
        }
        audioTrack = null
        _outputAmplitude.value = 0f
        _isPlaying.value = false
    }
}
