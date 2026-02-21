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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.MainScope


class IssueCardActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false
    private var isClearing = false

    private lateinit var memberIdText: TextView
    private lateinit var companyNameInput: com.google.android.material.textfield.MaterialAutoCompleteTextView
    private lateinit var passwordInput: com.google.android.material.textfield.TextInputEditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_card)

        nfcManager = AndroidNfcManager(this)

        memberIdText = findViewById(R.id.memberIdText)
        companyNameInput = findViewById(R.id.companyName)
        passwordInput = findViewById(R.id.passwordInput)
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
        val backButton = findViewById<ImageButton>(R.id.backButton)
        findViewById<TextView>(R.id.appBarTitle).text = "Issue New Card"
        findViewById<Button>(R.id.logoutButton)?.visibility = View.GONE

        backButton.setOnClickListener { finish() }

        startScanButton.setOnClickListener {
            val currentName = companyNameInput.text.toString()
            if (selectedCompanyName.isEmpty() || currentName != selectedCompanyName) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please select a company from the list"
                return@setOnClickListener
            }
            if (memberIdText.text.isEmpty() || memberIdText.text == "---") {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Member ID is missing"
                return@setOnClickListener
            }
            if (passwordInput.text.isNullOrEmpty()) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please enter password"
                statusMessage.text = "Error: Please enter password"
                return@setOnClickListener
            }
            if (cardTypeInput.text.isEmpty()) {
                statusMessage.setTextColor(resources.getColor(R.color.error_red, theme))
                statusMessage.text = "Error: Please select card type"
                return@setOnClickListener
            }
            startScanning()
        }

        cancelScanButton.setOnClickListener {
            stopScanning()
        }
        
        findViewById<Button>(R.id.clearCardButton).setOnClickListener {
             startClearCard()
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
        val cardTypes = arrayOf("Membership", "Add-on", "Event")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cardTypes)
        cardTypeInput.setAdapter(adapter)
        cardTypeInput.setText("Membership", false) // Default
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
                // Only show suggestions if text doesn't match the previously selected company
                if (query != selectedCompanyName || query.isEmpty()) {
                    fetchSuggestions(query)
                }
            }
        }

        companyNameInput.setOnClickListener {
            val query = companyNameInput.text.toString()
            if (!companyNameInput.isPopupShowing && (query != selectedCompanyName || query.isEmpty())) {
                fetchSuggestions(query)
            }
        }

        companyNameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
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
                    searchJob = coroutineScope.launch {
                        delay(300)
                        fetchSuggestions(query)
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun fetchSuggestions(query: String) {
        android.util.Log.d("IssueCardActivity", "Fetching suggestions for: $query")
        coroutineScope.launch {
            val result = apiClient.getApprovedMembers(query)
            if (result.isSuccess) {
                val members = result.getOrNull() ?: emptyList()
                android.util.Log.d("IssueCardActivity", "Success: found ${members.size} members")
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
            } else {
                android.util.Log.e("IssueCardActivity", "API Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun clearOtherFields() {
        memberIdText.text = "---"
        passwordInput.text?.clear()
        validUptoText.text = "---"
        phoneNumberText.text = "---"
        whatsappInputText.text = "---"
        emailText.text = "---"
        websiteText.text = "---"
        addressText.text = "---"
        memberInfoCard.visibility = View.GONE
        cardTypeInput.setText("Membership", false)
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


    private fun startScanning() {
        isScanning = true
        statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
        statusMessage.text = "Scanning... Tap Card"
        scanProgress.visibility = View.VISIBLE
        tapCardHint.visibility = View.VISIBLE
        startScanButton.visibility = View.GONE
        cancelScanButton.visibility = View.VISIBLE
        logAction("Scanning started for Member: ${memberIdText.text}")
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

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun verifyAndProcessCard(tag: Tag) {
        // Tag ID (MFID)
        val tagId = tag.id.joinToString("") { byte -> "%02X".format(byte) }
        val memberId = memberIdText.text.toString()
        val company = companyNameInput.text.toString()
        val password = passwordInput.text.toString()
        val cardType = cardTypeInput.text.toString()
        val validUpto = validUptoText.text.toString()

        runOnUiThread {
             statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
             statusMessage.text = "Verifying with API..."
        }
        
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { // Using GlobalScope for simplicity in Activity for now, ideally LifecycleScope
             logAction("API Request: Member=$memberId, Company=$company, MFID=$tagId, Validity=$validUpto")
             logAction("API Password: $password") // Added Log
             val result = apiClient.verifyMember(
                 memberId = memberId,
                 companyName = company,
                 password = password,
                 cardMfid = tagId,
                 cardValidity = validUpto,
                 cardType = cardType
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
        val memberId = memberIdText.text.toString()
        val company = companyNameInput.text.toString()
        val password = passwordInput.text.toString()
        val cardType = cardTypeInput.text.toString()
        val validUpto = validUptoText.text.toString()
        // val totalBuy = totalBuyInput.text.toString() // Removed
        val totalBuy = "0" // Defaulting to 0 since input is removed

        logAction("Starting Write Card. Member: $memberId, Pwd: $password") // Added Log

        nfcManager.writeCard(
            memberId = memberId,
            companyName = company,
            password = password,
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

    private fun performClearCard() {
        statusMessage.text = "Clearing card data..."
        nfcManager.clearCard { success, message ->
            runOnUiThread {
                statusMessage.text = message
                logAction("Clear Result: $message")
                
                if (success) {
                    memberIdText.text = "---"
                    companyNameInput.setText("")
                    memberIdText.text = "---"
                    validUptoText.text = "---"
                    phoneNumberText.text = "---"
                    whatsappInputText.text = "---"
                    emailText.text = "---"
                    websiteText.text = "---"
                    addressText.text = "---"
                    memberInfoCard.visibility = View.GONE
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

    private fun resetForm() {
        companyNameInput.setText("", false)
        selectedCompanyName = ""
        clearOtherFields()
        statusMessage.text = "Card Issued Successfully. Ready for next."
        statusMessage.setTextColor(resources.getColor(R.color.brand_blue, theme))
        
        // Auto-focus on company name for next entry
        companyNameInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(companyNameInput, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }
}
