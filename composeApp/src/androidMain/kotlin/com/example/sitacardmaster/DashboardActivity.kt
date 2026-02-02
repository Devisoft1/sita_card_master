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
import com.example.sitacardmaster.network.MemberApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DashboardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false
    private var isDeleteMode = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private val memberApiClient = MemberApiClient()

    private lateinit var logoCard: ImageView
    private lateinit var scanContainer: LinearLayout
    private lateinit var scanInstruction: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var detailsContainer: MaterialCardView
    private lateinit var displayMemberId: TextView
    private lateinit var displayCompany: TextView
    private lateinit var displayValidUpto: TextView
    private lateinit var displayTotalBuy: TextView
    private lateinit var displayAmount: TextView
    private lateinit var displayGlobalTotal: TextView
    private lateinit var displayMembershipValidity: TextView
    private lateinit var newCardButton: Button
    private lateinit var deleteCardButton: Button
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
        displayAmount = findViewById(R.id.displayAmount)
        displayGlobalTotal = findViewById(R.id.displayGlobalTotal)
        displayMembershipValidity = findViewById(R.id.displayMembershipValidity)
        newCardButton = findViewById(R.id.newCardButton)
        deleteCardButton = findViewById(R.id.deleteCardButton)
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

        deleteCardButton.setOnClickListener {
            isDeleteMode = true
            startScanMode()
            scanInstruction.text = "TAP CARD TO DELETE DATA..."
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
        deleteCardButton.visibility = View.GONE
        
        logAction("Scanning started")
        
        // Auto-stop after 1 minute
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 60000)
        
        nfcManager.startScanning()
    }

    private fun stopScanMode() {
        isScanning = false
        isDeleteMode = false
        scanInstruction.text = "Tap logo to scan card"
        scanProgress.visibility = View.GONE
        stopScanButton.visibility = View.GONE
        
        logAction("Scanning stopped")
        
        // Move logo back to center ONLY if no data is visible
        if (detailsContainer.visibility != View.VISIBLE) {
            val params = scanContainer.layoutParams as ConstraintLayout.LayoutParams
            params.verticalBias = 0.5f
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
        if (isDeleteMode) {
            scanInstruction.text = "Deleting data..."
            logAction("Processing delete card")
            nfcManager.clearCard { success, message ->
                runOnUiThread {
                    stopScanMode()
                    if (success) {
                        logAction("Card cleared successfully")
                        statusSnackbar("Card data deleted successfully!")
                    } else {
                        logAction("Card clear error: $message")
                        statusSnackbar("Delete Failed: $message")
                    }
                    newCardButton.visibility = View.VISIBLE
                    deleteCardButton.visibility = View.VISIBLE
                }
            }
            return
        }

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
                    deleteCardButton.visibility = View.VISIBLE
                } else if (success && data == null) {
                    // Blank card
                    logAction("Card read success: Blank card")
                    statusSnackbar("No data in the card")
                    newCardButton.visibility = View.VISIBLE
                    deleteCardButton.visibility = View.VISIBLE
                } else {
                    // Error
                    logAction("Card read error: $message")
                    statusSnackbar(message)
                    newCardButton.visibility = View.VISIBLE
                    deleteCardButton.visibility = View.VISIBLE
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
        displayTotalBuy.text = "₹${data["totalBuy"] ?: "0.00"}"
        
        displayAmount.text = "Loading..."
        displayGlobalTotal.text = "Loading..."
        displayMembershipValidity.text = "Loading..."
        
        scope.launch {
            val memberId = data["memberId"] ?: ""
            val companyName = data["companyName"] ?: ""
            
            logAction("API Request - Verifying Member: ID='$memberId', Company='$companyName'")
            
            if (memberId.isNotBlank()) {
                val cardMfid = data["card_mfid"] ?: ""
                val cardValidity = data["validUpto"] ?: ""

                val result = withContext(Dispatchers.IO) {
                    memberApiClient.verifyMember(
                        memberId = memberId, 
                        companyName = companyName,
                        cardMfid = cardMfid,
                        cardValidity = cardValidity
                    )
                }
                result.fold(
                    onSuccess = { response ->
                        logAction("API Response Success: $response")
                        logAction("Mapping Data - Current Total: ${response.currentTotal}, Global: ${response.globalTotal}")
                        displayAmount.text = "₹${response.currentTotal}"
                        displayGlobalTotal.text = "₹${response.globalTotal}"
                        displayMembershipValidity.text = response.validity?.take(10) ?: "N/A"
                    },
                    onFailure = { error ->
                        logAction("API Request Failed: ${error.message}")
                        displayAmount.text = "N/A"
                        displayGlobalTotal.text = "N/A"
                        displayMembershipValidity.text = "N/A"
                    }
                )
            } else {
                logAction("API Skipped: Member ID is blank")
                displayAmount.text = "N/A"
                displayGlobalTotal.text = "N/A"
                displayMembershipValidity.text = "N/A"
            }
        }

        statusSnackbar("Member Found: ${data["memberId"]}")

        // Auto-scroll to details
        dashboardScroll.post {
            dashboardScroll.smoothScrollTo(0, detailsContainer.top)
        }
    }

    private fun statusSnackbar(message: String) {
        logAction("Showing Snackbar: $message")
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.brand_blue, theme))
            .setTextColor(resources.getColor(R.color.white, theme))
            .show()
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
        android.util.Log.i("SITACardMaster_Verbose", "Dashboard: $action") // Duplicate to Info log in case Debug is filtered
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }
}
