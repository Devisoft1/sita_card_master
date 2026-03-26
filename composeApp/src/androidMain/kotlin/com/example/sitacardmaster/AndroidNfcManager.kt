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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.Charset

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
            nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, intentFilters, null)
        } catch (e: IllegalStateException) {
            platformLog("SITACardMaster", "Failed to enable foreground dispatch: ${e.message}")
        }
    }

    override fun stopScanning() {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (e: IllegalStateException) {
            platformLog("SITACardMaster", "Failed to disable foreground dispatch: ${e.message}")
        }
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
                
                // Heuristic 1: Duplicate Techs (Key indicator seen in user logs)
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
                platformLog("SITACardMaster", "Manufacturing Number: $logTagId")
                platformLog("SITACardMaster", "Technologies: ${it.techList.joinToString(", ")}")
            }
            (detectedTag as MutableState<Tag?>).value = tag
            (detectedTagId as MutableState<String?>).value = currentTagId
        }
    }

    private fun authenticateSector(mifare: MifareClassic, sector: Int): Boolean {
        platformLog("SITACardMaster", "Authenticating Sector $sector...")
        
        // Comprehensive Key Dictionary from various sources
        val commonKeys = arrayOf(
            MifareClassic.KEY_DEFAULT, // FF FF FF FF FF FF
            MifareClassic.KEY_NFC_FORUM, // D3 F7 D3 F7 D3 F7
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY, // A0 A1 A2 A3 A4 A5
            byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
            byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
            byteArrayOf(0x4D.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x32.toByte(), 0x33.toByte()),
            byteArrayOf(0x1A.toByte(), 0x2B.toByte(), 0x3C.toByte(), 0x4D.toByte(), 0x5E.toByte(), 0x6F.toByte()),
            byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
            byteArrayOf(0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte()),
            byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte())
        )

        val keyNames = arrayOf("DEFAULT", "NFC_FORUM", "MAD", "ZERO", "B0B1", "M101", "1A2B", "A0B0", "1122", "8888")

        for (i in commonKeys.indices) {
            val key = commonKeys[i]
            val keyName = keyNames[i]
            
            // Try Key A
            try {
                if (mifare.authenticateSectorWithKeyA(sector, key)) {
                    platformLog("SITACardMaster", "Authenticated Sector $sector using Key A ($keyName)")
                    return true
                }
            } catch (e: Exception) { 
                if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) throw e
            }

            // Try Key B
            try {
                if (mifare.authenticateSectorWithKeyB(sector, key)) {
                    platformLog("SITACardMaster", "Authenticated Sector $sector using Key B ($keyName)")
                    return true
                }
            } catch (e: Exception) { 
                if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) throw e
            }
        }

        platformLog("SITACardMaster", "Authentication failed for Sector $sector after trying all common A/B keys")
        return false
    }

    private fun logCardDiagnostics(mifare: MifareClassic) {
        try {
            val typeStr = when(mifare.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown (${mifare.type})"
            }
            platformLog("SITACardMaster", "Card Info - Type: $typeStr, Size: ${mifare.size} bytes, Sectors: ${mifare.sectorCount}")
            
            // Baseline test for Sectors 0, 1, 2
            for (s in 0..2) {
                val success = authenticateSector(mifare, s)
                platformLog("SITACardMaster", "Baseline - Sector $s Auth: ${if(success) "SUCCESS" else "FAILED"}")
            }
            
            if (!authenticateSector(mifare, 3)) {
                checkNdef(mifare.tag)
                checkUltralight(mifare.tag)
            }
        } catch (e: Exception) {
            platformLog("SITACardMaster", "Diagnostic Error: ${e.message}")
        }
    }

    private fun checkNdef(tag: Tag) {
        try {
            val ndef = android.nfc.tech.Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                platformLog("SITACardMaster", "NDEF Info - Type: ${ndef.type}, Size: ${ndef.maxSize} bytes")
                val msg = ndef.ndefMessage
                if (msg != null) {
                    platformLog("SITACardMaster", "NDEF Message found with ${msg.records.size} records")
                }
                ndef.close()
            }
        } catch (e: Exception) {
            platformLog("SITACardMaster", "NDEF Check Error: ${e.message}")
        }
    }

    private fun checkUltralight(tag: Tag) {
        try {
            val ultralight = android.nfc.tech.MifareUltralight.get(tag)
            if (ultralight != null) {
                ultralight.connect()
                platformLog("SITACardMaster", "Ultralight Info - Type: ${ultralight.type}")
                // Read first page just to verify
                val page = ultralight.readPages(0)
                platformLog("SITACardMaster", "Ultralight Page 0: ${bytesToHex(page)}")
                ultralight.close()
            }
        } catch (e: Exception) {
            platformLog("SITACardMaster", "Ultralight Check Error: ${e.message}")
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
            onResult(false, "No card detected (or multiple cards present). Please tap only one card.")
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
                platformLog("SITACardMaster", "Connecting to Mifare card for Write...")
                mifare.connect()
                logCardDiagnostics(mifare)
                platformLog("SITACardMaster", "Connected. Checking Sector 3...")

                // Sector 3 (Blocks 12, 13, 14)
                if (authenticateSector(mifare, 3)) {
                    platformLog("SITACardMaster", "Writing Member ID (Hex) to Block 12...")
                    // Treating MemberID as ASCII Hex
                    writeHexBlock(mifare, 12, stringToHex(memberId))
                    
                    // Write Company Name as ASCII Hex
                    platformLog("SITACardMaster", "Writing Company (Hex) to Block 13...")
                    writeHexBlock(mifare, 13, stringToHex(companyName))
            
                    platformLog("SITACardMaster", "Writing ValidUpto (Hex) to Block 14...")
                    // Convert DD-MM-YYYY or DD/MM/YYYY to DDMMYYYY for Hex storage
                    val cleanDate = validUpto.replace("-", "").replace("/", "")
                    writeHexBlock(mifare, 14, cleanDate)

                     // Sector 4 (Block 16, 17, 18)
                    if (authenticateSector(mifare, 4)) {
                        platformLog("SITACardMaster", "Writing TotalBuy (Hex) to Block 16...")
                        writeHexBlock(mifare, 16, stringToHex(totalBuy))
                        
                        platformLog("SITACardMaster", "Writing today's date (Hex) to Block 17...")
                        val today = java.text.SimpleDateFormat("ddMMyyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        writeHexBlock(mifare, 17, today)
                        
                        platformLog("SITACardMaster", "Writing Password (Hex) to Block 18: $password")
                        writeHexBlock(mifare, 18, stringToHex(password))

                        // Sector 5 (Block 20) for Card Type
                        if (authenticateSector(mifare, 5)) {
                            platformLog("SITACardMaster", "Writing Card Type (Hex) to Block 20: $cardType")
                            writeHexBlock(mifare, 20, stringToHex(cardType))

                            platformLog("SITACardMaster", "All blocks written successfully!")
                            success = true
                            resultMessage = "Data written successfully!"
                        } else {
                            success = false
                            resultMessage = "Card not detected properly please scan again"
                        }

                    } else {
                         success = false
                         resultMessage = "Card not detected properly please scan again"
                    }
                } else {
                   success = false
                   resultMessage = "Card not detected properly please scan again"
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
                    if (mifare.isConnected) {
                        mifare.close()
                    }
                    platformLog("SITACardMaster", "Mifare connection closed (writeCard)")
                } catch (e: Exception) {
                    platformLog("SITACardMaster", "Error closing Mifare: ${e.message}")
                }
            }
            
            // Callback AFTER closing connection
            onResult(success, resultMessage)
        }.start()
    }

    override fun writeLogoUrl(url: String, onResult: (Boolean, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            onResult(false, "No card detected (or multiple cards present). Please tap only one card.")
            return
        }

        // Try NDEF first
        val ndef = Ndef.get(tag as Tag)
        if (ndef != null) {
            Thread {
                var success = false
                var resultMessage = ""
                try {
                    platformLog("SITACardMaster", "🔗 Preparing to write URL via NDEF: '$url'")
                    ndef.connect()
                    if (!ndef.isWritable) {
                        resultMessage = "Tag is read-only"
                    } else {
                        val uriRecord = NdefRecord.createUri(url)
                        val message = NdefMessage(arrayOf(uriRecord))
                        
                        if (ndef.maxSize < message.byteArrayLength) {
                            resultMessage = "Data too large for tag (Max: ${ndef.maxSize}b, Data: ${message.byteArrayLength}b)"
                        } else {
                            ndef.writeNdefMessage(message)
                            success = true
                            resultMessage = "Logo URL written successfully!"
                            platformLog("SITACardMaster", "✅ NDEF Write Success")
                        }
                    }
                } catch (e: Exception) {
                    platformLog("SITACardMaster", "❌ NDEF Write Error: ${e.message}")
                    resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                        "Card not detected properly please scan again"
                    } else {
                        "Write failed: ${e.message}"
                    }
                } finally {
                    try { if (ndef.isConnected) ndef.close() } catch (e: Exception) {}
                }
                onResult(success, resultMessage)
            }.start()
            return
        }

        // Fallback to Mifare Classic Block 21 if it's a Mifare Classic tag but not NDEF formatted
        val mifare = MifareClassic.get(tag as Tag)
        if (mifare != null) {
            Thread {
                var success = false
                var resultMessage = ""
                try {
                    platformLog("SITACardMaster", "⚠️ NDEF not supported, falling back to Mifare Classic Block 21")
                    mifare.connect()
                    if (authenticateSector(mifare, 5)) {
                        platformLog("SITACardMaster", "Writing Logo URL (Hex) to Block 21: $url")
                        writeHexBlock(mifare, 21, stringToHex(url))
                        success = true
                        resultMessage = "Logo URL written successfully!"
                    } else {
                        resultMessage = "Card not detected properly please scan again"
                    }
                } catch (e: Exception) {
                    platformLog("SITACardMaster", "Write Logo URL Error: ${e.message}")
                    resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                        "Card not detected properly please scan again"
                    } else {
                        "Write failed: ${e.message}"
                    }
                } finally {
                    try { if (mifare.isConnected) mifare.close() } catch (e: Exception) {}
                }
                onResult(success, resultMessage)
            }.start()
        } else {
            onResult(false, "This card is not supported.")
        }
    }

    override fun readCard(onResult: (Boolean, Map<String, String>?, String) -> Unit) {
        val tag = detectedTag.value
        if (tag == null) {
            platformLog("SITACardMaster", "Read failed: No tag in state")
            onResult(false, null, "No card detected.")
            return
        }

        val mifare = MifareClassic.get(tag as Tag)
        if (mifare == null) {
            platformLog("SITACardMaster", "Read failed: Not a Mifare Classic card")
            onResult(false, null, "Card not supported.")
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

                // Add MFID to data
                val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
                data["card_mfid"] = tagId


                // Sector 3 (Blocks 12, 13, 14)
                if (authenticateSector(mifare, 3)) {
                    val memberIdHex = readBlockHexStrings(mifare, 12)
                    platformLog("SITACardMaster", "Block 12 (Member ID Hex): $memberIdHex")
                    
                    if (memberIdHex.replace(" ", "").all { it == '0' }) {
                        platformLog("SITACardMaster", "Card is blank (Block 12 is empty)")
                        success = true
                        resultData = null
                        resultMessage = "Blank card"
                    } else {
                        // Decode Member ID with Smart Decode
                        data["memberId"] = smartDecode(memberIdHex)
                        
                        val companyHex = readBlockHexStrings(mifare, 13)
                        platformLog("SITACardMaster", "Block 13 (Company Hex): $companyHex")
                        // Decode Hex to ASCII for Company Name
                        val companyAscii = hexToString(companyHex.replace(" ", ""))
                        data["companyName"] = companyAscii.trimNulls()
                        
                        val validUptoHex = readBlockHexStrings(mifare, 14)
                        platformLog("SITACardMaster", "Block 14 (Valid Upto Hex): $validUptoHex")
                        val validUpto = formatHexDate(validUptoHex)
                        data["validUpto"] = validUpto

                         // Sector 4 (Block 16, 17)
                        if (authenticateSector(mifare, 4)) {
                            val totalBuyHex = readBlockHexStrings(mifare, 16)
                            platformLog("SITACardMaster", "Block 16 (Total Buy Hex): $totalBuyHex")
                            // Decode Hex to ASCII for Total Buy
                            val totalBuyAscii = hexToString(totalBuyHex.replace(" ", ""))
                            data["totalBuy"] = totalBuyAscii.trimNulls()
                            
                            val lastBuyHex = readBlockHexStrings(mifare, 17)
                            platformLog("SITACardMaster", "Block 17 (Last Buy Hex): $lastBuyHex")
                            data["lastBuyDate"] = formatHexDate(lastBuyHex)
                            
                            val passwordHex = readBlockHexStrings(mifare, 18)
                            platformLog("SITACardMaster", "Block 18 (Password Hex): $passwordHex")
                            // Decode Hex to ASCII for Password
                            val passwordAscii = hexToString(passwordHex.replace(" ", ""))
                            data["password"] = passwordAscii.trimNulls()

                            // Sector 5 (Block 20)
                            if (authenticateSector(mifare, 5)) {
                                val cardTypeHex = readBlockHexStrings(mifare, 20)
                                platformLog("SITACardMaster", "Block 20 (Card Type Hex): $cardTypeHex")
                                // Decode Hex to ASCII/String for Card Type
                                val cardTypeAscii = hexToString(cardTypeHex.replace(" ", ""))
                                data["cardType"] = cardTypeAscii.trimNulls()
                            } else {
                                platformLog("SITACardMaster", "Sector 5 Authentication Failed")
                            }

                        } else {
                            platformLog("SITACardMaster", "Sector 4 Authentication Failed")
                        }

                        platformLog("SITACardMaster", "Full Card Data: $data")
                        success = true
                        resultData = data
                        resultMessage = "Data read successfully"
                    }
                } else {
                    platformLog("SITACardMaster", "Sector 3 Authentication Failed")
                    success = false
                    resultMessage = "Card not detected properly please scan again"
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Read Exception: ${e.message}")
                success = false
                resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                    "Card not detected properly please scan again"
                } else {
                    "Read error: ${e.message}"
                }
            } finally {
                try {
                    if (mifare.isConnected) {
                        mifare.close()
                    }
                    platformLog("SITACardMaster", "Mifare connection closed (readCard)")
                } catch (e: Exception) {
                    platformLog("SITACardMaster", "Error closing Mifare: ${e.message}")
                }
            }
            
            // Callback AFTER closing connection
            onResult(success, resultData, resultMessage)
        }.start()
    }

    private fun smartDecode(hexStr: String): String {
        // 1. Try standard ASCII decode
        val ascii = hexToString(hexStr)
        val cleanAscii = ascii.trimNulls()
        
        // 2. Check heuristically if it looks like garbage/control characters
        // Valid text should mostly be printable (ASCII 32-126)
        // If we see chars < 32 (except 0 which is padding), it's likely raw data
        val hasControlChars = cleanAscii.any { it.code < 32 && it.code != 0 }
        
        // Also check if it's empty but the input wasn't just zeros
        val rawInput = hexStr.replace(" ", "").replace("00", "")
        val isNotEmptyButDecodedEmpty = rawInput.isNotEmpty() && cleanAscii.isEmpty()

        if (hasControlChars || isNotEmptyButDecodedEmpty) {
             platformLog("SITACardMaster", "SmartDecode: Detected non-ASCII content ($cleanAscii), treating Hex as String")
             // Fallback: "10 10" -> "1010"
             val sb = StringBuilder()
             val parts = hexStr.trim().split(" ")
             for (p in parts) {
                 if (p == "00") break // Stop at null terminator
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
                if (charCode != 0) { // Skip null bytes
                    result.append(charCode.toChar())
                }
            } catch (e: NumberFormatException) {
                // Ignore invalid
            }
            i += 2
        }
        return result.toString()
    }
    
    // Extension to clean up string from nulls and extra spaces if needed
    private fun String.trimNulls(): String {
        return this.filter { it != '\u0000' }.trim()
    }

    private fun readBlockHexStrings(mifare: MifareClassic, blockIndex: Int): String {
        val bytes = mifare.readBlock(blockIndex)
        return bytesToHex(bytes)
    }

    private fun formatHexDate(hex: String): String {
        // Hex: "18 02 20 26 00 ..." -> "18022026"
        val clean = hex.replace(" ", "")
        if (clean.length >= 8) {
            val d = clean.substring(0, 2)
            val m = clean.substring(2, 4)
            val y = clean.substring(4, 8)
            // validating if digits
             if (d.all { it.isDigit() } && m.all { it.isDigit() } && y.all { it.isDigit() }) {
                 return "$d-$m-$y"
             }
        }
        return ""
    }

    private fun readBlock(mifare: MifareClassic, blockIndex: Int): String {
        val bytes = mifare.readBlock(blockIndex)
        val hex = bytesToHex(bytes)
        val content = String(bytes, Charset.forName("US-ASCII")).trim { it <= ' ' || it == '\u0000' }
        platformLog("SITACardMaster", "RAW Block $blockIndex [HEX]: $hex")
        platformLog("SITACardMaster", "RAW Block $blockIndex [TXT]: $content")
        return content
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
        // Ensure even length for hex conversion
        val paddedHex = if (hexString.length % 2 != 0) "0$hexString" else hexString
        
        try {
            val dataBytes = hexStringToByteArray(paddedHex)
            System.arraycopy(dataBytes, 0, bytes, 0, minOf(dataBytes.size, 16))
            mifare.writeBlock(blockIndex, bytes)
        } catch (e: Exception) {
            platformLog("SITACardMaster", "Error formatting Hex for Block $blockIndex: ${e.message}")
             // Fallback to ASCII if Hex fails? Or just write empty?
             // Writing what we can
             mifare.writeBlock(blockIndex, bytes)
        }
    }
    
    private fun writeBlock(mifare: MifareClassic, blockIndex: Int, data: String) {
        val bytes = ByteArray(16)
        val dataBytes = data.toByteArray(Charset.forName("US-ASCII"))
        System.arraycopy(dataBytes, 0, bytes, 0, minOf(dataBytes.size, 16))
        mifare.writeBlock(blockIndex, bytes)
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
                platformLog("SITACardMaster", "Connecting to clear card...")
                mifare.connect()

                    if (authenticateSector(mifare, 3)) {
                        platformLog("SITACardMaster", "Clearing card data...")
                        writeBlock(mifare, 12, "")
                        writeBlock(mifare, 13, "")
                        writeBlock(mifare, 14, "")

                        if (authenticateSector(mifare, 4)) {
                            platformLog("SITACardMaster", "Clearing card data...")
                            writeBlock(mifare, 16, "")
                            writeBlock(mifare, 17, "")
                            writeBlock(mifare, 18, "") // Also clear password block
                            
                            if (authenticateSector(mifare, 5)) {
                                platformLog("SITACardMaster", "Clearing card data...")
                                writeBlock(mifare, 20, "")
                                success = true
                                resultMessage = "Card cleared successfully."
                            } else {
                                success = false
                                resultMessage = "Card not detected properly please scan again"
                            }
                        } else {
                            success = false
                            resultMessage = "Card not detected properly please scan again"
                        }
                    } else {
                        success = false
                        resultMessage = "Card not detected properly please scan again"
                    }
            } catch (e: Exception) {
                platformLog("SITACardMaster", "Clear Error: ${e.message}")
                success = false
                resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                    "Card not detected properly please scan again"
                } else {
                    "Error: ${e.message}"
                }
            } finally {
                try {
                    if (mifare.isConnected)  mifare.close()
                } catch (e: Exception) { }
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
                platformLog("SITACardMaster", "Connecting to delete card data...")
                mifare.connect()

                // Wipe sectors 3, 4, and 5 which contain the member data
                val sectorsToWipe = intArrayOf(3, 4, 5)
                var sectorsWiped = 0

                for (sector in sectorsToWipe) {
                    if (authenticateSector(mifare, sector)) {
                        platformLog("SITACardMaster", "Wiping card data...")
                        val firstBlock = mifare.sectorToBlock(sector)
                        val numBlocks = mifare.getBlockCountInSector(sector)
                        
                        // Wipe all blocks in sector EXCEPT the last one (sector trailer)
                        for (i in 0 until (numBlocks - 1)) {
                            val blockIndex = firstBlock + i
                            platformLog("SITACardMaster", "Wiping Block $blockIndex")
                            mifare.writeBlock(blockIndex, ByteArray(16))
                        }
                        sectorsWiped++
                    } else {
                        platformLog("SITACardMaster", "Failed to authenticate Sector $sector for wiping")
                    }
                }

                if (sectorsWiped > 0) {
                    success = true
                    resultMessage = "Card data deleted successfully."
                } else {
                    success = false
                    resultMessage = "Card not detected properly please scan again"
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Delete Data Error: ${e.message}")
                success = false
                resultMessage = if (e is java.io.IOException || e.message?.contains("Tag lost", ignoreCase = true) == true) {
                    "Card not detected properly please scan again"
                } else {
                    "Error: ${e.message}"
                }
            } finally {
                try {
                    if (mifare.isConnected) mifare.close()
                } catch (e: Exception) { }
            }
            onResult(success, resultMessage)
        }.start()
    }
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    val context = LocalContext.current
    val activity = context as Activity
    val manager = remember { AndroidNfcManager(activity) }
    
    DisposableEffect(Unit) {
        onDispose {
            manager.stopScanning()
        }
    }
    
    return manager
}
