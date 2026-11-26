package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Motor de tonos sintéticos de radio PTT (apertura, cierre, advertencia TOT y corte automático).
 * REGLA CRÍTICA DE SEGURIDAD: Cuando [isAlarmEmitter] es true, toda salida de audio queda
 * estrictamente bloqueada (0% reproducción por altavoz) para proteger al emisor dentro del vehículo.
 */
class RadioToneEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playPttOpenTone(isAlarmEmitter: Boolean) {
        if (isAlarmEmitter) return
        scope.launch {
            playChirpSequence(
                listOf(
                    ToneSegment(freqHz = 980.0, durationMs = 55),
                    ToneSegment(freqHz = 1320.0, durationMs = 70)
                )
            )
        }
    }

    fun playPttCloseTone(isAlarmEmitter: Boolean) {
        if (isAlarmEmitter) return
        scope.launch {
            playChirpSequence(
                listOf(
                    ToneSegment(freqHz = 1180.0, durationMs = 55),
                    ToneSegment(freqHz = 780.0, durationMs = 80)
                )
            )
        }
    }

    fun playTotWarningBeep(isAlarmEmitter: Boolean) {
        if (isAlarmEmitter) return
        scope.launch {
            playChirpSequence(
                listOf(
                    ToneSegment(freqHz = 1560.0, durationMs = 65)
                )
            )
        }
    }

    fun playTotCutoffTone(isAlarmEmitter: Boolean) {
        if (isAlarmEmitter) return
        scope.launch {
            playChirpSequence(
                listOf(
                    ToneSegment(freqHz = 880.0, durationMs = 110),
                    ToneSegment(freqHz = 440.0, durationMs = 160)
                )
            )
        }
    }

    fun playSimulatedRxTone(isAlarmEmitter: Boolean) {
        if (isAlarmEmitter) return
        scope.launch {
            playChirpSequence(
                listOf(
                    ToneSegment(freqHz = 1040.0, durationMs = 45),
                    ToneSegment(freqHz = 1240.0, durationMs = 55)
                )
            )
        }
    }

    private data class ToneSegment(val freqHz: Double, val durationMs: Int)

    private fun playChirpSequence(segments: List<ToneSegment>) {
        try {
            val sampleRate = 22050
            val samplesPerSegment = segments.map { seg ->
                (sampleRate * seg.durationMs) / 1000
            }
            val totalSamples = samplesPerSegment.sum()
            if (totalSamples <= 0) return

            val pcm = ShortArray(totalSamples)
            var offset = 0
            segments.forEachIndexed { idx, seg ->
                val count = samplesPerSegment[idx]
                val fadeSamples = min(count / 5, 160).coerceAtLeast(1)
                for (i in 0 until count) {
                    val env = when {
                        i < fadeSamples -> i.toDouble() / fadeSamples
                        i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                        else -> 1.0
                    }
                    val angle = 2.0 * PI * seg.freqHz * i / sampleRate
                    pcm[offset + i] = (sin(angle) * env * 0.28 * Short.MAX_VALUE).toInt().toShort()
                }
                offset += count
            }

            val bufferBytes = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            val totalMs = segments.sumOf { it.durationMs }.toLong() + 40L
            Thread.sleep(totalMs)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Safe fallback on headless/JVM test environments without audio hardware
        }
    }
}
