package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.RadioToneEngine
import com.example.domain.ActiveEvent
import com.example.domain.AppTab
import com.example.domain.ChannelAccessDecision
import com.example.domain.OperatorRole
import com.example.domain.PulsoUiState
import com.example.domain.RadioChannel
import com.example.domain.RadioLogEntry
import com.example.domain.ResponderUnit
import com.example.domain.TransmissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PulsoViewModel @JvmOverloads constructor(
    private val toneEngine: RadioToneEngine = RadioToneEngine(),
    startTelemetry: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<PulsoUiState> = _uiState.asStateFlow()

    private var totTickerJob: Job? = null
    private var simRxJob: Job? = null
    private var telemetryJob: Job? = null
    private var logCounter = 1L

    init {
        if (startTelemetry) {
            startLiveTelemetrySimulation()
        }
    }

    private fun createInitialState(): PulsoUiState {
        val initialEvents = buildEventPool(count = 2)
        return PulsoUiState(
            currentTab = AppTab.TACTICAL,
            role = OperatorRole.DRIVER_UNCONFIRMED,
            activeEvents = initialEvents,
            focusedEventNumber = 1,
            selectedChannel = RadioChannel.EventChannel(1),
            confirmedEventNumber = null,
            transmissionState = TransmissionState.IDLE,
            totElapsedSeconds = 0,
            totRemainingSeconds = 30,
            totLimitSeconds = 30,
            audioOutputMutedBySafetyRule = false,
            logs = listOf(
                RadioLogEntry(
                    id = nextLogId(),
                    timestamp = nowStamp(),
                    channelLabel = "ANTHI",
                    actor = "CENTRAL M34",
                    detail = "Enlace táctico activo · 2 eventos simultáneos en curso.",
                    isAlert = true
                )
            )
        )
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab, isSiVoyDropdownExpanded = false) }
    }

    fun setSimulatedEventCount(count: Int) {
        val clamped = count.coerceIn(1, 3)
        stopTransmissionInternal(playCloseTone = false, reason = null)
        _uiState.update { state ->
            val newEvents = buildEventPool(clamped)
            val validConfirmed = state.confirmedEventNumber?.takeIf { it <= clamped }
            val updatedRole = if (state.role == OperatorRole.DRIVER_CONFIRMED && validConfirmed == null) {
                OperatorRole.DRIVER_UNCONFIRMED
            } else {
                state.role
            }
            val validFocused = state.focusedEventNumber.coerceIn(1, clamped)
            val validChannel = when (val ch = state.selectedChannel) {
                is RadioChannel.EventChannel -> {
                    if (ch.eventNumber <= clamped) ch else RadioChannel.EventChannel(1)
                }
                else -> ch
            }
            val limit = computeMaxTotFor(updatedRole, validChannel)
            state.copy(
                activeEvents = newEvents,
                focusedEventNumber = validFocused,
                selectedChannel = validChannel,
                confirmedEventNumber = validConfirmed,
                role = updatedRole,
                isSiVoyDropdownExpanded = false,
                totLimitSeconds = limit,
                totRemainingSeconds = limit,
                totElapsedSeconds = 0,
                totCutoffBanner = null
            )
        }
        appendLog(
            channel = "SIM",
            actor = "SIMULADOR",
            detail = "Escenario ajustado a $clamped evento(s) activo(s)."
        )
    }

    fun setRole(newRole: OperatorRole) {
        stopTransmissionInternal(playCloseTone = false, reason = null)
        simRxJob?.cancel()
        _uiState.update { state ->
            val confirmedEvent = when (newRole) {
                OperatorRole.DRIVER_CONFIRMED -> state.confirmedEventNumber ?: state.focusedEventNumber
                OperatorRole.DRIVER_UNCONFIRMED -> null
                OperatorRole.ADMIN -> null
                OperatorRole.ALARM_EMITTER -> null
            }
            val nextChannel = when (newRole) {
                OperatorRole.ALARM_EMITTER -> RadioChannel.VictimChannel
                OperatorRole.DRIVER_CONFIRMED -> RadioChannel.EventChannel(confirmedEvent ?: 1)
                else -> state.selectedChannel
            }
            val isEmitter = newRole == OperatorRole.ALARM_EMITTER
            val limit = computeMaxTotFor(newRole, nextChannel)
            state.copy(
                role = newRole,
                confirmedEventNumber = confirmedEvent,
                selectedChannel = nextChannel,
                isSiVoyDropdownExpanded = false,
                transmissionState = if (isEmitter) TransmissionState.SILENT_CAPTURE else TransmissionState.IDLE,
                activeSpeakerName = if (isEmitter) "CAPTURA DISCRETA (MÓVIL SOS)" else null,
                activeSpeakerRole = if (isEmitter) "SIN AUDIO RX" else null,
                isSimulatingRemoteRx = false,
                audioOutputMutedBySafetyRule = isEmitter,
                totLimitSeconds = limit,
                totRemainingSeconds = limit,
                totElapsedSeconds = 0,
                totWarningActive = false,
                totCutoffBanner = null
            )
        }
        val msg = when (newRole) {
            OperatorRole.ALARM_EMITTER ->
                "MODO EMISOR EN ALARMA: Altavoz silenciado 100%. Capturando cámara, mic y GPS."
            OperatorRole.ADMIN ->
                "Rol Administrador activo: acceso libre en Eventos, VÍCTIMA y ADMIN (TOT 180s)."
            OperatorRole.DRIVER_CONFIRMED ->
                "Rol Conductor con 'Sí voy': autorizado en su evento confirmado (TOT 30s)."
            OperatorRole.DRIVER_UNCONFIRMED ->
                "Rol Conductor sin confirmar: transmisión bloqueada hasta confirmar 'Sí voy'."
        }
        appendLog(
            channel = newRole.shortBadge,
            actor = "CAMBIO DE ROL",
            detail = msg,
            isAlert = newRole == OperatorRole.ALARM_EMITTER
        )
    }

    fun focusEvent(eventNumber: Int) {
        _uiState.update { state ->
            if (state.activeEvents.none { it.number == eventNumber }) return@update state
            state.copy(focusedEventNumber = eventNumber)
        }
    }

    fun selectChannel(channel: RadioChannel) {
        val current = _uiState.value
        if (current.transmissionState == TransmissionState.TRANSMITTING_SELF) {
            stopTransmissionInternal(playCloseTone = true, reason = null)
        }
        _uiState.update { state ->
            val newFocused = if (channel is RadioChannel.EventChannel) channel.eventNumber else state.focusedEventNumber
            val limit = computeMaxTotFor(state.role, channel)
            state.copy(
                selectedChannel = channel,
                focusedEventNumber = newFocused,
                totLimitSeconds = limit,
                totRemainingSeconds = limit,
                totElapsedSeconds = 0,
                totWarningActive = false,
                totCutoffBanner = null
            )
        }
    }

    fun onSiVoyButtonClicked() {
        val state = _uiState.value
        if (state.role == OperatorRole.ADMIN || state.role == OperatorRole.ALARM_EMITTER) return

        if (state.activeEvents.size <= 1) {
            val singleEvent = state.activeEvents.firstOrNull()?.number ?: 1
            if (state.confirmedEventNumber == singleEvent && state.role == OperatorRole.DRIVER_CONFIRMED) {
                cancelSiVoyConfirmation()
            } else {
                confirmSiVoyForEvent(singleEvent)
            }
        } else {
            _uiState.update { it.copy(isSiVoyDropdownExpanded = !it.isSiVoyDropdownExpanded) }
        }
    }

    fun dismissSiVoyDropdown() {
        _uiState.update { it.copy(isSiVoyDropdownExpanded = false) }
    }

    fun confirmSiVoyForEvent(eventNumber: Int) {
        stopTransmissionInternal(playCloseTone = false, reason = null)
        _uiState.update { state ->
            val targetChannel = RadioChannel.EventChannel(eventNumber)
            val limit = computeMaxTotFor(OperatorRole.DRIVER_CONFIRMED, targetChannel)
            state.copy(
                role = OperatorRole.DRIVER_CONFIRMED,
                confirmedEventNumber = eventNumber,
                focusedEventNumber = eventNumber,
                selectedChannel = targetChannel,
                isSiVoyDropdownExpanded = false,
                totLimitSeconds = limit,
                totRemainingSeconds = limit,
                totElapsedSeconds = 0,
                totCutoffBanner = null
            )
        }
        appendLog(
            channel = "EV-$eventNumber",
            actor = "SÍ VOY CONFIRMADO",
            detail = "Enlace ANTHI M34 confirmado para Evento $eventNumber. TX autorizado (30s TOT)."
        )
    }

    fun cancelSiVoyConfirmation() {
        stopTransmissionInternal(playCloseTone = false, reason = null)
        _uiState.update { state ->
            val limit = computeMaxTotFor(OperatorRole.DRIVER_UNCONFIRMED, state.selectedChannel)
            state.copy(
                role = OperatorRole.DRIVER_UNCONFIRMED,
                confirmedEventNumber = null,
                isSiVoyDropdownExpanded = false,
                totLimitSeconds = limit,
                totRemainingSeconds = limit,
                totElapsedSeconds = 0
            )
        }
        appendLog(
            channel = "ANTHI",
            actor = "DESPACHO",
            detail = "Confirmación 'Sí voy' liberada. TX bloqueado."
        )
    }

    fun evaluateChannelAccess(
        role: OperatorRole = _uiState.value.role,
        channel: RadioChannel = _uiState.value.selectedChannel,
        confirmedEventNumber: Int? = _uiState.value.confirmedEventNumber
    ): ChannelAccessDecision {
        return when (role) {
            OperatorRole.ALARM_EMITTER -> ChannelAccessDecision(
                canListen = false,
                canTransmit = false,
                lockReason = "SILENCIO TOTAL: altavoz bloqueado · captura activa",
                maxTotSeconds = 0
            )
            OperatorRole.ADMIN -> ChannelAccessDecision(
                canListen = true,
                canTransmit = true,
                lockReason = null,
                maxTotSeconds = 180
            )
            OperatorRole.DRIVER_UNCONFIRMED -> when (channel) {
                is RadioChannel.AdminChannel -> ChannelAccessDecision(
                    canListen = false,
                    canTransmit = false,
                    lockReason = "Canal exclusivo para Administradores",
                    maxTotSeconds = 30
                )
                is RadioChannel.VictimChannel -> ChannelAccessDecision(
                    canListen = false,
                    canTransmit = false,
                    lockReason = "Confirma 'Sí voy' en un evento para monitorear VÍCTIMA",
                    maxTotSeconds = 30
                )
                is RadioChannel.EventChannel -> ChannelAccessDecision(
                    canListen = true,
                    canTransmit = false,
                    lockReason = "Confirma 'Sí voy' para hablar en Evento ${channel.eventNumber}",
                    maxTotSeconds = 30
                )
            }
            OperatorRole.DRIVER_CONFIRMED -> when (channel) {
                is RadioChannel.AdminChannel -> ChannelAccessDecision(
                    canListen = false,
                    canTransmit = false,
                    lockReason = "Canal exclusivo para Administradores",
                    maxTotSeconds = 30
                )
                is RadioChannel.VictimChannel -> ChannelAccessDecision(
                    canListen = true,
                    canTransmit = false,
                    lockReason = "Canal VÍCTIMA: solo monitoreo de cabina y GPS",
                    maxTotSeconds = 30
                )
                is RadioChannel.EventChannel -> {
                    if (confirmedEventNumber == channel.eventNumber) {
                        ChannelAccessDecision(
                            canListen = true,
                            canTransmit = true,
                            lockReason = null,
                            maxTotSeconds = 30
                        )
                    } else {
                        ChannelAccessDecision(
                            canListen = true,
                            canTransmit = false,
                            lockReason = "Autorizado solo en Evento ${confirmedEventNumber ?: 1}",
                            maxTotSeconds = 30
                        )
                    }
                }
            }
        }
    }

    fun startPttTransmission() {
        val state = _uiState.value
        val access = evaluateChannelAccess(state.role, state.selectedChannel, state.confirmedEventNumber)
        if (!access.canTransmit) {
            return
        }
        if (state.transmissionState == TransmissionState.TRANSMITTING_SELF) return

        simRxJob?.cancel()
        val limit = access.maxTotSeconds
        val isEmitter = state.role == OperatorRole.ALARM_EMITTER
        toneEngine.playPttOpenTone(isAlarmEmitter = isEmitter)

        _uiState.update {
            it.copy(
                transmissionState = TransmissionState.TRANSMITTING_SELF,
                activeSpeakerName = if (it.role == OperatorRole.ADMIN) "TÚ · ADMIN-01" else "TÚ · MÓVIL-07",
                activeSpeakerRole = it.role.shortBadge,
                totLimitSeconds = limit,
                totElapsedSeconds = 0,
                totRemainingSeconds = limit,
                totWarningActive = false,
                totCutoffBanner = null,
                isSimulatingRemoteRx = false
            )
        }

        appendLog(
            channel = state.selectedChannel.keyLabel,
            actor = if (state.role == OperatorRole.ADMIN) "ADMIN-01" else "MÓVIL-07",
            detail = "PTT abierto en ${state.selectedChannel.fullTitle} (límite ${limit}s)."
        )

        startTotTicker(limit)
    }

    fun stopPttTransmission() {
        val state = _uiState.value
        if (state.transmissionState != TransmissionState.TRANSMITTING_SELF) return
        stopTransmissionInternal(
            playCloseTone = true,
            reason = null
        )
    }

    /**
     * Botón rápido del simulador para saltar a los últimos 5 segundos del límite TOT
     * y verificar visual y sonoramente el corte automático a los 30s o 180s.
     */
    fun jumpTotToLastFiveSeconds() {
        val state = _uiState.value
        val access = evaluateChannelAccess()
        if (!access.canTransmit) {
            _uiState.update {
                it.copy(
                    totCutoffBanner = "No se puede probar TOT: ${access.lockReason ?: "TX bloqueado"}"
                )
            }
            return
        }
        startTotFromLastFiveSeconds(access.maxTotSeconds)
    }

    fun testTotCutoff30s() {
        confirmSiVoyForEvent(_uiState.value.focusedEventNumber)
        startTotFromLastFiveSeconds(30)
    }

    fun testTotCutoff180s() {
        setRole(OperatorRole.ADMIN)
        selectChannel(RadioChannel.AdminChannel)
        startTotFromLastFiveSeconds(180)
    }

    fun testAlarmEmitterSilence() {
        setRole(OperatorRole.ALARM_EMITTER)
        simulateIncomingRadioTransmission()
    }

    fun updateHardwareTelemetry(micDb: Float?, gpsFix: Pair<Double, Double>?) {
        _uiState.update { state ->
            state.copy(
                micHardwareLiveDb = micDb ?: state.micHardwareLiveDb,
                deviceGpsFix = gpsFix ?: state.deviceGpsFix,
                simulatedCabinDb = micDb ?: state.simulatedCabinDb
            )
        }
    }

    private fun startTotFromLastFiveSeconds(limit: Int) {
        val state = _uiState.value
        val remaining = 5
        val elapsed = (limit - remaining).coerceAtLeast(0)

        if (state.transmissionState != TransmissionState.TRANSMITTING_SELF) {
            toneEngine.playPttOpenTone(isAlarmEmitter = state.role == OperatorRole.ALARM_EMITTER)
        }

        _uiState.update {
            it.copy(
                transmissionState = TransmissionState.TRANSMITTING_SELF,
                activeSpeakerName = if (it.role == OperatorRole.ADMIN) "TÚ · ADMIN-01" else "TÚ · MÓVIL-07",
                activeSpeakerRole = it.role.shortBadge,
                totLimitSeconds = limit,
                totElapsedSeconds = elapsed,
                totRemainingSeconds = remaining,
                totWarningActive = true,
                totCutoffBanner = "Prueba TOT (${limit}s): últimos 5s antes del corte automático"
            )
        }
        toneEngine.playTotWarningBeep(isAlarmEmitter = state.role == OperatorRole.ALARM_EMITTER)
        startTotTicker(limit, initialElapsed = elapsed)
    }

    fun simulateIncomingRadioTransmission() {
        val state = _uiState.value
        if (state.transmissionState == TransmissionState.TRANSMITTING_SELF) return

        simRxJob?.cancel()
        val isEmitter = state.role == OperatorRole.ALARM_EMITTER
        // CRITICAL: ToneEngine enforces silence if isAlarmEmitter == true
        toneEngine.playSimulatedRxTone(isAlarmEmitter = isEmitter)

        if (isEmitter) {
            appendLog(
                channel = state.selectedChannel.keyLabel,
                actor = "BLOQUEO SEGURIDAD",
                detail = "Audio RX entrante suprimido (0% altavoz) porque el dispositivo es Emisor en Alarma.",
                isAlert = true
            )
            _uiState.update {
                it.copy(
                    totCutoffBanner = "RX entrante bloqueado por regla de silencio del Emisor en Alarma"
                )
            }
            return
        }

        val speaker = when (state.selectedChannel) {
            is RadioChannel.AdminChannel -> "COORD. CENTRAL" to "ADMIN"
            is RadioChannel.VictimChannel -> "CABINA SOS EV.${state.focusedEventNumber}" to "AUDIO AMBIENTE"
            is RadioChannel.EventChannel -> "APOYO ALPHA-4" to "EN RUTA"
        }

        _uiState.update {
            it.copy(
                transmissionState = TransmissionState.RECEIVING_REMOTE,
                activeSpeakerName = speaker.first,
                activeSpeakerRole = speaker.second,
                isSimulatingRemoteRx = true,
                totCutoffBanner = null
            )
        }

        appendLog(
            channel = state.selectedChannel.keyLabel,
            actor = speaker.first,
            detail = "Transmisión entrante simulada en ${state.selectedChannel.fullTitle}."
        )

        simRxJob = viewModelScope.launch {
            delay(3600)
            _uiState.update { current ->
                if (current.role == OperatorRole.ALARM_EMITTER) {
                    current
                } else {
                    current.copy(
                        transmissionState = TransmissionState.IDLE,
                        activeSpeakerName = null,
                        activeSpeakerRole = null,
                        isSimulatingRemoteRx = false
                    )
                }
            }
            toneEngine.playPttCloseTone(isAlarmEmitter = _uiState.value.role == OperatorRole.ALARM_EMITTER)
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(totCutoffBanner = null) }
    }

    fun updatePermissionSnapshot(permissions: Map<String, Boolean>) {
        _uiState.update { it.copy(permissionSnapshot = permissions) }
    }

    fun setForegroundServiceState(active: Boolean) {
        _uiState.update { it.copy(isForegroundServiceActive = active) }
    }

    private fun startTotTicker(limitSeconds: Int, initialElapsed: Int = 0) {
        totTickerJob?.cancel()
        totTickerJob = viewModelScope.launch {
            var elapsed = initialElapsed
            while (isActive && elapsed < limitSeconds) {
                delay(1000L)
                elapsed += 1
                val remaining = (limitSeconds - elapsed).coerceAtLeast(0)
                val warningThreshold = if (limitSeconds >= 180) 10 else 5
                val inWarning = remaining in 1..warningThreshold

                if (inWarning) {
                    toneEngine.playTotWarningBeep(
                        isAlarmEmitter = _uiState.value.role == OperatorRole.ALARM_EMITTER
                    )
                }

                if (remaining == 0) {
                    handleTotCutoff(limitSeconds)
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            totElapsedSeconds = elapsed,
                            totRemainingSeconds = remaining,
                            totWarningActive = inWarning
                        )
                    }
                }
            }
        }
    }

    private fun handleTotCutoff(limitSeconds: Int) {
        totTickerJob?.cancel()
        val isEmitter = _uiState.value.role == OperatorRole.ALARM_EMITTER
        toneEngine.playTotCutoffTone(isAlarmEmitter = isEmitter)
        val channelLabel = _uiState.value.selectedChannel.keyLabel
        _uiState.update {
            it.copy(
                transmissionState = if (isEmitter) TransmissionState.SILENT_CAPTURE else TransmissionState.IDLE,
                activeSpeakerName = if (isEmitter) "CAPTURA DISCRETA (MÓVIL SOS)" else null,
                activeSpeakerRole = if (isEmitter) "SIN AUDIO RX" else null,
                totElapsedSeconds = 0,
                totRemainingSeconds = limitSeconds,
                totWarningActive = false,
                totCutoffBanner = "Corte automático TOT (${limitSeconds}s) ejecutado para liberar el canal."
            )
        }
        appendLog(
            channel = channelLabel,
            actor = "CONTROL TOT",
            detail = "Corte automático al cumplir límite de ${limitSeconds}s.",
            isAlert = true
        )
    }

    private fun stopTransmissionInternal(playCloseTone: Boolean, reason: String?) {
        totTickerJob?.cancel()
        val state = _uiState.value
        val isEmitter = state.role == OperatorRole.ALARM_EMITTER
        if (playCloseTone && state.transmissionState == TransmissionState.TRANSMITTING_SELF) {
            toneEngine.playPttCloseTone(isAlarmEmitter = isEmitter)
        }
        val limit = computeMaxTotFor(state.role, state.selectedChannel)
        _uiState.update {
            it.copy(
                transmissionState = if (isEmitter) TransmissionState.SILENT_CAPTURE else TransmissionState.IDLE,
                activeSpeakerName = if (isEmitter) "CAPTURA DISCRETA (MÓVIL SOS)" else null,
                activeSpeakerRole = if (isEmitter) "SIN AUDIO RX" else null,
                totElapsedSeconds = 0,
                totRemainingSeconds = limit,
                totLimitSeconds = limit,
                totWarningActive = false,
                totCutoffBanner = reason ?: it.totCutoffBanner
            )
        }
    }

    private fun startLiveTelemetrySimulation() {
        viewModelScope.launch {
            while (isActive) {
                delay(1200L)
                _uiState.update { state ->
                    val updatedEvents = state.activeEvents.map { ev ->
                        val rad = Math.toRadians((ev.bearingDegrees - 90f).toDouble())
                        val step = 0.0045f
                        var nx = ev.mapX + (cos(rad) * step).toFloat()
                        var ny = ev.mapY + (sin(rad) * step).toFloat()
                        if (nx !in 0.14f..0.86f) nx = 0.45f
                        if (ny !in 0.16f..0.82f) ny = 0.56f
                        val dLat = cos(Math.toRadians(ev.bearingDegrees.toDouble())) * 0.00018
                        val dLon = sin(Math.toRadians(ev.bearingDegrees.toDouble())) * 0.00018
                        val dbJitter = (Random.nextFloat() * 14f) - 7f
                        ev.copy(
                            mapX = nx,
                            mapY = ny,
                            latitude = ev.latitude + dLat,
                            longitude = ev.longitude + dLon,
                            victimAudioDb = (ev.victimAudioDb + dbJitter).coerceIn(42f, 88f),
                            responders = ev.responders.map { unit ->
                                // Slowly converge responders toward the event coordinates
                                val rx = unit.mapX + (nx - unit.mapX) * 0.03f
                                val ry = unit.mapY + (ny - unit.mapY) * 0.03f
                                unit.copy(mapX = rx, mapY = ry)
                            }
                        )
                    }
                    val focused = updatedEvents.firstOrNull { it.number == state.focusedEventNumber }
                    state.copy(
                        activeEvents = updatedEvents,
                        simulatedCabinDb = focused?.victimAudioDb ?: state.simulatedCabinDb
                    )
                }
            }
        }
    }

    private fun computeMaxTotFor(role: OperatorRole, channel: RadioChannel): Int {
        return when (role) {
            OperatorRole.ADMIN -> 180
            OperatorRole.DRIVER_CONFIRMED, OperatorRole.DRIVER_UNCONFIRMED -> 30
            OperatorRole.ALARM_EMITTER -> 0
        }
    }

    private fun buildEventPool(count: Int): List<ActiveEvent> {
        val all = listOf(
            ActiveEvent(
                id = "ev-1",
                number = 1,
                code = "ANTHI-3401",
                unitCallsign = "Móvil 14 · Taxi M34",
                sectorLabel = "Sector Café Madrid",
                directionLabel = "Hacia el Norte · Rumbo 012°",
                bearingDegrees = 12f,
                speedKmh = 54,
                latitude = 7.1524,
                longitude = -73.1296,
                mapX = 0.52f,
                mapY = 0.54f,
                victimAudioDb = 66f,
                victimCameraFps = 15,
                responders = listOf(
                    ResponderUnit("r1", "Apoyo A-07", 0.9, 2, 0.34f, 0.68f, 38f, isSelf = true),
                    ResponderUnit("r2", "Apoyo B-19", 1.4, 4, 0.72f, 0.64f, 320f),
                    ResponderUnit("r3", "Unidad M-22", 2.1, 5, 0.60f, 0.30f, 195f)
                )
            ),
            ActiveEvent(
                id = "ev-2",
                number = 2,
                code = "ANTHI-3402",
                unitCallsign = "Móvil 29 · Ejecutivo",
                sectorLabel = "Autopista Norte · Km 4",
                directionLabel = "Hacia el Noroeste · Rumbo 340°",
                bearingDegrees = 340f,
                speedKmh = 68,
                latitude = 7.1618,
                longitude = -73.1342,
                mapX = 0.30f,
                mapY = 0.34f,
                victimAudioDb = 58f,
                victimCameraFps = 15,
                responders = listOf(
                    ResponderUnit("r4", "Apoyo C-03", 1.2, 3, 0.22f, 0.52f, 15f),
                    ResponderUnit("r5", "Apoyo D-11", 1.8, 4, 0.48f, 0.24f, 230f)
                )
            ),
            ActiveEvent(
                id = "ev-3",
                number = 3,
                code = "ANTHI-3403",
                unitCallsign = "Móvil 08 · Urbano",
                sectorLabel = "Parque Santander · Cra 27",
                directionLabel = "Hacia el Sur · Rumbo 175°",
                bearingDegrees = 175f,
                speedKmh = 38,
                latitude = 7.1211,
                longitude = -73.1245,
                mapX = 0.74f,
                mapY = 0.72f,
                victimAudioDb = 71f,
                victimCameraFps = 15,
                responders = listOf(
                    ResponderUnit("r6", "Apoyo E-15", 0.7, 2, 0.82f, 0.56f, 200f)
                )
            )
        )
        return all.take(count)
    }

    private fun appendLog(
        channel: String,
        actor: String,
        detail: String,
        isAlert: Boolean = false
    ) {
        val entry = RadioLogEntry(
            id = nextLogId(),
            timestamp = nowStamp(),
            channelLabel = channel,
            actor = actor,
            detail = detail,
            isAlert = isAlert
        )
        _uiState.update { state ->
            state.copy(logs = (listOf(entry) + state.logs).take(35))
        }
    }

    private fun nextLogId(): Long = logCounter++

    private fun nowStamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        totTickerJob?.cancel()
        simRxJob?.cancel()
    }
}
