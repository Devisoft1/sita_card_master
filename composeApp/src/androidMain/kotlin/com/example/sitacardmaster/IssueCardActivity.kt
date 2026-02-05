package com.example.sitacardmaster

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.sitacardmaster.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class IssueCardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false
    private var isClearing = false

    private lateinit var memberIdInput: EditText
    private lateinit var companyNameInput: EditText
    private lateinit var validUptoInput: EditText
    // private lateinit var totalBuyInput: EditText // Removed
    private lateinit var statusMessage: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var tapCardHint: TextView
    private lateinit var startScanButton: Button
    private lateinit var cancelScanButton: Button
    private val apiClient = com.example.sitacardmaster.network.MemberApiClient()
    private val coroutineScope = kotlinx.coroutines.MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_card)

        nfcManager = AndroidNfcManager(this)

        memberIdInput = findViewById(R.id.memberId)
        companyNameInput = findViewById(R.id.companyName)
        validUptoInput = findViewById(R.id.validUpto)
        // totalBuyInput = findViewById(R.id.totalBuy) // Removed
        statusMessage = findViewById(R.id.statusMessage)
        scanProgress = findViewById(R.id.scanProgress)
        tapCardHint = findViewById(R.id.tapCardHint)
        startScanButton = findViewById(R.id.startScanButton)
        cancelScanButton = findViewById(R.id.cancelScanButton)
        val backButton = findViewById<ImageButton>(R.id.backButton)

        backButton.setOnClickListener { finish() }

        startScanButton.setOnClickListener {
            if (memberIdInput.text.isEmpty() || companyNameInput.text.isEmpty()) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please fill all fields"
                return@setOnClickListener
            }
            startScanning()
        }

        validUptoInput.setOnClickListener {
            showDatePickerDialog()
        }

        cancelScanButton.setOnClickListener {
            stopScanning()
        }
        
        findViewById<Button>(R.id.clearCardButton).setOnClickListener {
             startClearCard()
        }
    }

    private fun startClearCard() {
        isScanning = true
        statusMessage.text = "Tap Card to Clear Data..."
        scanProgress.visibility = View.VISIBLE
        tapCardHint.visibility = View.VISIBLE
        startScanButton.visibility = View.GONE
        cancelScanButton.visibility = View.VISIBLE
        findViewById<Button>(R.id.clearCardButton).visibility = View.GONE
        
        logAction("Clear Card Scanning started")
        nfcManager.startScanning()
        isClearing = true
    }

    private fun showDatePickerDialog() {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                validUptoInput.setText(date)
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun startScanning() {
        isScanning = true
        statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
        statusMessage.text = "Scanning... Tap Card"
        scanProgress.visibility = View.VISIBLE
        tapCardHint.visibility = View.VISIBLE
        startScanButton.visibility = View.GONE
        cancelScanButton.visibility = View.VISIBLE
        logAction("Scanning started for Member: ${memberIdInput.text}")
        nfcManager.startScanning()
    }

    private fun stopScanning() {
        isScanning = false
        isClearing = false
        statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
        statusMessage.text = "Ready to write"
        scanProgress.visibility = View.GONE
        tapCardHint.visibility = View.GONE
        startScanButton.visibility = View.VISIBLE
        cancelScanButton.visibility = View.GONE
        findViewById<Button>(R.id.clearCardButton).visibility = View.VISIBLE
        nfcManager.stopScanning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            nfcManager.onNewIntent(intent)
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                if (isClearing) {
                    performClearCard()
                } else {
                    // Start Verification Process
                    verifyAndProcessCard(tag)
                }
            }
        }
    }

    private fun verifyAndProcessCard(tag: Tag) {
        // Tag ID (MFID)
        val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
        val memberId = memberIdInput.text.toString()
        val company = companyNameInput.text.toString()
        val validUpto = validUptoInput.text.toString()

        runOnUiThread {
             statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
             statusMessage.text = "Verifying with API..."
        }
        
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { // Using GlobalScope for simplicity in Activity for now, ideally LifecycleScope
             logAction("API Request: Member=$memberId, Company=$company, MFID=$tagId, Validity=$validUpto")
             val result = apiClient.verifyMember(
                 memberId = memberId,
                 companyName = company,
                 cardMfid = tagId,
                 cardValidity = validUpto
             )
             
             runOnUiThread {
                 if (result.isSuccess) {
                     statusMessage.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                     statusMessage.text = "Member Verified! Writing to Card..."
                     writeCard()
                 } else {
                     val error = result.exceptionOrNull()?.message ?: "Verification Failed"
                     statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                     statusMessage.text = error
                     stopScanning()
                 }
             }
        }
    }

    private fun writeCard() {
        val memberId = memberIdInput.text.toString()
        val company = companyNameInput.text.toString()
        val validUpto = validUptoInput.text.toString()
        // val totalBuy = totalBuyInput.text.toString() // Removed
        val totalBuy = "0" // Defaulting to 0 since input is removed

        nfcManager.writeCard(
            memberId = memberId,
            companyName = company,
            validUpto = validUpto,
            totalBuy = totalBuy,
            onResult = { success, message ->
                runOnUiThread {
                    statusMessage.text = message
                    logAction("Write Result: $message")
                    if (success) {
                        statusMessage.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        // Save to local storage
                        // Assuming DatabaseHelper.saveIssuedCard signature might still need 'totalBuy', passing "0" or checking if it needs update
                        // If DatabaseHelper is strictly defined, I might need to update it too if I want to remove it there.
                        // For now sticking to minimal changes as "make ui also" was the ask.
                        try {
                             DatabaseHelper(this).saveIssuedCard(
                                memberId = memberId,
                                company = company,
                                validUpto = validUpto,
                                totalBuy = totalBuy
                            )
                            logAction("Local Storage: Saved Member $memberId")
                        } catch (e: Exception) {
                            logAction("Local Storage Error: ${e.message}")
                        }
                       
                        stopScanning()
                    } else {
                        statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                    }
                }
            }
        )
    }

    private fun performClearCard() {
        statusMessage.text = "Clearing card data..."
        nfcManager.clearCard { success, message ->
            runOnUiThread {
                statusMessage.text = message
                logAction("Clear Result: $message")
                
                if (success) {
                    memberIdInput.setText("")
                    companyNameInput.setText("")
                    validUptoInput.setText("")
                    // totalBuyInput.setText("")
                }
                stopScanning()
            }
        }
    }

    private fun logAction(action: String) {
        platformLog("SITACardMaster", "IssueCard: $action")
    }

    private fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        
        val inputFormats = arrayOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy")
        val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        
        for (format in inputFormats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) return outputFormat.format(date)
            } catch (e: Exception) {
                // Try next format
            }
        }
        return dateStr
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }
}
