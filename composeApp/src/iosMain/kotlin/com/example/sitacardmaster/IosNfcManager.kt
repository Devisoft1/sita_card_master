package com.example.sitacardmaster

import androidx.compose.runtime.*
import platform.CoreNFC.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosNfcManager : NfcManager {
    private var session: NFCNDEFReaderSession? = null
    // Hold a strong reference to the delegate so it isn't garbage collected
    private val delegate = IosNfcDelegate(this)
    
    override val detectedTag: State<Any?> = delegate.detectedTag

    // Callback storage for pending operations
    internal var onReadResult: ((Boolean, Map<String, String>?, String) -> Unit)? = null
    internal var onWriteResult: ((Boolean, String) -> Unit)? = null
    internal var onClearResult: ((Boolean, String) -> Unit)? = null
    
    // Data to write (if any)
    internal var pendingWriteData: Map<String, String>? = null

    override fun startScanning() {
        // Just generic scanning, no specific action pending
        cleanup()
        startSession()
    }

    override fun stopScanning() {
        session?.invalidateSession()
        cleanup()
    }
    
    private fun startSession() {
        session = NFCNDEFReaderSession(
            delegate = delegate,
            queue = null,
            invalidateAfterFirstRead = false
        )
        session?.alertMessage = "Hold your iPhone near the card."
        session?.beginSession()
    }

    private fun cleanup() {
        onReadResult = null
        onWriteResult = null
        onClearResult = null
        pendingWriteData = null
    }

    override fun writeCard(
        memberId: String,
        companyName: String,
        password: String,
        validUpto: String,
        totalBuy: String,
        onResult: (Boolean, String) -> Unit
    ) {
        cleanup()
        onWriteResult = onResult
        pendingWriteData = mapOf(
            "memberId" to memberId,
            "companyName" to companyName,
            "password" to password,
            "validUpto" to validUpto,
            "totalBuy" to totalBuy,
            "lastBuyDate" to "" // Optional or computed
        )
        // Re-start session if needed to ensure we capture a tag for writing
        startSession()
    }

    override fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit) {
        cleanup()
        onReadResult = onResult
        startSession()
    }

    override fun clearCard(onResult: (Boolean, String) -> Unit) {
        cleanup()
        onClearResult = onResult
        startSession()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNfcDelegate(private val manager: IosNfcManager) : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
    val detectedTag: MutableState<Any?> = mutableStateOf(null)

    // Handle session invalidation
    override fun readerSession(session: NFCNDEFReaderSession, didInvalidateWithError: NSError) {
        val errorMessage = didInvalidateWithError.localizedDescription
        
        // NFCReaderErrorReaderSessionInvalidationErrorUserCanceled = 200
        if (didInvalidateWithError.code != 200L && didInvalidateWithError.code != 201L) {
             manager.onReadResult?.invoke(false, null, errorMessage)
             manager.onWriteResult?.invoke(false, errorMessage)
             manager.onClearResult?.invoke(false, errorMessage)
        }
        
        detectedTag.value = null
    }

    // Resolve conflicting overloads with @ObjCSignatureOverride
    // Note: In some KMP versions, @Override might be strict.
    @ObjCSignatureOverride
    override fun readerSession(session: NFCNDEFReaderSession, didDetectNDEFs: List<*>) {
        val message = didDetectNDEFs.firstOrNull() as? NFCNDEFMessage
        if (message != null) {
            processRead(message)
        }
    }
    
    @ObjCSignatureOverride
    override fun readerSession(session: NFCNDEFReaderSession, didDetectTags: List<*>) {
        val tag = didDetectTags.firstOrNull() as? NFCNDEFTagProtocol ?: return
        
        session.connectToTag(tag) { error: NSError? ->
            if (error != null) {
                session.alertMessage = "Unable to connect to tag."
                session.invalidateSession()
                return@connectToTag
            }
            
            tag.queryNDEFStatusWithCompletionHandler { status: NFCNDEFStatus, capacity: ULong, error: NSError? ->
                if (error != null) {
                     session.invalidateSessionWithErrorMessage("Error querying NDEF status.")
                     return@queryNDEFStatusWithCompletionHandler
                }
                
                when (status) {
                    NFCNDEFStatusNotSupported -> {
                        session.invalidateSessionWithErrorMessage("Tag is not NDEF compliant.")
                    }
                    NFCNDEFStatusReadOnly -> {
                        if (manager.onWriteResult != null || manager.onClearResult != null) {
                             session.invalidateSessionWithErrorMessage("Tag is read-only.")
                        } else {
                            readTag(tag)
                        }
                    }
                    NFCNDEFStatusReadWrite -> {
                        if (manager.onWriteResult != null) {
                            writeTag(session, tag, manager.pendingWriteData ?: emptyMap())
                        } else if (manager.onClearResult != null) {
                            clearTag(session, tag)
                        } else {
                            readTag(tag)
                        }
                    }
                    else -> session.invalidateSessionWithErrorMessage("Unknown NDEF status.")
                }
            }
        }
    }
    
    private fun readTag(tag: NFCNDEFTagProtocol) {
        tag.readNDEFWithCompletionHandler { message: NFCNDEFMessage?, error: NSError? ->
            if (error != null || message == null) {
                manager.onReadResult?.invoke(false, null, "Read failed: ${error?.localizedDescription}")
                return@readNDEFWithCompletionHandler
            }
            processRead(message)
        }
    }
    
    private fun processRead(message: NFCNDEFMessage) {
        val data = mutableMapOf<String, String>()
        
        message.records.forEach { record ->
             record as NFCNDEFPayload
             val text = parseTextRecord(record)
             val parts = text.split(":", limit = 2)
             if (parts.size == 2) {
                 data[parts[0]] = parts[1]
             }
        }

        detectedTag.value = message 
        manager.onReadResult?.invoke(true, data, "Read Success")
    }
    
    private fun writeTag(session: NFCNDEFReaderSession, tag: NFCNDEFTagProtocol, dataMap: Map<String, String>) {
         val records = dataMap.map { (key, value) ->
             createTextRecord("$key:$value")
         }
         
         val message = NFCNDEFMessage(nDEFRecords = records)
         
         tag.writeNDEF(message) { error: NSError? ->
             if (error != null) {
                 manager.onWriteResult?.invoke(false, "Write failed: ${error.localizedDescription}")
             } else {
                 manager.onWriteResult?.invoke(true, "Write Success")
                 session.alertMessage = "Write Success"
                 session.invalidateSession()
             }
         }
    }
    
    private fun clearTag(session: NFCNDEFReaderSession, tag: NFCNDEFTagProtocol) {
         val emptyMessage = NFCNDEFMessage(nDEFRecords = emptyList<NFCNDEFPayload>())
         tag.writeNDEF(emptyMessage) { error: NSError? ->
             if (error != null) {
                 manager.onClearResult?.invoke(false, "Clear failed: ${error.localizedDescription}")
             } else {
                 manager.onClearResult?.invoke(true, "Cleared Successfully")
                 session.alertMessage = "Cleared"
                 session.invalidateSession()
             }
         }
    }

    private fun parseTextRecord(payload: NFCNDEFPayload): String {
        val data = payload.payload.toByteArray()
        if (data.isEmpty()) return ""
        
        val status = data[0].toInt()
        val langLength = status and 0x3F
        
        return if (data.size > 1 + langLength) {
             val textBytes = data.copyOfRange(1 + langLength, data.size)
             textBytes.decodeToString() 
        } else {
            ""
        }
    }

    private fun createTextRecord(text: String): NFCNDEFPayload {
        val lang = "en"
        val langBytes = lang.encodeToByteArray()
        val textBytes = text.encodeToByteArray()
        
        val payloadData = ByteArray(1 + langBytes.size + textBytes.size)
        payloadData[0] = langBytes.size.toByte() 
        
        langBytes.copyInto(payloadData, 1)
        textBytes.copyInto(payloadData, 1 + langBytes.size)
        
        val nsData = payloadData.toNSData()

        return NFCNDEFPayload(
            format = NFCTypeNameFormatNFCWellKnown,
            type = "T".encodeToByteArray().toNSData(),
            identifier = ByteArray(0).toNSData(),
            payload = nsData
        )
    }
    
    private fun ByteArray.toNSData(): NSData = memScoped {
        if (this@toNSData.isEmpty()) return NSData()
        return NSData.create(bytes = this@toNSData.refTo(0).getPointer(this), length = this@toNSData.size.toULong())
    }
    
    private fun NSData.toByteArray(): ByteArray = ByteArray(this.length.toInt()).apply {
        if (this@toByteArray.length > 0u) {
            usePinned {
               memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
            }
        }
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
