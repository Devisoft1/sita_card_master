package com.example.sitacardmaster

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
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

    override fun startScanning() {
        (detectedTag as MutableState<Tag?>).value = null
        (detectedTagId as MutableState<String?>).value = null
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
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            var currentTagId: String? = null
            tag?.let {
                val tagId = it.id.joinToString("") { byte -> "%02X".format(byte) }
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
        
        // Try Default Key (Factory Default)
        platformLog("SITACardMaster", "Attempting with KEY_DEFAULT...")
        if (mifare.authenticateSectorWithKeyA(sector, MifareClassic.KEY_DEFAULT)) {
            platformLog("SITACardMaster", "Authenticated with KEY_DEFAULT")
            return true
        }
        
        // Try NFC Forum Key (Commonly used after formatting)
        platformLog("SITACardMaster", "Attempting with KEY_NFC_FORUM...")
        if (mifare.authenticateSectorWithKeyA(sector, MifareClassic.KEY_NFC_FORUM)) {
            platformLog("SITACardMaster", "Authenticated with KEY_NFC_FORUM")
            return true
        }

        platformLog("SITACardMaster", "Authentication failed for Sector $sector with all known keys")
        return false
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
            onResult(false, "No card detected. Please tap a card.")
            return
        }

        val mifare = MifareClassic.get(tag)
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
                            resultMessage = "Authentication failed for Sector 5."
                        }

                    } else {
                         success = false
                         resultMessage = "Authentication failed for Sector 4."
                    }
                } else {
                   success = false
                   resultMessage = "Authentication failed for Sector 3. Please ensure the card is a standard Mifare Classic card."
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Write Error: ${e.message}")
                success = false
                resultMessage = "Error: ${e.message}"
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
                    resultMessage = "Auth failed for Sector 3"
                }

            } catch (e: Exception) {
                platformLog("SITACardMaster", "Read Exception: ${e.message}")
                success = false
                resultMessage = "Read error: ${e.message}"
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

        val mifare = MifareClassic.get(tag)
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
                    platformLog("SITACardMaster", "Clearing Sector 3...")
                    writeBlock(mifare, 12, "") // Empty string fills with 0s in writeBlock implementation? 
                    // Wait, my writeBlock uses ByteArray(16) which is 0-init, then copies data. 
                    // So passing "" makes it all 0s. Correct.
                    writeBlock(mifare, 13, "")
                    writeBlock(mifare, 14, "")

                    if (authenticateSector(mifare, 4)) {
                        platformLog("SITACardMaster", "Clearing Sector 4...")
                        writeBlock(mifare, 16, "")
                        writeBlock(mifare, 17, "")
                        success = true
                        resultMessage = "Card cleared successfully."
                    } else {
                        success = false
                        resultMessage = "Failed to auth Sector 4."
                    }
                } else {
                    success = false
                    resultMessage = "Failed to auth Sector 3."
                }
            } catch (e: Exception) {
                platformLog("SITACardMaster", "Clear Error: ${e.message}")
                success = false
                resultMessage = "Error: ${e.message}"
            } finally {
                try {
                    if (mifare.isConnected)  mifare.close()
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
