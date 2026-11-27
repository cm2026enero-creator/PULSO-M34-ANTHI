package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.firebase.AnthiFirestoreRepository
import com.example.firebase.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano persistente (START_STICKY) para Pulso M34 · ANTHI.
 * - Mantiene activa la conexión en tiempo real con Firebase, el canal de radio PTT,
 *   WakeLock y AudioFocus cuando el usuario minimiza la app, bloquea el celular o cierra la vista.
 * - Nunca cierra ni elimina eventos en Firebase al ejecutarse onDestroy() u onTaskRemoved().
 */
class PulsoRadioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var firebaseObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var currentRole: String = "OPERADOR"
    private var currentEventId: String = "ev_1"
    private var isEmitterSilent: Boolean = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (isEmitterSilent) {
            enforceZeroSpeakerOutput()
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            hasAudioFocus = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            clearPersistedServiceState()
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
        if (intent != null) {
            currentRole = intent.getStringExtra(EXTRA_ROLE).orEmpty().ifBlank {
                prefs.getString(KEY_ROLE, "OPERADOR").orEmpty()
            }
            currentEventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty().ifBlank {
                prefs.getString(KEY_EVENT_ID, "ev_1").orEmpty()
            }
            isEmitterSilent = intent.getBooleanExtra(
                EXTRA_EMITTER_SILENT,
                currentRole.equals("ALARM_EMITTER", ignoreCase = true)
            )
            persistServiceState(currentRole, currentEventId, isEmitterSilent)
        } else {
            // Reinicio automático del sistema bajo START_STICKY tras cierre de vista/memoria
            currentRole = prefs.getString(KEY_ROLE, "OPERADOR").orEmpty().ifBlank { "OPERADOR" }
            currentEventId = prefs.getString(KEY_EVENT_ID, "ev_1").orEmpty().ifBlank { "ev_1" }
            isEmitterSilent = prefs.getBoolean(KEY_EMITTER_SILENT, false) ||
                currentRole.equals("ALARM_EMITTER", ignoreCase = true)
        }

        val notification = buildNotification(buildStatusSubtitle(activeCount = null))
        startForegroundCompat(notification)
        acquirePartialWakeLock()
        configureAudioFocusAndMuteState()
        startFirebaseBackgroundSync()

        isRunning = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // El usuario cerró la vista reciente: el evento sigue activo en Firebase y el servicio persiste.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        firebaseObserverJob?.cancel()
        serviceScope.cancel()
        abandonAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startFirebaseBackgroundSync() {
        if (firebaseObserverJob?.isActive == true) return
        val repository = AnthiFirestoreRepository(applicationContext)
        firebaseObserverJob = serviceScope.launch {
            Firebase.auth.authStateFlow().collect { user ->
                if (user == null) {
                    updatePersistentNotification(
                        buildStatusSubtitle(activeCount = null)
                    )
                } else {
                    repository.observeActiveEvents()
                        .catch { error ->
                            if (error is CancellationException) throw error
                        }
                        .collect { activeEvents ->
                            val subtitle = buildStatusSubtitle(activeEvents.size)
                            updatePersistentNotification(subtitle)
                        }
                }
            }
        }
    }

    private fun buildStatusSubtitle(activeCount: Int?): String {
        val countLabel = if (activeCount != null) "$activeCount evento(s) activo(s)" else "Enlace en tiempo real"
        return if (isEmitterSilent || currentRole.equals("ALARM_EMITTER", ignoreCase = true)) {
            "EMISOR SOS · $currentEventId · Silencio 0% altavoz · $countLabel"
        } else {
            "Rol: $currentRole · $currentEventId · $countLabel · PTT en 2º plano"
        }
    }

    private fun updatePersistentNotification(subtitle: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(subtitle))
    }

    private fun configureAudioFocusAndMuteState() {
        if (isEmitterSilent) {
            abandonAudioFocus()
            enforceZeroSpeakerOutput()
        } else {
            requestRadioAudioFocus()
        }
    }

    private fun enforceZeroSpeakerOutput() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_SYSTEM
        )
        streams.forEach { stream ->
            try {
                audioManager.setStreamVolume(stream, 0, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                }
            } catch (_: SecurityException) {
            }
        }
    }

    private fun requestRadioAudioFocus() {
        if (hasAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                audioManager.abandonAudioFocusRequest(req)
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasPermission(Manifest.permission.CAMERA)) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (_: SecurityException) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun acquirePartialWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(6L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        } finally {
            wakeLock = null
        }
    }

    private fun stopSelfSafely() {
        isRunning = false
        firebaseObserverJob?.cancel()
        abandonAudioFocus()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun persistServiceState(role: String, eventId: String, emitterSilent: Boolean) {
        getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ROLE, role)
            .putString(KEY_EVENT_ID, eventId)
            .putBoolean(KEY_EMITTER_SILENT, emitterSilent)
            .apply()
    }

    private fun clearPersistedServiceState() {
        getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE).edit()
            .clear()
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la radio PTT, geolocalización y captura táctica."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "pulso_m34_radio_service_channel"
        const val NOTIFICATION_ID = 3402
        const val WAKE_LOCK_TAG = "PulsoM34::TacticalRadioWakeLock"
        const val EXTRA_ROLE = "extra_role"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EMITTER_SILENT = "extra_emitter_silent"
        const val ACTION_STOP = "com.example.action.STOP_PULSO_RADIO"

        private const val SERVICE_PREFS = "pulso_m34_service_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_ROLE = "role"
        private const val KEY_EVENT_ID = "event_id"
        private const val KEY_EMITTER_SILENT = "emitter_silent"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
