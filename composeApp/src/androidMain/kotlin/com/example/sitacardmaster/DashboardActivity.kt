package com.example.sitacardmaster

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.card.MaterialCardView
import com.example.sitacardmaster.R
import com.google.android.material.snackbar.Snackbar


class DashboardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false

    private lateinit var logoCard: ImageView
    private lateinit var scanContainer: LinearLayout
    private lateinit var scanInstruction: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var detailsContainer: MaterialCardView
    private lateinit var displayMemberId: TextView
    private lateinit var displayCompany: TextView
    private lateinit var displayValidUpto: TextView
    private lateinit var displayTotalBuy: TextView
    private lateinit var newCardButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var dashboardScroll: ScrollView
    private val scanTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            stopScanMode()
            statusSnackbar("Scanning timed out")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        nfcManager = AndroidNfcManager(this)

        logoCard = findViewById(R.id.logoCard)
        scanContainer = findViewById(R.id.scanContainer)
        scanInstruction = findViewById(R.id.scanInstruction)
        scanProgress = findViewById(R.id.scanProgress)
        detailsContainer = findViewById(R.id.detailsContainer)
        displayMemberId = findViewById(R.id.displayMemberId)
        displayCompany = findViewById(R.id.displayCompany)
        displayValidUpto = findViewById(R.id.displayValidUpto)
        displayTotalBuy = findViewById(R.id.displayTotalBuy)
        newCardButton = findViewById(R.id.newCardButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        dashboardScroll = findViewById(R.id.dashboardScroll)
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val titleText = findViewById<TextView>(R.id.dashboardTitle)

        val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val adminId = sharedPref.getString("adminId", "Admin")
        titleText.text = adminId ?: "Admin"

        backButton.setOnClickListener {
            // Force return to login page by resetting the session flag
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()
            
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        logoCard.setOnClickListener {
            startScanMode()
        }

        stopScanButton.setOnClickListener {
            stopScanMode()
        }

        newCardButton.setOnClickListener {
            val intent = Intent(this, IssueCardActivity::class.java)
            startActivity(intent)
        }

        logoutButton.setOnClickListener {
            val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()
            
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun startScanMode() {
        isScanning = true
        scanInstruction.text = "TAP CARD NOW..."
        scanProgress.visibility = View.VISIBLE
        stopScanButton.visibility = View.VISIBLE
        detailsContainer.visibility = View.GONE
        newCardButton.visibility = View.GONE
        
        logAction("Scanning started")
        
        // Auto-stop after 1 minute
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 60000)
        
        nfcManager.startScanning()
    }

    private fun stopScanMode() {
        isScanning = false
        scanInstruction.text = "Tap logo to scan card"
        scanProgress.visibility = View.GONE
        stopScanButton.visibility = View.GONE
        
        logAction("Scanning stopped")
        
        // Move logo back to center ONLY if no data is visible
        if (detailsContainer.visibility != View.VISIBLE) {
            val params = scanContainer.layoutParams as ConstraintLayout.LayoutParams
            params.verticalBias = 0.45f
            scanContainer.layoutParams = params
        }
        
        // Clear timeout
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
        
        nfcManager.stopScanning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            nfcManager.onNewIntent(intent)
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                processCard()
            }
        }
    }

    private fun processCard() {
        scanInstruction.text = "Checking card..."
        logAction("Processing detected card")
        nfcManager.readCard { success, data, message ->
            runOnUiThread {
                stopScanMode()
                if (success && data != null) {
                    // Member exists
                    logAction("Card read success: ${data["memberId"]}")
                    showCardDetails(data)
                    newCardButton.visibility = View.VISIBLE
                } else if (success && data == null) {
                    // Blank card
                    logAction("Card read success: Blank card")
                    statusSnackbar("Blank card detected.")
                    newCardButton.visibility = View.VISIBLE
                } else {
                    // Error
                    logAction("Card read error: $message")
                    statusSnackbar(message)
                    newCardButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showCardDetails(data: Map<String, String>) {
        // Move logo to top before showing details
        val params = scanContainer.layoutParams as ConstraintLayout.LayoutParams
        params.verticalBias = 0.0f
        scanContainer.layoutParams = params

        detailsContainer.visibility = View.VISIBLE
        displayMemberId.text = data["memberId"] ?: "N/A"
        displayCompany.text = data["companyName"] ?: "N/A"
        displayValidUpto.text = formatDate(data["validUpto"])
        displayTotalBuy.text = data["totalBuy"] ?: "0.00"
        statusSnackbar("Member Found: ${data["memberId"]}")

        // Auto-scroll to details
        dashboardScroll.post {
            dashboardScroll.smoothScrollTo(0, detailsContainer.top)
        }
    }

    private fun statusSnackbar(message: String) {
        // Removed as per request (Snackbar replaced with logging if needed)
        logAction("Feedback hidden: $message")
    }

    private fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "N/A"
        
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
        return dateStr // Return original if all parsing fails
    }

    private fun logAction(action: String) {
        platformLog("SITACardMaster", "Dashboard: $action")
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }
}
