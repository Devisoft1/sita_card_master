package com.example.sitacardmaster

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform