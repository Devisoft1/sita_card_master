package com.example.sitacardmaster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

interface NfcManager {
    fun startScanning()
    fun stopScanning()
    val detectedTag: State<Any?>
    fun writeCard(
        memberId: String,
        companyName: String,
        password: String,
        validUpto: String,
        totalBuy: String,
        cardType: String,
        onResult: (Boolean, String) -> Unit
    )
    fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit)
    fun clearCard(onResult: (Boolean, String) -> Unit)
}

@Composable
expect fun rememberNfcManager(): NfcManager
