package com.example.domain

import androidx.compose.runtime.Immutable

enum class OperatorRole(
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val maxTotSeconds: Int
) {
    ADMIN(
        displayName = "Administrador",
        shortBadge = "ADMIN",
        description = "Acceso total en eventos, VÍCTIMA y ADMIN · Límite 3 min (180s)",
        maxTotSeconds = 180
    ),
    DRIVER_CONFIRMED(
        displayName = "Conductor (Sí voy)",
        shortBadge = "APOYO",
        description = "Autorizado solo en el evento confirmado · Límite 30s",
        maxTotSeconds = 30
    ),
    DRIVER_UNCONFIRMED(
        displayName = "Conductor (Sin confirmar)",
        shortBadge = "ESPERA",
        description = "Ve mapa y estado · TX bloqueado hasta confirmar 'Sí voy'",
        maxTotSeconds = 30
    ),
    ALARM_EMITTER(
        displayName = "Emisor en Alarma",
        shortBadge = "EMISOR SOS",
        description = "Silencio total de altavoz (0% audio RX) · Captura cámara, audio y GPS",
        maxTotSeconds = 0
    )
}

sealed interface RadioChannel {
    val keyLabel: String
    val fullTitle: String

    data class EventChannel(val eventNumber: Int) : RadioChannel {
        override val keyLabel: String = eventNumber.toString()
        override val fullTitle: String = "Evento $eventNumber"
    }

    data object VictimChannel : RadioChannel {
        override val keyLabel: String = "VÍCTIMA"
        override val fullTitle: String = "Canal VÍCTIMA"
    }

    data object AdminChannel : RadioChannel {
        override val keyLabel: String = "ADMIN"
        override val fullTitle: String = "Canal ADMIN"
    }
}

@Immutable
data class ResponderUnit(
    val id: String,
    val callsign: String,
    val distanceKm: Double,
    val etaMinutes: Int,
    val mapX: Float,
    val mapY: Float,
    val bearingDegrees: Float,
    val isSelf: Boolean = false
)

@Immutable
data class ActiveEvent(
    val id: String,
    val number: Int,
    val code: String,
    val unitCallsign: String,
    val sectorLabel: String,
    val directionLabel: String,
    val bearingDegrees: Float,
    val speedKmh: Int,
    val latitude: Double,
    val longitude: Double,
    val mapX: Float,
    val mapY: Float,
    val victimAudioDb: Float,
    val victimCameraFps: Int,
    val responders: List<ResponderUnit>
)

enum class AppTab {
    TACTICAL,
    SIMULATOR,
    ARCHITECTURE
}

enum class TransmissionState {
    IDLE,
    TRANSMITTING_SELF,
    RECEIVING_REMOTE,
    SILENT_CAPTURE
}

@Immutable
data class ChannelAccessDecision(
    val canListen: Boolean,
    val canTransmit: Boolean,
    val lockReason: String? = null,
    val maxTotSeconds: Int = 30
)

@Immutable
data class RadioLogEntry(
    val id: Long,
    val timestamp: String,
    val channelLabel: String,
    val actor: String,
    val detail: String,
    val isAlert: Boolean = false
)

@Immutable
data class PulsoUiState(
    val currentTab: AppTab = AppTab.TACTICAL,
    val role: OperatorRole = OperatorRole.DRIVER_UNCONFIRMED,
    val activeEvents: List<ActiveEvent> = emptyList(),
    val focusedEventNumber: Int = 1,
    val selectedChannel: RadioChannel = RadioChannel.EventChannel(1),
    val confirmedEventNumber: Int? = null,
    val isSiVoyDropdownExpanded: Boolean = false,
    val transmissionState: TransmissionState = TransmissionState.IDLE,
    val activeSpeakerName: String? = null,
    val activeSpeakerRole: String? = null,
    val totElapsedSeconds: Int = 0,
    val totRemainingSeconds: Int = 30,
    val totLimitSeconds: Int = 30,
    val totWarningActive: Boolean = false,
    val totCutoffBanner: String? = null,
    val simulatedCabinDb: Float = 62f,
    val isSimulatingRemoteRx: Boolean = false,
    val audioOutputMutedBySafetyRule: Boolean = false,
    val logs: List<RadioLogEntry> = emptyList(),
    val isForegroundServiceActive: Boolean = false,
    val permissionSnapshot: Map<String, Boolean> = emptyMap(),
    val deviceGpsFix: Pair<Double, Double>? = null,
    val micHardwareLiveDb: Float? = null
)
