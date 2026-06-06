package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    selectedTab = state.selectedTab,
                    onNavigateSettings = {
                        navigator.push(SettingsScreen())
                    }
                )
            },
            bottomBar = {
                GlassBottomNavigationBar(
                    selectedTab = state.selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) }
                )
            },
            containerColor = KineticBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = state.selectedTab,
                    label = "TabContentAnimation"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> ScannerTab(state = state, viewModel = viewModel, onNavigateDetail = { dev ->
                            navigator.push(DetailScreen(dev.macAddress))
                        })
                        1 -> BroadcasterTab(state = state, viewModel = viewModel)
                        2 -> AttendanceLogsTab(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ── CUSTOM HEADER ──

@Composable
fun HomeHeader(
    selectedTab: Int,
    onNavigateSettings: () -> Unit
) {
    val subtitle = when (selectedTab) {
        0 -> "BLE SCANNER"
        1 -> "BLE BROADCASTER"
        else -> "ATTENDANCE LOGS"
    }

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
                    .background(KineticSurfaceGhost)
                    .border(1.dp, KineticBorderSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (selectedTab) {
                        0 -> Icons.Default.Search
                        1 -> Icons.Default.Share
                        else -> Icons.Default.List
                    },
                    contentDescription = null,
                    tint = KineticAccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Attendance Sync",
                    color = KineticTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = subtitle,
                    color = KineticTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
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

// ── GLASS BOTTOM NAVIGATION BAR ──

@Composable
fun GlassBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = KineticGlassStrong,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, KineticBorderStrong),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Scanner", Icons.Default.Search, 0),
                Triple("Broadcaster", Icons.Default.Share, 1),
                Triple("Logs", Icons.Default.List, 2)
            )

            tabs.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                val activeBg = if (isSelected) KineticSurfaceTabActive else Color.Transparent
                val activeTint = if (isSelected) KineticAccentBlue else KineticTextSecondary

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(activeBg)
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = activeTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = activeTint,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── TAB 0: SCANNER/RECEIVER MODE ──

@Composable
fun ScannerTab(
    state: HomeState,
    viewModel: HomeViewModel,
    onNavigateDetail: (BleDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Control Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
                border = BorderStroke(1.dp, KineticBorderSoft)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BLE Scanner is ${if (state.isScanning) "active" else "offline"}",
                            color = if (state.isScanning) KineticAccentGreen else KineticTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (state.isScanning) "Scanning for nearby student beacons..." else "Start scanning to record check-ins",
                            color = KineticTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (state.isScanning) viewModel.stopScanning() else viewModel.startScanning()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isScanning) KineticAccentGreenTint else KineticSurfaceGhost,
                            contentColor = if (state.isScanning) KineticAccentGreen else KineticTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.isScanning) KineticAccentGreen.copy(alpha = 0.3f) else KineticBorderSoft
                        )
                    ) {
                        Text(
                            text = if (state.isScanning) "STOP SCAN" else "START SCAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Radar Visualizer
        item {
            BleRadar(
                isScanning = state.isScanning,
                devices = state.filteredDevices,
                onDeviceClick = onNavigateDetail
            )
        }

        // Search Bar & Filter Options
        item {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )
        }

        item {
            RssiFilterControl(
                rssiFilter = state.rssiFilter,
                onRssiChange = { viewModel.updateRssiFilter(it) }
            )
        }

        // Mock Testing Board for Single-Device Flow
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KineticGlassMd),
                border = BorderStroke(1.dp, KineticAccentBlue.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(KineticAccentBlue)
                        )
                        Text(
                            text = "SIMULATE STUDENT CHECK-IN",
                            color = KineticTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Simulate mock student beacons checking in to test the automatic Room database attendance log system.",
                        color = KineticTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val testClassmates = listOf(
                        Triple("Alice Smith", "1002", "Present"),
                        Triple("Michael Vance", "1019", "Late"),
                        Triple("Elena Rostova", "1044", "Present")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        testClassmates.forEach { (name, id, status) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(KineticSurfaceGhost)
                                    .border(1.dp, KineticBorderSoft, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val mockDevice = BleDevice(
                                            name = "$name (Student)",
                                            macAddress = "E5:31:AA:88:00:$id",
                                            rssi = -42,
                                            isConnectable = true,
                                            category = "Student ID: $id",
                                            services = listOf("ffe0")
                                        )
                                        viewModel.simulateStudentCheckIn(mockDevice, id, name, status)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = name.substringBefore(" "), color = KineticTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "ID $id", color = KineticAccentBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Discovered Peer Section
        item {
            Text(
                text = "DISCOVERED DEVICES (${state.filteredDevices.size})",
                color = KineticTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.filteredDevices.isEmpty()) {
            item {
                EmptyDiscoveryState(isScanning = state.isScanning)
            }
        } else {
            items(state.filteredDevices, key = { it.macAddress }) { device ->
                val dbMatch = state.bookmarks.find { it.macAddress == device.macAddress }
                DeviceCard(
                    device = device,
                    customName = dbMatch?.customName,
                    category = dbMatch?.category,
                    onClick = onNavigateDetail
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ── TAB 1: BROADCASTER/SENDER MODE ──

@Composable
fun BroadcasterTab(
    state: HomeState,
    viewModel: HomeViewModel
) {
    var studentNameInput by remember { mutableStateOf("Steve Rogers") }
    var studentIdInput by remember { mutableStateOf("10842") }
    var selectedStatus by remember { mutableStateOf("Present") }

    val infiniteTransition = rememberInfiniteTransition(label = "BroadcasterPulse")
    val pulseGlowScale by if (state.isAdvertising) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 11.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulse wave element at top
        item {
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Expanding Beacon Signal Glow
                if (state.isAdvertising) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(
                                        KineticAccentBlue.copy(alpha = 0.22f / pulseGlowScale),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Core Beacon Widget
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (state.isAdvertising) KineticAccentBlueTint else KineticSurfaceGhost)
                        .border(
                            1.dp,
                            if (state.isAdvertising) KineticAccentBlue else KineticBorderSoft,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Beacon",
                            tint = if (state.isAdvertising) KineticAccentBlue else KineticTextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isAdvertising) "BROADCASTING" else "STANDBY",
                            color = if (state.isAdvertising) KineticAccentBlue else KineticTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Student Card Details Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
                border = BorderStroke(1.dp, KineticBorderSoft)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STUDENT IDENTITY",
                        color = KineticTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = studentNameInput,
                        onValueChange = { studentNameInput = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KineticAccentBlue,
                            unfocusedBorderColor = KineticBorderSoft,
                            focusedContainerColor = KineticGlassBase,
                            unfocusedContainerColor = KineticGlassBase,
                            focusedTextColor = KineticTextPrimary,
                            unfocusedTextColor = KineticTextPrimary,
                            focusedLabelColor = KineticTextSecondary,
                            unfocusedLabelColor = KineticTextMuted
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = studentIdInput,
                        onValueChange = { studentIdInput = it },
                        label = { Text("Student ID Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KineticAccentBlue,
                            unfocusedBorderColor = KineticBorderSoft,
                            focusedContainerColor = KineticGlassBase,
                            unfocusedContainerColor = KineticGlassBase,
                            focusedTextColor = KineticTextPrimary,
                            unfocusedTextColor = KineticTextPrimary,
                            focusedLabelColor = KineticTextSecondary,
                            unfocusedLabelColor = KineticTextMuted
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Attendance Status",
                        color = KineticTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val statuses = listOf("Present", "Late", "Excused")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statuses.forEach { status ->
                            val isSelected = selectedStatus == status
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) KineticSurfaceTabActive else KineticSurfaceGhost)
                                    .border(
                                        1.dp,
                                        if (isSelected) KineticAccentBlue else KineticBorderSoft,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedStatus = status }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.uppercase(),
                                    color = if (isSelected) KineticAccentBlue else KineticTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Advertising Control Button
                    Button(
                        onClick = {
                            if (state.isAdvertising) {
                                viewModel.stopAdvertising()
                            } else {
                                viewModel.startAdvertising(studentNameInput, studentIdInput, selectedStatus)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isAdvertising) Color(0x1AFF5F57) else KineticAccentGreenTint,
                            contentColor = if (state.isAdvertising) Color(0xFFFF5F57) else KineticAccentGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.isAdvertising) Color(0x66FF5F57) else KineticAccentGreen.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isAdvertising) Icons.Default.Stop else Icons.Default.CellTower,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isAdvertising) "STOP BROADCAST" else "START BROADCAST",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Broadcast Info / Bytes detail
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = KineticGlassMd),
                border = BorderStroke(1.dp, KineticBorderSoft)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BROADCAST PAYLOAD DATA (HEX)",
                        color = KineticTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val rawPayloadString = "$studentIdInput;$studentNameInput;$selectedStatus"
                    val hexRepresentation = rawPayloadString.toByteArray(Charsets.UTF_8)
                        .joinToString("") { "%02X ".format(it) }.trim()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KineticBackground, RoundedCornerShape(10.dp))
                            .border(1.dp, KineticBorderSubtle, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = hexRepresentation.ifEmpty { "00" },
                            color = KineticAccentBlue,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "The app broadcasts your student parameters over standard Bluetooth LE advertising packets. Any nearby scanning teacher or receiver will automatically record your check-in.",
                        color = KineticTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ── TAB 2: ATTENDANCE DATABASE LOGS ──

@Composable
fun AttendanceLogsTab(
    state: HomeState,
    viewModel: HomeViewModel
) {
    val logs = state.attendanceRecords

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Attendance Dashboard Card
        item {
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
                        Column {
                            Text(
                                text = "ATTENDANCE ROSTER",
                                color = KineticTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${logs.filter { it.status != "Late" }.size} Present / ${logs.filter { it.status == "Late" }.size} Late Checks",
                                color = KineticTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.clearAllAttendanceLogs() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x1AFF5F57),
                                contentColor = Color(0xFFFF5F57)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF5F57).copy(alpha = 0.2f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLEAR LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Ledger List
        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
                    border = BorderStroke(1.dp, KineticBorderSoft)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Empty",
                            tint = KineticTextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No attendance logs recorded yet",
                            color = KineticTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Turn on the scanner to search for nearby student beacons or use the test check-in simulator to record logs.",
                            color = KineticTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        } else {
            items(logs, key = { it.id }) { record ->
                AttendanceRecordCard(record = record, onDelete = { viewModel.deleteAttendanceLog(record.id) })
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun AttendanceRecordCard(
    record: com.example.data.db.AttendanceRecord,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("hh:mm:ss a / MMM d", Locale.getDefault()) }
    val formatTime = formatter.format(Date(record.timestamp))

    val isLate = record.status.equals("Late", ignoreCase = true)
    val statusBg = if (isLate) Color(0x2BFFC107) else KineticAccentGreenTint
    val statusColor = if (isLate) Color(0xFFFFC107) else KineticAccentGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.studentName,
                            color = KineticTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .background(statusBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = record.status.uppercase(),
                                color = statusColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "STUDENT ID: ${record.studentId}",
                        color = KineticAccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove record",
                        tint = KineticTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-info box containing MAC and Raw received RF byte sequence
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KineticSurfaceGhost, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MAC ADDRESS",
                        color = KineticTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = record.macAddress,
                        color = KineticTextPrimary,
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HEX PAYLOAD",
                        color = KineticTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = record.payloadBytesHex,
                        color = KineticAccentBlue,
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp).align(Alignment.CenterVertically)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Recorded at $formatTime",
                color = KineticTextSecondary,
                fontSize = 10.sp
            )
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
        placeholder = { Text("Filter devices by name, MAC...", color = KineticTextMuted, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = KineticTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = KineticTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KineticAccentBlue,
            unfocusedBorderColor = KineticBorderSoft,
            focusedContainerColor = KineticGlassBase,
            unfocusedContainerColor = KineticGlassBase,
            focusedTextColor = KineticTextPrimary,
            unfocusedTextColor = KineticTextPrimary
        )
    )
}

@Composable
fun RssiFilterControl(
    rssiFilter: Int,
    onRssiChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SIGNAL STRENGTH FILTER",
                    color = KineticTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (rssiFilter <= -100) "Show All" else ">= $rssiFilter dBm",
                    color = KineticAccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = rssiFilter.toFloat(),
                onValueChange = { onRssiChange(it.toInt()) },
                valueRange = -100f..-40f,
                colors = SliderDefaults.colors(
                    activeTrackColor = KineticAccentBlue,
                    inactiveTrackColor = KineticBorderSoft,
                    thumbColor = KineticForeground
                )
            )
        }
    }
}

@Composable
fun EmptyDiscoveryState(isScanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = KineticTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isScanning) "No devices found yet" else "Discovery is offline",
                color = KineticTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = if (isScanning) "No signals detected yet" else "Turn on scanning above to search for nearby student signals",
                color = KineticTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
fun DeviceCard(
    device: BleDevice,
    customName: String?,
    category: String?,
    onClick: (BleDevice) -> Unit
) {
    val isStudent = device.name.contains("(Student)")
    val highlightColor = if (isStudent) KineticAccentGreen else KineticAccentBlue
    val cardBg = if (isStudent) KineticGlassMd else KineticGlassBase

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(device) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(highlightColor.copy(alpha = 0.15f))
                        .border(1.dp, highlightColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStudent) Icons.Default.Person else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = highlightColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = customName ?: device.name,
                            color = KineticTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isStudent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(KineticAccentGreenTint, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("STUDENT", color = KineticAccentGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = device.macAddress,
                        color = KineticTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    color = KineticTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = category ?: device.category,
                    color = KineticTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
