package com.example.sitacardmaster.screens

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate

fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "N/A"
    try {
        // Handle ISO-8601 or space-separated date/time strings
        val datePart = dateStr.substringBefore("T").substringBefore(" ")
        
        if (datePart.contains("-")) {
            val parts = datePart.split("-")
            if (parts.size == 3) {
                // Check if YYYY-MM-DD or DD-MM-YYYY
                return if (parts[0].length == 4) "${parts[2]}/${parts[1]}/${parts[0]}"
                else "${parts[0]}/${parts[1]}/${parts[2]}"
            }
        } else if (datePart.contains("/")) {
            val parts = datePart.split("/")
            if (parts.size == 3) {
                return if (parts[0].length == 4) "${parts[2]}/${parts[1]}/${parts[0]}"
                else "${parts[0]}/${parts[1]}/${parts[2]}"
            }
        }
    } catch (_: Exception) {}
    return dateStr
}

fun isValidityExpired(dateString: String): Boolean {
    if (dateString.isBlank() || dateString == "---" || dateString == "N/A") return false
    return try {
        val parts = dateString.split("/")
        if (parts.size != 3) return false
        val day = parts[0].toInt()
        val month = parts[1].toInt()
        val year = parts[2].toInt()
        
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(com.example.sitacardmaster.getCurrentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val validity = LocalDate(year, month, day)
        validity < now
    } catch (_: Exception) {
        false
    }
}
