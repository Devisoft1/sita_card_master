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
    // Hold a strong reference to the delegate so it isn't garbage collected
    private val delegate = IosNfcDelegate(this)
    
    override val detectedTag: State<Any?> = delegate.detectedTag
    override val detectedTagId: State<String?> = delegate.detectedTagId

    // Callback storage for pending operations
    internal var onReadResult: ((Boolean, Map<String, String>?, String) -> Unit)? = null
    internal var onWriteResult: ((Boolean, String) -> Unit)? = null
    internal var onClearResult: ((Boolean, String) -> Unit)? = null
    internal var onDeleteResult: ((Boolean, String) -> Unit)? = null
    
    // Data to write (if any)
    internal var pendingWriteData: Map<String, String>? = null

    override fun startScanning() {
        cleanup()
        startSession()
    }

    override fun stopScanning() {
        session?.invalidateSession()
        cleanup()
    }
    
    private fun startSession() {
        // Use NFCTagReaderSession instead of NFCNDEFReaderSession
        session = NFCTagReaderSession(
            pollingOption = NFCPollingISO14443 or NFCPollingISO15693,
            delegate = delegate,
            queue = null
        )
        session?.alertMessage = "Hold your iPhone near the card."
        session?.beginSession()
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
}

@OptIn(ExperimentalForeignApi::class)
private class IosNfcDelegate(private val manager: IosNfcManager) : NSObject(), NFCTagReaderSessionDelegateProtocol {
    val detectedTag: MutableState<Any?> = mutableStateOf(null)
    val detectedTagId: MutableState<String?> = mutableStateOf(null)

