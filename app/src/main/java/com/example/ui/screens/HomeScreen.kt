package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.ble.BleDevice
import com.example.ui.components.BleRadar
import com.example.ui.components.headless.HeadlessSlider
import com.example.ui.viewmodel.HomeSideEffect
import com.example.ui.viewmodel.HomeState
import com.example.ui.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import com.example.ui.theme.*

class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<HomeViewModel>()
        val state by viewModel.container.stateFlow.collectAsState()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // Handle Side Effects reactively
        LaunchedEffect(Unit) {
            viewModel.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    is HomeSideEffect.ShowToast -> {
                        Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    }
                    is HomeSideEffect.NavigateToDetail -> {
                        navigator.push(DetailScreen(effect.deviceMacAddress))
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                HomeHeader(
                    isScanning = state.isScanning,
                    onToggleScan = {
                        if (state.isScanning) viewModel.stopScanning() else viewModel.startScanning()
                    },
                    onNavigateSettings = {
                        navigator.push(SettingsScreen())
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Radar view at top
                BleRadar(
                    isScanning = state.isScanning,
                    devices = state.filteredDevices,
                    onDeviceClick = { dev ->
                        navigator.push(DetailScreen(dev.macAddress))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Headless RSSI Filter Slider
                RssiFilterControl(
                    rssiFilter = state.rssiFilter,
                    onRssiChange = { viewModel.updateRssiFilter(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Search Bar
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section Title with counts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERED DEVICES (${state.filteredDevices.size})",
                        color = KineticTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    if (state.isScanning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(KineticAccentGreen)
                            )
                            Text(
                                text = "LIVE FEED",
                                color = KineticAccentGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Device Cards Lazy List
                if (state.filteredDevices.isEmpty()) {
                    EmptyDiscoveryState(isScanning = state.isScanning)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.filteredDevices, key = { it.macAddress }) { device ->
                            // Look up if has bookmarks/custom names in DB
                            val dbMatch = state.bookmarks.find { it.macAddress == device.macAddress }
                            DeviceCard(
                                device = device,
                                customName = dbMatch?.customName,
                                category = dbMatch?.category,
                                onClick = {
                                    navigator.push(DetailScreen(device.macAddress))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeHeader(
    isScanning: Boolean,
    onToggleScan: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KineticBackground)
            .padding(top = 48.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KineticSurfaceGhost),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = KineticAccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Kinetic",
                    color = KineticTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "TELEMETRY SCANNER",
                    color = KineticTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onToggleScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) KineticAccentGreenTint else KineticSurfaceGhost,
                    contentColor = if (isScanning) KineticAccentGreen else KineticTextSecondary
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isScanning) KineticAccentGreen.copy(alpha = 0.3f) else KineticBorderSoft
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isScanning) "Stop" else "Scan",
                        modifier = Modifier.size(16.dp),
                        tint = if (isScanning) KineticAccentGreen else KineticTextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "STOP SCAN" else "START SCAN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isScanning) KineticAccentGreen else KineticTextSecondary
                    )
                }
            }

            IconButton(
                onClick = onNavigateSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KineticSurfaceGhost)
                    .border(1.dp, KineticBorderSoft, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Preferences",
                    tint = KineticTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * HeadlessSlider utilized cleanly to implement a brutalist visual Slider control panel
 */
@Composable
fun RssiFilterControl(
    rssiFilter: Int,
    onRssiChange: (Int) -> Unit
) {
    // Standardize rssi (-100 to -40) to percentage (0f to 1f)
    val percentage = ((rssiFilter + 100) / 60.0f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(KineticAccentBlue))
                    Text(
                        text = "RSSI FIELD FILTER",
                        color = KineticTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = ">= $rssiFilter dBm",
                    color = KineticTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Headless Slider Implementation
            HeadlessSlider(
                value = percentage,
                onValueChange = { fraction ->
                    val actualRssi = (-100 + (fraction * 60f)).toInt()
                    onRssiChange(actualRssi)
                },
                modifier = Modifier.fillMaxWidth()
            ) { progress, offsetPx, isDragging, interactionModifier ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(KineticBorderSoft, RoundedCornerShape(12.dp))
                        .then(interactionModifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Track Filled fraction
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                color = if (isDragging) KineticAccentBlue else KineticAccentBlue.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )

                    // Drag value Indicator layout text on track
                    Text(
                        text = if (progress < 0.1f) "Weaker" else if (progress > 0.9f) "Stronger Only" else "Filtering Field Strength",
                        color = if (progress > 0.4f) KineticBackground else KineticTextPrimary,
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
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search by name or MAC...", color = KineticTextMuted, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = KineticTextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KineticAccentBlue,
            unfocusedBorderColor = KineticBorderSoft,
            focusedContainerColor = KineticGlassBase,
            unfocusedContainerColor = KineticGlassBase,
            focusedTextColor = KineticTextPrimary,
            unfocusedTextColor = KineticTextPrimary
        ),
        singleLine = true
    )
}

@Composable
fun DeviceCard(
    device: BleDevice,
    customName: String?,
    category: String?,
    onClick: () -> Unit
) {
    val isBentoGreen = category == "Secure" || category == "Tracker" || category == "Smart Tag"
    val isBentoPurple = category != null && !isBentoGreen
    
    val (colorsTriple, badgePair) = when {
        isBentoGreen -> {
            Triple(KineticGlassMd, KineticAccentGreen.copy(alpha = 0.25f), KineticTextPrimary) to (KineticAccentGreenGlow.copy(alpha = 0.15f) to KineticAccentGreen)
        }
        isBentoPurple -> {
            Triple(KineticGlassMd, KineticAccentBlue.copy(alpha = 0.25f), KineticTextPrimary) to (KineticAccentBlueTint to KineticAccentBlue)
        }
        device.isConnectable && device.rssi >= -70 -> {
            Triple(KineticGlassStrong, KineticBorderStrong, KineticTextPrimary) to (KineticSurfaceGhostHover to KineticAccentBlue)
        }
        else -> {
            Triple(KineticGlassBase, KineticBorderSoft, KineticTextPrimary) to (KineticSurfaceGhost to KineticTextSecondary)
        }
    }
    
    val (cardBg, cardBorder, cardText) = colorsTriple
    val (badgeBg, badgeText) = badgePair

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customName ?: device.name,
                        color = cardText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (category != null || customName != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = category ?: "Saves",
                                color = badgeText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.macAddress,
                    color = cardText.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (device.isConnectable) "CONNECTABLE LINK" else "BROADCAST ONLY",
                    color = if (device.isConnectable) KineticAccentBlue else KineticTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    color = cardText,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.1f m", device.distanceMeters),
                    color = KineticAccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun EmptyDiscoveryState(isScanning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Search Info",
                tint = KineticAccentBlue.copy(alpha = 0.40f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isScanning) "Searching for transmission waves..." else "Discovery stream offline",
                color = KineticTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isScanning) "Verify filters or activate nearby peripherals" else "Click SCAN to initialize active telemetry",
                color = KineticTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
