package com.baim.autokoteka

import android.util.Log

object ReportParser {

    private val regexTanggal = Regex("(?i)(?:hari|tanggal|𝙃𝘼𝙍𝙄|𝙏𝘼𝙉𝙂𝙂𝘼𝙇)[^\\n]*?[=:]\\s*([^\\n]+)")
    private val regexTHariIni = Regex("(?i)(?:perolehan|total|jumlah)\\s+penebangan[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPK = Regex("(?i)1\\.[^\\n]*?5\\s*-\\s*20[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPS = Regex("(?i)2\\.[^\\n]*?20\\s*-\\s*30[^\\n]*?[=:]?[ \\t]*(\\d+)")
    private val regexPB1 = Regex("(?i)3\\.[^\\n]*?30\\s*-\\s*50[^\\n]*?[=:]?[ \\t]*(\\d+)")
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
        val matchTHariIni = regexTHariIni.find(message)
        
        if (matchTanggal == null || matchTHariIni == null) {
            Log.d("ReportParser", "Failed to parse required fields (Tanggal / T_Hari_Ini)")
            return null
        }

        val tanggal = matchTanggal.groupValues[1].trim()
        val tHariIni = matchTHariIni.groupValues[1].toIntOrNull() ?: 0

        val pk = regexPK.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ps = regexPS.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb1 = regexPB1.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb2 = regexPB2.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val pb = pb1 + pb2
        
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
        wamenaBulan: Int, 
        wamenaTahun: Int,
        yalimoBulan: Int,
        yalimoTahun: Int
    ): String {
        
        // UP3 Wamena = Total Gabungan
        val totalUP3Bulan = wamenaBulan + yalimoBulan
        val totalUP3Tahun = wamenaTahun + yalimoTahun
        
        // Harian UP3 = Hari ini Yalimo ATAU Wamena (karena dilaporkan salah satu)
        val tHariIniUP3 = data.tHariIni
        
        // Logika tampilan berdasar ULP mana yang update
        val wamenaHariIni = if (!data.isYalimo) data.tHariIni else 0
        val wamenaPK = if (!data.isYalimo) data.pk else 0
        val wamenaPS = if (!data.isYalimo) data.ps else 0
        val wamenaPB = if (!data.isYalimo) data.pb else 0
        
        val yalimoHariIni = if (data.isYalimo) data.tHariIni else 0
        val yalimoPK = if (data.isYalimo) data.pk else 0
        val yalimoPS = if (data.isYalimo) data.ps else 0
        val yalimoPB = if (data.isYalimo) data.pb else 0

        return """
*Laporan Realisasi Tebang Program KOTEKA 2026 UP3 Wamena*
Hari / ${data.tanggal}

UP3 Wamena : PK ${data.pk} btg / PS ${data.ps} btg / PB ${data.pb} btg / T. Hari ini $tHariIniUP3 btg / T. Bulan Ini $totalUP3Bulan btg / T. s.d. hari ini $totalUP3Tahun btg

Perhitungan Realisasi mulai dihitung sejak 1 Januari 2026, semua foto penebangan difoto (1 foto untuk 1 titik)

1. ULP Wamena Kota : PK $wamenaPK btg / PS $wamenaPS btg / PB $wamenaPB btg / T. Hari ini $wamenaHariIni btg / T. Bulan Ini $wamenaBulan btg / T. s.d. hari ini $wamenaTahun btg
- PT Nusa Daya : PK $wamenaPK btg / PS $wamenaPS btg / PB $wamenaPB btg / T. Hari ini $wamenaHariIni btg / T. Bulan Ini $wamenaBulan btg / T. s.d. hari ini $wamenaTahun btg

2. ULP Yalimo : PK $yalimoPK btg / PS $yalimoPS btg / PB $yalimoPB btg / T. Hari ini $yalimoHariIni btg / T. Bulan Ini $yalimoBulan btg / T. s.d. hari ini $yalimoTahun btg
* PT  : PK $yalimoPK btg / PS $yalimoPS btg / PB $yalimoPB btg / T. Hari ini $yalimoHariIni btg / T. Bulan Ini $yalimoBulan btg / T. s.d. hari ini $yalimoTahun btg

Ket : 
PK : Pohon Kecil
PS : Pohon Sedang
PB : Pohon Besar
T   : Total
""".trimIndent()
    }
}