    // Common Keys from Android implementation
    private val commonKeys = listOf(
        byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), // DEFAULT
        byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()), // NFC FORUM
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()), // MAD
        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()), // ZERO
        byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
        byteArrayOf(0x4D.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x32.toByte(), 0x33.toByte()),
        byteArrayOf(0x1A.toByte(), 0x2B.toByte(), 0x3C.toByte(), 0x4D.toByte(), 0x5E.toByte(), 0x6F.toByte()),
        byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
        byteArrayOf(0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte()),
        byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte())
    )

    override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
        val errorMessage = didInvalidateWithError.localizedDescription
        if (didInvalidateWithError.code != 200L && didInvalidateWithError.code != 201L) {
             manager.onReadResult?.invoke(false, null, errorMessage)
             manager.onWriteResult?.invoke(false, errorMessage)
             manager.onClearResult?.invoke(false, errorMessage)
             manager.onDeleteResult?.invoke(false, errorMessage)
        }
        detectedTag.value = null
        detectedTagId.value = null
    }

    override fun tagReaderSession(session: NFCTagReaderSession, didDetectTags: List<*>) {
        val tag = didDetectTags.firstOrNull() as? NFCTagProtocol ?: return
        
        session.connectToTag(tag) { error: NSError? ->
            if (error != null) {
                session.invalidateSessionWithErrorMessage("Connect failed: ${error.localizedDescription}")
                return@connectToTag
            }
            
            val mifareTag = tag as? NFCMifareTagProtocol
            if (mifareTag == null) {
                session.invalidateSessionWithErrorMessage("Not a Mifare Classic card.")
                return@connectToTag
            }
            
            // Extract Tag ID (MFID)
            val tagId = mifareTag.identifier.toByteArray().toHex()
            detectedTagId.value = tagId
            detectedTag.value = mifareTag
            
            if (manager.onWriteResult != null) {
                processWrite(session, mifareTag)
            } else if (manager.onReadResult != null) {
                processRead(session, mifareTag)
            } else if (manager.onClearResult != null) {
                processClear(session, mifareTag)
            } else if (manager.onDeleteResult != null) {
                processDelete(session, mifareTag)
            } else {
                session.invalidateSession()
            }
        }
    }

    private fun processRead(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val results = mutableMapOf<String, String>()
        results["card_mfid"] = detectedTagId.value ?: ""

        // Sector 3
        authenticateAndReadSector(tag, 3) { success3, sector3Data ->
            if (!success3) {
                manager.onReadResult?.invoke(false, null, "Sector 3 Authentication Failed")
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

            // Sector 4
            authenticateAndReadSector(tag, 4) { success4, sector4Data ->
                if (success4) {
                    results["totalBuy"] = hexToString(sector4Data[16] ?: "").trimNulls()
                    results["lastBuyDate"] = formatHexDate(sector4Data[17] ?: "")
                    results["password"] = hexToString(sector4Data[18] ?: "").trimNulls()
                }

                // Sector 5
                authenticateAndReadSector(tag, 5) { success5, sector5Data ->
                    if (success5) {
                        results["cardType"] = hexToString(sector5Data[20] ?: "").trimNulls()
                    }
                    
                    manager.onReadResult?.invoke(true, results, "Read Success")
                    session.invalidateSession()
                }
            }
        }
    }

    private fun processWrite(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val data = manager.pendingWriteData ?: return
        
        // Write Sector 3
        val sector3Blocks = mapOf(
            12 to stringToHex(data["memberId"] ?: ""),
            13 to stringToHex(data["companyName"] ?: ""),
            14 to (data["validUpto"] ?: "").replace("-", "").replace("/", "")
        )
        
        authenticateAndWriteSector(tag, 3, sector3Blocks) { success3 ->
            if (!success3) {
                manager.onWriteResult?.invoke(false, "Sector 3 Write Failed")
                session.invalidateSession()
                return@authenticateAndWriteSector
            }
            
            // Sector 4
            val today = NSDate().toFormat("ddMMyyyy")
            val sector4Blocks = mapOf(
                16 to stringToHex(data["totalBuy"] ?: "0"),
                17 to today,
                18 to stringToHex(data["password"] ?: "")
            )
            
            authenticateAndWriteSector(tag, 4, sector4Blocks) { success4 ->
                if (!success4) {
                    manager.onWriteResult?.invoke(false, "Sector 4 Write Failed")
                    session.invalidateSession()
                    return@authenticateAndWriteSector
                }
                
                // Sector 5
                val sector5Blocks = mapOf(
                    20 to stringToHex(data["cardType"] ?: "")
                )
                
                authenticateAndWriteSector(tag, 5, sector5Blocks) { success5 ->
                    if (!success5) {
                        manager.onWriteResult?.invoke(false, "Sector 5 Write Failed")
                    } else {
                        manager.onWriteResult?.invoke(true, "Write Success")
                    }
                    session.invalidateSession()
                }
            }
        }
    }

    private fun processClear(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) {
        val emptyBlocks3 = mapOf(12 to "00000000000000000000000000000000", 13 to "00000000000000000000000000000000", 14 to "00000000000000000000000000000000")
        authenticateAndWriteSector(tag, 3, emptyBlocks3) { success3 ->
            val emptyBlocks4 = mapOf(16 to "00000000000000000000000000000000", 17 to "00000000000000000000000000000000", 18 to "00000000000000000000000000000000")
            authenticateAndWriteSector(tag, 4, emptyBlocks4) { success4 ->
                val emptyBlocks5 = mapOf(20 to "00000000000000000000000000000000")
                authenticateAndWriteSector(tag, 5, emptyBlocks5) { success5 ->
                    manager.onClearResult?.invoke(true, "Card Cleared")
                    session.invalidateSession()
                }
            }
        }
    }

    private fun processDelete(session: NFCTagReaderSession, tag: NFCMifareTagProtocol) = processClear(session, tag)

    private fun authenticateAndReadSector(tag: NFCMifareTagProtocol, sector: Int, onResult: (Boolean, Map<Int, String>) -> Unit) {
        rotateAuthenticate(tag, sector, 0) { success ->
            if (!success) {
                onResult(false, emptyMap())
                return@rotateAuthenticate
            }
            
            val data = mutableMapOf<Int, String>()
            val blocks = when(sector) {
                3 -> listOf(12, 13, 14)
                4 -> listOf(16, 17, 18)
                5 -> listOf(20)
                else -> emptyList()
            }
            
            readMultipleBlocks(tag, blocks, 0, data) {
                onResult(true, data)
            }
        }
    }

    private fun authenticateAndWriteSector(tag: NFCMifareTagProtocol, sector: Int, blockData: Map<Int, String>, onResult: (Boolean) -> Unit) {
        rotateAuthenticate(tag, sector, 0) { success ->
            if (!success) {
                onResult(false)
                return@rotateAuthenticate
            }
            
            writeMultipleBlocks(tag, blockData.toList(), 0) {
                onResult(true)
            }
        }
    }

    private fun rotateAuthenticate(tag: NFCMifareTagProtocol, sector: Int, keyIndex: Int, onResult: (Boolean) -> Unit) {
        if (keyIndex >= commonKeys.size) {
            onResult(false)
            return
        }
        
        val keyData = commonKeys[keyIndex].toNSData()
        tag.mifareClassicAuthenticateWithSector(sector.toLong(), NFCMifareKeyTypeA, keyData) { error: NSError? ->
            if (error == null) {
                onResult(true)
            } else {
                // Try Key B
                tag.mifareClassicAuthenticateWithSector(sector.toLong(), NFCMifareKeyTypeB, keyData) { errorB: NSError? ->
                    if (errorB == null) {
                        onResult(true)
                    } else {
                        rotateAuthenticate(tag, sector, keyIndex + 1, onResult)
                    }
                }
            }
        }
    }

    private fun readMultipleBlocks(tag: NFCMifareTagProtocol, blocks: List<Int>, index: Int, results: MutableMap<Int, String>, onComplete: () -> Unit) {
        if (index >= blocks.size) {
            onComplete()
            return
        }
        
        val blockIndex = blocks[index]
        tag.mifareClassicReadBlockAtIndex(blockIndex.toLong()) { data: NSData?, error: NSError? ->
            if (error == null && data != null) {
                results[blockIndex] = data.toByteArray().toHexWithSpaces()
            } else {
                results[blockIndex] = ""
            }
            readMultipleBlocks(tag, blocks, index + 1, results, onComplete)
        }
    }

    private fun writeMultipleBlocks(tag: NFCMifareTagProtocol, blocks: List<Pair<Int, String>>, index: Int, onComplete: () -> Unit) {
        if (index >= blocks.size) {
            onComplete()
            return
        }
        
        val (blockIndex, hexData) = blocks[index]
        val bytes = ByteArray(16)
        val dataBytes = hexData.hexToByteArray()
        dataBytes.copyInto(bytes, 0, 0, minOf(dataBytes.size, 16))
        
        tag.mifareClassicWriteBlockAtIndex(blockIndex.toLong(), bytes.toNSData()) { error: NSError? ->
            writeMultipleBlocks(tag, blocks, index + 1, onComplete)
        }
    }

    // Helper conversion methods
    private fun smartDecode(hexStr: String): String {
        val ascii = hexToString(hexStr.replace(" ", ""))
        val cleanAscii = ascii.trimNulls()
        
        val hasControlChars = cleanAscii.any { it.code < 32 && it.code != 0 }
        val rawInput = hexStr.replace(" ", "").replace("00", "")
        
        if (hasControlChars || (rawInput.isNotEmpty() && cleanAscii.isEmpty())) {
             val sb = StringBuilder()
             val parts = hexStr.trim().split(" ")
             for (p in parts) {
                 if (p == "00") break
                 sb.append(p)
             }
             return sb.toString()
        }
        return cleanAscii
    }

    private fun hexToString(hex: String): String {
        val cleanHex = hex.replace(" ", "")
        val result = StringBuilder()
        var i = 0
        while (i < cleanHex.length - 1) {
            val str = cleanHex.substring(i, i + 2)
            try {
                val charCode = str.toInt(16)
                if (charCode != 0) result.append(charCode.toChar())
            } catch (e: Exception) {}
            i += 2
        }
        return result.toString()
    }

    private fun stringToHex(input: String): String = 
        input.encodeToByteArray().toHex()

    private fun formatHexDate(hex: String): String {
        val clean = hex.replace(" ", "")
        if (clean.length >= 8) {
            val d = clean.substring(0, 2)
            val m = clean.substring(2, 4)
            val y = clean.substring(4, 8)
             if (d.all { it.isDigit() } && m.all { it.isDigit() } && y.all { it.isDigit() }) {
                 return "$d-$m-$y"
             }
        }
        return ""
    }

    private fun String.trimNulls(): String = 
        this.filter { it != '\u0000' }.trim()

    private fun ByteArray.toHex(): String = 
        joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }

    private fun ByteArray.toHexWithSpaces(): String = 
        joinToString(" ") { it.toUByte().toString(16).padStart(2, '0').uppercase() }

    private fun String.hexToByteArray(): ByteArray {
        val s = this.replace(" ", "")
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i+1].digitToInt(16)).toByte()
            i += 2
        }
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
        val length = length.toInt()
        val result = ByteArray(length)
        if (length > 0) {
            memcpy(result.refTo(0), bytes, length.toULong())
        }
        return result
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
