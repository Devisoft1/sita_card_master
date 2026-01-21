package com.example.sitacardmaster

import androidx.compose.runtime.mutableStateListOf

val actionLogs = mutableStateListOf<String>()

expect fun currentTime(): String

fun logAction(message: String) {
    val fullLog = "${currentTime()} - $message"
    actionLogs.add(fullLog)
    platformLog("SITACardMaster", fullLog)
}
