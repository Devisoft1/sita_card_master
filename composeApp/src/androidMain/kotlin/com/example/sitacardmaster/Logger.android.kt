package com.example.sitacardmaster

import android.util.Log

actual fun platformLog(tag: String, message: String) {
    Log.i(tag, message)
}

actual fun currentTime(): String {
    val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}
