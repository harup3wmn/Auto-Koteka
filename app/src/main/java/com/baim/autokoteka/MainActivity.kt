package com.baim.autokoteka

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoKotekaApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Coba hidupkan ulang NotificationListenerService secara paksa jika mati setelah update APK atau di-kill system
        try {
            val componentName = android.content.ComponentName(this, NotificationService::class.java)
            val pm = packageManager
            // Hack untuk merestart service listener
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_COPY_TEXT") {
            val textToCopy = intent.getStringExtra("EXTRA_TEXT")
            if (!textToCopy.isNullOrEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Koteka Report", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Teks Laporan Berhasil Di-copy!", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoKotekaApp() {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val wamenaBulan by dataStoreManager.wamenaBulanIniFlow.collectAsState(initial = 21)
    val wamenaTahun by dataStoreManager.wamenaTahunIniFlow.collectAsState(initial = 294)
    val yalimoBulan by dataStoreManager.yalimoBulanIniFlow.collectAsState(initial = 0)
    val yalimoTahun by dataStoreManager.yalimoTahunIniFlow.collectAsState(initial = 0)
    val targetBulanan by dataStoreManager.targetBulananFlow.collectAsState(initial = 80)
    
    val latestReport by dataStoreManager.latestReportFlow.collectAsState(initial = "")
    val logHistory by dataStoreManager.logHistoryFlow.collectAsState(initial = emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    var logFilter by remember { mutableStateOf("Semua") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Auto Koteka",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PermissionCheckSection(context)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Laporan Terakhir",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (latestReport.isEmpty()) {
                    Text("Belum ada laporan yang diekstrak.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = latestReport,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Koteka Report", latestReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Berhasil di-copy!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tampilan Data Akumulasi
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Wamena Kota", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: $wamenaBulan | Tahun Ini: $wamenaTahun")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Yalimo", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: $yalimoBulan | Tahun Ini: $yalimoTahun")
                Spacer(modifier = Modifier.height(8.dp))
                Text("UP3 Wamena (Total)", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: ${wamenaBulan + yalimoBulan} | Tahun Ini: ${wamenaTahun + yalimoTahun}")
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Target", fontWeight = FontWeight.Bold)
                Text("Bulanan: $targetBulanan | Tahunan: ${targetBulanan * 12}")
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Data Akumulasi")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Input Manual Laporan
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Proses Laporan Manual", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualInputText,
                    onValueChange = { manualInputText = it },
                    label = { Text("Paste Laporan WA di sini") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val parsedData = ReportParser.parseMessage(manualInputText)
                            if (parsedData != null) {
                                val latestRaw = dataStoreManager.latestRawTextFlow.first()
                                val validation = ReportParser.validateReport(parsedData, manualInputText, latestRaw)
                                
                                val currentTime = System.currentTimeMillis()
                                val entry = LogEntry(
                                    id = currentTime,
                                    timestamp = currentTime,
                                    rawText = manualInputText,
                                    isAnomaly = !validation.isValid,
                                    anomalyReason = validation.reason,
                                    status = if (validation.isValid) "PROCESSED" else "PENDING"
                                )
                                dataStoreManager.addLogEntry(entry)
                                
                                if (validation.isValid) {
                                    dataStoreManager.setLatestRawText(manualInputText)
                                    dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                    
                                    val wh = dataStoreManager.wamenaHariIniFlow.first()
                                    val wb = dataStoreManager.wamenaBulanIniFlow.first()
                                    val wt = dataStoreManager.wamenaTahunIniFlow.first()
                                    val yh = dataStoreManager.yalimoHariIniFlow.first()
                                    val yb = dataStoreManager.yalimoBulanIniFlow.first()
                                    val yt = dataStoreManager.yalimoTahunIniFlow.first()
                                    val tb = dataStoreManager.targetBulananFlow.first()
                                    
                                    val finalReport = ReportParser.formatReport(parsedData, wh, wb, wt, yh, yb, yt, tb)
                                    dataStoreManager.saveLatestReport(finalReport)
                                    
                                    Toast.makeText(context, "Berhasil Diproses!", Toast.LENGTH_SHORT).show()
                                    manualInputText = ""
                                } else {
                                    Toast.makeText(context, "Terdeteksi Anomali. Cek Buku Log!", Toast.LENGTH_LONG).show()
                                    manualInputText = ""
                                }
                            } else {
                                Toast.makeText(context, "Format tidak dikenali!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Proses Manual")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Buku Log (Otomatis Hapus 3 Hari)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = logFilter == "Semua",
                onClick = { logFilter = "Semua" },
                label = { Text("Semua") }
            )
            FilterChip(
                selected = logFilter == "Menunggu",
                onClick = { logFilter = "Menunggu" },
                label = { Text("Menunggu Respon") }
            )
            FilterChip(
                selected = logFilter == "Selesai",
                onClick = { logFilter = "Selesai" },
                label = { Text("Selesai") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        val filteredLogs = logHistory.filter { entry ->
            when (logFilter) {
                "Menunggu" -> entry.status == "PENDING"
                "Selesai" -> entry.status != "PENDING"
                else -> true
            }
        }

        if (filteredLogs.isEmpty()) {
            Text("Belum ada riwayat laporan.", style = MaterialTheme.typography.bodyMedium)
        } else {
            filteredLogs.forEach { entry ->
                LogEntryCard(
                    entry = entry, 
                    dataStoreManager = dataStoreManager, 
                    context = context, 
                    coroutineScope = coroutineScope
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showEditDialog) {
        var editWamenaBulan by remember { mutableStateOf(wamenaBulan.toString()) }
        var editWamenaTahun by remember { mutableStateOf(wamenaTahun.toString()) }
        var editYalimoBulan by remember { mutableStateOf(yalimoBulan.toString()) }
        var editYalimoTahun by remember { mutableStateOf(yalimoTahun.toString()) }
        var editTargetBulanan by remember { mutableStateOf(targetBulanan.toString()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Akumulasi") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Wamena Kota", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editWamenaBulan,
                        onValueChange = { editWamenaBulan = it },
                        label = { Text("Bulan Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editWamenaTahun,
                        onValueChange = { editWamenaTahun = it },
                        label = { Text("Tahun Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Yalimo", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editYalimoBulan,
                        onValueChange = { editYalimoBulan = it },
                        label = { Text("Bulan Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editYalimoTahun,
                        onValueChange = { editYalimoTahun = it },
                        label = { Text("Tahun Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Target", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTargetBulanan,
                        onValueChange = { editTargetBulanan = it },
                        label = { Text("Target Bulanan (Pohon)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val wb = editWamenaBulan.toIntOrNull() ?: wamenaBulan
                            val wt = editWamenaTahun.toIntOrNull() ?: wamenaTahun
                            val yb = editYalimoBulan.toIntOrNull() ?: yalimoBulan
                            val yt = editYalimoTahun.toIntOrNull() ?: yalimoTahun
                            val tb = editTargetBulanan.toIntOrNull() ?: targetBulanan
                            
                            dataStoreManager.updateDataWamena(wb, wt)
                            dataStoreManager.updateDataYalimo(yb, yt)
                            dataStoreManager.updateTargetBulanan(tb)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun PermissionCheckSection(context: Context) {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    val isGranted = enabledListeners != null && enabledListeners.contains(packageName)

    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Izin Notifikasi Belum Aktif",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aplikasi butuh akses untuk membaca notifikasi WhatsApp.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    ) {
                        Text("Buka Pengaturan")
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Izin Notifikasi Aktif ✓",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (!isIgnoringBattery) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Awas Aplikasi Tertidur!",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sistem HP Anda mungkin mematikan aplikasi secara diam-diam. Harap nonaktifkan optimalisasi baterai untuk aplikasi ini.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Izinkan Berjalan Penuh")
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry, 
    dataStoreManager: DataStoreManager, 
    context: Context, 
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val dateString = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(entry.timestamp))

    val (containerColor, contentColor) = when {
        entry.status == "PENDING" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        entry.status == "PROCESSED" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateString, style = MaterialTheme.typography.labelSmall, color = contentColor)
                Text(entry.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = contentColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (entry.isAnomaly || entry.anomalyReason.isNotEmpty()) {
                val prefix = if (entry.isAnomaly) "⚠️ Anomali: " else "⏳ "
                Text(
                    text = "$prefix${entry.anomalyReason}", 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = contentColor)

            if (entry.status == "PENDING") {
                Spacer(modifier = Modifier.height(16.dp))
                if (entry.anomalyReason.contains("mirip", ignoreCase = true)) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val parsedData = ReportParser.parseMessage(entry.rawText)
                                        val latestRawText = dataStoreManager.latestRawTextFlow.first()
                                        val oldParsedData = ReportParser.parseMessage(latestRawText)
                                        
                                        if (parsedData != null && oldParsedData != null) {
                                            dataStoreManager.subtractAccumulation(oldParsedData.tHariIni, oldParsedData.isYalimo)
                                            dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                            dataStoreManager.setLatestRawText(entry.rawText)
                                            
                                            val wamenaHari = dataStoreManager.wamenaHariIniFlow.first()
                                            val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                                            val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                                            val yalimoHari = dataStoreManager.yalimoHariIniFlow.first()
                                            val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                                            val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                                            val targetBln = dataStoreManager.targetBulananFlow.first()
                                            
                                            val finalReport = ReportParser.formatReport(
                                                parsedData, wamenaHari, wamenaBulan, wamenaTahun, yalimoHari, yalimoBulan, yalimoTahun, targetBln
                                            )
                                            dataStoreManager.saveLatestReport(finalReport)
                                        }
                                        dataStoreManager.updateLogStatus(entry.id, "APPROVED")
                                        Toast.makeText(context, "Laporan Lama Diganti!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ganti Lama", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val parsedData = ReportParser.parseMessage(entry.rawText)
                                        if (parsedData != null) {
                                            dataStoreManager.setLatestRawText(entry.rawText)
                                            dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                            
                                            val wamenaHari = dataStoreManager.wamenaHariIniFlow.first()
                                            val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                                            val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                                            val yalimoHari = dataStoreManager.yalimoHariIniFlow.first()
                                            val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                                            val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                                            val targetBln = dataStoreManager.targetBulananFlow.first()
                                            
                                            val finalReport = ReportParser.formatReport(
                                                parsedData, wamenaHari, wamenaBulan, wamenaTahun, yalimoHari, yalimoBulan, yalimoTahun, targetBln
                                            )
                                            dataStoreManager.saveLatestReport(finalReport)
                                        }
                                        dataStoreManager.updateLogStatus(entry.id, "APPROVED")
                                        Toast.makeText(context, "Laporan Baru Ditambahkan!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Tambahkan Baru", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        dataStoreManager.updateLogStatus(entry.id, "REJECTED")
                                        Toast.makeText(context, "Laporan Ditolak!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Tolak", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    dataStoreManager.updateLogStatus(entry.id, "REJECTED")
                                    Toast.makeText(context, "Laporan Ditolak", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Tolak / Abaikan")
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    dataStoreManager.updateLogStatus(entry.id, "REJECTED")
                                    Toast.makeText(context, "Laporan Ditolak", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Tolak")
                        }
                        
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val parsedData = ReportParser.parseMessage(entry.rawText)
                                    if (parsedData != null) {
                                        dataStoreManager.setLatestRawText(entry.rawText)
                                        dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                        
                                        val wamenaHari = dataStoreManager.wamenaHariIniFlow.first()
                                        val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                                        val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                                        val yalimoHari = dataStoreManager.yalimoHariIniFlow.first()
                                        val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                                        val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                                        val targetBln = dataStoreManager.targetBulananFlow.first()
                                        
                                        val finalReport = ReportParser.formatReport(
                                            parsedData, wamenaHari, wamenaBulan, wamenaTahun, yalimoHari, yalimoBulan, yalimoTahun, targetBln
                                        )
                                        dataStoreManager.saveLatestReport(finalReport)
                                    }
                                    dataStoreManager.updateLogStatus(entry.id, "APPROVED")
                                    Toast.makeText(context, "Laporan Disetujui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Setujui")
                        }
                    }
                }
            }
        }
    }
}
