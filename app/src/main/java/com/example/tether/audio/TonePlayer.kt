package com.example.tether.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

class TonePlayer(private val context: Context? = null) {
    private var audioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun playLocatorBeepSequence(frequencyHz: Int = 3000, durationMs: Long = 5000) {
        if (_isPlaying.value) {
            stop()
            return
        }

        stop()
        triggerVibration()

        // Maximize phone media volume to 100% for loud Bluetooth earbud output
        context?.let { ctx ->
            try {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.let { am ->
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        playerJob = scope.launch {
            _isPlaying.value = true
            val sampleRate = 44100
            val numSamplesPerPulse = sampleRate * 150 / 1000 // 150ms pulse
            val generatedSnd = ByteArray(2 * numSamplesPerPulse)

            for (i in 0 until numSamplesPerPulse) {
                val angle = 2.0 * Math.PI * i / (sampleRate.toDouble() / frequencyHz)
                val sample = (sin(angle) * 32767).toInt().toShort()
                generatedSnd[2 * i] = (sample.toInt() and 0x00ff).toByte()
                generatedSnd[2 * i + 1] = (sample.toInt() and 0xff00 shr 8).toByte()
            }

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(generatedSnd.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack?.write(generatedSnd, 0, generatedSnd.size)

                val startTime = System.currentTimeMillis()
                while (_isPlaying.value && (System.currentTimeMillis() - startTime < durationMs)) {
                    audioTrack?.stop()
                    audioTrack?.reloadStaticData()
                    audioTrack?.play()
                    delay(300)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 2000)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } finally {
                _isPlaying.value = false
                stopInternal()
            }
        }
    }

    fun playEmergencyAlarm() {
        stop()
        triggerVibration()

        playerJob = scope.launch {
            _isPlaying.value = true
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                for (i in 0..4) {
                    if (!_isPlaying.value) break
                    toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 400)
                    delay(500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isPlaying.value = false
            }
        }
    }

    fun triggerVibration() {
        context?.let { ctx ->
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val timings = longArrayOf(0, 500, 200, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        _isPlaying.value = false
        playerJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
