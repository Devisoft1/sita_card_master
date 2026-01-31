package com.example.sitacardmaster

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.example.sitacardmaster.R


class IssueCardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false
    private var isClearing = false

    private lateinit var memberIdInput: EditText
    private lateinit var companyNameInput: EditText
    private lateinit var validUptoInput: EditText
    private lateinit var totalBuyInput: EditText
    private lateinit var statusMessage: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var tapCardHint: TextView
    private lateinit var startScanButton: Button
    private lateinit var cancelScanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_card)

        nfcManager = AndroidNfcManager(this)

        memberIdInput = findViewById(R.id.memberId)
        companyNameInput = findViewById(R.id.companyName)
        validUptoInput = findViewById(R.id.validUpto)
        totalBuyInput = findViewById(R.id.totalBuy)
        statusMessage = findViewById(R.id.statusMessage)
        scanProgress = findViewById(R.id.scanProgress)
        tapCardHint = findViewById(R.id.tapCardHint)
        startScanButton = findViewById(R.id.startScanButton)
        cancelScanButton = findViewById(R.id.cancelScanButton)
        val backButton = findViewById<ImageButton>(R.id.backButton)

        backButton.setOnClickListener { finish() }

        startScanButton.setOnClickListener {
            if (memberIdInput.text.isEmpty() || companyNameInput.text.isEmpty()) {
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
        
        // We need a flag to differentiate read/write vs clear, or just handle in onNewIntent/checkAndWriteCard
        // Since onNewIntent triggers checkAndWriteCard, we should modify that flow or use a flag.
        // Let's use a flag.
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
                    checkAndWriteCard()
                }
            }
        }
    }

    private fun checkAndWriteCard() {
        statusMessage.text = "Checking card..."
        nfcManager.readCard { success, data, message ->
            runOnUiThread {
                if (success && data != null) {
                    // Member exists
                    val existingMemberId = data["memberId"] ?: ""
                    statusMessage.text = "Member already exist: $existingMemberId"
                    
                    // Show existing data
                    memberIdInput.setText(existingMemberId)
                    companyNameInput.setText(data["companyName"] ?: "")
                    validUptoInput.setText(formatDate(data["validUpto"]))
                    totalBuyInput.setText(data["totalBuy"] ?: "")
                    
                    logAction("Attempted write to existing card: $existingMemberId")
                    stopScanning()
                } else {
                    // Safe to write or error (writeCard will handle error if tag gone)
                    writeCard()
                }
            }
        }
    }

    private fun writeCard() {
        val memberId = memberIdInput.text.toString()
        val company = companyNameInput.text.toString()
        val validUpto = validUptoInput.text.toString()
        val totalBuy = totalBuyInput.text.toString()

        statusMessage.text = "Card detected! Writing..."
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
                        // Save to local storage
                        DatabaseHelper(this).saveIssuedCard(
                            memberId = memberId,
                            company = company,
                            validUpto = validUpto,
                            totalBuy = totalBuy
                        )
                        
                        logAction("Local Storage: Saved Member $memberId")
                        stopScanning()
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
                    totalBuyInput.setText("")
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
