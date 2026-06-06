package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.data.datastore.ScanPreferences
import com.example.ui.components.headless.HeadlessSlider
import com.example.ui.components.headless.HeadlessToggle
import com.example.ui.components.headless.InteractiveSignalMap
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.example.ui.theme.*

class SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        val scanPreferences = koinInject<ScanPreferences>()

        // Collect preferences state from flow datastore
        val scanDuration by scanPreferences.scanDurationFlow.collectAsState(initial = 10)
        val rssiThreshold by scanPreferences.rssiThresholdFlow.collectAsState(initial = -100)
        val autoConnect by scanPreferences.autoConnectFlow.collectAsState(initial = false)
        val showUnidentified by scanPreferences.showUnidentifiedFlow.collectAsState(initial = true)

        Scaffold(
            topBar = {
                SettingsHeader(onBack = { navigator.pop() })
            },
            containerColor = KineticBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Settings Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = KineticAccentBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CORE TELEMETRY DIAGNOSTICS",
                        color = KineticTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                // 1. Scan Duration Slider
                ScanDurationCard(
                    scanDuration = scanDuration,
                    onDurationChange = { valSec ->
                        coroutineScope.launch {
                            scanPreferences.saveScanDuration(valSec)
                        }
                    }
                )

                // 2. Unstyled Toggle Controls Card to configure boolean variables in DataStore
                ToggleControlsCard(
                    autoConnect = autoConnect,
                    showUnidentified = showUnidentified,
                    onAutoConnectChange = { enable ->
                        coroutineScope.launch {
                            scanPreferences.saveAutoConnect(enable)
                        }
                    },
                    onShowUnidentifiedChange = { enable ->
                        coroutineScope.launch {
                            scanPreferences.saveShowUnidentified(enable)
                        }
                    }
                )

                // 3. Laboratory / Gesture Sandbox map Section
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = KineticAccentBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ANTENNA LABORATORY (CUSTOM GESTURES)",
                        color = KineticTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                InteractiveSignalSandboxPanel(
                    onSimulatePing = { dist ->
                        Toast.makeText(context, String.format("Transceiver delay: %.1f ms", dist * 0.12), Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KineticBackground)
            .padding(top = 48.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(KineticSurfaceGhost)
                .border(1.dp, KineticBorderSoft, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = KineticTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Preferences",
                color = KineticTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Tweak scanner intervals and physics parameters",
                color = KineticTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ScanDurationCard(
    scanDuration: Int,
    onDurationChange: (Int) -> Unit
) {
    // Map duration (5 to 30) to slider fraction (0f to 1f)
    val fraction = ((scanDuration - 5) / 25.0f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISCOVERY CYCLE LENGTH",
                    color = KineticTextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$scanDuration Seconds",
                    color = KineticAccentBlue,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Headless Slider Implementation
            HeadlessSlider(
                value = fraction,
                onValueChange = { frac ->
                    val actualSec = (5 + (frac * 25f)).toInt()
                    onDurationChange(actualSec)
                },
                modifier = Modifier.fillMaxWidth()
            ) { progress, offsetPx, isDragging, interactionModifier ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(KineticGlassMd, RoundedCornerShape(12.dp))
                        .border(1.dp, KineticBorderSoft, RoundedCornerShape(12.dp))
                        .then(interactionModifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                color = if (isDragging) KineticAccentBlue else KineticAccentBlue.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )

                    Text(
                        text = "Cycle limits scanning load",
                        color = KineticTextMuted.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleControlsCard(
    autoConnect: Boolean,
    showUnidentified: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowUnidentifiedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Toggle item 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Connect Target", color = KineticTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Connect to closest RSSI transmitter on startup", color = KineticTextSecondary, fontSize = 11.sp)
                }

                HeadlessToggle(
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange
                ) { animatedOffset, toggleActionModifier ->
                    // Beautifully rendered custom Toggle Switch
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (animatedOffset > 0.5f) KineticAccentBlue.copy(alpha = 0.25f) else KineticSurfaceGhost)
                            .border(1.dp, KineticBorderSoft, RoundedCornerShape(16.dp))
                            .then(toggleActionModifier)
                            .padding(4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Slider thumb
                        val offsetPct = 22.dp * animatedOffset
                        Box(
                            modifier = Modifier
                                .offset(x = offsetPct)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (animatedOffset > 0.5f) KineticAccentBlue else KineticTextMuted)
                        )
                    }
                }
            }

            HorizontalDivider(color = KineticBorderSubtle)

            // Toggle item 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Unidentified Nodes", color = KineticTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Include items transmitting no ID identifiers", color = KineticTextSecondary, fontSize = 11.sp)
                }

                HeadlessToggle(
                    checked = showUnidentified,
                    onCheckedChange = onShowUnidentifiedChange
                ) { animatedOffset, toggleActionModifier ->
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (animatedOffset > 0.5f) KineticAccentBlue.copy(alpha = 0.25f) else KineticSurfaceGhost)
                            .border(1.dp, KineticBorderSoft, RoundedCornerShape(16.dp))
                            .then(toggleActionModifier)
                            .padding(4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val offsetPct = 22.dp * animatedOffset
                        Box(
                            modifier = Modifier
                                .offset(x = offsetPct)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (animatedOffset > 0.5f) KineticAccentBlue else KineticTextMuted)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the Interactive Signal Map along with description labels
 */
@Composable
fun InteractiveSignalSandboxPanel(
    onSimulatePing: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FIELD INTENSITY MODELING",
                color = KineticTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Hold & drag the sensor probe to model relative wave connectivity vector feeds. Tap inside the field to emit a ping propagation.",
                color = KineticTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // The Interactive map panel
            InteractiveSignalMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KineticGlassMd)
                    .border(1.dp, KineticBorderSubtle, RoundedCornerShape(16.dp)),
                onSimulatePing = onSimulatePing
            )
        }
    }
}
