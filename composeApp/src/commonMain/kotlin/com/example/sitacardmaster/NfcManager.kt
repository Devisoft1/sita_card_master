package com.example.sitacardmaster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

interface NfcManager {
    fun startScanning()
    fun stopScanning()
    val detectedTag: State<Any?>
    val detectedTagId: State<String?>
    val isMultipleTagsDetected: State<Boolean>
    fun writeCard(
        memberId: String,
        companyName: String,
        password: String,
        validUpto: String,
        totalBuy: String,
        cardType: String,
        onResult: (Boolean, String) -> Unit
    )
    fun writeLogoUrl(url: String, onResult: (Boolean, String) -> Unit)
    fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit)
    fun clearCard(onResult: (Boolean, String) -> Unit)
    fun deleteCardData(onResult: (Boolean, String) -> Unit)
    fun clearScanData()
}

@Composable
expect fun rememberNfcManager(): NfcManager
