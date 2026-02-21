package com.example.sitacardmaster.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sitacardmaster.NfcManager
import com.example.sitacardmaster.PlatformDatePicker
import com.example.sitacardmaster.platformLog
import com.example.sitacardmaster.network.MemberApiClient
import com.example.sitacardmaster.network.models.VerifyMemberResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueCardScreen(nfcManager: NfcManager, onBack: () -> Unit) {
    val apiClient = remember { MemberApiClient() }
    
    var memberId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validUpto by remember { mutableStateOf("") }
    
    var phoneNumber by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var emailText by remember { mutableStateOf("") }
    var websiteText by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var showMemberInfoCard by remember { mutableStateOf(false) }

    var cardType by remember { mutableStateOf("Membership") }
    var isCardTypeDropdownExpanded by remember { mutableStateOf(false) }
    val cardTypeOptions = listOf("Membership", "Add-on", "Event")

    var companySearchQuery by remember { mutableStateOf("") }
    var isCompanyDropdownExpanded by remember { mutableStateOf(false) }
    var companySuggestions by remember { mutableStateOf<List<VerifyMemberResponse>>(emptyList()) }
    var selectedCompanyName by remember { mutableStateOf("") }

    val totalBuy = "0"
    var statusMessage by remember { mutableStateOf("Ready to write") }
    var scanningMode by remember { mutableStateOf<ScanMode>(ScanMode.None) }
    
    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val white = Color.White
    val errorRed = Color(0xFFD32F2F)
    val grayText = Color(0xFF757575)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val tag by nfcManager.detectedTag
    val tagId by nfcManager.detectedTagId

    var isCompanyFocused by remember { mutableStateOf(false) }

    LaunchedEffect(companySearchQuery, isCompanyFocused) {
        if (!isCompanyFocused) return@LaunchedEffect
        delay(300)
        val query = if (companySearchQuery == selectedCompanyName) "" else companySearchQuery
        val result = apiClient.getApprovedMembers(query)
        if (result.isSuccess) {
            companySuggestions = result.getOrNull() ?: emptyList()
            // Only show dropdown if there are suggestions and text field is focused
            isCompanyDropdownExpanded = companySuggestions.isNotEmpty() && isCompanyFocused
        }
    }

    LaunchedEffect(tag) {
        if (scanningMode != ScanMode.None && tag != null) {
            if (scanningMode == ScanMode.Writing) {
                statusMessage = "Card detected! Verifying with API..."
                scope.launch {
                    val mfid = tagId ?: ""
                    platformLog("IssueCardScreen", "Verifying Member: $memberId, Company: $selectedCompanyName, MFID: $mfid")
                    val result = apiClient.verifyMember(
                        memberId = memberId,
                        companyName = selectedCompanyName,
                        password = password,
                        cardMfid = mfid,
                        cardValidity = validUpto,
                        cardType = cardType
                    )
                    
                    if (result.isSuccess) {
                        statusMessage = "Member Verified! Writing to Card..."
                        nfcManager.writeCard(
                            memberId = memberId,
                            companyName = selectedCompanyName,
                            password = password,
                            validUpto = validUpto,
                            totalBuy = totalBuy,
                            cardType = cardType,
                            onResult = { success, message ->
                                statusMessage = message
                                scanningMode = ScanMode.None
                                if (success) {
                                    companySearchQuery = ""
                                    selectedCompanyName = ""
                                    password = ""
                                    memberId = ""
                                    validUpto = ""
                                    showMemberInfoCard = false
                                    statusMessage = "Card Issued Successfully. Ready for next."
                                    nfcManager.stopScanning()
                                }
                            }
                        )
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Verification Failed"
                        statusMessage = "Error: $error"
                        scanningMode = ScanMode.None
                        nfcManager.stopScanning()
                    }
                }
            } else if (scanningMode == ScanMode.Clearing) {
                statusMessage = "Card detected! Clearing..."
                nfcManager.clearCard { success, message ->
                    statusMessage = message
                    scanningMode = ScanMode.None
                    if (success) {
                        companySearchQuery = ""
                        selectedCompanyName = ""
                        password = ""
                        memberId = ""
                        validUpto = ""
                        showMemberInfoCard = false
                        scope.launch { snackbarHostState.showSnackbar("Card Cleared: $message") }
                    }
                    nfcManager.stopScanning()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 4.dp,
                color = white
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        scanningMode = ScanMode.None
                        nfcManager.stopScanning()
                        onBack()
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "Back",
                            tint = brandBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Issue New Card",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = brandBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        containerColor = surfaceGray,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = white),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Card Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = brandBlue,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = isCompanyDropdownExpanded,
                        onExpandedChange = { isCompanyDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = companySearchQuery,
                            onValueChange = { 
                                companySearchQuery = it
                                if (it != selectedCompanyName) {
                                    selectedCompanyName = ""
                                    showMemberInfoCard = false
                                }
                            },
                            label = { Text("Company Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .menuAnchor()
                                .onFocusChanged { focusState ->
                                    isCompanyFocused = focusState.isFocused
                                },
                            leadingIcon = {
                                Icon(Icons.Default.Business, contentDescription = null, tint = brandBlue)
                            },
                            trailingIcon = {
                                if (companySearchQuery.isNotEmpty() && companySearchQuery != selectedCompanyName) {
                                    IconButton(onClick = { 
                                        companySearchQuery = ""
                                        selectedCompanyName = ""
                                        showMemberInfoCard = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = grayText)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        
                        ExposedDropdownMenu(
                            expanded = isCompanyDropdownExpanded,
                            onDismissRequest = { isCompanyDropdownExpanded = false }
                        ) {
                            companySuggestions.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text("${member.companyName} (${member.memberId})") },
                                    onClick = {
                                        selectedCompanyName = member.companyName ?: ""
                                        companySearchQuery = selectedCompanyName
                                        memberId = member.memberId ?: ""
                                        validUpto = formatDate(member.validity)
                                        phoneNumber = member.phoneNumber ?: "---"
                                        whatsappNumber = member.whatsapp ?: "---"
                                        emailText = member.email ?: "---"
                                        websiteText = member.website ?: "---"
                                        addressText = member.companyAddress ?: "---"
                                        showMemberInfoCard = true
                                        isCompanyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = brandBlue) },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = isCardTypeDropdownExpanded,
                        onExpandedChange = { isCardTypeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = cardType,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Card Type") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .menuAnchor(),
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = brandBlue) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCardTypeDropdownExpanded) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        ExposedDropdownMenu(
                            expanded = isCardTypeDropdownExpanded,
                            onDismissRequest = { isCardTypeDropdownExpanded = false }
                        ) {
                            cardTypeOptions.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        cardType = selectionOption
                                        isCardTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (showMemberInfoCard) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 16.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MEMBER ID", fontSize = 10.sp, color = grayText, fontWeight = FontWeight.Bold)
                                        Text(memberId, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("VALID UPTO", fontSize = 10.sp, color = grayText, fontWeight = FontWeight.Bold)
                                        Text(validUpto, fontSize = 14.sp, color = brandBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                
                                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))
                                
                                InfoRow(icon = Icons.Default.Phone, text = phoneNumber, tint = brandBlue)
                                InfoRow(icon = Icons.Default.Phone, text = whatsappNumber, tint = brandBlue)
                                InfoRow(icon = Icons.Default.Email, text = emailText, tint = brandBlue)
                                InfoRow(icon = Icons.Default.Language, text = websiteText, tint = brandBlue)
                                InfoRow(icon = Icons.Default.LocationOn, text = addressText, tint = brandBlue, alignTop = true)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = Color(0xFFE0E0E0),
                        thickness = 1.dp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (statusMessage.contains("Error") || statusMessage.contains("failed") || statusMessage.contains("Failed")) errorRed else grayText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (scanningMode != ScanMode.None) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(bottom = 12.dp),
                                color = brandBlue
                            )
                            Text(
                                text = if(scanningMode == ScanMode.Clearing) "TAP CARD TO CLEAR..." else "TAP CARD NOW...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = brandBlue,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { 
                                    scanningMode = ScanMode.None
                                    statusMessage = "Ready to write"
                                    nfcManager.stopScanning()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = errorRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel Scan", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (selectedCompanyName.isEmpty()) {
                                        statusMessage = "Error: Please select a company from the list"
                                        return@Button
                                    }
                                    if (memberId.isEmpty()) {
                                        statusMessage = "Error: Member ID is missing"
                                        return@Button
                                    }
                                    if (password.isEmpty()) {
                                        statusMessage = "Error: Please enter password"
                                        return@Button
                                    }
                                    if (cardType.isEmpty()) {
                                        statusMessage = "Error: Please select card type"
                                        return@Button
                                    }
                                    scanningMode = ScanMode.Writing
                                    statusMessage = "Scanning... Tap Card"
                                    nfcManager.startScanning()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = brandBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Start Scan & Write", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    scanningMode = ScanMode.Clearing
                                    statusMessage = "Tap Card to Clear Data..."
                                    nfcManager.startScanning()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = grayText),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear Card", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, alignTop: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(16.dp)
                .then(if (alignTop) Modifier.padding(top = 2.dp) else Modifier)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 10.dp),
            lineHeight = 16.sp
        )
    }
}

enum class ScanMode {
    None, Writing, Clearing
}

fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    try {
        val datePart = dateStr.substringBefore("T").substringBefore(" ")
        val components = datePart.split("-")
        if (components.size == 3) {
            val year = components[0]
            val month = components[1]
            val day = components[2]
            return "$day/$month/$year"
        }
    } catch (e: Exception) {
        // Fallback to returning original string
    }
    return dateStr
}
