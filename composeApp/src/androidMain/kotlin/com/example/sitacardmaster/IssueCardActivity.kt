package com.example.sitacardmaster

import android.content.Intent
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.sitacardmaster.R
import com.example.sitacardmaster.screens.formatDate
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.MainScope


class IssueCardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false

    private lateinit var memberIdText: TextView
    private lateinit var companyNameInput: com.google.android.material.textfield.MaterialAutoCompleteTextView
    private lateinit var cardTypeInput: com.google.android.material.textfield.MaterialAutoCompleteTextView
    private lateinit var validUptoText: TextView
    private lateinit var phoneNumberText: TextView
    private lateinit var whatsappInputText: TextView
    private lateinit var emailText: TextView
    private lateinit var websiteText: TextView
    private lateinit var addressText: TextView
    private lateinit var memberInfoCard: View

    private lateinit var statusMessage: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var tapCardHint: TextView
    private lateinit var startScanButton: Button
    private lateinit var cancelScanButton: Button
    private val apiClient = com.example.sitacardmaster.network.MemberApiClient()
    private val coroutineScope = kotlinx.coroutines.MainScope()
    private lateinit var timerText: TextView
    private var secondsElapsed = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (isScanning) {
            logAction("Scan timeout reached (60s)")
            stopScanning()
            statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
            statusMessage.text = "Timeout: No card detected"
            com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content),
                "Scanning timed out (60s)",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                secondsElapsed++
                val remaining = 60 - secondsElapsed
                timerText.text = "Time Elapsed : ${remaining}s"
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_card)

        nfcManager = AndroidNfcManager(this)

        memberIdText = findViewById(R.id.memberIdText)
        companyNameInput = findViewById(R.id.companyName)
        cardTypeInput = findViewById(R.id.cardTypeInput)
        validUptoText = findViewById(R.id.validUptoText)
        phoneNumberText = findViewById(R.id.phoneNumberText)
        whatsappInputText = findViewById(R.id.whatsappNumberText)
        emailText = findViewById(R.id.emailText)
        websiteText = findViewById(R.id.websiteText)
        addressText = findViewById(R.id.addressText)
        memberInfoCard = findViewById(R.id.memberInfoCard)

        statusMessage = findViewById(R.id.statusMessage)
        scanProgress = findViewById(R.id.scanProgress)
        tapCardHint = findViewById(R.id.tapCardHint)
        startScanButton = findViewById(R.id.startScanButton)
        cancelScanButton = findViewById(R.id.cancelScanButton)
        timerText = findViewById(R.id.timerText)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        findViewById<TextView>(R.id.appBarTitle).text = "Issue New Card"
        findViewById<ImageButton>(R.id.logoutButton)?.visibility = View.GONE

        findViewById<View>(R.id.llPoweredBy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://devisoft.co.in"))
            startActivity(intent)
        }

        backButton.setOnClickListener { finish() }

        startScanButton.setOnClickListener {
            val currentName = companyNameInput.text.toString()
            if (selectedCompanyName.isEmpty() || currentName != selectedCompanyName) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please select a company from the list"
                return@setOnClickListener
            }
            val memberId = memberIdText.text.toString()
            if (memberId.isEmpty() || memberId == "---") {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Member ID is missing"
                return@setOnClickListener
            }
            val selectedCardType = cardTypeInput.text.toString()
            if (selectedCardType.isEmpty()) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please select card type"
                return@setOnClickListener
            }
            
            // Validate existing card types
            statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
            statusMessage.text = "Validating member cards..."
            startScanButton.isEnabled = false
            
            coroutineScope.launch {
                val result = apiClient.getMemberById(memberId)
                startScanButton.isEnabled = true
                if (result.isSuccess) {
                    val memberDetails = result.getOrNull()
                    val hasExistingCard = memberDetails?.cards?.any { 
                        it.cardType.equals(selectedCardType, ignoreCase = true) 
                    } == true
                    
                    if (hasExistingCard) {
                        statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                        statusMessage.text = "Error: Member already has an assigned card of type '$selectedCardType'"
                    } else {
                        startScanning()
                    }
                } else {
                    statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                    val errorMsg = result.exceptionOrNull()?.message ?: "Validation failed"
                    statusMessage.text = "Error validating cards: $errorMsg"
                }
            }
        }

        cancelScanButton.setOnClickListener {
            stopScanning()
        }

        setupAutoComplete()
        setupCardTypeDropdown()
        
        logTotalCompanyCount()
    }
    
    private fun logTotalCompanyCount() {
        coroutineScope.launch {
            val result = apiClient.getApprovedMembers("") // Empty search to get all
            if (result.isSuccess) {
                val members = result.getOrNull() ?: emptyList()
                platformLog("SITACardMaster", "IssueCard: Total companies available: ${members.size}")
            } else {
                platformLog("SITACardMaster", "IssueCard: Failed to fetch companies for count: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun setupCardTypeDropdown() {
        val cardTypes = arrayOf("Member", "Add-on", "Company Executive", "Corporate Member")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cardTypes)
        cardTypeInput.setAdapter(adapter)
        cardTypeInput.setText("Member", false) // Default
    }

    private var selectedCompanyName: String = ""
    private var searchJob: kotlinx.coroutines.Job? = null

    private fun setupAutoComplete() {
        companyNameInput.threshold = 0
        companyNameInput.setOnItemClickListener { parent, view, position, id ->
            val member = parent.getItemAtPosition(position) as? com.example.sitacardmaster.network.models.VerifyMemberResponse
            android.util.Log.d("IssueCardActivity", "Suggestion clicked: ${member?.companyName}")
            member?.let {
                selectedCompanyName = it.companyName ?: ""
                memberIdText.text = it.memberId ?: ""
                validUptoText.text = formatDate(it.validity)
                companyNameInput.setText(selectedCompanyName, false)
                phoneNumberText.text = it.phoneNumber ?: ""
                whatsappInputText.text = it.whatsapp ?: ""
                emailText.text = it.email ?: ""
                websiteText.text = it.website ?: ""
                addressText.text = it.companyAddress ?: ""
                memberInfoCard.visibility = View.VISIBLE
                companyNameInput.dismissDropDown()
            }
        }

        companyNameInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val query = companyNameInput.text.toString()
                android.util.Log.d("IssueCardActivity", "Focused: query=$query, selected=$selectedCompanyName")
                if (query != selectedCompanyName || query.isEmpty()) {
                    searchJob?.cancel()
                    searchJob = coroutineScope.launch {
                        fetchSuggestions(query)
                    }
                }
            }
        }

        companyNameInput.setOnClickListener {
            val query = companyNameInput.text.toString()
            if (!companyNameInput.isPopupShowing && (query != selectedCompanyName || query.isEmpty())) {
                searchJob?.cancel()
                searchJob = coroutineScope.launch {
                    fetchSuggestions(query)
                }
            }
        }

        companyNameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                android.util.Log.d("IssueCardActivity", "onTextChanged: query=$query")
                
                if (query.isEmpty()) {
                    selectedCompanyName = ""
                    clearOtherFields()
                }

                // If user changes text from the selected one, reset selection and fetch
                if (query != selectedCompanyName) {
                    if (selectedCompanyName.isNotEmpty()) {
                        selectedCompanyName = ""
                    }
                    searchJob?.cancel()
                    searchJob = coroutineScope.launch {
                        delay(300)
                        fetchSuggestions(query)
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private suspend fun fetchSuggestions(query: String) {
        android.util.Log.d("IssueCardActivity", "Fetching suggestions for: $query")
        // Double check if query already matches selected to avoid double-opening
        if (query.isNotEmpty() && query == selectedCompanyName) {
            android.util.Log.d("IssueCardActivity", "Query matches selected, skipping fetch")
            return
        }

        val result = apiClient.getApprovedMembers(query)
        if (result.isSuccess) {
            val members = result.getOrNull() ?: emptyList()
            android.util.Log.d("IssueCardActivity", "Success: found ${members.size} members")
            
            withContext(Dispatchers.Main) {
                // Final check before showing dropdown
                val currentText = companyNameInput.text.toString()
                if (currentText == selectedCompanyName && selectedCompanyName.isNotEmpty()) {
                    return@withContext
                }

                val adapter = object : ArrayAdapter<com.example.sitacardmaster.network.models.VerifyMemberResponse>(
                    this@IssueCardActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    members
                ) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val member = getItem(position)
                        view.text = "${member?.companyName} (${member?.memberId})"
                        return view
                    }
                    
                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(constraint: CharSequence?): FilterResults {
                                val results = FilterResults()
                                results.values = members
                                results.count = members.size
                                return results
                            }
                            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                notifyDataSetChanged()
                            }
                            override fun convertResultToString(resultValue: Any?): CharSequence {
                                return (resultValue as? com.example.sitacardmaster.network.models.VerifyMemberResponse)?.companyName ?: ""
                            }
                        }
                    }
                }
                companyNameInput.setAdapter(adapter)
                adapter.notifyDataSetChanged()
                if (members.isNotEmpty() && companyNameInput.hasFocus()) {
                    companyNameInput.showDropDown()
                }
            }
        } else {
            android.util.Log.e("IssueCardActivity", "API Error: ${result.exceptionOrNull()?.message}")
        }
    }

    private fun clearOtherFields() {
        memberIdText.text = "---"
        validUptoText.text = "---"
        phoneNumberText.text = "---"
        whatsappInputText.text = "---"
        emailText.text = "---"
        websiteText.text = "---"
        addressText.text = "---"
        memberInfoCard.visibility = View.GONE
        cardTypeInput.setText("Member", false)
    }

    private fun startScanning() {
        isScanning = true
        statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
        statusMessage.text = "Scanning..."
        scanProgress.visibility = View.VISIBLE
        tapCardHint.visibility = View.VISIBLE
        startScanButton.visibility = View.GONE
        cancelScanButton.visibility = View.VISIBLE
        logAction("Scanning started for Member: ${memberIdText.text}")
        hideKeyboard()
        nfcManager.startScanning()
        
        // Timer Setup
        timerText.text = "Time Elapsed: 60s"
        timerText.visibility = View.VISIBLE
        secondsElapsed = 0
        handler.postDelayed(timeoutRunnable, 60000)
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun stopScanning() {
        isScanning = false
        statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
        statusMessage.text = "Ready to write"
        scanProgress.visibility = View.GONE
        tapCardHint.visibility = View.GONE
        startScanButton.visibility = View.VISIBLE
        cancelScanButton.visibility = View.GONE
        nfcManager.stopScanning()
        timerText.visibility = View.GONE
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(timerRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            nfcManager.onNewIntent(intent)
            
            if (nfcManager.isMultipleTagsDetected.value) {
                runOnUiThread {
                    stopScanning()
                    statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                    statusMessage.text = "Multiple cards detected! Please hold one card only."
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
                // Start Verification Process
                verifyAndProcessCard(tag)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun verifyAndProcessCard(tag: Tag) {
        // Tag ID (MFID)
        val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
        val memberId = memberIdText.text.toString()
        val company = companyNameInput.text.toString()
        val cardType = cardTypeInput.text.toString()
        val validUpto = validUptoText.text.toString()

        runOnUiThread {
             statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
             statusMessage.text = "Verified"
        }
        
        nfcManager.readCard { readSuccess, cardData, _ ->
            val existingCompany = cardData?.get("companyName")?.takeIf { it.isNotBlank() }
            val existingMemberId = cardData?.get("memberId")?.takeIf { it.isNotBlank() }

            if (existingMemberId != null && existingMemberId != memberId) {
                val message = "Card already registered to other member: ${existingCompany ?: "Unknown"}"
                logAction("CARD_ALREADY_REGISTERED: MFID=$tagId, OldMember=$existingMemberId, NewMember=$memberId")
                runOnUiThread {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                        .setTitle("Card Already Assigned")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                    statusMessage.text = "Error: $message"
                    stopScanning()
                }
                return@readCard
            }

            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { // Using GlobalScope for simplicity in Activity for now, ideally LifecycleScope
                 val cardPassword = cardData?.get("password") ?: ""
                 
                 if (cardPassword.isEmpty()) {
                     logAction("BLANK_CARD_DETECTED: Card is not registered.")
                     runOnUiThread {
                         val padding = (resources.displayMetrics.density * 24).toInt()
                         val container = android.widget.FrameLayout(this@IssueCardActivity).apply {
                             setPadding(padding, padding, padding, 0)
                         }
                         val messageView = TextView(this@IssueCardActivity).apply {
                             text = "Wrong Card Detected"
                             gravity = android.view.Gravity.CENTER
                             textSize = 18f
                             setTextColor(resources.getColor(R.color.error_red, theme))
                             setTypeface(null, android.graphics.Typeface.BOLD)
                         }
                         container.addView(messageView)

                         com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                             .setView(container)
                             .setPositiveButton("OK", null)
                             .show()
                         statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                         statusMessage.text = "Error: Wrong card detected"
                         stopScanning()
                     }
                     return@launch
                 }

                 logAction("READ_CARD: MFID=$tagId, Password (from card)=$cardPassword")
                 logAction("VERIFY_API_START: MemberID=$memberId, Company=$company, MFID=$tagId, Validity=$validUpto")
                 val result = apiClient.verifyMember(
                     memberId = memberId,
                     companyName = company,
                     password = cardPassword,
                     cardMfid = tagId,
                     cardValidity = validUpto,
                     cardType = cardType
                 )
                 
                 if (result.isSuccess) {
                     logAction("VERIFY_API_SUCCESS: Member Verified!")
                     runOnUiThread {
                         statusMessage.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                         statusMessage.text = "Member Verified! Writing to Card..."
                         writeCard(cardPassword)
                     }
                 } else {
                     val error = result.exceptionOrNull()?.message ?: "Verification failed"
                     logAction("VERIFY_API_FAILED: $error")
                     
                     runOnUiThread {
                         com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                             .setTitle("Verification Failed")
                             .setMessage(error)
                             .setPositiveButton("OK", null)
                             .show()
                         statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                         statusMessage.text = error
                         stopScanning()
                     }
                 }
            } // end of GlobalScope.launch
        } // end of readCard
    } // end of verifyAndProcessCard

    private fun writeCard(cardPassword: String) {
        val memberId = memberIdText.text.toString()
        val company = companyNameInput.text.toString()
        val cardType = cardTypeInput.text.toString()
        val validUpto = validUptoText.text.toString()
        // val totalBuy = totalBuyInput.text.toString() // Removed
        val totalBuy = "0" // Defaulting to 0 since input is removed

        logAction("Starting Write Card. Member: $memberId, Pwd (from card): $cardPassword") 

        nfcManager.writeCard(
            memberId = memberId,
            companyName = company,
            password = cardPassword,
            validUpto = validUpto,
            totalBuy = totalBuy,
            cardType = cardType,
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
                        // Refresh/Reset the page
                        resetForm()
                    } else {
                        statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                    }
                }
            }
        )
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

    private fun resetForm() {
        companyNameInput.setText("", false)
        selectedCompanyName = ""
        clearOtherFields()
        statusMessage.text = "Card Issued Successfully. Ready for next."
        statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
        companyNameInput.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
        nfcManager.clearScanData()
    }
}
