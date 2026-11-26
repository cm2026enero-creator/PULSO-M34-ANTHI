package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.AppTab
import com.example.domain.OperatorRole
import com.example.service.PulsoForegroundService
import com.example.ui.PulsoViewModel
import com.example.ui.components.ApkArchitectureScreen
import com.example.ui.components.MiniRadioDock
import com.example.ui.components.SimulatorScreen
import com.example.ui.components.TacticalMapPanel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulsoM34Theme
import com.example.ui.theme.ResponderEmeraldBright
import com.example.ui.theme.SosCrimsonBright
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberContainer
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalDeck
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulsoM34Theme {
                PulsoM34App()
            }
        }
    }
}

@Composable
fun PulsoM34App(
    viewModel: PulsoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun refreshPermissions() {
        val snapshot = requiredPermissions.associateWith { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.updatePermissionSnapshot(snapshot)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    LaunchedEffect(state.role, state.transmissionState, state.permissionSnapshot) {
        if (state.role == OperatorRole.ALARM_EMITTER ||
            state.transmissionState == com.example.domain.TransmissionState.TRANSMITTING_SELF
        ) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val micDb = com.example.hardware.TacticalHardwareBridge.sampleMicrophoneDb(context)
                val gps = com.example.hardware.TacticalHardwareBridge.readLastKnownGps(context)
                viewModel.updateHardwareTelemetry(micDb, gps)
            }
        }
    }

    // BackHandler on secondary tabs returns smoothly to Mapa & Radio
    BackHandler(enabled = state.currentTab != AppTab.TACTICAL) {
        viewModel.selectTab(AppTab.TACTICAL)
    }

    val accessDecision = viewModel.evaluateChannelAccess(
        role = state.role,
        channel = state.selectedChannel,
        confirmedEventNumber = state.confirmedEventNumber
    )

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TacticalTopBar(
                currentTab = state.currentTab,
                activeEventsCount = state.activeEvents.size,
                isEmitterSilent = state.role == OperatorRole.ALARM_EMITTER,
                onSelectTab = viewModel::selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when (state.currentTab) {
                AppTab.TACTICAL -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpandedWidth = maxWidth >= 600.dp
                        if (isExpandedWidth) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TacticalMapPanel(
                                    events = state.activeEvents,
                                    focusedEventNumber = state.focusedEventNumber,
                                    role = state.role,
                                    confirmedEventNumber = state.confirmedEventNumber,
                                    onFocusEvent = viewModel::focusEvent,
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .fillMaxHeight()
                                )
                                MiniRadioDock(
                                    state = state,
                                    access = accessDecision,
                                    onSelectChannel = viewModel::selectChannel,
                                    onSiVoyClick = viewModel::onSiVoyButtonClicked,
                                    onSelectSiVoyEvent = viewModel::confirmSiVoyForEvent,
                                    onDismissSiVoyDropdown = viewModel::dismissSiVoyDropdown,
                                    onStartPtt = viewModel::startPttTransmission,
                                    onStopPtt = viewModel::stopPttTransmission,
                                    onClearBanner = viewModel::clearBanner,
                                    onOpenSimulator = { viewModel.selectTab(AppTab.SIMULATOR) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .align(Alignment.Bottom)
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TacticalMapPanel(
                                    events = state.activeEvents,
                                    focusedEventNumber = state.focusedEventNumber,
                                    role = state.role,
                                    confirmedEventNumber = state.confirmedEventNumber,
                                    onFocusEvent = viewModel::focusEvent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                                MiniRadioDock(
                                    state = state,
                                    access = accessDecision,
                                    onSelectChannel = viewModel::selectChannel,
                                    onSiVoyClick = viewModel::onSiVoyButtonClicked,
                                    onSelectSiVoyEvent = viewModel::confirmSiVoyForEvent,
                                    onDismissSiVoyDropdown = viewModel::dismissSiVoyDropdown,
                                    onStartPtt = viewModel::startPttTransmission,
                                    onStopPtt = viewModel::stopPttTransmission,
                                    onClearBanner = viewModel::clearBanner,
                                    onOpenSimulator = { viewModel.selectTab(AppTab.SIMULATOR) }
                                )
                            }
                        }
                    }
                }

                AppTab.SIMULATOR -> {
                    SimulatorScreen(
                        state = state,
                        onSelectRole = viewModel::setRole,
                        onSelectEventCount = viewModel::setSimulatedEventCount,
                        onTestTot30s = viewModel::testTotCutoff30s,
                        onTestTot180s = viewModel::testTotCutoff180s,
                        onTestAlarmEmitterSilence = viewModel::testAlarmEmitterSilence,
                        onSimulateRemoteRx = viewModel::simulateIncomingRadioTransmission,
                        onReturnToRadio = { viewModel.selectTab(AppTab.TACTICAL) }
                    )
                }

                AppTab.ARCHITECTURE -> {
                    ApkArchitectureScreen(
                        state = state,
                        onRequestPermissions = {
                            permissionLauncher.launch(requiredPermissions)
                        },
                        onToggleForegroundService = {
                            val serviceIntent = Intent(context, PulsoForegroundService::class.java).apply {
                                putExtra(
                                    PulsoForegroundService.EXTRA_MODE_LABEL,
                                    "Pulso M34 · ${state.role.displayName}"
                                )
                            }
                            if (state.isForegroundServiceActive) {
                                context.stopService(serviceIntent)
                                viewModel.setForegroundServiceState(false)
                            } else {
                                ContextCompat.startForegroundService(context, serviceIntent)
                                viewModel.setForegroundServiceState(true)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalTopBar(
    currentTab: AppTab,
    activeEventsCount: Int,
    isEmitterSilent: Boolean,
    onSelectTab: (AppTab) -> Unit
) {
    Surface(
        color = TacticalDeck,
        border = BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isEmitterSilent) SosCrimsonBright else ResponderEmeraldBright)
                    )
                    Text(
                        text = "PULSO M34",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextPrimary
                    )
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TacticalBorder)
                    ) {
                        Text(
                            text = "ANTHI · $activeEventsCount EV",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isEmitterSilent) "MODO SILENCIOSO" else "PTT EN LÍNEA",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEmitterSilent) SosCrimsonBright else TelemetryCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TopNavTabPill(
                    label = "Mapa y Radio",
                    icon = Icons.Default.Map,
                    selected = currentTab == AppTab.TACTICAL,
                    onClick = { onSelectTab(AppTab.TACTICAL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_tactical")
                )
                TopNavTabPill(
                    label = "Simulador",
                    icon = Icons.Default.Tune,
                    selected = currentTab == AppTab.SIMULATOR,
                    onClick = { onSelectTab(AppTab.SIMULATOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_simulator")
                )
                TopNavTabPill(
                    label = "APK / Firebase",
                    icon = Icons.Default.Code,
                    selected = currentTab == AppTab.ARCHITECTURE,
                    onClick = { onSelectTab(AppTab.ARCHITECTURE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_architecture")
                )
            }
        }
    }
}

@Composable
private fun TopNavTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) TacticalAmberContainer else TacticalSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) TacticalAmber else TacticalBorder),
        modifier = modifier
            .heightIn(min = 42.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) TacticalAmber else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
