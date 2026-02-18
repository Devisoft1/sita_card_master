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

    override fun startScanning() {
        (detectedTag as MutableState<Tag?>).value = null
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
            tag?.let {
                val tagId = it.id.joinToString(":") { byte -> "%02X".format(byte) }
                platformLog("SITACardMaster", "NFC Tag Detected! ID: $tagId")
                platformLog("SITACardMaster", "Manufacturing Number: $tagId")
                platformLog("SITACardMaster", "Technologies: ${it.techList.joinToString(", ")}")
            }
            (detectedTag as MutableState<Tag?>).value = tag
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
                    platformLog("SITACardMaster", "Writing Member ID to Block 12...")
                    writeBlock(mifare, 12, memberId)
                    platformLog("SITACardMaster", "Writing Company to Block 13...")
                    writeBlock(mifare, 13, companyName)
                    platformLog("SITACardMaster", "Writing ValidUpto to Block 14...")
                    writeBlock(mifare, 14, validUpto)

                     // Sector 4 (Block 16, 17, 18)
                    if (authenticateSector(mifare, 4)) {
                        platformLog("SITACardMaster", "Writing TotalBuy to Block 16...")
                        writeBlock(mifare, 16, totalBuy)
                        platformLog("SITACardMaster", "Writing today's date to Block 17...")
                        val today = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        writeBlock(mifare, 17, today)
                        
                        platformLog("SITACardMaster", "Writing Password to Block 18: $password") // Updated Log
                        writeBlock(mifare, 18, password)
                        
                        platformLog("SITACardMaster", "All blocks written successfully!")
                        success = true
                        resultMessage = "Data written successfully!"

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
                    val memberId = readBlock(mifare, 12)
                    platformLog("SITACardMaster", "Block 12 (Member ID): $memberId")
                    
                    if (memberId.isBlank()) {
                        platformLog("SITACardMaster", "Card is blank (Block 12 is empty)")
                        success = true
                        resultData = null
                        resultMessage = "Blank card"
                    } else {
                        data["memberId"] = memberId
                        
                        val company = readBlock(mifare, 13)
                        platformLog("SITACardMaster", "Block 13 (Company): $company")
                        data["companyName"] = company
                        
                        val validUpto = readBlock(mifare, 14)
                        platformLog("SITACardMaster", "Block 14 (Valid Upto): $validUpto")
                        data["validUpto"] = validUpto

                         // Sector 4 (Block 16, 17)
                        if (authenticateSector(mifare, 4)) {
                            val totalBuy = readBlock(mifare, 16)
                            platformLog("SITACardMaster", "Block 16 (Total Buy): $totalBuy")
                            data["totalBuy"] = totalBuy
                            
                            val lastBuy = readBlock(mifare, 17)
                            platformLog("SITACardMaster", "Block 17 (Last Buy): $lastBuy")
                            data["lastBuyDate"] = lastBuy
                            
                            val password = readBlock(mifare, 18)
                            platformLog("SITACardMaster", "Block 18 (Password): $password")
                            data["password"] = password
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

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    private fun readBlock(mifare: MifareClassic, blockIndex: Int): String {
        val bytes = mifare.readBlock(blockIndex)
        val hex = bytesToHex(bytes)
        val content = String(bytes, Charset.forName("US-ASCII")).trim { it <= ' ' || it == '\u0000' }
        platformLog("SITACardMaster", "RAW Block $blockIndex [HEX]: $hex")
        platformLog("SITACardMaster", "RAW Block $blockIndex [TXT]: $content")
        return content
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
