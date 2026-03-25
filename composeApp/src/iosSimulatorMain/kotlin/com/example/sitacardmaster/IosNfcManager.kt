package com.example.sitacardmaster

import androidx.compose.runtime.*

class IosNfcManager : NfcManager {
    override val detectedTag: State<Any?> = mutableStateOf(null)
    override val detectedTagId: State<String?> = mutableStateOf(null)
    override val isMultipleTagsDetected: State<Boolean> = mutableStateOf(false)

    override fun startScanning() {
        println("NFC Scanning not supported on Simulator")
    }

    override fun stopScanning() {
        // No-op
    }

    override fun writeCard(
        memberId: String,
        companyName: String,
        password: String,
        validUpto: String,
        totalBuy: String,
        cardType: String,
        onResult: (Boolean, String) -> Unit
    ) {
        onResult(false, "NFC not supported on Simulator")
    }

    override fun writeLogoUrl(url: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "NFC not supported on Simulator")
    }

    override fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit) {
        onResult(false, null, "NFC not supported on Simulator")
    }

    override fun clearCard(onResult: (Boolean, String) -> Unit) {
        onResult(false, "NFC not supported on Simulator")
    }

    override fun deleteCardData(onResult: (Boolean, String) -> Unit) {
        onResult(false, "NFC not supported on Simulator")
    }
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    return remember { IosNfcManager() }
}
