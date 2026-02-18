package com.example.sitacardmaster

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private lateinit var displayAmount: TextView // Restored
    private lateinit var displayAddress: TextView
    private lateinit var displayPhone: TextView
    private lateinit var displayEmail: TextView
    private lateinit var displayWebsite: TextView
    private lateinit var displayWhatsapp: TextView
    private lateinit var newCardButton: Button
    private lateinit var deleteCardButton: Button
    private lateinit var clearButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var dashboardScroll: ScrollView
    
    // Error Views
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    
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
        displayValidUpto = findViewById(R.id.displayValidUpto)
        displayAddress = findViewById(R.id.displayAddress)
        displayPhone = findViewById(R.id.displayPhone)
        displayEmail = findViewById(R.id.displayEmail)
        displayWebsite = findViewById(R.id.displayWebsite)
        displayWhatsapp = findViewById(R.id.displayWhatsapp)
        newCardButton = findViewById(R.id.newCardButton)
        deleteCardButton = findViewById(R.id.deleteCardButton)
        clearButton = findViewById(R.id.clearButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        dashboardScroll = findViewById(R.id.dashboardScroll)
        
        errorContainer = findViewById(R.id.errorContainer)
        errorText = findViewById(R.id.errorText)
        
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val titleText = findViewById<TextView>(R.id.dashboardTitle)

        val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val adminId = sharedPref.getString("adminId", "Admin")
        val authToken = sharedPref.getString("authToken", "")
        val logoUrl = sharedPref.getString("logoUrl", null)
        titleText.text = adminId ?: "Admin"
        
        // LOG: Retrieved logo URL from SharedPreferences
        logAction("Dashboard - Retrieved Logo URL from SharedPreferences: $logoUrl")

        // Load shop logo from URL if available
        if (!logoUrl.isNullOrEmpty()) {
            // Convert relative path to full URL
            val fullLogoUrl = if (logoUrl.startsWith("http")) {
                logoUrl
            } else {
                "https://apisita.shanti-pos.com$logoUrl"
            }
            
            logAction("Dashboard - Attempting to load logo from URL: $fullLogoUrl")
            try {
                val imageLoader = coil.ImageLoader.Builder(this)
                    .build()
                val request = coil.request.ImageRequest.Builder(this)
                    .data(fullLogoUrl)
                    .target(
                        onStart = {
                            logAction("Dashboard - Logo loading started")
                        },
                        onSuccess = { result ->
                            logoCard.setImageDrawable(result)
                            logAction("Dashboard - Logo loaded successfully from: $fullLogoUrl")
                        },
                        onError = { error ->
                            logoCard.setImageResource(R.drawable.logo)
                            logAction("Dashboard - Logo loading failed: ${error?.toString()}, using default logo")
                        }
                    )
                    .placeholder(R.drawable.logo)
                    .error(R.drawable.logo)
                    .build()
                imageLoader.enqueue(request)
            } catch (e: Exception) {
                // Fallback to default logo on error
                logAction("Dashboard - Exception loading logo: ${e.message}, using default logo")
                logoCard.setImageResource(R.drawable.logo)
            }
        } else {
            // Use default logo if no URL available
            logAction("Dashboard - No logo URL available, using default logo")
            logoCard.setImageResource(R.drawable.logo)
        }

        // Verify/Fetch latest profile
        // Removed as per request - relying on stored session
        // if (!authToken.isNullOrEmpty()) { ... }

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

        clearButton.setOnClickListener {
            resetUI()
        }

        logoutButton.setOnClickListener {
            val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()
            
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        // Click listeners for contact fields
        displayAddress.setOnClickListener {
            val address = displayAddress.text.toString()
            if (address.isNotBlank() && address != "N/A") {
                openLocationInMaps(address)
            }
        }
        
        displayPhone.setOnClickListener {
            val phone = displayPhone.text.toString()
            if (phone.isNotBlank() && phone != "N/A") {
                openDialer(phone)
            }
        }
        
        displayEmail.setOnClickListener {
            val email = displayEmail.text.toString()
            if (email.isNotBlank() && email != "N/A") {
                openEmailApp(email)
            }
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
        clearButton.visibility = View.GONE
        errorContainer.visibility = View.GONE
        
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
                    clearButton.visibility = View.VISIBLE
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
                    clearButton.visibility = View.VISIBLE
                } else if (success && data == null) {
                    // Blank card
                    logAction("Card read success: Blank card")
                    statusSnackbar("No data in the card")
                    newCardButton.visibility = View.VISIBLE
                    deleteCardButton.visibility = View.VISIBLE
                    clearButton.visibility = View.VISIBLE
                } else {
                    // Error
                    logAction("Card read error: $message")
                    statusSnackbar(message)
                    newCardButton.visibility = View.VISIBLE
                    deleteCardButton.visibility = View.VISIBLE
                    clearButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showCardDetails(data: Map<String, String>) {


        detailsContainer.visibility = View.VISIBLE
        displayMemberId.text = data["memberId"] ?: "N/A"
        displayCompany.text = data["companyName"] ?: "N/A"
        displayValidUpto.text = formatDate(data["validUpto"])
        displayTotalBuy.text = "₹${data["totalBuy"] ?: "0.00"}"
        displayAmount.text = "Loading..." // Restored
        
        // Hide extra details while loading
        displayAddress.visibility = View.GONE
        displayPhone.visibility = View.GONE
        displayEmail.visibility = View.GONE
        displayWebsite.visibility = View.GONE
        displayWhatsapp.visibility = View.GONE
        
        scope.launch {
            val memberId = data["memberId"] ?: ""
            val companyName = data["companyName"] ?: ""
            
            logAction("API Request - Verifying Member: ID='$memberId', Company='$companyName'")
            
            if (memberId.isNotBlank()) {
                val cardMfid = data["card_mfid"] ?: ""
                val cardValidity = data["validUpto"] ?: ""
                val password = data["password"] ?: ""

                val result = withContext(Dispatchers.IO) {
                    memberApiClient.verifyMember(
                        memberId = memberId, 
                        companyName = companyName,
                        password = password,
                        cardMfid = cardMfid,
                        cardValidity = cardValidity
                    )
                }
                result.fold(
                    onSuccess = { response ->
                        logAction("API Response Success: $response")
                        logAction("Mapping Data - Global Total: ${response.globalTotal}, Current: ${response.currentTotal}")
                        displayTotalBuy.text = "₹${response.globalTotal}" // Global mapped to Total Buy
                        displayAmount.text = "₹${response.currentTotal}" // Restored Current Amount
                        displayValidUpto.text = formatDate(response.validity)
                        
                        // Bind Contact Info
                        bindValue(displayAddress, response.companyAddress)
                        bindValue(displayPhone, response.phoneNumber)
                        bindValue(displayEmail, response.email)
                        bindValue(displayWebsite, response.website)
                        bindValue(displayWhatsapp, response.whatsapp)
                    },
                    onFailure = { error ->
                        logAction("API Request Failed: ${error.message}")
                        
                        // Show Error UI
                        val errorMessage = error.message ?: "Member verification failed"
                        runOnUiThread {
                             showError(errorMessage)
                        }
                    }
                )
            } else {
                logAction("API Skipped: Member ID is blank")
                displayTotalBuy.text = "N/A"
                displayAmount.text = "N/A" // Restored
                displayValidUpto.text = "N/A"
            }
        }



        // Auto-scroll to details
        dashboardScroll.post {
            dashboardScroll.smoothScrollTo(0, detailsContainer.top)
        }
    }
    
    private fun showError(message: String) {


        detailsContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorText.text = message
        
        // Auto-scroll to error
        dashboardScroll.post {
            dashboardScroll.smoothScrollTo(0, errorContainer.top)
        }
    }
    
    private fun resetUI() {
         // Reset UI to initial state
        detailsContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE



        scanInstruction.text = "Tap logo to scan card"
    }

    private fun statusSnackbar(message: String) {
        logAction("Showing Snackbar: $message")
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.brand_blue, theme))
            .setTextColor(resources.getColor(R.color.white, theme))
            .show()
    }
    
    private fun bindValue(view: TextView, value: String?) {
        if (!value.isNullOrBlank() && value != "null") {
            view.text = value
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
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

    private fun openLocationInMaps(address: String) {
        try {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val intent = Intent(Intent.ACTION_VIEW, geoUri)
            intent.setPackage("com.google.android.apps.maps")
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                logAction("Opened location in Maps: $address")
            } else {
                // Fallback to browser if Maps app not available
                val browserIntent = Intent(Intent.ACTION_VIEW, geoUri)
                startActivity(browserIntent)
                logAction("Opened location in browser: $address")
            }
        } catch (e: Exception) {
            logAction("Error opening maps: ${e.message}")
            statusSnackbar("Could not open maps")
        }
    }
    
    private fun openDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
            logAction("Opened dialer for: $phoneNumber")
        } catch (e: Exception) {
            logAction("Error opening dialer: ${e.message}")
            statusSnackbar("Could not open dialer")
        }
    }
    
    private fun openEmailApp(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$email")
            startActivity(intent)
            logAction("Opened email app for: $email")
        } catch (e: Exception) {
            logAction("Error opening email app: ${e.message}")
            statusSnackbar("Could not open email app")
        }
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
