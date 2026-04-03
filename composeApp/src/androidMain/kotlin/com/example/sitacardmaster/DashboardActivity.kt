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
import com.google.android.material.card.MaterialCardView
import com.example.sitacardmaster.R
import com.google.android.material.snackbar.Snackbar
import com.example.sitacardmaster.network.MemberApiClient
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
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
    private lateinit var displayAddress: TextView
    private lateinit var displayPhone: TextView
    private lateinit var displayEmail: TextView
    private lateinit var displayWebsite: TextView
    private lateinit var displayWhatsapp: TextView
    private lateinit var displayStatus: TextView
    private lateinit var premiumMemberLabel: TextView
    private lateinit var newCardButton: Button
    private lateinit var clearButton: Button
    private lateinit var deleteCardButton: Button 
    private lateinit var stopScanButton: Button
    private lateinit var writeLogoUrlButton: Button
    private lateinit var scanStatus: TextView 
    private lateinit var dashboardScroll: ScrollView
    private lateinit var timerText: TextView
    
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    
    private val scanTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            stopScanMode()
            showResultPopup("Timeout", "No card detected within 60 seconds.")
        }
    }
    private var secondsElapsed = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                secondsElapsed++
                val remaining = 60 - secondsElapsed
                timerText.text = "Time Elapsed : ${remaining}s"
                scanTimeoutHandler.postDelayed(this, 1000)
            }
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
        displayAddress = findViewById(R.id.displayAddress)
        displayPhone = findViewById(R.id.displayPhone)
        displayEmail = findViewById(R.id.displayEmail)
        displayWebsite = findViewById(R.id.displayWebsite)
        displayWhatsapp = findViewById(R.id.displayWhatsapp)
        displayStatus = findViewById(R.id.displayStatus)
        premiumMemberLabel = findViewById(R.id.premiumMemberLabel)
        newCardButton = findViewById(R.id.newCardButton)
        clearButton = findViewById(R.id.clearButton)
        deleteCardButton = findViewById(R.id.deleteCardButton) 
        writeLogoUrlButton = findViewById(R.id.writeLogoUrlButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        timerText = findViewById(R.id.timerText)
        scanStatus = findViewById(R.id.scanStatus) 
        dashboardScroll = findViewById(R.id.dashboardScroll)
        
        errorContainer = findViewById(R.id.errorContainer)
        errorText = findViewById(R.id.errorText)
        
        val logoutButton = findViewById<ImageButton>(R.id.logoutButton)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val titleText = findViewById<TextView>(R.id.appBarTitle)

        backButton.visibility = View.GONE

        val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val adminId = sharedPref.getString("adminId", "Admin")
        val logoUrl = sharedPref.getString("logoUrl", null)
        titleText.text = adminId ?: "Admin"
        
        logAction("Dashboard - Retrieved Logo URL: $logoUrl")

        if (!logoUrl.isNullOrEmpty()) {
            val fullLogoUrl = if (logoUrl.startsWith("http")) logoUrl else "https://apisita.shanti-pos.com$logoUrl"
            try {
                val imageLoader = coil.ImageLoader.Builder(this).build()
                val request = coil.request.ImageRequest.Builder(this)
                    .data(fullLogoUrl)
                    .target(
                        onSuccess = { result ->
                            logoCard.setImageDrawable(result)
                        },
                        onError = {
                            logoCard.setImageResource(R.drawable.logo)
                        }
                    )
                    .placeholder(R.drawable.logo)
                    .error(R.drawable.logo)
                    .build()
                imageLoader.enqueue(request)
            } catch (e: Exception) {
                logoCard.setImageResource(R.drawable.logo)
            }
        } else {
            logoCard.setImageResource(R.drawable.logo)
        }

        logoCard.setOnClickListener { startScanMode() }
        stopScanButton.setOnClickListener { stopScanMode() }
        newCardButton.setOnClickListener {
            val intent = Intent(this, IssueCardActivity::class.java)
            startActivity(intent)
        }
        clearButton.setOnClickListener { resetUI() }
        deleteCardButton.setOnClickListener { startDeleteMode() }
        writeLogoUrlButton.setOnClickListener {
            val intent = Intent(this, WriteUrlActivity::class.java)
            startActivity(intent)
        }

        logoutButton.setOnClickListener {
            val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        displayAddress.setOnClickListener {
            val address = displayAddress.text.toString()
            if (address.isNotBlank() && address != "N/A") openLocationInMaps(address)
        }
        
        displayPhone.setOnClickListener {
            val phone = displayPhone.text.toString()
            if (phone.isNotBlank() && phone != "N/A") openDialer(phone)
        }
        
        displayEmail.setOnClickListener {
            val email = displayEmail.text.toString()
            if (email.isNotBlank() && email != "N/A") openEmailApp(email)
        }

        findViewById<View>(R.id.llPoweredBy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://devisoft.co.in"))
            startActivity(intent)
        }
    }

    private fun startScanMode() {
        isScanning = true
        isDeleteMode = false
        scanInstruction.text = "TAP CARD NOW..."
        scanProgress.visibility = View.VISIBLE
        stopScanButton.visibility = View.VISIBLE
        detailsContainer.visibility = View.GONE
        newCardButton.visibility = View.GONE
        writeLogoUrlButton.visibility = View.GONE
        clearButton.visibility = View.GONE
        deleteCardButton.visibility = View.GONE 
        errorContainer.visibility = View.GONE
        scanStatus.visibility = View.GONE 
        
        logAction("Scanning started")
        timerText.text = "Time Elapsed: 60s"
        timerText.visibility = View.VISIBLE
        secondsElapsed = 0
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 60000)
        scanTimeoutHandler.postDelayed(timerRunnable, 1000)
        nfcManager.startScanning()
    }

    private fun startDeleteMode() {
        isScanning = true
        isDeleteMode = true
        scanInstruction.text = "TAP CARD TO DELETE DATA..." 
        scanProgress.visibility = View.VISIBLE
        stopScanButton.visibility = View.VISIBLE
        detailsContainer.visibility = View.GONE
        newCardButton.visibility = View.GONE
        writeLogoUrlButton.visibility = View.GONE
        clearButton.visibility = View.GONE
        deleteCardButton.visibility = View.GONE
        errorContainer.visibility = View.GONE
        
        logAction("Delete mode started")
        timerText.text = "Time Elapsed: 60s"
        timerText.visibility = View.VISIBLE
        secondsElapsed = 0
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 60000)
        scanTimeoutHandler.postDelayed(timerRunnable, 1000)
        nfcManager.startScanning()
    }

    private fun stopScanMode() {
        isScanning = false
        scanInstruction.text = "Tap logo to scan"
        scanProgress.visibility = View.GONE
        stopScanButton.visibility = View.GONE
        newCardButton.visibility = View.VISIBLE
        writeLogoUrlButton.visibility = View.VISIBLE
        clearButton.visibility = View.VISIBLE
        deleteCardButton.visibility = View.VISIBLE
        logAction("Scanning stopped")
        timerText.visibility = View.GONE
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
        scanTimeoutHandler.removeCallbacks(timerRunnable)
        nfcManager.stopScanning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            nfcManager.onNewIntent(intent)
            
            if (nfcManager.isMultipleTagsDetected.value) {
                runOnUiThread {
                    stopScanMode()
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Multiple Cards Detected")
                        .setMessage("Multiple cards detected! Please hold one card only.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                return
            }

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val url = nfcManager.extractUrl(tag)
                if (url != null) {
                    logAction("URL detected on card: $url")
                    runOnUiThread {
                        try {
                            stopScanMode()
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(browserIntent)
                        } catch (e: Exception) {
                            logAction("Failed to open URL: ${e.message}")
                            showResultPopup("Error", "Failed to open URL: ${e.message}")
                        }
                    }
                    return
                }
                processCard()
            }
        }
    }

    private fun processCard() {
        if (isDeleteMode) processDelete() else processRead()
    }

    private fun processRead() {
        scanInstruction.text = "Checking card..."
        logAction("Processing detected card (Read)")
        nfcManager.readCard { success, data, message ->
            runOnUiThread {
                stopScanMode()
                if (success && data != null && !data["password"].isNullOrBlank() && !data["memberId"].isNullOrBlank()) {
                    logAction("Card read success: MemberID=${data["memberId"]}, CardType=${data["cardType"]}")
                    showCardDetails(data)
                } else if (success) {
                    val reason = if (data == null) "card is empty" 
                                else if (data["memberId"].isNullOrBlank()) "Missing MemberID" 
                                else "Missing Password"
                    logAction("Card read success: Card is empty ($reason)")
                    showResultPopup("Empty Card", "card is empty not assigne to any member")
                } else {
                    logAction("Card read error: $message")
                    showResultPopup("Read Error", message)
                }
            }
        }
    }

    private fun showResultPopup(title: String, message: String, isError: Boolean = true, shouldReset: Boolean = true) {
        val cleanMessage = message.replace("(found in CardTransaction Log)", "").trim()
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle(title)
        builder.setMessage(cleanMessage)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            if (shouldReset) {
                stopScanMode()
                resetUI()
                nfcManager.clearScanData()
            }
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun processDelete() {
        // scanInstruction.text = "Reading card before deletion..."
        logAction("Processing detected card (Delete - Step 1: Read)")
        
        nfcManager.readCard { success, data, message ->
            if (!success) {
                runOnUiThread {
                    logAction("Card read error (Delete): $message")
                    showResultPopup("Read Error", message)
                }
                return@readCard
            }

            val cardMfid = (data?.get("card_mfid") ?: "").lowercase()
            val password = data?.get("password") ?: ""
            logAction("Read Step Successful - MFID: $cardMfid")

            if (cardMfid.isEmpty()) {
                runOnUiThread {
                    logAction("Card is already blank - No MFID found")
                    showResultPopup("Already Blank", "This card is already blank or not registered.")
                }
                return@readCard
            }

            // runOnUiThread { scanInstruction.text = "Validating deletion with server..." }
            
            scope.launch {
                logAction("Step 2: Sending API Request for Deletion - MFID='$cardMfid'")
                val result = withContext(Dispatchers.IO) {
                    memberApiClient.deleteCard(cardMfid, password)
                }
                
                result.fold(
                    onSuccess = { response ->
                        logAction("Step 2 Successful: Server deletion confirmed - ${response.message}")
                        // runOnUiThread { scanInstruction.text = "API Success. Wiping card..." }
                        
                        nfcManager.deleteCardData { wipeSuccess: Boolean, wipeMessage: String ->
                            runOnUiThread {
                                if (wipeSuccess) {
                                    logAction("DATABASE_DELETED: Card removed from server record successfully.")
                                    logAction("CARD_DELETED: Card data physically wiped successfully.")
                                    showResultPopup("Success", "Data cleared successfully", isError = false)
                                } else {
                                    logAction("DELETE_PARTIAL_FAILURE: Server record deleted, but CARD_WIPE_FAILED: $wipeMessage")
                                    showResultPopup("Partial Success", "API deleted record, but physical wipe failed: $wipeMessage. Please try again.")
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        val errorMessage = error.message ?: "Deletion failed"
                        
                        // Handle 404 (Card not found) specifically to allow physical wipe
                        if (errorMessage.lowercase().contains("card not found") || errorMessage.lowercase().contains("404")) {
                            logAction("Step 2 (404): Card not found in DB - Proceeding to wipe orphaned card.")
                            // runOnUiThread { scanInstruction.text = "Card not in registry. Wiping anyway..." }
                            
                            nfcManager.deleteCardData { wipeSuccess: Boolean, wipeMessage: String ->
                                runOnUiThread {
                                    if (wipeSuccess) {
                                        logAction("DATABASE_DELETED: Card already missing from server (404 path).")
                                        logAction("CARD_DELETED: Orphaned card physically wiped successfully.")
                                        showResultPopup("Success", "Data cleared successfully", isError = false)
                                    } else {
                                        showResultPopup("Wipe Failed", "Record not found in database, and physical wipe failed: $wipeMessage.")
                                    }
                                }
                            }
                        } else {
                            logAction("Step 2 Failed: Server deletion declined - $errorMessage")
                            runOnUiThread {
                                showResultPopup("Deletion Declined", errorMessage)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showCardDetails(data: Map<String, String>) {
        detailsContainer.visibility = View.VISIBLE
        val cardType = data["cardType"] ?: "Member"
        premiumMemberLabel.text = cardType.uppercase()
        
        displayMemberId.text = data["memberId"] ?: "N/A"
        displayCompany.text = data["companyName"] ?: "N/A"
        displayValidUpto.text = formatDate(data["validUpto"])
        val amountFormatter = java.text.DecimalFormat("#,###.00")
        displayTotalBuy.text = "₹${amountFormatter.format((data["totalBuy"] ?: "0.00").toDoubleOrNull() ?: 0.0)}"
        displayAmount.text = "Loading..." 
        displayStatus.text = "Loading..."
        displayStatus.setTextColor(resources.getColor(R.color.brand_blue, theme))
        
        displayAddress.visibility = View.GONE
        displayPhone.visibility = View.GONE
        displayEmail.visibility = View.GONE
        displayWebsite.visibility = View.GONE
        displayWhatsapp.visibility = View.GONE
        
        scope.launch {
            val memberId = data["memberId"] ?: ""
            val companyName = data["companyName"] ?: ""
            logAction("API Request - Verifying Member: ID='$memberId'")
            
            if (memberId.isNotBlank()) {
                val cardMfid = data["card_mfid"] ?: ""
                val cardValidity = data["validUpto"] ?: ""
                val password = data["password"] ?: ""

                val result = withContext(Dispatchers.IO) {
                    memberApiClient.verifyMember(memberId, companyName, password, cardMfid, cardValidity, cardType)
                }
                result.fold(
                    onSuccess = { response ->
                        val scannedMfid = data["card_mfid"] ?: ""
                        val matchingCard = response.cards?.find { it.card_mfid.equals(scannedMfid, ignoreCase = true) }
                        
                        if (matchingCard != null) {
                            val displayTotal = if (matchingCard.cardTotal > 0) matchingCard.cardTotal else if (response.cardTotal > 0) response.cardTotal else response.currentTotal
                            displayTotalBuy.text = "₹${amountFormatter.format(displayTotal)}" 
                            displayAmount.text = "₹${amountFormatter.format(response.globalTotal)}"
                        } else {
                            displayTotalBuy.text = "₹${amountFormatter.format(response.currentTotal)}" 
                            displayAmount.text = "₹${amountFormatter.format(response.globalTotal)}" 
                        }
                        displayValidUpto.text = formatDate(response.validity)
                        if (!response.companyName.isNullOrBlank()) displayCompany.text = response.companyName
                        if (!response.memberId.isNullOrBlank()) displayMemberId.text = response.memberId
                        
                        // Set Status (Prioritize Card Status over Member Status)
                        val rawStatus = matchingCard?.status ?: response.status ?: "Active"
                        val statusText = if (rawStatus == "1") "Active" else rawStatus
                        
                        // Update Label with actual Card Type
                        val displayCardType = matchingCard?.cardType ?: response.cardType ?: cardType
                        premiumMemberLabel.text = displayCardType.uppercase()
                        
                        displayStatus.text = statusText
                        if (statusText.equals("Blocked", ignoreCase = true)) {
                            displayStatus.setTextColor(resources.getColor(R.color.error_red, theme))
                        } else {
                            displayStatus.setTextColor(resources.getColor(R.color.brand_blue, theme))
                        }
                        
                        bindValue(displayAddress, response.companyAddress)
                        bindValue(displayPhone, response.phoneNumber)
                        bindValue(displayEmail, response.email)
                        bindValue(displayWebsite, response.website)
                        bindValue(displayWhatsapp, response.whatsapp)
                    },
                    onFailure = { error ->
                        val errorMessage = error.message ?: "Verification failed"
                        if (errorMessage.contains("already registered", ignoreCase = true) || errorMessage.contains("different member", ignoreCase = true)) {
                            runOnUiThread {
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@DashboardActivity)
                                    .setTitle("Card Error").setMessage(errorMessage).setPositiveButton("OK", null).show()
                                displayAmount.text = "BLOCKED: $errorMessage"
                                displayAmount.setTextColor(resources.getColor(R.color.error_red, theme))
                            }
                        } else {
                            scope.launch {
                                val fallbackResult = withContext(Dispatchers.IO) { memberApiClient.getMemberById(memberId) }
                                fallbackResult.fold(
                                    onSuccess = { response ->
                                        val scannedMfid = data["card_mfid"] ?: ""
                                        val matchingCard = response.cards?.find { it.card_mfid.equals(scannedMfid, ignoreCase = true) }
                                        if (matchingCard != null) {
                                            val displayTotal = if (matchingCard.cardTotal > 0) matchingCard.cardTotal else if (response.cardTotal > 0) response.cardTotal else response.currentTotal
                                            displayTotalBuy.text = "₹${amountFormatter.format(displayTotal)}" 
                                            displayAmount.text = "₹${amountFormatter.format(response.globalTotal)}"
                                        } else {
                                            displayTotalBuy.text = "₹${amountFormatter.format(response.currentTotal)}"
                                            displayAmount.text = "₹${amountFormatter.format(response.globalTotal)}"
                                        }
                                        displayValidUpto.text = formatDate(response.validity)
                                        if (!response.companyName.isNullOrBlank()) displayCompany.text = response.companyName
                                        if (!response.memberId.isNullOrBlank()) displayMemberId.text = response.memberId
                                        
                                        // Set Status (Prioritize Card Status over Member Status)
                                        val rawStatus = matchingCard?.status ?: response.status ?: "Active"
                                        val statusText = if (rawStatus == "1") "Active" else rawStatus
                                        
                                        // Update Label with actual Card Type (Fallback path)
                                        val displayCardType = matchingCard?.cardType ?: response.cardType ?: cardType
                                        premiumMemberLabel.text = displayCardType.uppercase()
                                        
                                        displayStatus.text = statusText
                                        if (statusText.equals("Blocked", ignoreCase = true)) {
                                            displayStatus.setTextColor(resources.getColor(R.color.error_red, theme))
                                        } else {
                                            displayStatus.setTextColor(resources.getColor(R.color.brand_blue, theme))
                                        }
                                        bindValue(displayAddress, response.companyAddress)
                                        bindValue(displayPhone, response.phoneNumber)
                                        bindValue(displayEmail, response.email)
                                        bindValue(displayWebsite, response.website)
                                        bindValue(displayWhatsapp, response.whatsapp)
                                        errorContainer.visibility = View.GONE
                                    },
                                    onFailure = { runOnUiThread { showError(errorMessage) } }
                                )
                            }
                        }
                    }
                )
            } else {
                displayTotalBuy.text = "N/A"
                displayAmount.text = "N/A" 
                displayValidUpto.text = "N/A"
            }
        }

        dashboardScroll.post { dashboardScroll.smoothScrollTo(0, detailsContainer.top) }
    }
    
    private fun showError(message: String) {
        detailsContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.VISIBLE
        errorText.text = message
        dashboardScroll.post { dashboardScroll.smoothScrollTo(0, detailsContainer.top) }
    }
    
    private fun resetUI() {
        detailsContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        scanInstruction.text = "Tap logo to scan card"
    }

    private fun statusSnackbar(message: String) {
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
            } catch (e: Exception) { }
        }
        return dateStr 
    }

    private fun openLocationInMaps(address: String) {
        try {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val intent = Intent(Intent.ACTION_VIEW, geoUri)
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else startActivity(Intent(Intent.ACTION_VIEW, geoUri))
        } catch (e: Exception) { showResultPopup("Error", "Could not open maps") }
    }
    
    private fun openDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        } catch (e: Exception) { showResultPopup("Error", "Could not open dialer") }
    }
    
    private fun openEmailApp(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$email")
            startActivity(intent)
        } catch (e: Exception) { showResultPopup("Error", "Could not open email app") }
    }
    
    private fun logAction(action: String) {
        platformLog("SITACardMaster", "Dashboard: $action")
    }

    override fun onStart() {
        super.onStart()
        resetUI()
        nfcManager.clearScanData()
    }

    override fun onResume() {
        super.onResume()
        // FIX: Only restart scanning if we don't already have a detected tag.
        // This prevents the foreground dispatch reset from wiping state during processDelete.
        if (isScanning && (nfcManager.detectedTag.value == null)) {
            nfcManager.startScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }

    override fun onStop() {
        super.onStop()
    }
}
