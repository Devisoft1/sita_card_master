package com.example.sitacardmaster

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun getCurrentTimeMillis(): Long
expect fun isNetworkAvailable(): Boolean