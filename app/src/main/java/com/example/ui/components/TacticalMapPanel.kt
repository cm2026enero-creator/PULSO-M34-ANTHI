package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ActiveEvent
import com.example.domain.OperatorRole
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ResponderEmerald
import com.example.ui.theme.ResponderEmeraldBright
import com.example.ui.theme.SosCrimson
import com.example.ui.theme.SosCrimsonBright
import com.example.ui.theme.SosCrimsonContainer
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalDeck
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun TacticalMapPanel(
    events: List<ActiveEvent>,
    focusedEventNumber: Int,
    role: OperatorRole,
    confirmedEventNumber: Int?,
    onFocusEvent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusedEvent = events.firstOrNull { it.number == focusedEventNumber } ?: events.firstOrNull()
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_fraction"
    )

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
    ) {
        // Interactive Tactical OpenStreetMap / osmdroid-structured Vector Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tactical_map_canvas")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset = Offset(
                            x = (panOffset.x + dragAmount.x).coerceIn(-280f, 280f),
                            y = (panOffset.y + dragAmount.y).coerceIn(-240f, 240f)
                        )
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            fun toScreen(nx: Float, ny: Float): Offset {
                val baseX = nx * w
                val baseY = ny * h
                return Offset(
                    x = center.x + (baseX - center.x) * zoom + panOffset.x,
                    y = center.y + (baseY - center.y) * zoom + panOffset.y
                )
            }

            // 1. Tactical Dark Map Base
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0E1B2E), Color(0xFF070C15)),
                    center = center,
                    radius = hypot(w, h) * 0.65f
                )
            )

            // 2. Coordinate Grid
            val gridStep = 56f * zoom.coerceIn(0.8f, 1.4f)
            val startX = (panOffset.x % gridStep)
            val startY = (panOffset.y % gridStep)
            var gx = startX
            while (gx < w) {
                drawLine(
                    color = Color(0xFF142238),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = startY
            while (gy < h) {
                drawLine(
                    color = Color(0xFF142238),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            // 3. OpenStreetMap-style Urban Sectors & Arteries (Bucaramanga Norte / Café Madrid)
            drawTacticalRoadNetwork(
                toScreen = ::toScreen,
                textMeasurer = textMeasurer,
                zoom = zoom
            )

            // 4. Radar Range Rings around focused emergency event
            focusedEvent?.let { ev ->
                val evPos = toScreen(ev.mapX, ev.mapY)
                listOf(64f, 128f, 196f).forEach { baseR ->
                    drawCircle(
                        color = SosCrimson.copy(alpha = 0.14f),
                        radius = baseR * zoom,
                        center = evPos,
                        style = Stroke(
                            width = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    )
                }
            }

            // 5. Render each active event, vector heading, and confirmed responders
            events.forEach { ev ->
                val isFocused = ev.number == focusedEvent?.number
                val evPos = toScreen(ev.mapX, ev.mapY)

                // Responder intercept lines + units
                ev.responders.forEach { unit ->
                    val uPos = toScreen(unit.mapX, unit.mapY)
                    drawLine(
                        color = if (unit.isSelf) ResponderEmeraldBright.copy(alpha = 0.65f)
                        else ResponderEmerald.copy(alpha = 0.40f),
                        start = uPos,
                        end = evPos,
                        strokeWidth = if (unit.isSelf) 2.5f else 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                    drawResponderMarker(
                        pos = uPos,
                        callsign = unit.callsign,
                        isSelf = unit.isSelf,
                        isConfirmedHere = confirmedEventNumber == ev.number && unit.isSelf,
                        textMeasurer = textMeasurer
                    )
                }

                // Heading cone + directional arrow for emergency unit
                drawBearingVector(
                    origin = evPos,
                    bearingDeg = ev.bearingDegrees,
                    isFocused = isFocused,
                    zoom = zoom
                )

                // Pulsing SOS beacon
                if (isFocused) {
                    drawCircle(
                        color = SosCrimsonBright.copy(alpha = (1f - pulseFraction) * 0.45f),
                        radius = (18f + 38f * pulseFraction) * zoom,
                        center = evPos
                    )
                }
                drawCircle(
                    color = if (isFocused) SosCrimson else SosCrimson.copy(alpha = 0.78f),
                    radius = if (isFocused) 13f else 10f,
                    center = evPos
                )
                drawCircle(
                    color = TextPrimary,
                    radius = if (isFocused) 5f else 3.5f,
                    center = evPos
                )

                // Event badge label
                val labelText = "EV.${ev.number} · ${ev.speedKmh} km/h"
                drawMapTag(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    anchor = Offset(evPos.x + 16f, evPos.y - 22f),
                    bgColor = if (isFocused) SosCrimsonContainer else TacticalDeck.copy(alpha = 0.9f),
                    borderColor = if (isFocused) SosCrimsonBright else TacticalBorder,
                    textColor = TextPrimary
                )
            }
        }

        // Top-left Telemetry & Event Header HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            focusedEvent?.let { ev ->
                Surface(
                    color = TacticalDeck.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TacticalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SosCrimsonBright)
                            )
                            Text(
                                text = "${ev.code} · ${ev.unitCallsign}",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Rumbo",
                                tint = TacticalAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${ev.sectorLabel} · ${ev.directionLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TacticalAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.4f, %.4f",
                                    ev.latitude,
                                    ev.longitude
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = TelemetryCyan
                            )
                            Text(
                                text = "${ev.responders.size} apoyos",
                                style = MaterialTheme.typography.labelSmall,
                                color = ResponderEmeraldBright
                            )
                        }
                    }
                }
            }

            // Multi-event switcher chips on map
            if (events.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    events.forEach { ev ->
                        val selected = ev.number == focusedEvent?.number
                        Surface(
                            color = if (selected) SosCrimsonContainer.copy(alpha = 0.95f)
                            else TacticalDeck.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) SosCrimsonBright else TacticalBorder
                            ),
                            modifier = Modifier
                                .testTag("map_event_chip_${ev.number}")
                                .clickable { onFocusEvent(ev.number) }
                        ) {
                            Text(
                                text = "Ev.${ev.number}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) TextPrimary else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top-right Map Zoom & Recenter Controls + osmdroid/OSM Bridge Badge
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = TacticalDeck.copy(alpha = 0.90f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "OSM osmdroid",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "OSM · osmdroid",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                color = TacticalDeck.copy(alpha = 0.90f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TacticalBorder)
            ) {
                Column {
                    IconButton(
                        onClick = { zoom = (zoom + 0.2f).coerceAtMost(2.0f) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("map_zoom_in")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Acercar mapa",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.75f) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("map_zoom_out")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Alejar mapa",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            zoom = 1.0f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("map_recenter")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Centrar mapa",
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Bottom-left Silent Alarm Emitter Security Pill
        AnimatedVisibility(
            visible = role == OperatorRole.ALARM_EMITTER,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Surface(
                color = SosCrimsonContainer.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SosCrimsonBright),
                modifier = Modifier.testTag("alarm_emitter_silent_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = "Altavoz silenciado",
                        tint = SosCrimsonBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Captura discreta",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SILENCIO TOTAL · CAPTURA CAM/MIC/GPS ACTIVA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }

        // Bottom-right North Compass Pill
        Surface(
            color = TacticalDeck.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Norte magnético",
                    tint = TacticalAmber,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "N ↑ · ${String.format(Locale.US, "%.1fx", zoom)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun DrawScope.drawTacticalRoadNetwork(
    toScreen: (Float, Float) -> Offset,
    textMeasurer: TextMeasurer,
    zoom: Float
) {
    // Waterway / green corridor
    val riverPath = Path().apply {
        val p0 = toScreen(0.05f, 0.25f)
        val p1 = toScreen(0.32f, 0.42f)
        val p2 = toScreen(0.62f, 0.58f)
        val p3 = toScreen(0.95f, 0.88f)
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
    }
    drawPath(
        path = riverPath,
        color = Color(0xFF0D273A),
        style = Stroke(width = 16f * zoom, cap = StrokeCap.Round)
    )

    // Primary arterial highways (Autopista Norte, Carrera 27, Av Quebradaseca, Café Madrid)
    val mainRoads = listOf(
        Pair(0.50f to 0.04f, 0.44f to 0.96f), // Autopista Norte / Cra 15
        Pair(0.15f to 0.12f, 0.85f to 0.86f), // Diagonal Café Madrid
        Pair(0.08f to 0.54f, 0.94f to 0.48f), // Av. Quebradaseca
        Pair(0.72f to 0.08f, 0.68f to 0.94f), // Carrera 27
        Pair(0.12f to 0.30f, 0.90f to 0.26f)  // Anillo Norte
    )
    mainRoads.forEach { (a, b) ->
        drawLine(
            color = Color(0xFF23354F),
            start = toScreen(a.first, a.second),
            end = toScreen(b.first, b.second),
            strokeWidth = 5.5f * zoom,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF31496B),
            start = toScreen(a.first, a.second),
            end = toScreen(b.first, b.second),
            strokeWidth = 1.4f * zoom,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )
    }

    // Secondary street blocks
    val secondaryRoads = listOf(
        Pair(0.24f to 0.10f, 0.20f to 0.90f),
        Pair(0.36f to 0.08f, 0.35f to 0.92f),
        Pair(0.10f to 0.70f, 0.92f to 0.66f),
        Pair(0.10f to 0.84f, 0.92f to 0.82f)
    )
    secondaryRoads.forEach { (a, b) ->
        drawLine(
            color = Color(0xFF19283E),
            start = toScreen(a.first, a.second),
            end = toScreen(b.first, b.second),
            strokeWidth = 2.5f * zoom
        )
    }

    // Sector labels
    val sectors = listOf(
        Triple("CAFÉ MADRID", 0.56f, 0.20f),
        Triple("AUTOPISTA NORTE", 0.16f, 0.42f),
        Triple("CRA 27 · CENTRO", 0.64f, 0.80f)
    )
    sectors.forEach { (label, sx, sy) ->
        val pos = toScreen(sx, sy)
        val measured = textMeasurer.measure(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4B678E)
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(pos.x - measured.size.width / 2f, pos.y)
        )
    }
}

private fun DrawScope.drawBearingVector(
    origin: Offset,
    bearingDeg: Float,
    isFocused: Boolean,
    zoom: Float
) {
    val length = (if (isFocused) 78f else 54f) * zoom
    val rad = Math.toRadians((bearingDeg - 90f).toDouble())
    val tip = Offset(
        x = origin.x + (cos(rad) * length).toFloat(),
        y = origin.y + (sin(rad) * length).toFloat()
    )

    // Directional cone
    rotate(degrees = bearingDeg, pivot = origin) {
        val conePath = Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(origin.x - 18f * zoom, origin.y - length * 0.72f)
            lineTo(origin.x + 18f * zoom, origin.y - length * 0.72f)
            close()
        }
        drawPath(
            path = conePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    TacticalAmber.copy(alpha = 0.0f),
                    TacticalAmber.copy(alpha = if (isFocused) 0.28f else 0.16f)
                ),
                startY = origin.y - length * 0.72f,
                endY = origin.y
            )
        )
    }

    // Main vector line
    drawLine(
        color = TacticalAmber,
        start = origin,
        end = tip,
        strokeWidth = if (isFocused) 3.5f else 2.2f,
        cap = StrokeCap.Round
    )

    // Arrowhead
    val headLen = 11f
    val angle1 = rad + Math.toRadians(148.0)
    val angle2 = rad - Math.toRadians(148.0)
    drawLine(
        color = TacticalAmber,
        start = tip,
        end = Offset(
            tip.x + (cos(angle1) * headLen).toFloat(),
            tip.y + (sin(angle1) * headLen).toFloat()
        ),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = TacticalAmber,
        start = tip,
        end = Offset(
            tip.x + (cos(angle2) * headLen).toFloat(),
            tip.y + (sin(angle2) * headLen).toFloat()
        ),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawResponderMarker(
    pos: Offset,
    callsign: String,
    isSelf: Boolean,
    isConfirmedHere: Boolean,
    textMeasurer: TextMeasurer
) {
    val fill = when {
        isConfirmedHere -> ResponderEmeraldBright
        isSelf -> TelemetryCyan
        else -> ResponderEmerald
    }
    drawCircle(
        color = fill.copy(alpha = 0.25f),
        radius = 15f,
        center = pos
    )
    drawCircle(
        color = fill,
        radius = 7f,
        center = pos
    )
    drawCircle(
        color = ObsidianBg,
        radius = 2.6f,
        center = pos
    )
    val tag = if (isSelf) "TÚ · $callsign" else callsign
    drawMapTag(
        textMeasurer = textMeasurer,
        text = tag,
        anchor = Offset(pos.x + 10f, pos.y + 8f),
        bgColor = TacticalSurface.copy(alpha = 0.90f),
        borderColor = fill.copy(alpha = 0.7f),
        textColor = TextPrimary
    )
}

private fun DrawScope.drawMapTag(
    textMeasurer: TextMeasurer,
    text: String,
    anchor: Offset,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    val measured = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    )
    val padX = 6f
    val padY = 3f
    val rectSize = androidx.compose.ui.geometry.Size(
        width = measured.size.width + padX * 2,
        height = measured.size.height + padY * 2
    )
    drawRoundRect(
        color = bgColor,
        topLeft = anchor,
        size = rectSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = borderColor,
        topLeft = anchor,
        size = rectSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        style = Stroke(width = 1.2f)
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(anchor.x + padX, anchor.y + padY)
    )
}
