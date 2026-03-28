package com.example.sitacardmaster

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidNfcManager(private val activity: Activity) : NfcManager {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val pendingIntent: PendingIntent = PendingIntent.getActivity(
        activity, 0,
        Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_MUTABLE
    )

    private val intentFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))

    override val detectedTag: State<Tag?> = mutableStateOf(null)
    override val detectedTagId: State<String?> = mutableStateOf(null)
    override val isMultipleTagsDetected: State<Boolean> = mutableStateOf(false)

    private var lastTagId: String? = null
    private var lastTagTimestamp: Long = 0

    override fun startScanning() {
        (detectedTag as MutableState<Tag?>).value = null
        (detectedTagId as MutableState<String?>).value = null
        (isMultipleTagsDetected as MutableState<Boolean>).value = false
        lastTagId = null
        lastTagTimestamp = 0
        try {
            platformLog("SITACardMaster", "Starting NFC scanning...")
            nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, intentFilters, null)
            platformLog("SITACardMaster", "NFC Scanning Started.")
        } catch (e: IllegalStateException) {
            platformLog("SITACardMaster", "Failed to enable foreground dispatch: ${e.message}")
        }
    }

    override fun stopScanning() {
        try {
            platformLog("SITACardMaster", "Stopping NFC scanning...")
            nfcAdapter?.disableForegroundDispatch(activity)
            platformLog("SITACardMaster", "NFC Scanning Stopped.")
        } catch (e: IllegalStateException) {
            platformLog("SITACardMaster", "Failed to disable foreground dispatch: ${e.message}")
        }
    }

    override fun clearScanData() {
        (detectedTag as MutableState<Tag?>).value = null
        (detectedTagId as MutableState<String?>).value = null
        (isMultipleTagsDetected as MutableState<Boolean>).value = false
        lastTagId = null
    }

    fun onNewIntent(intent: Intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
        ) {
            platformLog("SITACardMaster", "MULTIPLE_CARD_CHECK: New Intent Received - Action: ${intent.action}")

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            var currentTagId: String? = null
            tag?.let {
                val tagId = it.id.joinToString("") { byte -> "%02X".format(byte) }
                
                // Heuristic 1: Duplicate Techs
                val techs = it.techList
                platformLog("SITACardMaster", "MULTIPLE_CARD_CHECK: Tag ID: $tagId, Techs: ${techs.joinToString(", ")}")
                val uniqueTechs = techs.distinct()
                if (techs.size != uniqueTechs.size) {
                    platformLog("SITACardMaster", "MULTIPLE_CARD_CHECK: FAILED - Duplicate technologies detected in techList!")
                    (isMultipleTagsDetected as MutableState<Boolean>).value = true
                }

                // Heuristic 2: Rapid ID change
                val currentTime = System.currentTimeMillis()
                if (lastTagId != null && lastTagId != tagId && (currentTime - lastTagTimestamp) < 4000) {
                    platformLog("SITACardMaster", "MULTIPLE_CARD_CHECK: FAILED - Rapid ID change detected (Last: $lastTagId, Current: $tagId, Delta: ${currentTime - lastTagTimestamp}ms)")
                    (isMultipleTagsDetected as MutableState<Boolean>).value = true
                }
                
                if (!(isMultipleTagsDetected as MutableState<Boolean>).value) {
                    platformLog("SITACardMaster", "MULTIPLE_CARD_CHECK: PASSED - No multiple cards detected.")
                }
                
                lastTagId = tagId
                lastTagTimestamp = currentTime
                
                currentTagId = tagId
                val logTagId = it.id.joinToString(":") { byte -> "%02X".format(byte) }
                platformLog("SITACardMaster", "NFC Tag Detected! ID: $logTagId")
            }
            (detectedTag as MutableState<Tag?>).value = tag
            (detectedTagId as MutableState<String?>).value = currentTagId
        }
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
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, "No card detected.")
            return
        }

        val mifare = MifareClassic.get(tag as Tag)
        if (mifare == null) {
            onResult(false, "Not a Mifare Classic card.")
            return
        }

        Thread {
            var success = false
            var resultMessage = ""
            
            try {
                platformLog("SITACardMaster", "Reading card diagnostics...")
                mifare.connect()
                logCardDiagnostics(mifare)
                
                val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
                platformLog("SITACardMaster", "Connected to card for writing. MFID: $tagId")

                // Write Sector 3: Member Info
                if (authenticateSector(mifare, 3)) {
                    platformLog("SITACardMaster", "Sector 3 Authenticated. Writing Member ID, Company, Validity...")
                    writeBlock(mifare, 12, memberId)
                    platformLog("SITACardMaster", "Block 12 Written: $memberId")
                    writeBlock(mifare, 13, companyName)
                    platformLog("SITACardMaster", "Block 13 Written: $companyName")
                    writeBlock(mifare, 14, validUpto.replace("-", "").replace("/", ""))
                    platformLog("SITACardMaster", "Block 14 Written: $validUpto")

                    // Write Sector 4: Purchase Info & Password
                    if (authenticateSector(mifare, 4)) {
                        platformLog("SITACardMaster", "Sector 4 Authenticated. Writing Total Buy, Date, Password...")
                        writeHexBlock(mifare, 16, stringToHex(totalBuy))
                        platformLog("SITACardMaster", "Block 16 Written (Hex): ${stringToHex(totalBuy)}")
                        
                        val today = SimpleDateFormat("ddMMyyyy", Locale.US).format(Date())
                        writeHexBlock(mifare, 17, today)
                        platformLog("SITACardMaster", "Block 17 Written (Date): $today")
                        
                        writeBlock(mifare, 18, password)
                        platformLog("SITACardMaster", "Block 18 Written (Password): $password")

                        // Write Sector 5: Card Type
                        if (authenticateSector(mifare, 5)) {
                            platformLog("SITACardMaster", "Sector 5 Authenticated. Writing Card Type...")
                            writeBlock(mifare, 20, cardType)
                            platformLog("SITACardMaster", "Block 20 Written: $cardType")
                            
                            success = true
                            resultMessage = "Card issued successfully!"
                            platformLog("SITACardMaster", "WRITE_SUCCESS: All sectors written.")
                        } else {
                            platformLog("SITACardMaster", "WRITE_ERROR: Sector 5 Authentication Failed")
                            resultMessage = "Failed to write card type. Sector 5 error."
                        }
                    } else {
                        platformLog("SITACardMaster", "WRITE_ERROR: Sector 4 Authentication Failed")
                        resultMessage = "Failed to write purchase data. Sector 4 error."
                    }
                } else {
                    platformLog("SITACardMaster", "WRITE_ERROR: Sector 3 Authentication Failed")
                    resultMessage = "Failed to write member data. Sector 3 error."
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Write Error: ${e.message}")
                success = false
                resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                    "Card not detected properly please scan again"
                } else {
                    "Error: ${e.message}"
                }
            } finally {
                try {
                    if (mifare.isConnected) mifare.close()
                    platformLog("SITACardMaster", "Mifare connection closed (writeCard)")
                } catch (e: Exception) {}
            }
            onResult(success, resultMessage)
        }.start()
    }

    override fun writeLogoUrl(url: String, onResult: (Boolean, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, "No card detected.")
            return
        }

        val ndef = Ndef.get(tag as Tag)
        if (ndef != null) {
            Thread {
                var success = false
                var resultMessage = ""
                try {
                    platformLog("SITACardMaster", "NDEF Tag Detected. Writing URL...")
                    ndef.connect()
                    if (!ndef.isWritable) {
                        resultMessage = "Tag is read-only"
                    } else {
                        val uriRecord = NdefRecord.createUri(url)
                        val message = NdefMessage(arrayOf(uriRecord))
                        ndef.writeNdefMessage(message)
                        success = true
                        resultMessage = "Logo URL written successfully!"
                        platformLog("SITACardMaster", "EXTRACT_URL_SUCCESS: URL written to tag.")
                    }
                } catch (e: Exception) {
                    platformLog("SITACardMaster", "NDEF Write Error: ${e.message}")
                    resultMessage = "Write failed: ${e.message}"
                } finally {
                    try { if (ndef.isConnected) ndef.close() } catch (e: Exception) {}
                }
                onResult(success, resultMessage)
            }.start()
            return
        }

        // Fallback to Mifare Classic Block 21
        val mifare = MifareClassic.get(tag as Tag)
        if (mifare != null) {
            Thread {
                var success = false
                var resultMessage = ""
                try {
                    platformLog("SITACardMaster", "NDEF not supported, using Mifare Classic Block 21")
                    mifare.connect()
                    if (authenticateSector(mifare, 5)) {
                        writeHexBlock(mifare, 21, stringToHex(url))
                        success = true
                        resultMessage = "Logo URL written successfully!"
                    } else {
                        resultMessage = "Authentication failed"
                    }
                } catch (e: Exception) {
                    resultMessage = "Write failed: ${e.message}"
                } finally {
                    try { if (mifare.isConnected) mifare.close() } catch (e: Exception) {}
                }
                onResult(success, resultMessage)
            }.start()
        } else {
            onResult(false, "Card type not supported.")
        }
    }

    override fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, null, "No card detected.")
            return
        }

        val mifare = MifareClassic.get(tag as Tag)
        if (mifare == null) {
            onResult(false, null, "Not a Mifare Classic card.")
            return
        }

        Thread {
            var success = false
            var resultData: Map<String, String>? = null
            var resultMessage = ""

            try {
                platformLog("SITACardMaster", "Reading card...")
                mifare.connect()
                logCardDiagnostics(mifare)
                val data = mutableMapOf<String, String>()

                val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
                data["card_mfid"] = tagId

                // Sector 3 (Blocks 12, 13, 14)
                if (authenticateSector(mifare, 3)) {
                    platformLog("SITACardMaster", "Sector 3 Authenticated. Reading Member data...")
                    val memberIdHex = readBlockHexStrings(mifare, 12)
                    data["memberId"] = smartDecode(memberIdHex)
                    platformLog("SITACardMaster", "Block 12 (Member ID): ${data["memberId"]}")
                    
                    val companyHex = readBlockHexStrings(mifare, 13)
                    data["companyName"] = hexToString(companyHex.replace(" ", "")).trimNulls()
                    platformLog("SITACardMaster", "Block 13 (Company): ${data["companyName"]}")
                    
                    val validUptoHex = readBlockHexStrings(mifare, 14)
                    data["validUpto"] = formatHexDate(validUptoHex)
                    platformLog("SITACardMaster", "Block 14 (Valid Upto): ${data["validUpto"]}")

                    // Sector 4 (Block 16, 17, 18)
                    if (authenticateSector(mifare, 4)) {
                        platformLog("SITACardMaster", "Sector 4 Authenticated. Reading Purchase & Password...")
                        val totalBuyHex = readBlockHexStrings(mifare, 16)
                        data["totalBuy"] = hexToString(totalBuyHex.replace(" ", "")).trimNulls()
                        platformLog("SITACardMaster", "Block 16 (Total Buy): ${data["totalBuy"]}")
                        
                        val lastBuyHex = readBlockHexStrings(mifare, 17)
                        data["lastBuyDate"] = formatHexDate(lastBuyHex)
                        platformLog("SITACardMaster", "Block 17 (Last Buy Date): ${data["lastBuyDate"]}")
                        
                        val passwordHex = readBlockHexStrings(mifare, 18)
                        data["password"] = hexToString(passwordHex.replace(" ", "")).trimNulls()
                        platformLog("SITACardMaster", "Block 18 (Password): ${data["password"]}")

                        // Sector 5 (Block 20)
                        if (authenticateSector(mifare, 5)) {
                            platformLog("SITACardMaster", "Sector 5 Authenticated. Reading Card Type...")
                            val cardTypeHex = readBlockHexStrings(mifare, 20)
                            data["cardType"] = hexToString(cardTypeHex.replace(" ", "")).trimNulls()
                            platformLog("SITACardMaster", "Block 20 (Card Type): ${data["cardType"]}")
                        }
                    }

                    val isTrulyBlank = data["memberId"].isNullOrBlank() && 
                                      data["password"].isNullOrBlank() &&
                                      data["companyName"].isNullOrBlank()
                    
                    if (isTrulyBlank) {
                        success = true
                        resultData = null
                        resultMessage = "Blank card"
                    } else {
                        success = true
                        resultData = data
                        resultMessage = "Data read successfully"
                    }
                } else {
                    success = false
                    resultMessage = "Authentication failed"
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Read Error: ${e.message}")
                success = false
                resultMessage = "Read error: ${e.message}"
            } finally {
                try { if (mifare.isConnected) mifare.close() } catch (e: Exception) {}
            }
            onResult(success, resultData, resultMessage)
        }.start()
    }

    override fun clearCard(onResult: (Boolean, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, "No card detected.")
            return
        }

        val mifare = MifareClassic.get(tag as Tag)
        if (mifare == null) {
            onResult(false, "Not a Mifare Classic card.")
            return
        }

        Thread {
            var success = false
            var resultMessage = ""
            try {
                mifare.connect()
                if (authenticateSector(mifare, 3)) {
                    platformLog("SITACardMaster", "Clearing card data...")
                    writeBlock(mifare, 12, "")
                    writeBlock(mifare, 13, "")
                    writeBlock(mifare, 14, "")
                    if (authenticateSector(mifare, 4)) {
                        writeBlock(mifare, 16, "")
                        writeBlock(mifare, 17, "")
                        writeBlock(mifare, 18, "")
                        if (authenticateSector(mifare, 5)) {
                            writeBlock(mifare, 20, "")
                            success = true
                            resultMessage = "Card cleared successfully."
                        }
                    }
                }
            } catch (e: Exception) {
                resultMessage = "Error: ${e.message}"
            } finally {
                try { if (mifare.isConnected) mifare.close() } catch (e: Exception) {}
            }
            onResult(success, resultMessage)
        }.start()
    }

    override fun deleteCardData(onResult: (Boolean, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, "No card detected.")
            return
        }

        val mifare = MifareClassic.get(tag as Tag)
        if (mifare == null) {
            onResult(false, "Not a Mifare Classic card.")
            return
        }

        Thread {
            var success = false
            var resultMessage = ""
            try {
                mifare.connect()
                val sectorsToWipe = intArrayOf(3, 4, 5)
                var sectorsWiped = 0
                for (sector in sectorsToWipe) {
                    if (authenticateSector(mifare, sector)) {
                        val firstBlock = mifare.sectorToBlock(sector)
                        val numBlocks = mifare.getBlockCountInSector(sector)
                        for (i in 0 until (numBlocks - 1)) {
                            mifare.writeBlock(firstBlock + i, ByteArray(16))
                        }
                        sectorsWiped++
                    }
                }
                if (sectorsWiped > 0) {
                    success = true
                    resultMessage = "Card data deleted successfully."
                } else {
                    resultMessage = "Wipe failed"
                }
            } catch (e: Exception) {
                resultMessage = "Error: ${e.message}"
            } finally {
                try { if (mifare.isConnected) mifare.close() } catch (e: Exception) {}
            }
            onResult(success, resultMessage)
        }.start()
    }

    override fun extractUrl(tag: Any?): String? {
        val nfcTag = tag as? Tag ?: return null
        try {
            val ndef = Ndef.get(nfcTag)
            if (ndef != null) {
                ndef.connect()
                val msg = ndef.ndefMessage
                if (msg != null) {
                    for (record in msg.records) {
                        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                            java.util.Arrays.equals(record.type, NdefRecord.RTD_URI)) {
                            val uri = record.toUri()
                            if (uri != null) {
                                ndef.close()
                                return uri.toString()
                            }
                        }
                    }
                }
                ndef.close()
            }
        } catch (e: Exception) {
            platformLog("SITACardMaster", "URL Extraction Error: ${e.message}")
        }
        return null
    }

    private fun authenticateSector(mifare: MifareClassic, sector: Int): Boolean {
        val commonKeys = arrayOf(
            MifareClassic.KEY_DEFAULT,
            MifareClassic.KEY_NFC_FORUM,
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
            byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
            byteArrayOf(0x4D.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x32.toByte(), 0x33.toByte())
        )
        for (key in commonKeys) {
            try {
                if (mifare.authenticateSectorWithKeyA(sector, key)) return true
            } catch (e: Exception) {}
            try {
                if (mifare.authenticateSectorWithKeyB(sector, key)) return true
            } catch (e: Exception) {}
        }
        return false
    }

    private fun logCardDiagnostics(mifare: MifareClassic) {
        try {
            platformLog("SITACardMaster", "Card Info - Type: ${mifare.type}, Size: ${mifare.size}, Sectors: ${mifare.sectorCount}")
        } catch (e: Exception) {}
    }

    private fun smartDecode(hexStr: String): String {
        val ascii = hexToString(hexStr)
        val cleanAscii = ascii.trimNulls()
        val rawInput = hexStr.replace(" ", "").replace("00", "")
        if (cleanAscii.isEmpty() && rawInput.isNotEmpty()) {
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

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    private fun stringToHex(input: String): String {
        return input.toByteArray(Charset.forName("US-ASCII")).joinToString("") { "%02X".format(it) }
    }

    private fun hexToString(hex: String): String {
        val cleanHex = hex.replace(" ", "")
        val result = StringBuilder()
        var i = 0
        while (i < cleanHex.length - 1) {
            val str = cleanHex.substring(i, i + 2)
            try {
                val charCode = Integer.parseInt(str, 16)
                if (charCode != 0) result.append(charCode.toChar())
            } catch (e: Exception) {}
            i += 2
        }
        return result.toString()
    }

    private fun String.trimNulls(): String {
        return this.filter { it != '\u0000' }.trim()
    }

    private fun readBlockHexStrings(mifare: MifareClassic, blockIndex: Int): String {
        return bytesToHex(mifare.readBlock(blockIndex))
    }

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

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun writeHexBlock(mifare: MifareClassic, blockIndex: Int, hexString: String) {
        val bytes = ByteArray(16)
        val paddedHex = if (hexString.length % 2 != 0) "0$hexString" else hexString
        try {
            val dataBytes = hexStringToByteArray(paddedHex)
            System.arraycopy(dataBytes, 0, bytes, 0, minOf(dataBytes.size, 16))
            mifare.writeBlock(blockIndex, bytes)
        } catch (e: Exception) {
            mifare.writeBlock(blockIndex, bytes)
        }
    }

    private fun writeBlock(mifare: MifareClassic, blockIndex: Int, data: String) {
        val bytes = ByteArray(16)
        val dataBytes = data.toByteArray(Charset.forName("US-ASCII"))
        System.arraycopy(dataBytes, 0, bytes, 0, minOf(dataBytes.size, 16))
        mifare.writeBlock(blockIndex, bytes)
    }
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    val context = LocalContext.current
    val activity = context as Activity
    val manager = remember { AndroidNfcManager(activity) }
    DisposableEffect(Unit) {
        onDispose { manager.stopScanning() }
    }
    return manager
}
