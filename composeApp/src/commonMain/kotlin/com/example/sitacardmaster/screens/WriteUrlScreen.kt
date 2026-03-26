package com.example.sitacardmaster.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sitacardmaster.NfcManager
import com.example.sitacardmaster.PoweredBySection
import com.example.sitacardmaster.network.MemberApiClient
import com.example.sitacardmaster.network.models.VerifyMemberResponse
import com.example.sitacardmaster.platformLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteUrlScreen(nfcManager: NfcManager, onBack: () -> Unit) {
    val apiClient = remember { MemberApiClient() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var companySearchQuery by remember { mutableStateOf("") }
    var selectedCompanyName by remember { mutableStateOf("") }
    var logoUrlInput by remember { mutableStateOf("") }
    var isCompanyDropdownExpanded by remember { mutableStateOf(false) }
    var companySuggestions by remember { mutableStateOf<List<VerifyMemberResponse>>(emptyList()) }
    var isCompanyFocused by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf("Ready to write") }
    var isScanning by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(60) }

    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val white = Color.White
    val successGreen = Color(0xFF4CAF50)
    val errorRed = Color(0xFFD32F2F)
    val grayText = Color(0xFF757575)

    val tag by nfcManager.detectedTag

    DisposableEffect(Unit) {
        onDispose {
            nfcManager.clearScanData()
        }
    }

    // Fetch suggestions
    LaunchedEffect(companySearchQuery, isCompanyFocused) {
        if (!isCompanyFocused) return@LaunchedEffect
        delay(300)
        val query = if (companySearchQuery == selectedCompanyName) "" else companySearchQuery
        val result = apiClient.getApprovedMembers(query)
        if (result.isSuccess) {
            companySuggestions = result.getOrNull() ?: emptyList()
            isCompanyDropdownExpanded = companySuggestions.isNotEmpty() && isCompanyFocused
        }
    }

    // Timer logic
    LaunchedEffect(isScanning) {
        if (isScanning) {
            remainingSeconds = 60
            while (remainingSeconds > 0 && isScanning) {
                delay(1000)
                remainingSeconds--
            }
            if (isScanning && remainingSeconds <= 0) {
                isScanning = false
                nfcManager.stopScanning()
                statusMessage = "No card detected"
            }
        }
    }

    // Scan result logic
    LaunchedEffect(tag) {
        if (isScanning && tag != null) {
            statusMessage = "Card detected! Writing URL..."
            nfcManager.writeLogoUrl(logoUrlInput) { success, message ->
                statusMessage = message
                isScanning = false
                nfcManager.stopScanning()
                if (success) {
                    companySearchQuery = ""
                    selectedCompanyName = ""
                    logoUrlInput = ""
                    statusMessage = "URL Written! Ready for next."
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
                        isScanning = false
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
                        text = "Write Logo URL",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = brandBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        containerColor = surfaceGray,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PoweredBySection(
                modifier = Modifier
                    .background(white)
                    .padding(bottom = 16.dp)
            )
        }
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
                        text = "Logo URL Details",
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
                                        val memberId = member.memberId ?: ""
                                        if (memberId.isNotBlank()) {
                                            logoUrlInput = "https://sita.shanti-pos.com/member-card/$memberId"
                                        }
                                        isCompanyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = logoUrlInput,
                        onValueChange = { logoUrlInput = it },
                        label = { Text("Logo URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = brandBlue) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Text(
                        text = "Select member to add url in logo and Tap Logo to write.",
                        style = MaterialTheme.typography.bodySmall,
                        color = grayText,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

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
                            color = when {
                                statusMessage.contains("successfully", ignoreCase = true) || statusMessage.contains("Success", ignoreCase = true) || statusMessage.contains("Written") -> successGreen
                                statusMessage.contains("Error") || statusMessage.contains("failed") || statusMessage.contains("Failed") || statusMessage.contains("not detected", ignoreCase = true) -> errorRed
                                else -> grayText
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(bottom = 8.dp),
                                color = brandBlue
                            )
                            Text(
                                text = "Time Elapsed : ${remainingSeconds}s",
                                color = grayText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "TAP CARD NOW...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = brandBlue,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { 
                                    isScanning = false
                                    statusMessage = "Ready to write"
                                    nfcManager.stopScanning()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = errorRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop Scanning", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (selectedCompanyName.isEmpty()) {
                                        statusMessage = "Error: Please select a company from the list"
                                        return@Button
                                    }
                                    if (logoUrlInput.isEmpty()) {
                                        statusMessage = "Error: Logo URL is missing"
                                        return@Button
                                    }
                                    isScanning = true
                                    statusMessage = "Scanning..."
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
                        }
                    }
                }
            }
        }
    }
}
