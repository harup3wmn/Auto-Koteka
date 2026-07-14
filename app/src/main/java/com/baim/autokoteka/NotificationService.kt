package com.baim.autokoteka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val processedPostTimes = mutableSetOf<Long>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        if (packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (text.contains("LAPORAN KEGIATAN PEKERJAAN TEAM ROW", ignoreCase = true) ||
            text.contains("PEROLEHAN PENEBANGAN SEJUMLAH", ignoreCase = true) ||
            text.contains("REALISASI PEKERJAAN ROW", ignoreCase = true)) {
            
            val postTime = sbn.postTime
            
            // Cegah ghost/unread notifications: Cek apakah ID waktu ini sudah pernah diproses
            synchronized(processedPostTimes) {
                if (processedPostTimes.contains(postTime)) {
                    return
                }
                
                processedPostTimes.add(postTime)
                // Jaga agar memory tidak bengkak (maksimal simpan 50 ID terakhir)
                if (processedPostTimes.size > 50) {
                    val iterator = processedPostTimes.iterator()
                    iterator.next()
                    iterator.remove()
                }
            }

            Log.d("NotificationService", "Pesan laporan terdeteksi: $text")
            processReport(text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    private fun processReport(message: String) {
        serviceScope.launch {
            val parsedData = ReportParser.parseMessage(message)
            if (parsedData != null) {
                val dataStoreManager = DataStoreManager(applicationContext)
                
                val latestRawText = dataStoreManager.latestRawTextFlow.first()
                val validation = ReportParser.validateReport(parsedData, message, latestRawText)

                val currentTime = System.currentTimeMillis()

                if (!validation.isValid) {
                    // Masukkan ke karantina log
                    val entry = LogEntry(
                        id = currentTime,
                        timestamp = currentTime,
                        rawText = message,
                        isAnomaly = true,
                        anomalyReason = validation.reason,
                        status = "PENDING"
                    )
                    dataStoreManager.addLogEntry(entry)
                    showWarningNotification()
                    return@launch
                }
                
                // Lulus Validasi -> Eksekusi normal
                val normalEntry = LogEntry(
                    id = currentTime,
                    timestamp = currentTime,
                    rawText = message,
                    isAnomaly = false,
                    anomalyReason = "",
                    status = "PROCESSED"
                )
                dataStoreManager.addLogEntry(normalEntry)
                dataStoreManager.setLatestRawText(message)
                dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)

                // Ambil nilai terbaru untuk ke-4 keranjang
                val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                val targetBulanan = dataStoreManager.targetBulananFlow.first()

                // Buat format report final
                val finalReport = ReportParser.formatReport(
                    parsedData, 
                    wamenaBulan, wamenaTahun, 
                    yalimoBulan, yalimoTahun,
                    targetBulanan
                )
                
                dataStoreManager.saveLatestReport(finalReport)
                showCopyNotification(finalReport)
            }
        }
    }

    private fun showWarningNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_koteka_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Koteka Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Laporan Tertahan!")
            .setContentText("Ditemukan keanehan pada laporan. Klik untuk meninjau.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(888, notification) // ID unik untuk warning
    }

    private fun showCopyNotification(reportText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_koteka_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Koteka Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val copyIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_COPY_TEXT"
            putExtra("EXTRA_TEXT", reportText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Laporan Baru Siap!")
            .setContentText("Klik di sini untuk meng-copy teks ke Clipboard.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
