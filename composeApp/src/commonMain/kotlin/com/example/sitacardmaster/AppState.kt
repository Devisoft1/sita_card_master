package com.example.sitacardmaster

import androidx.compose.runtime.mutableStateListOf

val actionLogs = mutableStateListOf<String>()

fun logAction(message: String) {
    val fullLog = "${currentTime()} - $message"
    actionLogs.add(fullLog)
    platformLog("SITACardMaster", fullLog)
}

fun currentTime(): String {
    val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}
