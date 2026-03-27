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
    private var session: NFCTagReaderSession? = null
    private val delegate = IosNfcDelegate(this)
    
    override val detectedTag: State<Any?> = delegate.detectedTag
    override val detectedTagId: State<String?> = delegate.detectedTagId
    override val isMultipleTagsDetected: State<Boolean> = delegate.isMultipleTagsDetected

    internal var onReadResult: ((Boolean, Map<String, String>?, String) -> Unit)? = null
    internal var onWriteResult: ((Boolean, String) -> Unit)? = null
    internal var onClearResult: ((Boolean, String) -> Unit)? = null
    internal var onDeleteResult: ((Boolean, String) -> Unit)? = null
    
    internal var pendingWriteData: Map<String, String>? = null
    
    private var scanTimer: NSTimer? = null
    private var secondsElapsed = 0

    override fun startScanning() {
        cleanup()
        startSession()
    }

    override fun stopScanning() {
        session?.invalidateSession()
        stopTimer()
        cleanup()
    }
    
    private fun startSession() {
        if (session != null) return // Already running
        
        secondsElapsed = 0
        session = NFCTagReaderSession(
            pollingOption = NFCPollingISO14443 or NFCPollingISO15693,
            delegate = delegate,
            queue = null
        )
        session?.alertMessage = "Hold your iPhone near the card. (Time Elapsed: 60s)"
        session?.beginSession()
        
        startTimer()
    }

    private fun startTimer() {
        stopTimer()
        scanTimer = NSTimer.scheduledTimerWithTimeInterval(1.0, true) {
            secondsElapsed++
            val remaining = 60 - secondsElapsed
            val timeStr = "Time Elapsed: ${remaining}s"
            
            if (secondsElapsed >= 60) {
                session?.invalidateSessionWithErrorMessage("No card detected (60s timeout)")
                stopTimer()
                onWriteResult?.invoke(false, "No card detected")
            } else {
                session?.alertMessage = "Hold your iPhone near the card. ($timeStr)"
            }
        }
    }

    private fun stopTimer() {
        scanTimer?.invalidate()
        scanTimer = null
    }

    private fun cleanup() {
        onReadResult = null
        onWriteResult = null
        onClearResult = null
        onDeleteResult = null
        pendingWriteData = null
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
        cleanup()
        onWriteResult = onResult
        pendingWriteData = mapOf(
            "memberId" to memberId,
            "companyName" to companyName,
            "password" to password,
            "validUpto" to validUpto,
            "totalBuy" to totalBuy,
            "cardType" to cardType
        )
        startSession()
    }

    override fun writeLogoUrl(url: String, onResult: (Boolean, String) -> Unit) {
        cleanup()
        onWriteResult = onResult
        pendingWriteData = mapOf("logoUrl" to url)
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

    override fun deleteCardData(onResult: (Boolean, String) -> Unit) {
        cleanup()
        onDeleteResult = onResult
        startSession()
    }

    override fun clearScanData() {
        cleanup()
        delegate.detectedTag.value = null
        delegate.detectedTagId.value = null
        delegate.isMultipleTagsDetected.value = false
    }

    override fun extractUrl(tag: Any?): String? {
        val ndefTag = (tag as? NFCNDEFTagProtocol) ?: return null
        // On iOS, we need to have read the NDEF message already or do it async.
        // For sync extractUrl, we rely on the delegate having pre-read it or 
        // we'd need to change the architecture. 
        // Given the current structure, we'll return the pre-extracted URL from the delegate.
        return delegate.detectedUrl.value
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNfcDelegate(private val manager: IosNfcManager) : NSObject(), NFCTagReaderSessionDelegateProtocol {
    val detectedTag: MutableState<Any?> = mutableStateOf(null)
    val detectedTagId: MutableState<String?> = mutableStateOf(null)
    val isMultipleTagsDetected: MutableState<Boolean> = mutableStateOf(false)
    val detectedUrl: MutableState<String?> = mutableStateOf(null)

    private val commonKeys = listOf(
        byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
        byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
        byteArrayOf(0x4D.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x32.toByte(), 0x33.toByte()),
        byteArrayOf(0x1A.toByte(), 0x2B.toByte(), 0x3C.toByte(), 0x4D.toByte(), 0x5E.toByte(), 0x6F.toByte()),
        byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
        byteArrayOf(0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte()),
        byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte())
    )

    override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
        if (didInvalidateWithError.code != 200L && didInvalidateWithError.code != 201L) {
             val errorMessage = if (didInvalidateWithError.code == 203L) {
                 "Card not detected properly please scan again"
             } else {
                 didInvalidateWithError.localizedDescription
             }
             manager.onReadResult?.invoke(false, null, errorMessage)
             manager.onWriteResult?.invoke(false, errorMessage)
             manager.onClearResult?.invoke(false, errorMessage)
             manager.onDeleteResult?.invoke(false, errorMessage)
        }
        detectedTag.value = null
        detectedTagId.value = null
        manager.stopTimer()
    }

    override fun tagReaderSession(session: NFCTagReaderSession, didDetectTags: List<*>) {
        if (didDetectTags.size > 1) {
            isMultipleTagsDetected.value = true
        }
        
        val tag = didDetectTags.firstOrNull() as? NFCTagProtocol ?: return
        
        session.connectToTag(tag) { error: NSError? ->
            if (error != null) {
                session.invalidateSessionWithErrorMessage("Connect failed: ${error.localizedDescription}")
                return@connectToTag
            }
            
            manager.stopTimer()
            
            // Re-interpret the tag through the NFCTag wrapper safely
            val nfcTag = (tag as? NFCTag) ?: (tag as? NSObject)?.let { 
                if (it.conformsToProtocol(NFCMifareTagProtocol)) {
                    NFCTag.mifareTag(it as NFCMifareTagProtocol)
                } else if (it.conformsToProtocol(NFCNDEFTagProtocol)) {
                    // This is trickier in Kotlin/Native, we might need to handle NDEF tags specifically
                    null
                } else null
            }

            val mifareTag = nfcTag?.asNFCMifareTag()
            val ndefTag = nfcTag?.asNFCNDEFTag() ?: (tag as? NFCNDEFTagProtocol)

            if (mifareTag == null && ndefTag == null) {
                session.invalidateSessionWithErrorMessage("Card not supported.")
                return@connectToTag
            }
            
            val tagId = mifareTag?.identifier()?.toByteArray()?.toHex() 
                        ?: (ndefTag as? NFCTagProtocol)?.let { it.description } // Fallback for NDEF tag ID if possible
                        ?: "NDEF-TAG"

            detectedTagId.value = tagId
            // Don't set detectedTag yet if it's NDEF, wait for read
            detectedUrl.value = null
            
            if (ndefTag != null) {
                ndefTag.readNDEFWithCompletionHandler { message, error ->
                    if (error == null && message != null) {
                        val record = message.records.firstOrNull() as? NFCNdefPayload
                        if (record != null && record.typeNameFormat == NFCNdefTypeNameFormatWellKnown) {
                            val url = record.wellKnownTypeURIPayload()?.absoluteString
                            detectedUrl.value = url
                        }
                    }
                    // NOW set the tag to trigger the LaunchedEffect in UI
                    detectedTag.value = mifareTag ?: ndefTag
                    
                    // Proceed with standard write/read if pending
                    handlePendingOperations(session, mifareTag, ndefTag)
                }
            } else {
                detectedTag.value = mifareTag
                handlePendingOperations(session, mifareTag, null)
            }
        }
    }

    private fun handlePendingOperations(session: NFCTagReaderSession, mifareTag: NFCMifareTagProtocol?, ndefTag: NFCNDEFTagProtocol?) {
        if (manager.onWriteResult != null) {
            if (manager.pendingWriteData?.containsKey("logoUrl") == true && ndefTag != null) {
                processNdefWrite(session, ndefTag)
            } else if (mifareTag != null) {
                processWrite(session, mifareTag)
            } else {
                session.invalidateSessionWithErrorMessage("Card not supported.")
            }
        }
        else if (manager.onReadResult != null) {
            if (mifareTag != null) processRead(session, mifareTag)
            else session.invalidateSessionWithErrorMessage("Card not supported.")
        }
        else if (manager.onClearResult != null) {
            if (mifareTag != null) processClear(session, mifareTag)
            else session.invalidateSessionWithErrorMessage("Card not supported.")
        }
        else if (manager.onDeleteResult != null) {
            if (mifareTag != null) processDelete(session, mifareTag)
            else session.invalidateSessionWithErrorMessage("Card not supported.")
        }
    }

    private fun processNdefWrite(session: NFCTagReaderSession, tag: NFCNDEFTagProtocol) {
        val data = manager.pendingWriteData ?: return
        val url = data["logoUrl"] ?: ""
        
        tag.queryNDEFStatusWithCompletionHandler { status, capacity, error ->
            if (error != null) {
                manager.onWriteResult?.invoke(false, "NDEF Query Failed: ${error.localizedDescription}")
                session.invalidateSession()
                return@queryNDEFStatusWithCompletionHandler
            }
            
            if (status == NFCNdefStatusReadOnly) {
                manager.onWriteResult?.invoke(false, "Tag is read-only")
                session.invalidateSession()
                return@queryNDEFStatusWithCompletionHandler
            }
            
            val uriRecord = NFCNdefPayload.wellKnownTypeURIPayloadWithString(url)
            if (uriRecord == null) {
                manager.onWriteResult?.invoke(false, "Failed to create URI payload")
                session.invalidateSession()
                return@queryNDEFStatusWithCompletionHandler
            }
            
            val message = NFCNdefMessage(listOf(uriRecord))
            tag.writeNDEF(message) { writeError ->
                if (writeError != null) {
                    manager.onWriteResult?.invoke(false, "Write Failed: ${writeError.localizedDescription}")
                } else {
                    manager.onWriteResult?.invoke(true, "Logo URL written successfully!")
                }
                session.invalidateSession()
            }
        }
    }

    private fun processRead(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val results = mutableMapOf<String, String>()
        results["card_mfid"] = detectedTagId.value ?: ""

        authenticateAndReadSector(tag, 3) { success3, sector3Data ->
            if (!success3) {
                manager.onReadResult?.invoke(false, null, "Card not detected properly please scan again")
                session.invalidateSession()
                return@authenticateAndReadSector
            }
            
            val block12 = sector3Data[12] ?: ""
            if (block12.all { it == '0' || it == ' ' }) {
                manager.onReadResult?.invoke(true, null, "Blank card")
                session.invalidateSession()
                return@authenticateAndReadSector
            }
            
            results["memberId"] = smartDecode(block12)
            results["companyName"] = hexToString(sector3Data[13] ?: "").trimNulls()
            results["validUpto"] = formatHexDate(sector3Data[14] ?: "")

            authenticateAndReadSector(tag, 4) { success4, sector4Data ->
                if (success4) {
                    results["totalBuy"] = hexToString(sector4Data[16] ?: "").trimNulls()
                    results["lastBuyDate"] = formatHexDate(sector4Data[17] ?: "")
                    results["password"] = hexToString(sector4Data[18] ?: "").trimNulls()
                }

                authenticateAndReadSector(tag, 5) { success5, sector5Data ->
                    if (success5) {
                        results["cardType"] = hexToString(sector5Data[20] ?: "").trimNulls()
                        results["logoUrl"] = hexToString(sector5Data[21] ?: "").trimNulls()
                    }
                    manager.onReadResult?.invoke(true, results, "Read Success")
                    session.invalidateSession()
                }
            }
        }
    }

    private fun processWrite(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val data = manager.pendingWriteData ?: return
        
        if (data.containsKey("logoUrl")) {
            val url = data["logoUrl"] ?: ""
            val sector5Blocks = mapOf(21 to stringToHex(url))
            authenticateAndWriteSector(tag, 5, sector5Blocks) { success ->
                if (!success) manager.onWriteResult?.invoke(false, "Card not detected properly please scan again")
                else manager.onWriteResult?.invoke(true, "Logo URL Written Successfully")
                session.invalidateSession()
            }
            return
        }

        val sector3Blocks = mapOf(
            12 to stringToHex(data["memberId"] ?: ""),
            13 to stringToHex(data["companyName"] ?: ""),
            14 to (data["validUpto"] ?: "").replace("-", "").replace("/", "")
        )
        
        authenticateAndWriteSector(tag, 3, sector3Blocks) { success3 ->
            if (!success3) {
                manager.onWriteResult?.invoke(false, "Card not detected properly please scan again")
                session.invalidateSession()
                return@authenticateAndWriteSector
            }
            
            val today = NSDate().toFormat("ddMMyyyy")
            val sector4Blocks = mapOf(
                16 to stringToHex(data["totalBuy"] ?: "0"),
                17 to today,
                18 to stringToHex(data["password"] ?: "")
            )
            
            authenticateAndWriteSector(tag, 4, sector4Blocks) { success4 ->
                if (!success4) {
                    manager.onWriteResult?.invoke(false, "Card not detected properly please scan again")
                    session.invalidateSession()
                    return@authenticateAndWriteSector
                }
                
                val sector5Blocks = mapOf(20 to stringToHex(data["cardType"] ?: ""))
                authenticateAndWriteSector(tag, 5, sector5Blocks) { success5 ->
                    if (!success5) manager.onWriteResult?.invoke(false, "Card not detected properly please scan again")
                    else manager.onWriteResult?.invoke(true, "Write Success")
                    session.invalidateSession()
                }
            }
        }
    }

    private fun processClear(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val emptyBlocks3 = mapOf(
            12 to "00000000000000000000000000000000",
            13 to "00000000000000000000000000000000",
            14 to "00000000000000000000000000000000"
        )
        authenticateAndWriteSector(tag, 3, emptyBlocks3) {
            manager.onClearResult?.invoke(true, "Card Cleared")
            session.invalidateSession()
        }
    }

    private fun processDelete(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) = processClear(session, tag)

    private fun authenticateAndReadSector(tag: NFCMifareTagProtocol, sector: Int, onResult: (Boolean, Map<Int, String>) -> Unit) {
        rotateAuthenticate(tag, sector, 0) { success ->
            if (!success) { onResult(false, emptyMap()); return@rotateAuthenticate }
            val data = mutableMapOf<Int, String>()
            val blocks = when(sector) {
                3 -> listOf(12, 13, 14)
                4 -> listOf(16, 17, 18)
                5 -> listOf(20, 21)
                else -> emptyList()
            }
            readMultipleBlocks(tag, blocks, 0, data) { onResult(true, data) }
        }
    }

    private fun authenticateAndWriteSector(tag: NFCMifareTagProtocol, sector: Int, blockData: Map<Int, String>, onResult: (Boolean) -> Unit) {
        rotateAuthenticate(tag, sector, 0) { success ->
            if (!success) { onResult(false); return@rotateAuthenticate }
            writeMultipleBlocks(tag, blockData.toList(), 0) { onResult(true) }
        }
    }

    private fun rotateAuthenticate(tag: NFCMifareTagProtocol, sector: Int, keyIndex: Int, onResult: (Boolean) -> Unit) {
        if (keyIndex >= commonKeys.size) { onResult(false); return }
        
        val keyData = commonKeys[keyIndex].toNSData()
        tag.mifareClassicAuthenticateWithSector(sector.toLong(), keyData, NFCMifareKeyTypeA) { error: NSError? ->
            if (error == null) onResult(true)
            else {
                tag.mifareClassicAuthenticateWithSector(sector.toLong(), keyData, NFCMifareKeyTypeB) { errorB: NSError? ->
                    if (errorB == null) onResult(true)
                    else rotateAuthenticate(tag, sector, keyIndex + 1, onResult)
                }
            }
        }
    }

    private fun readMultipleBlocks(tag: NFCMifareTagProtocol, blocks: List<Int>, index: Int, results: MutableMap<Int, String>, onComplete: () -> Unit) {
        if (index >= blocks.size) { onComplete(); return }
        val blockIndex = blocks[index]
        tag.mifareClassicReadBlockAtIndex(blockIndex.toLong()) { data: NSData?, error: NSError? ->
            if (error == null && data != null) results[blockIndex] = data.toByteArray().toHexWithSpaces()
            else results[blockIndex] = ""
            readMultipleBlocks(tag, blocks, index + 1, results, onComplete)
        }
    }

    private fun writeMultipleBlocks(tag: NFCMifareTagProtocol, blocks: List<Pair<Int, String>>, index: Int, onComplete: () -> Unit) {
        if (index >= blocks.size) { onComplete(); return }
        val (blockIndex, hexData) = blocks[index]
        val bytes = ByteArray(16)
        val dataBytes = hexData.hexToByteArray()
        dataBytes.copyInto(bytes, 0, 0, minOf(dataBytes.size, 16))
        tag.mifareClassicWriteBlockAtIndex(blockIndex.toLong(), bytes.toNSData()) { writeMultipleBlocks(tag, blocks, index + 1, onComplete) }
    }

    private fun smartDecode(hexStr: String): String {
        val ascii = hexToString(hexStr.replace(" ", "")).trimNulls()
        if (ascii.any { it.code < 32 && it.code != 0 }) return hexStr.replace(" ", "").replace("00", "")
        return ascii
    }

    private fun hexToString(hex: String): String {
        val cleanHex = hex.replace(" ", "")
        val result = StringBuilder()
        for (i in 0 until cleanHex.length - 1 step 2) {
            try {
                val charCode = cleanHex.substring(i, i + 2).toInt(16)
                if (charCode != 0) result.append(charCode.toChar())
            } catch (e: Exception) {}
        }
        return result.toString()
    }

    private fun stringToHex(input: String): String = input.encodeToByteArray().toHex()

    private fun formatHexDate(hex: String): String {
        val clean = hex.replace(" ", "")
        if (clean.length >= 8) {
            val d = clean.substring(0, 2); val m = clean.substring(2, 4); val y = clean.substring(4, 8)
            if (d.all { it.isDigit() } && m.all { it.isDigit() } && y.all { it.isDigit() }) return "$d-$m-$y"
        }
        return ""
    }

    private fun String.trimNulls(): String = filter { it != '\u0000' }.trim()
    private fun ByteArray.toHex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
    private fun ByteArray.toHexWithSpaces(): String = joinToString(" ") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
    private fun String.hexToByteArray(): ByteArray {
        val s = replace(" ", ""); val data = ByteArray(s.length / 2)
        for (i in 0 until s.length step 2) data[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i+1].digitToInt(16)).toByte()
        return data
    }
    private fun NSDate.toFormat(format: String): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = format
        return formatter.stringFromDate(this)
    }
    private fun ByteArray.toNSData(): NSData = memScoped {
        if (isEmpty()) return NSData()
        return NSData.create(bytes = refTo(0).getPointer(this), length = size.toULong())
    }
    private fun NSData.toByteArray(): ByteArray {
        val length = length.toInt(); val result = ByteArray(length)
        if (length > 0) memcpy(result.refTo(0), bytes, length.toULong())
        return result
    }
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    val manager = remember { IosNfcManager() }
    DisposableEffect(Unit) { onDispose { manager.stopScanning() } }
    return manager
}
