package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.ble.BleDevice
import com.example.ble.BleManager
import com.example.ble.GattService
import com.example.ble.GattCharacteristic
import androidx.compose.foundation.BorderStroke
import com.example.data.db.DeviceBookmark
import com.example.ui.theme.*
import com.example.data.repository.DeviceRepository
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class DetailScreen(val deviceMacAddress: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        val bleManager = koinInject<BleManager>()
        val deviceRepository = koinInject<DeviceRepository>()

        // Find the active device mapping inside scanned devices
        val scannedDevices by bleManager.scannedDevices.collectAsState()
        val activeDevice = scannedDevices.find { it.macAddress == deviceMacAddress }

        // Fetch or create bookmark representation from database
        var customNameInput by remember { mutableStateOf("") }
        var notesInput by remember { mutableStateOf("") }
        var categorySelected by remember { mutableStateOf("Tracker") }
        var existingBookmark by remember { mutableStateOf<DeviceBookmark?>(null) }

        LaunchedEffect(deviceMacAddress) {
            val bookmark = deviceRepository.getBookmarkByMac(deviceMacAddress)
            existingBookmark = bookmark
            if (bookmark != null) {
                customNameInput = bookmark.customName
                notesInput = bookmark.notes
                categorySelected = bookmark.category
            } else {
                customNameInput = activeDevice?.name ?: "Peripheral Device"
            }
        }

        // Connect/disconnect lifecycle hook of details screen
        LaunchedEffect(activeDevice) {
            if (activeDevice != null && !activeDevice.isConnected) {
                bleManager.connectDevice(activeDevice)
            }
        }

        // Clean up on exit
        DisposableEffect(Unit) {
            onDispose {
                bleManager.disconnectDevice()
            }
        }

        val connectedDevice by bleManager.connectedDevice.collectAsState()
        val services by bleManager.gattServicesAndCharacteristics.collectAsState()

        Scaffold(
            topBar = {
                DetailHeader(
                    deviceName = customNameInput.ifBlank { activeDevice?.name ?: "Unknown" },
                    macAddress = deviceMacAddress,
                    onBack = { navigator.pop() }
                )
            },
            containerColor = KineticBackground
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Connection Status Card
                item {
                    ConnectionStatsCard(
                        device = activeDevice,
                        connectedDevice = connectedDevice
                    )
                }

                // 2. Real-time RSSI Signal strength path graph
                if (activeDevice != null) {
                    item {
                        RssiSignalPathGraph(
                            signalHistory = activeDevice.signalHistory
                        )
                    }
                }

                // 3. User Personal Tags & Notes (Room Persistence UI)
                item {
                    NotesPersistenceCard(
                        customName = customNameInput,
                        notes = notesInput,
                        category = categorySelected,
                        onNameChange = { customNameInput = it },
                        onNotesChange = { notesInput = it },
                        onCategoryChange = { categorySelected = it },
                        onSave = {
                            coroutineScope.launch {
                                deviceRepository.saveBookmark(
                                    deviceMacAddress,
                                    customNameInput,
                                    notesInput,
                                    categorySelected
                                )
                                Toast.makeText(context, "Note bookmarked securely!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClear = {
                            coroutineScope.launch {
                                deviceRepository.removeBookmark(deviceMacAddress)
                                customNameInput = activeDevice?.name ?: ""
                                notesInput = ""
                                categorySelected = "Other"
                                Toast.makeText(context, "Cleared databases record!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 4. GATT Services & Characteristics
                item {
                    Text(
                        text = "SERVICES & CHARACTERISTICS",
                        color = KineticTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (services.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, KineticBorderSoft)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(color = KineticAccentBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Discovering device capabilities...", color = KineticTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else {
                    items(services) { service ->
                        GattServiceSectionCard(service = service)
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun DetailHeader(
    deviceName: String,
    macAddress: String,
    onBack: () -> Unit
) {
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
                text = deviceName,
                color = KineticTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = macAddress,
                color = KineticTextSecondary,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ConnectionStatsCard(
    device: BleDevice?,
    connectedDevice: BleDevice?
) {
    val isConnected = connectedDevice != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) KineticAccentGreenTint else Color(0x1AFF5F57))
                        .border(1.dp, if (isConnected) KineticAccentGreen.copy(alpha = 0.3f) else Color(0x4DFF5F57), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                        contentDescription = "Status",
                        tint = if (isConnected) KineticAccentGreen else Color(0xFFFF5F57),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isConnected) "CONNECTED" else "CONNECTING...",
                        color = if (isConnected) KineticAccentGreen else Color(0xFFFF5F57),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isConnected) "Connected to GATT Server" else "Reading BLE services...",
                        color = KineticTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (device != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${device.rssi} dBm",
                        color = KineticTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "TX: ${device.txPower} dBm",
                        color = KineticTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RssiSignalPathGraph(
    signalHistory: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "SIGNAL STRENGTH HISTORY",
                color = KineticTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas drawing signal history path
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(KineticGlassMd, RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, KineticBorderSubtle), RoundedCornerShape(8.dp))
            ) {
                if (signalHistory.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height

                // Draw coordinate scale lines
                for (k in 1..3) {
                    val y = height * (k / 4f)
                    drawLine(
                        color = KineticTextSecondary.copy(alpha = 0.04f),
                        start = Offset(0f, y),
                        end = Offset(width, y)
                    )
                }

                val maxItems = 10
                val items = signalHistory.takeLast(maxItems)
                val stepX = width / (maxItems - 1)

                // Render RSSI (-100 to -40) relative height
                fun getCoordinateY(rssi: Int): Float {
                    val fraction = ((rssi + 100) / 60.0f).coerceIn(0f, 1f)
                    return height * (1.0f - fraction)
                }

                val path = Path()
                items.forEachIndexed { idx, rssi ->
                    val x = idx * stepX
                    val y = getCoordinateY(rssi)
                    if (idx == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Smooth gradient fill below coordinate path line
                val fillPath = Path().apply {
                    addPath(path)
                    // close the path loop
                    lineTo((items.size - 1) * stepX, height)
                    lineTo(0f, height)
                    close()
                }

                // Draw gradient under trace
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(KineticAccentBlue.copy(alpha = 0.18f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Highlight trace path line
                drawPath(
                    path = path,
                    color = KineticAccentBlue,
                    style = Stroke(width = 3f)
                )

                // Draw active terminal node dots
                items.forEachIndexed { idx, rssi ->
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(idx * stepX, getCoordinateY(rssi))
                    )
                }
            }
        }
    }
}

@Composable
fun NotesPersistenceCard(
    customName: String,
    notes: String,
    category: String,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DEVICE ALIAS & NOTES",
                color = KineticTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = customName,
                onValueChange = onNameChange,
                label = { Text("Device Alias / Custom Name") },
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

            // Category select row
            Text("Category Classification", color = KineticTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            val categories = listOf("Tracker", "SmartHome", "Sensor", "Audio", "Beacon")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == category
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) KineticSurfaceTabActive else KineticSurfaceGhost)
                            .border(1.dp, if (isSelected) KineticAccentBlue else KineticBorderSoft, RoundedCornerShape(10.dp))
                            .clickable { onCategoryChange(cat) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) KineticAccentBlue else KineticTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Add comments or descriptions...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KineticAccentGreenTint,
                        contentColor = KineticAccentGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KineticAccentGreen.copy(alpha = 0.3f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE ALIAS", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x1AFF5F57),
                        contentColor = Color(0xFFFF5F57)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5F57).copy(alpha = 0.3f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CLEAR", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GattServiceSectionCard(service: GattService) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KineticGlassBase),
        border = BorderStroke(1.dp, KineticBorderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = KineticAccentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = service.name,
                    color = KineticTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            for (char in service.characteristics) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(KineticGlassMd, RoundedCornerShape(10.dp))
                        .border(1.dp, KineticBorderSoft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = char.uuidName, color = KineticTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(text = "READ, NOTIFY", color = KineticTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = char.value,
                        color = KineticAccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
