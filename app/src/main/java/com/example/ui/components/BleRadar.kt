package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BleDevice
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BleRadar(
    isScanning: Boolean,
    devices: List<BleDevice>,
    onDeviceClick: (BleDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    // Rotate the sweeping arm infinitely if scanning
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SweepAngle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Pulse circles continuously when scanning
    val pulseScale by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "PulseScale"
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(KineticGlassBase)
            .border(BorderStroke(1.dp, KineticBorderSoft), RoundedCornerShape(22.dp))
            .onSizeChanged { sizePx = it },
        contentAlignment = Alignment.Center
    ) {
        // 1. Radar background coordinate drawing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = (size.width.coerceAtMost(size.height) / 2) * 0.9f

            // Base Grid Circles
            drawCircle(color = KineticTextSecondary.copy(alpha = 0.08f), radius = maxRadius * 0.95f, style = Stroke(width = 1.5f))
            drawCircle(color = KineticTextSecondary.copy(alpha = 0.08f), radius = maxRadius * 0.65f, style = Stroke(width = 1.2f))
            drawCircle(color = KineticTextSecondary.copy(alpha = 0.08f), radius = maxRadius * 0.35f, style = Stroke(width = 1f))

            // Pulsing scan wave circle
            if (isScanning) {
                drawCircle(
                    color = KineticAccentGreen.copy(alpha = 0.20f * (1f - pulseScale)),
                    radius = maxRadius * 0.95f * pulseScale,
                    style = Stroke(width = 2.5f)
                )
            }

            // Crosshair lines
            drawLine(
                color = KineticTextSecondary.copy(alpha = 0.08f),
                start = Offset(center.x - maxRadius * 0.95f, center.y),
                end = Offset(center.x + maxRadius * 0.95f, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = KineticTextSecondary.copy(alpha = 0.08f),
                start = Offset(center.x, center.y - maxRadius * 0.95f),
                end = Offset(center.x, center.y + maxRadius * 0.95f),
                strokeWidth = 1f
            )

            // Rotational Sweep Arm
            if (isScanning) {
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                KineticAccentBlue.copy(alpha = 0.04f),
                                KineticAccentBlue.copy(alpha = 0.22f)
                            ),
                            center = center
                        ),
                        startAngle = -45f,
                        sweepAngle = 45f,
                        useCenter = true,
                        size = androidx.compose.ui.geometry.Size(maxRadius * 1.9f, maxRadius * 1.9f),
                        topLeft = Offset(center.x - maxRadius * 0.95f, center.y - maxRadius * 0.95f)
                    )

                    // Scanner focus line
                    drawLine(
                        color = KineticAccentBlue,
                        start = center,
                        end = Offset(center.x + maxRadius * 0.95f, center.y),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Center Blinking Core
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(KineticGlassStrong)
                .border(BorderStroke(1.dp, KineticBorderSoft), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Core",
                tint = if (isScanning) KineticAccentGreen else KineticAccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        // 2. Render clickable simulated nodes for detected devices on the Radar map
        val density = LocalDensity.current
        devices.forEachIndexed { index, device ->
            // Use distinct static angles for different devices to map them dynamically in 2D space
            val baseAngleDeg = (index * 60) + 25f
            val rad = Math.toRadians(baseAngleDeg.toDouble())

            // Map closer distances (weaker RSSI) closer to the outside bounds. Perfect RSSI closer to the inside!
            // Map RSSI (-100 to -40) to standard radius ratio (0.85f to 0.15f)
            val rssiFraction = ((device.rssi + 100) / 60.0f).coerceIn(0.15f, 0.85f)
            val radiusMultiplier = 1.0f - rssiFraction // inverted: strong RSSI = small radius (closer to core)

            if (sizePx.width > 0 && sizePx.height > 0) {
                val maxRadiusPx = (sizePx.width.coerceAtMost(sizePx.height) / 2) * 0.82f
                val deviceDistancePx = maxRadiusPx * radiusMultiplier

                val xOffsetPx = (deviceDistancePx * cos(rad)).toFloat()
                val yOffsetPx = (deviceDistancePx * sin(rad)).toFloat()

                val xOffsetDp = with(density) { xOffsetPx.toDp() }
                val yOffsetDp = with(density) { yOffsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = xOffsetDp, y = yOffsetDp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KineticAccentGreen.copy(alpha = if (isScanning) 0.60f else 0.25f))
                        .clickable { onDeviceClick(device) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                // Small Tag label for nodes
                Text(
                    text = device.name.take(8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = KineticTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = xOffsetDp, y = yOffsetDp + 18.dp)
                        .background(KineticGlassStrong, RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, KineticBorderSubtle), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Extension to avoid custom helper complexity
@Composable
private fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }
