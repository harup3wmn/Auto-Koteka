package com.baim.autokoteka

import android.util.Log

object ReportParser {

    private val regexTanggal = Regex("(?i)(?:hari|tanggal|𝙃𝘼𝙍𝙄|𝙏𝘼𝙉𝙂𝙂𝘼𝙇)[^\\n]*?[=:]\\s*([^\\n]+)")
    private val regexTHariIni = Regex("(?i)(?:perolehan|total|jumlah)\\s+penebangan[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPK = Regex("(?i)(?:1\\.[^\\n]*?5\\s*-\\s*20|pohon\\s+kecil)[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPS = Regex("(?i)(?:2\\.[^\\n]*?20\\s*-\\s*30|pohon\\s+sedang)[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPB1 = Regex("(?i)(?:3\\.[^\\n]*?30\\s*-\\s*50|pohon\\s+besar)[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPB2 = Regex("(?i)4\\.[^\\n]*?50\\s*>[^\\n]*?[=:]?[ \\t]*(\\d+)")

    data class ParsedData(
        val isYalimo: Boolean,
        val tanggal: String,
        val tHariIni: Int,
        val pk: Int,
        val ps: Int,
        val pb: Int
    )

    fun parseMessage(message: String): ParsedData? {
        Log.d("ReportParser", "Parsing message: $message")
        
        val matchTanggal = regexTanggal.find(message)
        var tanggal = matchTanggal?.groupValues?.getOrNull(1)?.trim()
        
        if (tanggal.isNullOrEmpty()) {
            val fallbackMatch = Regex("(?i)(Senin|Selasa|Rabu|Kamis|Jumat|Sabtu|Minggu)[^\\n]*\\d+\\s+[a-zA-Z]+\\s+20\\d{2}").find(message)
            tanggal = fallbackMatch?.value?.trim()
        }
        
        if (tanggal.isNullOrEmpty()) {
            Log.d("ReportParser", "Failed to parse required field (Tanggal)")
            return null
        }

        val pk = regexPK.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ps = regexPS.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb1 = regexPB1.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb2 = regexPB2.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb = pb1 + pb2

        val matchTHariIni = regexTHariIni.find(message)
        val tHariIni = matchTHariIni?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (pk + ps + pb)
        
        val isYalimo = message.contains("elelim", ignoreCase = true) || message.contains("yalimo", ignoreCase = true)

        return ParsedData(isYalimo, tanggal, tHariIni, pk, ps, pb)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String = ""
    )

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 100.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val costs = IntArray(s2.length + 1)
        for (j in 0..s2.length) {
            costs[j] = j
        }
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..s2.length) {
                val cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), if (s1[i - 1] == s2[j - 1]) nw else nw + 1)
                nw = costs[j]
                costs[j] = cj
            }
        }
        val distance = costs[s2.length]
        val maxLength = Math.max(s1.length, s2.length)
        return (1.0 - distance.toDouble() / maxLength) * 100
    }

    fun validateReport(data: ParsedData, rawText: String, latestRawText: String): ValidationResult {
        // 1. Validasi Matematika (Apakah penjabaran PK+PS+PB sesuai dengan Total Harian)
        val sum = data.pk + data.ps + data.pb
        if (sum != data.tHariIni) {
            return ValidationResult(
                isValid = false,
                reason = "Jumlah PK(${data.pk}) + PS(${data.ps}) + PB(${data.pb}) = $sum. Namun tertulis Total Harian = ${data.tHariIni}."
            )
        }

        // 2. Validasi Pesan Diedit / Kemiripan
        if (latestRawText.isNotEmpty()) {
            val similarity = calculateSimilarity(rawText, latestRawText)
            if (similarity >= 90.0) {
                return ValidationResult(
                    isValid = false,
                    reason = "Laporan ini ${similarity.toInt()}% mirip dengan laporan terakhir. Indikasi pesan diedit atau dikirim ulang."
                )
            }
        }

        return ValidationResult(isValid = true)
    }

    fun formatReport(
        data: ParsedData, 
        wamenaHari: Int,
        wamenaBulan: Int, 
        wamenaTahun: Int,
        yalimoHari: Int,
        yalimoBulan: Int,
        yalimoTahun: Int,
        pkHari: Int,
        psHari: Int,
        pbHari: Int,
        targetBulanan: Int
    ): String {
        
        // UP3 Wamena = Total Gabungan
        val totalUP3Bulan = wamenaBulan + yalimoBulan
        val totalUP3Tahun = wamenaTahun + yalimoTahun
        
        val totalHariIniWamena = wamenaHari
        val totalHariIniYalimo = yalimoHari
        val totalUP3HariIni = wamenaHari + yalimoHari
        
        val targetTahunan = targetBulanan * 12
        val pctBulan = if (targetBulanan > 0) (totalUP3Bulan.toDouble() / targetBulanan) * 100 else 0.0
        val pctTahun = if (targetTahunan > 0) (totalUP3Tahun.toDouble() / targetTahunan) * 100 else 0.0

        return """
            *Laporan KOTEKA UP3 Wamena*
            📅 Tanggal: ${data.tanggal}

            *Total Pencapaian UP3 Wamena*
            • Hari ini: $totalUP3HariIni Pohon (PK:$pkHari, PS:$psHari, PB:$pbHari)
            • Bulan ini: $totalUP3Bulan Pohon
            • Tahun ini: $totalUP3Tahun Pohon

            *Target vs Realisasi*
            🎯 Bulanan: $totalUP3Bulan / $targetBulanan Pohon (${String.format("%.1f", pctBulan)}%)
            🎯 Tahunan: $totalUP3Tahun / $targetTahunan Pohon (${String.format("%.1f", pctTahun)}%)

            *Rincian per ULP*
            1. Wamena Kota (PT Nusa Daya)
               Hari ini: $totalHariIniWamena | Bulan ini: $wamenaBulan | Tahun ini: $wamenaTahun
            2. Yalimo
               Hari ini: $totalHariIniYalimo | Bulan ini: $yalimoBulan | Tahun ini: $yalimoTahun
               
            Ket :
            PK : Pohon Kecil
            PS : Pohon Sedang
            PB : Pohon Besar
        """.trimIndent()
    }
}
