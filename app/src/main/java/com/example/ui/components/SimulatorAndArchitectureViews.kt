package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.OperatorRole
import com.example.domain.PulsoUiState
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
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryCyanContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SimulatorScreen(
    state: PulsoUiState,
    onSelectRole: (OperatorRole) -> Unit,
    onSelectEventCount: (Int) -> Unit,
    onTestTot30s: () -> Unit,
    onTestTot180s: () -> Unit,
    onTestAlarmEmitterSilence: () -> Unit,
    onSimulateRemoteRx: () -> Unit,
    onReturnToRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Simulador de Escenarios ANTHI M34",
                subtitle = "Prueba eventos simultáneos, roles operativos, silencio de emisor y corte TOT."
            )
        }

        // Event count simulator (1, 2, 3 active events)
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Eventos activos simultáneos (botón 'Sí voy' directo vs desplegable)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val selected = state.activeEvents.size == count
                            Surface(
                                color = if (selected) TacticalAmberContainer else TacticalSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) TacticalAmber else TacticalBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("sim_events_$count")
                                    .clickable { onSelectEventCount(count) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$count ${if (count == 1) "Evento" else "Eventos"}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                        color = if (selected) TacticalAmberBright else TextPrimary
                                    )
                                    Text(
                                        text = if (count == 1) "Sí voy directo" else "Desplegable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Role switcher
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Rol del dispositivo actual",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    OperatorRole.entries.forEach { role ->
                        val selected = state.role == role
                        val accent = when (role) {
                            OperatorRole.ADMIN -> TelemetryCyan
                            OperatorRole.DRIVER_CONFIRMED -> ResponderEmeraldBright
                            OperatorRole.DRIVER_UNCONFIRMED -> TacticalAmber
                            OperatorRole.ALARM_EMITTER -> SosCrimsonBright
                        }
                        Surface(
                            color = if (selected) accent.copy(alpha = 0.14f) else TacticalSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) accent else TacticalBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("sim_role_${role.name}")
                                .clickable { onSelectRole(role) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = role.displayName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                        color = if (selected) accent else TextPrimary
                                    )
                                    Text(
                                        text = role.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick TOT & RX Tests
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pruebas rápidas de corte TOT y silencio de emisor",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onTestTot30s()
                                onReturnToRadio()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalAmber,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sim_test_tot_30")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TOT 30s (Cond.)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onTestTot180s()
                                onReturnToRadio()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TelemetryCyan,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sim_test_tot_180")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TOT 180s (Admin)", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onTestAlarmEmitterSilence()
                            onReturnToRadio()
                        },
                        border = BorderStroke(1.dp, SosCrimsonBright),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sim_test_emitter_silence")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SosCrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Probar Silencio Emisor en Alarma (0% audio RX)",
                            color = SosCrimsonBright,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onSimulateRemoteRx()
                            onReturnToRadio()
                        },
                        border = BorderStroke(1.dp, TelemetryCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sim_test_rx")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simular RX entrante en el rol actual",
                            color = TelemetryCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Live Tactical Log
        item {
            Text(
                text = "Bitácora Táctica en Vivo",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
        }

        items(state.logs, key = { it.id }) { entry ->
            Surface(
                color = if (entry.isAlert) SosCrimsonContainer.copy(alpha = 0.55f) else TacticalDeck,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (entry.isAlert) SosCrimson else TacticalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = TelemetryCyan
                    )
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.channelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalAmberBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.actor,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun ApkArchitectureScreen(
    state: PulsoUiState,
    onRequestPermissions: () -> Unit,
    onToggleForegroundService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val permissionsList = buildList {
        add(Manifest.permission.RECORD_AUDIO to "RECORD_AUDIO (PTT + Audio Cabina)")
        add(Manifest.permission.CAMERA to "CAMERA (Captura discreta Emisor SOS)")
        add(Manifest.permission.ACCESS_FINE_LOCATION to "ACCESS_FINE_LOCATION (GPS + Rumbo)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS to "POST_NOTIFICATIONS (Servicio táctico)")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Arquitectura APK Android & Firebase RTDB",
                subtitle = "Permisos de compilación Android 14+, servicio en primer plano, osmdroid y esquema en tiempo real."
            )
        }

        // Android Manifest & Runtime Permissions Card
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = ResponderEmeraldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Permisos Android listos para APK",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }

                    permissionsList.forEach { (perm, label) ->
                        val granted = state.permissionSnapshot[perm] == true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = if (granted) ResponderEmeraldContainer else TacticalSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (granted) ResponderEmerald else TacticalBorder
                                )
                            ) {
                                Text(
                                    text = if (granted) "CONCEDIDO" else "PENDIENTE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (granted) ResponderEmeraldBright else TacticalAmber,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FOREGROUND_SERVICE (microphone|location|camera)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = TelemetryCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = TelemetryCyanContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TelemetryCyan)
                        ) {
                            Text(
                                text = "MANIFEST OK",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (state.deviceGpsFix != null || state.micHardwareLiveDb != null) {
                        Surface(
                            color = ObsidianBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ResponderEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sensores reales activos · Mic: ${state.micHardwareLiveDb?.toInt() ?: "--"} dB · GPS: ${
                                    state.deviceGpsFix?.let { String.format(java.util.Locale.US, "%.4f, %.4f", it.first, it.second) } ?: "--"
                                }",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ResponderEmeraldBright,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ResponderEmerald,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("request_permissions_btn")
                        ) {
                            Text("Solicitar Permisos", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onToggleForegroundService,
                            border = BorderStroke(
                                1.dp,
                                if (state.isForegroundServiceActive) SosCrimsonBright else TelemetryCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("toggle_fgs_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (state.isForegroundServiceActive) SosCrimsonBright else TelemetryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isForegroundServiceActive) "Detener FGS" else "Probar FGS",
                                color = if (state.isForegroundServiceActive) SosCrimsonBright else TelemetryCyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Firebase Realtime Database Structure
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = TacticalAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Firebase Realtime Database",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                        Surface(
                            color = TacticalSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TacticalBorder),
                            modifier = Modifier
                                .testTag("copy_rtdb_btn")
                                .clickable {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(FIREBASE_RTDB_SCHEMA))
                                }
                        ) {
                            Text(
                                text = "Copiar JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalAmberBright,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = FIREBASE_RTDB_SCHEMA,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                lineHeight = 16.sp
                            ),
                            color = TelemetryCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "Reglas RTDB (Control de acceso + Silencio Emisor)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = FIREBASE_RTDB_RULES,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                lineHeight = 15.sp
                            ),
                            color = ResponderEmeraldBright,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Native APK Integration Bridges (osmdroid + WebRTC/AudioTrack + ForegroundService)
        item {
            Surface(
                color = TacticalDeck,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Puente Nativo Android (ANTHI M34 <-> Pulso M34)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "• Mapa osmdroid / OSM: Marcadores dinámicos de unidad en alerta, cono de rumbo GPS y apoyos confirmados.\n" +
                            "• Radio PTT: Control de turno exclusivo por canal (ptt_locks) + AudioTrack nativo con bloqueo 0% en Emisor SOS.\n" +
                            "• Canal VÍCTIMA: Flujo unidireccional de audio cabina, frames CameraX y telemetría separado de canales 1..N.\n" +
                            "• Regla TOT: Corte duro en servidor y cliente a los 30s (Conductores) y 180s (Administradores).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

private const val FIREBASE_RTDB_SCHEMA = """{
  "anthi_m34": {
    "active_events": {
      "event_1": {
        "number": 1,
        "code": "ANTHI-3401",
        "emitter_uid": "unit_movil_14",
        "sector": "Sector Café Madrid",
        "heading_deg": 12.0,
        "speed_kmh": 54,
        "lat": 7.1524,
        "lng": -73.1296,
        "status": "ACTIVE_ALARM"
      }
    }
  },
  "pulso_m34": {
    "responders": {
      "uid_driver_07": {
        "role": "DRIVER_CONFIRMED",
        "si_voy_event": 1,
        "authorized_channels": ["event_1"],
        "tot_limit_sec": 30
      }
    },
    "channels": {
      "event_1": {
        "ptt_lock": {
          "holder_uid": "uid_driver_07",
          "started_at": 1732638800,
          "expires_at": 1732638830
        }
      },
      "victim_event_1": {
        "mode": "SILENT_CAPTURE_UPSTREAM",
        "speaker_output_enabled": false,
        "audio_stream_url": "rtc://pulso/victim/ev1",
        "camera_active": true
      },
      "admin": {
        "allowed_roles": ["ADMIN"],
        "tot_limit_sec": 180
      }
    }
  }
}"""

private const val FIREBASE_RTDB_RULES = """{
  "rules": {
    "pulso_m34": {
      "channels": {
        "admin": {
          ".read": "auth.token.role === 'ADMIN'",
          ".write": "auth.token.role === 'ADMIN'"
        },
        "${'$'}eventId": {
          ".read": "auth != null && auth.token.role !== 'ALARM_EMITTER'",
          "ptt_lock": {
            ".write": "auth.token.role === 'ADMIN' || (auth.token.role === 'DRIVER_CONFIRMED' && root.child('pulso_m34/responders/' + auth.uid + '/si_voy_event').val() == ${'$'}eventId)"
          }
        }
      }
    }
  }
}"""
