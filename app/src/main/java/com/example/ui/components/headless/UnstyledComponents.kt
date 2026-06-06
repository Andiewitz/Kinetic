package com.example.ui.components.headless

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import com.example.ui.theme.*

/**
 * HeadlessSlider (Unstyled Slider)
 * Decouples state, touch target coordinates, and interactive maths from rendering.
 * Allowing you to customize track shape, color, thumb, and labels inside a custom slot scope.
 */
@Composable
fun HeadlessSlider(
    value: Float, // Range 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(
        progress: Float,
        thumbOffsetPx: Float,
        isDragging: Boolean,
        interactionModifier: Modifier
    ) -> Unit
) {
    var width by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedProgress = remember { Animatable(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            animatedProgress.animateTo(
                value,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    val finalProgress = if (isDragging) value else animatedProgress.value
    val thumbOffsetPx = finalProgress * width

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width }
    ) {
        val interactionModifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false },
                onDragCancel = { isDragging = false },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (width > 0) {
                        val newValue = (value + dragAmount.x / width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                }
            )
        }.pointerInput(Unit) {
            detectTapGestures { offset ->
                if (width > 0) {
                    val newValue = (offset.x / width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
        }

        content(finalProgress, thumbOffsetPx, isDragging, interactionModifier)
    }
}

/**
 * HeadlessToggle (Unstyled Switch/Toggle)
 * Handles physics transitions and triggers between boolean states.
 * Yields styling and visuals entirely to the custom slot-scope.
 */
@Composable
fun HeadlessToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(
        animatedOffset: Float, // 0f to 1f
        toggleActionModifier: Modifier
    ) -> Unit
) {
    val animatedFrac = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        animatedFrac.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(modifier = modifier) {
        val toggleActionModifier = Modifier.pointerInput(Unit) {
            detectTapGestures {
                onCheckedChange(!checked)
            }
        }

        content(animatedFrac.value, toggleActionModifier)
    }
}

/**
 * InteractiveSignalMap (Custom Gesture & Touch Sandbox Panel)
 * Features real physics, animatable feedback, and pointer inputs. You drag an active point
 * around, and the grid lights up or calculates signal distances reactively with fine-grained haptic circles.
 */
@Composable
fun InteractiveSignalMap(
    modifier: Modifier = Modifier,
    onSimulatePing: (distanceFactor: Double) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    // Position of the draggable ping transceiver probe (relative offsets)
    val probeX = remember { Animatable(250f) }
    val probeY = remember { Animatable(250f) }

    // Wave ripples originating from clicks or pings
    val pingRipples = remember { mutableListOf<PingRippleInstance>() }

    // Keeps target of scan
    val targetDotObj = remember { Offset(400f, 300f) }

    Box(
        modifier = modifier
            .onSizeChanged { panelSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val dx = offset.x - targetDotObj.x
                    val dy = offset.y - targetDotObj.y
                    val dist = sqrt(dx * dx + dy * dy).toDouble()
                    onSimulatePing(dist)

                    val newRipple = PingRippleInstance(offset, Animatable(0f))
                    pingRipples.add(newRipple)
                    coroutineScope.launch {
                        newRipple.anim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                        )
                        pingRipples.remove(newRipple)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        val limitX = panelSize.width.toFloat()
                        val limitY = panelSize.height.toFloat()
                        probeX.snapTo((probeX.value + dragAmount.x).coerceIn(0f, limitX))
                        probeY.snapTo((probeY.value + dragAmount.y).coerceIn(0f, limitY))
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw a technical radar matrix background
            val cols = 12
            val rows = 12
            val colSpacing = width / cols
            val rowSpacing = height / rows

            // Mesh Lines
            for (i in 0..cols) {
                drawLine(
                    color = KineticTextSecondary.copy(alpha = 0.04f),
                    start = Offset(i * colSpacing, 0f),
                    end = Offset(i * colSpacing, height),
                    strokeWidth = 1f
                )
            }
            for (k in 0..rows) {
                drawLine(
                    color = KineticTextSecondary.copy(alpha = 0.04f),
                    start = Offset(0f, k * rowSpacing),
                    end = Offset(width, k * rowSpacing),
                    strokeWidth = 1f
                )
            }

            // Target Node in center/side representing a Simulated BLE Module
            drawCircle(
                color = KineticAccentGreen.copy(alpha = 0.15f),
                radius = 12f,
                center = targetDotObj
            )
            drawCircle(
                color = KineticAccentGreen,
                radius = 6f,
                center = targetDotObj
            )

            // 2. Draw user dragged Sensor Probe
            val pPos = Offset(probeX.value, probeY.value)
            drawLine(
                color = KineticAccentBlue.copy(alpha = 0.35f),
                start = pPos,
                end = targetDotObj,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                strokeWidth = 2.5f
            )

            // Draw range boundary indicators around sensor probe
            drawCircle(
                color = KineticAccentBlue.copy(alpha = 0.06f),
                radius = 120f,
                center = pPos
            )
            drawCircle(
                color = KineticAccentBlue.copy(alpha = 0.12f),
                radius = 45f,
                center = pPos
            )

            // Outer ring of probe
            drawCircle(
                color = KineticAccentBlue,
                radius = 28f,
                center = pPos,
                style = Stroke(width = 3f)
            )

            // Center of probe
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = pPos
            )

            // Interactive Dynamic Ripples on tap
            pingRipples.forEach { r ->
                val scale = r.anim.value
                if (scale < 1.0f) {
                    drawCircle(
                        color = KineticAccentBlue.copy(alpha = 0.5f * (1f - scale)),
                        radius = scale * 260f,
                        center = r.center,
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
    }
}

data class PingRippleInstance(
    val center: Offset,
    val anim: Animatable<Float, *>
)
