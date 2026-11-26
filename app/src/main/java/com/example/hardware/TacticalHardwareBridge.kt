package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Puente nativo Android para muestreo real de micrófono (AudioRecord) y GPS (LocationManager)
 * cuando los permisos de ejecución han sido concedidos por el usuario.
 */
object TacticalHardwareBridge {

    @SuppressLint("MissingPermission")
    fun sampleMicrophoneDb(context: Context): Float? {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) return null

        return try {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return null
            val bufferSize = minBuf.coerceAtLeast(2048)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return null
            }
            val data = ShortArray(512)
            recorder.startRecording()
            val read = recorder.read(data, 0, data.size)
            recorder.stop()
            recorder.release()
            if (read <= 0) return null

            var sumSq = 0.0
            for (i in 0 until read) {
                val s = data[i].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
            val dbFs = 20.0 * log10(rms / Short.MAX_VALUE.toDouble())
            (dbFs + 96.0).toFloat().coerceIn(32f, 96f)
        } catch (_: Throwable) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun readLastKnownGps(context: Context): Pair<Double, Double>? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val gpsLoc = if (hasFine) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = gpsLoc ?: netLoc ?: return null
            best.latitude to best.longitude
        } catch (_: Throwable) {
            null
        }
    }
}
