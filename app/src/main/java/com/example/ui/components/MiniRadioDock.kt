package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ChannelAccessDecision
import com.example.domain.OperatorRole
import com.example.domain.PulsoUiState
import com.example.domain.RadioChannel
import com.example.domain.TransmissionState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ResponderEmerald
import com.example.ui.theme.ResponderEmeraldBright
import com.example.ui.theme.ResponderEmeraldContainer
import com.example.ui.theme.SosCrimson
import com.example.ui.theme.SosCrimsonBright
import com.example.ui.theme.SosCrimsonContainer
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberBright
import com.example.ui.theme.TacticalAmberContainer
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalDeck
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceAlt
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryCyanContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun MiniRadioDock(
    state: PulsoUiState,
    access: ChannelAccessDecision,
    onSelectChannel: (RadioChannel) -> Unit,
    onSiVoyClick: () -> Unit,
    onSelectSiVoyEvent: (Int) -> Unit,
    onDismissSiVoyDropdown: () -> Unit,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
    onClearBanner: () -> Unit,
    onOpenSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmitter = state.role == OperatorRole.ALARM_EMITTER

    Surface(
        color = TacticalDeck.copy(alpha = 0.96f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, TacticalBorder),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Optional cutoff / security notice banner
            AnimatedVisibility(visible = state.totCutoffBanner != null) {
                state.totCutoffBanner?.let { banner ->
                    Surface(
                        color = SosCrimsonContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SosCrimsonBright),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Aviso",
                                    tint = TacticalAmberBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = banner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onClearBanner,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar aviso",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Row 1: Role indicator + Enlace ANTHI M34 "Sí voy" (direct or dropdown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = when (state.role) {
                        OperatorRole.ADMIN -> TelemetryCyanContainer
                        OperatorRole.DRIVER_CONFIRMED -> ResponderEmeraldContainer
                        OperatorRole.ALARM_EMITTER -> SosCrimsonContainer
                        OperatorRole.DRIVER_UNCONFIRMED -> TacticalSurface
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        when (state.role) {
                            OperatorRole.ADMIN -> TelemetryCyan
                            OperatorRole.DRIVER_CONFIRMED -> ResponderEmerald
                            OperatorRole.ALARM_EMITTER -> SosCrimsonBright
                            OperatorRole.DRIVER_UNCONFIRMED -> TacticalBorder
                        }
                    ),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("role_quick_chip")
                        .clickable { onOpenSimulator() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isEmitter) Icons.Default.VolumeOff else Icons.Default.Shield,
                            contentDescription = "Rol actual",
                            tint = when (state.role) {
                                OperatorRole.ADMIN -> TelemetryCyan
                                OperatorRole.DRIVER_CONFIRMED -> ResponderEmeraldBright
                                OperatorRole.ALARM_EMITTER -> SosCrimsonBright
                                OperatorRole.DRIVER_UNCONFIRMED -> TacticalAmber
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = state.role.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // "Sí voy" Control (for Drivers: 1 event direct vs 2+ events dropdown)
                when (state.role) {
                    OperatorRole.ADMIN -> {
                        Surface(
                            color = TelemetryCyanContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "ACCESO LIBRE · 180s",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    OperatorRole.ALARM_EMITTER -> {
                        Surface(
                            color = SosCrimsonContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SosCrimsonBright)
                        ) {
                            Text(
                                text = "ALTAVOZ 0% · SOS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SosCrimsonBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    OperatorRole.DRIVER_UNCONFIRMED, OperatorRole.DRIVER_CONFIRMED -> {
                        Box {
                            val isConfirmed = state.role == OperatorRole.DRIVER_CONFIRMED &&
                                state.confirmedEventNumber != null
                            val hasMultipleEvents = state.activeEvents.size >= 2
                            val labelText = when {
                                isConfirmed && hasMultipleEvents ->
                                    "En ruta · Ev. ${state.confirmedEventNumber} ▾"
                                isConfirmed ->
                                    "En ruta · Ev. ${state.confirmedEventNumber} ✓"
                                hasMultipleEvents ->
                                    "Sí voy ▾"
                                else ->
                                    "Sí voy · Ev. ${state.activeEvents.firstOrNull()?.number ?: 1}"
                            }

                            Surface(
                                color = if (isConfirmed) ResponderEmeraldContainer else ResponderEmerald,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isConfirmed) ResponderEmeraldBright else ResponderEmerald
                                ),
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .testTag("si_voy_button")
                                    .clickable { onSiVoyClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.DirectionsCar,
                                        contentDescription = "Confirmar Sí voy",
                                        tint = if (isConfirmed) ResponderEmeraldBright else ObsidianBg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                        color = if (isConfirmed) ResponderEmeraldBright else ObsidianBg
                                    )
                                    if (hasMultipleEvents) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Elegir evento",
                                            tint = if (isConfirmed) ResponderEmeraldBright else ObsidianBg,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = state.isSiVoyDropdownExpanded,
                                onDismissRequest = onDismissSiVoyDropdown,
                                containerColor = TacticalSurface
                            ) {
                                state.activeEvents.forEach { ev ->
                                    val selectedHere = state.confirmedEventNumber == ev.number
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Evento ${ev.number} · ${ev.sectorLabel}",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                                    color = if (selectedHere) ResponderEmeraldBright else TextPrimary
                                                )
                                                Text(
                                                    text = "${ev.unitCallsign} (${ev.directionLabel})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        },
                                        onClick = { onSelectSiVoyEvent(ev.number) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (selectedHere) Icons.Default.CheckCircle else Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = if (selectedHere) ResponderEmeraldBright else TacticalAmber
                                            )
                                        },
                                        modifier = Modifier.testTag("si_voy_option_${ev.number}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Row 2: Live "Al Aire" indicator + TOT Timer + Victim Telemetry when on VÍCTIMA
            OnAirAndTotStrip(
                state = state,
                access = access
            )

            // Row 3: Channel Keypad (1, 2, 3..., VÍCTIMA, ADMIN)
            ChannelKeypadRow(
                state = state,
                onSelectChannel = onSelectChannel
            )

            // Row 4: Ergonomic One-Handed PTT Trigger Button
            PttActionButton(
                state = state,
                access = access,
                onStartPtt = onStartPtt,
                onStopPtt = onStopPtt
            )
        }
    }
}

@Composable
private fun OnAirAndTotStrip(
    state: PulsoUiState,
    access: ChannelAccessDecision
) {
    val isTx = state.transmissionState == TransmissionState.TRANSMITTING_SELF
    val isRx = state.transmissionState == TransmissionState.RECEIVING_REMOTE
    val isSilentCapture = state.transmissionState == TransmissionState.SILENT_CAPTURE
    val isVictimChannel = state.selectedChannel is RadioChannel.VictimChannel

    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_phase"
    )

    val stripBg by animateColorAsState(
        targetValue = when {
            state.totWarningActive -> SosCrimsonContainer
            isTx -> TacticalAmberContainer
            isRx -> TelemetryCyanContainer
            isSilentCapture -> SosCrimsonContainer.copy(alpha = 0.7f)
            else -> TacticalSurface
        },
        label = "strip_bg"
    )

    Surface(
        color = stripBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                state.totWarningActive -> SosCrimsonBright
                isTx -> TacticalAmber
                isRx -> TelemetryCyan
                isSilentCapture -> SosCrimsonBright
                else -> TacticalBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("on_air_strip")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            isSilentCapture -> Icons.Default.VolumeOff
                            isTx || isRx -> Icons.Default.GraphicEq
                            else -> Icons.Default.Radio
                        },
                        contentDescription = "Estado de canal",
                        tint = when {
                            state.totWarningActive || isSilentCapture -> SosCrimsonBright
                            isTx -> TacticalAmberBright
                            isRx -> TelemetryCyan
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        val speakerText = when {
                            isSilentCapture -> "CAPTURA SILENCIOSA · ALTAVOZ 0%"
                            isTx -> "AL AIRE: ${state.activeSpeakerName ?: "TÚ"}"
                            isRx -> "RX EN VIVO: ${state.activeSpeakerName ?: "CENTRAL"}"
                            isVictimChannel -> "MONITOREO VÍCTIMA · EVENTO ${state.focusedEventNumber}"
                            else -> "${state.selectedChannel.fullTitle} · CANAL LIBRE"
                        }
                        Text(
                            text = speakerText,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subText = when {
                            isSilentCapture -> "Audio cabina + cámara + GPS hacia receptores autorizados"
                            isVictimChannel && access.canListen ->
                                "Cabina: ${state.simulatedCabinDb.toInt()} dB · Cam 15fps · GPS OK"
                            isVictimChannel ->
                                access.lockReason ?: "Monitoreo restringido"
                            !access.canTransmit ->
                                access.lockReason ?: "Solo escucha"
                            else ->
                                "Canal listo · Máx ${access.maxTotSeconds}s por pulsación"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTx || isRx) TextPrimary.copy(alpha = 0.85f) else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Precision TOT Countdown Badge
                if (state.role != OperatorRole.ALARM_EMITTER) {
                    val secondsShown = if (isTx) state.totRemainingSeconds else access.maxTotSeconds
                    val mm = secondsShown / 60
                    val ss = secondsShown % 60
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                state.totWarningActive -> SosCrimsonBright
                                isTx -> TacticalAmber
                                else -> TacticalBorder
                            }
                        ),
                        modifier = Modifier.testTag("tot_timer_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TOT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.totWarningActive) SosCrimsonBright else TextMuted
                            )
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", mm, ss),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = when {
                                    state.totWarningActive -> SosCrimsonBright
                                    isTx -> TacticalAmberBright
                                    else -> TextPrimary
                                }
                            )
                        }
                    }
                } else {
                    Surface(
                        color = SosCrimsonContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SosCrimsonBright)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = SosCrimsonBright,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${state.simulatedCabinDb.toInt()} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // TOT Progress Bar during transmission or active RX wave
            if (isTx && state.totLimitSeconds > 0) {
                val progress = (state.totRemainingSeconds.toFloat() / state.totLimitSeconds.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (state.totWarningActive) SosCrimsonBright else TacticalAmber,
                    trackColor = ObsidianBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            } else if (isRx) {
                LinearProgressIndicator(
                    progress = { wavePhase },
                    color = TelemetryCyan,
                    trackColor = ObsidianBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun ChannelKeypadRow(
    state: PulsoUiState,
    onSelectChannel: (RadioChannel) -> Unit
) {
    val channels = buildList {
        state.activeEvents.forEach { ev ->
            add(RadioChannel.EventChannel(ev.number))
        }
        add(RadioChannel.VictimChannel)
        add(RadioChannel.AdminChannel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_keypad"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        channels.forEach { channel ->
            val isSelected = state.selectedChannel == channel
            val isConfirmedHere = channel is RadioChannel.EventChannel &&
                state.role == OperatorRole.DRIVER_CONFIRMED &&
                state.confirmedEventNumber == channel.eventNumber

            val isLockedForRole = when (state.role) {
                OperatorRole.ADMIN -> false
                OperatorRole.ALARM_EMITTER -> channel != RadioChannel.VictimChannel
                OperatorRole.DRIVER_UNCONFIRMED -> true
                OperatorRole.DRIVER_CONFIRMED -> when (channel) {
                    is RadioChannel.EventChannel -> state.confirmedEventNumber != channel.eventNumber
                    RadioChannel.VictimChannel -> false // listen-only
                    RadioChannel.AdminChannel -> true
                }
            }

            val keyColor = when {
                isSelected && channel is RadioChannel.VictimChannel -> SosCrimsonContainer
                isSelected && channel is RadioChannel.AdminChannel -> TelemetryCyanContainer
                isSelected -> TacticalAmberContainer
                else -> TacticalSurface
            }

            val borderColor = when {
                isSelected && channel is RadioChannel.VictimChannel -> SosCrimsonBright
                isSelected && channel is RadioChannel.AdminChannel -> TelemetryCyan
                isSelected -> TacticalAmber
                isConfirmedHere -> ResponderEmerald
                else -> TacticalBorder
            }

            Surface(
                color = keyColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                modifier = Modifier
                    .weight(if (channel is RadioChannel.EventChannel) 0.9f else 1.35f)
                    .height(48.dp)
                    .testTag("channel_key_${channel.keyLabel}")
                    .clickable { onSelectChannel(channel) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isConfirmedHere) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ResponderEmeraldBright)
                            )
                        } else if (isLockedForRole) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                tint = TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = channel.keyLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = if (channel is RadioChannel.EventChannel) 15.sp else 11.sp
                            ),
                            color = when {
                                isSelected -> TextPrimary
                                isLockedForRole -> TextMuted
                                else -> TextSecondary
                            },
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PttActionButton(
    state: PulsoUiState,
    access: ChannelAccessDecision,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit
) {
    val isEmitter = state.role == OperatorRole.ALARM_EMITTER
    val isTx = state.transmissionState == TransmissionState.TRANSMITTING_SELF
    val canTx = access.canTransmit

    val bgColor by animateColorAsState(
        targetValue = when {
            isEmitter -> SosCrimsonContainer
            isTx && state.totWarningActive -> SosCrimson
            isTx -> TacticalAmber
            canTx -> TacticalSurfaceAlt
            else -> TacticalSurface
        },
        label = "ptt_bg"
    )

    val borderColor = when {
        isEmitter -> SosCrimsonBright
        isTx && state.totWarningActive -> SosCrimsonBright
        isTx -> TacticalAmberBright
        canTx -> ResponderEmerald
        else -> TacticalBorder
    }

    val contentColor = when {
        isTx -> ObsidianBg
        isEmitter -> TextPrimary
        canTx -> TextPrimary
        else -> TextMuted
    }

    val label = when {
        isEmitter -> "MODO EMISOR EN ALARMA · SILENCIO TOTAL (CAPTURA ACTIVA)"
        isTx -> "AL AIRE (${state.totRemainingSeconds}s) · SUELTA PARA CORTAR"
        canTx -> "OPRIME Y MANTÉN PARA HABLAR (${state.selectedChannel.keyLabel})"
        else -> (access.lockReason ?: "TRANSMISIÓN BLOQUEADA").uppercase(Locale.getDefault())
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isTx || canTx) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("ptt_button")
            .pointerInput(canTx, isEmitter) {
                if (!canTx || isEmitter) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onStartPtt()
                    waitForUpOrCancellation()
                    onStopPtt()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when {
                    isEmitter -> Icons.Default.VolumeOff
                    isTx -> Icons.Default.Mic
                    canTx -> Icons.Default.Mic
                    else -> Icons.Default.MicOff
                },
                contentDescription = "Botón PTT",
                tint = when {
                    isTx -> ObsidianBg
                    isEmitter -> SosCrimsonBright
                    canTx -> ResponderEmeraldBright
                    else -> TextMuted
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
