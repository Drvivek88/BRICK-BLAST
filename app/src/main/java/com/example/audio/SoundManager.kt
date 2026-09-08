package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SoundManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    var isMuted: Boolean = false

    private val sampleRate = 22050
    private var lastHitTime = 0L

    // Precomputed short waveforms to avoid audio generation overhead during intense play
    private val cachedBuffers = ConcurrentHashMap<String, ByteArray>()

    init {
        // Pre-warm common sounds
        scope.launch {
            cachedBuffers["tick"] = generateTone(620f, 0.04f, WaveType.SQUARE, 0.2f, 760f)
            cachedBuffers["launch"] = generateTone(170f, 0.12f, WaveType.SQUARE, 0.25f, 360f)
            cachedBuffers["break"] = generateNoiseHit(0.35f, 0.12f)
            cachedBuffers["power"] = generatePowerSound()
            cachedBuffers["combo"] = generateComboSound()
            cachedBuffers["over"] = generateGameOverSound()
        }
    }

    private enum class WaveType { SINE, TRIANGLE, SQUARE, NOISE }

    fun playLaunch() {
        if (isMuted) return
        playSoundFromCache("launch") { generateTone(170f, 0.12f, WaveType.SQUARE, 0.25f, 360f) }
    }

    fun playTick() {
        if (isMuted) return
        playSoundFromCache("tick") { generateTone(620f, 0.04f, WaveType.SQUARE, 0.2f, 760f) }
    }

    fun playHit(combo: Int) {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - lastHitTime < 28) return // throttle to prevent audio clipping during multi-ball hits
        lastHitTime = now

        val cappedCombo = min(combo, 24)
        val freq = 300f * (2f.pow(cappedCombo / 12f))
        val cacheKey = "hit_$cappedCombo"
        playSoundFromCache(cacheKey) {
            generateTone(freq, 0.06f, WaveType.TRIANGLE, 0.3f, null)
        }
    }

    fun playBreak() {
        if (isMuted) return
        playSoundFromCache("break") { generateNoiseHit(0.35f, 0.12f) }
    }

    fun playPower() {
        if (isMuted) return
        playSoundFromCache("power") { generatePowerSound() }
    }

    fun playCombo() {
        if (isMuted) return
        playSoundFromCache("combo") { generateComboSound() }
    }

    fun playGameOver() {
        if (isMuted) return
        playSoundFromCache("over") { generateGameOverSound() }
    }

    private fun playSoundFromCache(key: String, generator: () -> ByteArray) {
        scope.launch {
            val buffer = cachedBuffers.getOrPut(key) { generator() }
            playAudioTrack(buffer)
        }
    }

    private val activeTracks = java.util.concurrent.atomic.AtomicInteger(0)
    private val maxConcurrentTracks = 3

    private fun playAudioTrack(pcmData: ByteArray) {
        if (activeTracks.get() >= maxConcurrentTracks) return
        activeTracks.incrementAndGet()
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmData.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()

            // Release track after audio finishes playing
            val durationMs = (pcmData.size * 1000L) / (sampleRate * 2) + 60
            scope.launch {
                kotlinx.coroutines.delay(durationMs)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Throwable) {}
                finally {
                    activeTracks.decrementAndGet()
                }
            }
        } catch (_: Throwable) {
            activeTracks.decrementAndGet()
        }
    }

    private fun generateTone(
        startFreq: Float,
        durationSec: Float,
        waveType: WaveType,
        volume: Float,
        endFreq: Float?
    ): ByteArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val data = ByteArray(totalSamples * 2)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / totalSamples
            val currentFreq = if (endFreq != null) {
                startFreq + (endFreq - startFreq) * t
            } else {
                startFreq
            }

            phase += 2.0 * PI * currentFreq / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            val sampleVal: Double = when (waveType) {
                WaveType.SINE -> sin(phase)
                WaveType.TRIANGLE -> (2.0 / PI) * kotlin.math.asin(sin(phase))
                WaveType.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                WaveType.NOISE -> (Math.random() * 2.0 - 1.0)
            }

            // Exponential fade-out envelope
            val envelope = exp(-t * 6.0)
            val amp = (sampleVal * volume * envelope * 32767).toInt().coerceIn(-32768, 32767).toShort()

            data[i * 2] = (amp.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = ((amp.toInt() shr 8) and 0xFF).toByte()
        }
        return data
    }

    private fun generateNoiseHit(volume: Float, durationSec: Float): ByteArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val data = ByteArray(totalSamples * 2)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / totalSamples
            val noise = (Math.random() * 2.0 - 1.0)
            val sub = sin(2.0 * PI * 120.0 * (1.0 - t * 0.5) * (i.toDouble() / sampleRate))
            val combined = noise * 0.7 + sub * 0.3
            val envelope = exp(-t * 8.0)
            val amp = (combined * volume * envelope * 32767).toInt().coerceIn(-32768, 32767).toShort()

            data[i * 2] = (amp.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = ((amp.toInt() shr 8) and 0xFF).toByte()
        }
        return data
    }

    private fun generatePowerSound(): ByteArray {
        val tone1 = generateTone(540f, 0.09f, WaveType.SINE, 0.3f, null)
        val tone2 = generateTone(810f, 0.14f, WaveType.SINE, 0.3f, null)
        return tone1 + tone2
    }

    private fun generateComboSound(): ByteArray {
        val tone1 = generateTone(880f, 0.08f, WaveType.TRIANGLE, 0.25f, null)
        val tone2 = generateTone(1175f, 0.12f, WaveType.TRIANGLE, 0.25f, null)
        return tone1 + tone2
    }

    private fun generateGameOverSound(): ByteArray {
        val freqs = floatArrayOf(392f, 311f, 262f, 196f)
        var combined = ByteArray(0)
        for (f in freqs) {
            combined += generateTone(f, 0.22f, WaveType.SQUARE, 0.2f, f * 0.8f)
        }
        return combined
    }
}
