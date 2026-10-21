package com.phantom.tube.core.util

import java.util.Locale

data class RegionOption(
    val code: String,
    val name: String,
    val flag: String
)

object RegionHelper {

    val SUPPORTED_REGIONS = listOf(
        RegionOption("ID", "Indonesia", "ID"),
        RegionOption("GLOBAL", "Global / Internasional", "GL"),
        RegionOption("US", "United States", "US"),
        RegionOption("JP", "Japan", "JP"),
        RegionOption("KR", "South Korea", "KR"),
        RegionOption("GB", "United Kingdom", "GB"),
        RegionOption("IN", "India", "IN"),
        RegionOption("BR", "Brazil", "BR"),
        RegionOption("DE", "Germany", "DE"),
        RegionOption("FR", "France", "FR"),
        RegionOption("RU", "Russia", "RU"),
        RegionOption("ES", "Spain", "ES"),
        RegionOption("CA", "Canada", "CA"),
        RegionOption("AU", "Australia", "AU"),
        RegionOption("SG", "Singapore", "SG"),
        RegionOption("MY", "Malaysia", "MY")
    )

    val SUPPORTED_REGION_CODES = SUPPORTED_REGIONS.map { it.code }.toSet()

    /**
     * Otomatis deteksi wilayah / negara sistem Android user pada first install.
     * Jika negara pengguna tidak ada di daftar pilihan terdaftar, otomatis fallback ke "GLOBAL" (Internasional).
     */
    fun resolveDefaultCountry(): String {
        return try {
            val systemCountry = Locale.getDefault().country.uppercase()
            if (systemCountry.isNotBlank() && systemCountry in SUPPORTED_REGION_CODES) {
                systemCountry
            } else {
                "GLOBAL" // Otomatis fallback ke Global / Internasional bila tidak terdaftar
            }
        } catch (_: Exception) {
            "GLOBAL"
        }
    }
}
