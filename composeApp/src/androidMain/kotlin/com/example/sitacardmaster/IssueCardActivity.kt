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
    private lateinit var clearPageButton: Button
    private lateinit var buttonContainer: View
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
        clearPageButton = findViewById(R.id.clearPageButton)
        buttonContainer = findViewById(R.id.buttonContainer)
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
                    val existingCards = memberDetails?.cards ?: emptyList()
                    
                    platformLog("SITACardMaster", "VALIDATION_LOG (Pre-Scan): Checking for duplicate card type: $selectedCardType")
                    platformLog("SITACardMaster", "VALIDATION_LOG (Pre-Scan): Existing cards count: ${existingCards.size}")
                    
                    var isDuplicateType = false
                    existingCards.forEachIndexed { index, card ->
                        val existingType = card.cardType ?: "N/A"
                        val match = existingType.trim().equals(selectedCardType.trim(), ignoreCase = true)
                        platformLog("SITACardMaster", "VALIDATION_LOG (Pre-Scan): Card #$index - ID: ${card.card_mfid}, Type: $existingType, Match: $match")
                        if (match) {
                            isDuplicateType = true
                            platformLog("SITACardMaster", "VALIDATION_LOG (Pre-Scan): DUPLICATE DETECTED for type '$selectedCardType'")
                        }
                    }
                    
                    if (isDuplicateType) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                            .setTitle("Duplicate Card Type")
                            .setMessage("Member already has an assigned card of type '$selectedCardType'")
                            .setPositiveButton("OK", null)
                            .show()
                        statusMessage.text = "Ready to write" // Reset to neutral state
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

        clearPageButton.setOnClickListener {
            resetForm()
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
        buttonContainer.visibility = View.GONE
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
        buttonContainer.visibility = View.VISIBLE
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
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Scan Error")
                        .setMessage("card not detected properly please hold it again")
                        .setPositiveButton("OK", null)
                        .show()
                    statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                    statusMessage.text = "Ready to write"
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
        val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
        val memberId = memberIdText.text.toString()
        val company = companyNameInput.text.toString()
        val cardType = cardTypeInput.text.toString()
        val validUpto = validUptoText.text.toString()
        
        logAction("Starting verification for Card: $tagId")
        runOnUiThread {
             statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
             statusMessage.text = "Verifying..."
        }
        
        nfcManager.readCard { readSuccess, cardData, _ ->
            logAction("Read Result: success=$readSuccess, data=$cardData")
            
            val existingMemberId = cardData?.get("memberId")
            val existingCompanyName = cardData?.get("companyName")
            
            if (!existingMemberId.isNullOrBlank()) {
                if (existingMemberId != memberId) {
                    val ownerName = existingCompanyName ?: "Unknown Member"
                    val message = "This card is already registered to other member: $ownerName ($existingMemberId)"
                    logAction("DUPLICATE_FOUND_ON_CARD: MFID=$tagId, Owner=$ownerName ($existingMemberId)")
                    runOnUiThread {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                            .setTitle("Card Already Assigned")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                        statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                        statusMessage.text = "Ready to write"
                        stopScanning()
                    }
                    return@readCard
                } else {
                    // Same member - check card type
                    val existingCardType = cardData?.get("cardType") ?: "Member"
                    if (!existingCardType.trim().equals(cardType.trim(), ignoreCase = true)) {
                        val message = "Card already assigned to this member with different card type: $existingCardType"
                        logAction("SAME_MEMBER_DIFFERENT_TYPE: MFID=$tagId, TypeOnCard=$existingCardType, TargetType=$cardType")
                        runOnUiThread {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                                .setTitle("Card Already Assigned")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                            statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                            statusMessage.text = "Ready to write"
                            stopScanning()
                        }
                        return@readCard
                    }
                }
            }

            // Check if card MFID is already assigned in the DATABASE
            // Strategy:
            //   1. Use getMemberById(memberId) to reliably check if THIS member already
            //      has this physical card (list API returns cards as ObjectIDs, not MFIDs).
            //   2. Use getApprovedMembers("") to check if a DIFFERENT member owns it.
            coroutineScope.launch {
                // --- Step 1: Check if current member already owns this card ---
                val currentMemberResult = apiClient.getMemberById(memberId)
                if (currentMemberResult.isSuccess) {
                    val currentMember = currentMemberResult.getOrNull()
                    val alreadyOwnedBySelf = currentMember?.cards?.any {
                        it.card_mfid?.trim()?.equals(tagId.trim(), ignoreCase = true) == true
                    } == true
                    
                    if (alreadyOwnedBySelf) {
                        val ownerName = currentMember?.companyName ?: "this member"
                        val message = "Card already registered to this member ($ownerName). Cannot reassign with a different card type."
                        logAction("CARD_ALREADY_REGISTERED_SELF: MFID=$tagId, MemberId=$memberId")
                        runOnUiThread {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                                .setTitle("Card Already Assigned")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                            statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                            statusMessage.text = "Ready to write"
                            stopScanning()
                        }
                        return@launch
                    }
                }
                
                // --- Step 2: Check if a different member owns this card ---
                val membersResult = apiClient.getApprovedMembers("")
                if (membersResult.isSuccess) {
                    val allMembers = membersResult.getOrNull() ?: emptyList()
                    var owningMember: com.example.sitacardmaster.network.models.VerifyMemberResponse? = null
                    
                    for (m in allMembers) {
                        if (m.memberId == memberId) continue // skip self (already checked above)
                        val hasMfidMatch = m.card_mfid?.trim()?.equals(tagId.trim(), ignoreCase = true) == true ||
                                          m.cards?.any { it.card_mfid?.trim()?.equals(tagId.trim(), ignoreCase = true) == true } == true
                        if (hasMfidMatch) {
                            owningMember = m
                            break
                        }
                    }
                    
                    if (owningMember != null) {
                        val ownerName = owningMember.companyName ?: "Unknown Member"
                        val ownerId = owningMember.memberId ?: "N/A"
                        val message = "Card already registered to: $ownerName ($ownerId)"
                        logAction("CARD_ALREADY_REGISTERED_OTHER: MFID=$tagId, Owner=$ownerName ($ownerId), TargetId=$memberId")
                        runOnUiThread {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                                .setTitle("Card Already Assigned")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                            statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                            statusMessage.text = "Ready to write"
                            stopScanning()
                        }
                        return@launch
                    }
                    
                    // Card not registered anywhere — proceed
                    verifyMemberWithApi(tagId, cardData, memberId, company, cardType, validUpto)
                } else {
                    // Fallback to API verify directly if member search fails
                    verifyMemberWithApi(tagId, cardData, memberId, company, cardType, validUpto)
                }
            }
        }
    }

    private fun verifyMemberWithApi(tagId: String, cardData: Map<String, String>?, memberId: String, company: String, cardType: String, validUpto: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                 val cardPassword = cardData?.get("password") ?: ""
                 
                 if (cardPassword.isEmpty()) {
                      logAction("BLANK_CARD_DETECTED: Card is truly blank (no member data and no password).")
                      showWrongCardAlert()
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
                     val response = result.getOrNull()
                     val existingCards = response?.cards ?: emptyList()
                     
                     platformLog("SITACardMaster", "VALIDATION_LOG (Post-Scan): Checking for duplicate card type: $cardType")
                     platformLog("SITACardMaster", "VALIDATION_LOG (Post-Scan): Existing cards count: ${existingCards.size}")
                     
                     var isDuplicateType = false
                     existingCards.forEachIndexed { index, card ->
                         val existingType = card.cardType ?: "N/A"
                         val match = existingType.trim().equals(cardType.trim(), ignoreCase = true)
                         platformLog("SITACardMaster", "VALIDATION_LOG (Post-Scan): Card #$index - ID: ${card.card_mfid}, Type: $existingType, Match: $match")
                         if (match) isDuplicateType = true
                     }

                     if (isDuplicateType) {
                         logAction("VERIFY_API_FAILED: Card of type $cardType already assigned.")
                         runOnUiThread {
                             com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                                 .setTitle("Duplicate Card Type")
                                 .setMessage("Card of type '$cardType' already assigned to this member")
                                 .setPositiveButton("OK", null)
                                 .show()
                             statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
                             statusMessage.text = "Ready to write"
                             stopScanning()
                         }
                         return@launch
                     }

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
                         if (error.lowercase().contains("card not found") || error.lowercase().contains("register the card")) {
                             showWrongCardAlert()
                         } else {
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
                 }
            } // end of GlobalScope.launch
        }

    private fun showWrongCardAlert() {
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
            statusMessage.setTextColor(resources.getColor(R.color.gray_text, theme))
            statusMessage.text = "Ready to write"
            stopScanning()
        }
    }

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
            onResult = { success: Boolean, message: String ->
                runOnUiThread {
                    statusMessage.text = message
                    logAction("Write Result: $message")
                    if (success) {
                        statusMessage.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        
                        // Show Success Alert
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@IssueCardActivity)
                            .setTitle("Card Issued Successfully")
                            .setMessage("Member: $company\nCard Type: $cardType")
                            .setPositiveButton("OK", null)
                            .show()
                            
                        // Save to local storage
                        // Assuming DatabaseHelper.saveIssuedCard signature might still need 'totalBuy', passing "0" or checking if it needs update
                        // If DatabaseHelper is strictly defined, I might need to update it too if I want to remove it there.
                        // For now sticking to minimal changes as "make ui also" was the ask.
                        try {
                            DatabaseHelper(this@IssueCardActivity).saveIssuedCard(
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