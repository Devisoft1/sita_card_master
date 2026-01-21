package com.example.sitacardmaster

import androidx.compose.runtime.*
import platform.CoreNFC.*
import platform.Foundation.*
import platform.darwin.NSObject

class IosNfcManager : NfcManager {
    private var session: NFCNDEFReaderSession? = null
    private val delegate = IosNfcDelegate()
    
    override val detectedTag: State<Any?> = delegate.detectedTag

    override fun startScanning() {
        session = NFCNDEFReaderSession(
            delegate = delegate,
            queue = null,
            invalidateAfterFirstRead = false
        )
        session?.alertMessage = "Hold your iPhone near the card."
        session?.beginSession()
    }

    override fun stopScanning() {
        session?.invalidateSession()
    }

    override fun writeCard(
        memberId: String,
        companyName: String,
        validUpto: String,
        totalBuy: String,
        onResult: (Boolean, String) -> Unit
    ) {
        onResult(false, "NFC Writing is currently limited on iOS. NDEF is supported.")
    }

    override fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit) {
        onResult(false, null, "Please tap the card to read.")
    }
}

private class IosNfcDelegate : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
    val detectedTag: MutableState<Any?> = mutableStateOf(null)

    override fun readerSession(session: NFCNDEFReaderSession, didInvalidateWithError: NSError) {
        platformLog("NFC", "Session invalidated: ${didInvalidateWithError.localizedDescription}")
    }

    override fun readerSession(session: NFCNDEFReaderSession, didDetectNDEFs: List<*>) {
        platformLog("NFC", "Detected NDEF messages")
    }
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    val manager = remember { IosNfcManager() }
    DisposableEffect(Unit) {
        onDispose {
            manager.stopScanning()
        }
    }
    return manager
}
