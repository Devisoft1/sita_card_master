package com.example.sitacardmaster.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import com.example.sitacardmaster.NfcManager
import com.example.sitacardmaster.network.MemberApiClient
import com.example.sitacardmaster.PoweredBySection
import com.example.sitacardmaster.platformLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    nfcManager: NfcManager,
    onIssueCardClick: () -> Unit,
    onVerifyMemberClick: () -> Unit,
    onWriteLogoUrlClick: () -> Unit,
    onLogsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val grayText = Color(0xFF666666)
    val errorRed = Color(0xFFE53935)
    val successGreen = Color(0xFF4CAF50)

    DisposableEffect(Unit) {
        onDispose {
            nfcManager.clearScanData()
        }
    }

    val settings = remember { com.example.sitacardmaster.SettingsStorage() }
    var userName by remember { mutableStateOf("Admin") }

    LaunchedEffect(Unit) {
        userName = settings.getString("adminId", "Admin")
    }

    var isScanning by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var cardData by remember { mutableStateOf<Map<String, String>?>(null) }
    var scanStatus by remember { mutableStateOf("") }
    
    // Dialog State
    var showResultDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }

    var remainingSeconds by remember { mutableStateOf(60) }
    var currentAmount by remember { mutableStateOf<String?>("Loading...") }
    var globalAmount by remember { mutableStateOf<String?>("0.00") }
    var apiResponse by remember {
        mutableStateOf<com.example.sitacardmaster.network.models.VerifyMemberResponse?>(
            null
        )
    }
    var verificationError by remember { mutableStateOf<String?>(null) }

    val resetDashboard = {
        cardData = null
        apiResponse = null
        verificationError = null
        currentAmount = "Loading..."
        globalAmount = "0.00"
        scanStatus = ""
        isScanning = false
        isDeleteMode = false
        nfcManager.clearScanData()
    }

    // API Integration
    val uriHandler = LocalUriHandler.current
    val apiClient = remember { MemberApiClient() }

    val scope = rememberCoroutineScope()

    // Logic to handle scan results
    val detectedTag by nfcManager.detectedTag
    LaunchedEffect(detectedTag) {
        if (isScanning && detectedTag != null) {
            // New Feature: URL Detection (Matching Android logic)
            val url = nfcManager.extractUrl(detectedTag)
            if (url != null) {
                platformLog("Dashboard", "URL detected on card: $url")
                isScanning = false
                nfcManager.stopScanning()
                try {
                    uriHandler.openUri(url)
                } catch (e: Exception) {
                    platformLog("Dashboard", "Failed to open URL: ${e.message}")
                    dialogTitle = "URL Error"
                    dialogMessage = "Failed to open URL: ${e.message}"
                    showResultDialog = true
                }
                return@LaunchedEffect
            }

            if (isDeleteMode) {
                platformLog("Dashboard", "Processing card deletion...")
                // scanStatus = "Reading card before deletion..."

                nfcManager.readCard { readSuccess, data, readMessage ->
                    if (!readSuccess) {
                        dialogTitle = "Read Error"
                        dialogMessage = readMessage
                        showResultDialog = true
                        return@readCard
                    }

                    val cardMfid = (data?.get("card_mfid") ?: "").lowercase()
                    val password = data?.get("password") ?: ""

                    if (cardMfid.isEmpty()) {
                        dialogTitle = "Already Blank"
                        dialogMessage = "This card is already blank or not registered."
                        showResultDialog = true
                        return@readCard
                    }

                    // scanStatus = "Validating deletion..."
                    scope.launch {
                        val deleteResult = apiClient.deleteCard(cardMfid, password)
                        deleteResult.fold(
                            onSuccess = { response ->
                                platformLog("Dashboard", "Server deletion success: ${response.message}")
                                // scanStatus = "Database record cleared. Wiping card..."

                                nfcManager.clearCard { clearSuccess, clearMessage ->
                                    if (clearSuccess) {
                                        platformLog("Dashboard", "DATABASE_DELETED: Card record removed from server.")
                                        platformLog("Dashboard", "CARD_DELETED: Card data physically wiped.")
                                        dialogTitle = "Success"
                                        dialogMessage = "Data cleared successfully"
                                    } else {
                                        platformLog("Dashboard", "DELETE_PARTIAL_FAILURE: Server record deleted, but CARD_WIPE_FAILED: $clearMessage")
                                        dialogTitle = "Partial Success"
                                        dialogMessage = "API deleted record, but physical wipe failed: $clearMessage. Please try again."
                                    }
                                    showResultDialog = true
                                }
                            },
                            onFailure = { error ->
                                val errorMessage = error.message ?: "Deletion failed"
                                
                                if (errorMessage.lowercase().contains("card not found") || errorMessage.lowercase().contains("404")) {
                                    platformLog("Dashboard", "Step 2 (404): Card not found in DB - Proceeding to wipe orphaned card.")
                                    // scanStatus = "Card not in registry. Wiping anyway..."
                                    
                                    nfcManager.clearCard { clearSuccess, clearMessage ->
                                        if (clearSuccess) {
                                            platformLog("Dashboard", "DATABASE_DELETED: Card already missing from server (404 path).")
                                            platformLog("Dashboard", "CARD_DELETED: Orphaned card physically wiped.")
                                            dialogTitle = "Success"
                                            dialogMessage = "Data cleared successfully"
                                        } else {
                                            dialogTitle = "Wipe Failed"
                                            dialogMessage = "Record not found in database, and physical wipe failed: $clearMessage."
                                        }
                                        showResultDialog = true
                                    }
                                } else {
                                    platformLog("Dashboard", "Server deletion failed: $errorMessage")
                                    dialogTitle = "Deletion Declined"
                                    dialogMessage = errorMessage
                                    showResultDialog = true
                                }
                            }
                        )
                    }
                }
                return@LaunchedEffect
            }


            platformLog("Dashboard", "Reading card data...")
            nfcManager.readCard { success, data, message ->
                if (success) {
                    cardData = data
                    if (data == null) {
                        dialogTitle = "Empty Card"
                        dialogMessage = "card is empty not assigne to any member"
                        showResultDialog = true
                    } else {
                        platformLog("Dashboard", "Card read success: ${data["memberId"]}")
                        val memberId = data["memberId"] ?: ""
                        val companyName = data["companyName"] ?: ""
                        platformLog(
                            "Dashboard",
                            "Fetching Amount for ID: $memberId, Company: $companyName"
                        )

                        currentAmount = "Loading..."
                        verificationError = null // Reset error before new request

                        val password = data["password"] ?: ""
                        val cardType = data["cardType"] ?: "Member"
                        scope.launch {
                            val result = apiClient.verifyMember(memberId, companyName, password, cardType = cardType)
                            result.fold(
                                onSuccess = { response ->
                                    apiResponse = response
                                    val scannedMfid = data["card_mfid"] ?: ""
                                    val matchingCard = response.cards?.find {
                                        it.card_mfid.equals(
                                            scannedMfid,
                                            ignoreCase = true
                                        )
                                    }

                                    if (matchingCard != null) {
                                        // Use cardTotal from matchingCard if available, otherwise from response root, otherwise currentTotal
                                        val displayTotal =
                                            if (matchingCard.cardTotal > 0) matchingCard.cardTotal else if (response.cardTotal > 0) response.cardTotal else response.currentTotal
                                        currentAmount = formatAmount(displayTotal)
                                        globalAmount = formatAmount(response.globalTotal)
                                        platformLog(
                                            "Dashboard",
                                            "Card-specific balance: $displayTotal (from cards list or fallback)"
                                        )
                                    } else {
                                        currentAmount = formatAmount(response.globalTotal)
                                        globalAmount = formatAmount(response.currentTotal)
                                        platformLog(
                                            "Dashboard",
                                            "No matching card. Fallback to global totals."
                                        )
                                    }
                                },
                                onFailure = { error ->
                                    apiResponse = null
                                    currentAmount = "N/A"
                                    globalAmount = "N/A"
                                    verificationError =
                                        error.message ?: "Member verification failed"
                                    platformLog("Dashboard", "Amount fetch error: ${error.message}")
                                }
                            )
                        }
                    }
                } else {
                    platformLog("Dashboard", "Card read error: $message")
                    dialogTitle = "Read Error"
                    dialogMessage = message
                    showResultDialog = true
                }
                isScanning = false
            }
        }
    }


    // 1-minute auto-timeout with visible countdown
    LaunchedEffect(isScanning) {
        if (isScanning) {
            remainingSeconds = 60
            while (remainingSeconds > 0 && isScanning) {
                delay(1000)
                remainingSeconds--
            }
            if (isScanning && remainingSeconds <= 0) {
                isScanning = false
                isDeleteMode = false
                nfcManager.stopScanning()
                dialogTitle = "Timeout"
                dialogMessage = "No card detected within 60 seconds."
                showResultDialog = true
            }
        }
    }

    // Result Dialog
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { 
                showResultDialog = false
                resetDashboard()
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage.replace("(found in CardTransaction Log)", "").trim()) },
            confirmButton = {
                Button(onClick = {
                    showResultDialog = false
                    resetDashboard()
                }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                shadowElevation = 2.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = userName.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = brandBlue,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = errorRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        containerColor = surfaceGray,

        bottomBar = {
            // Footer: Exact match for Android XML 3-text Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
                    .clickable { uriHandler.openUri("https://devisoft.co.in") },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Powered by ",
                    color = grayText,
                    fontSize = 11.sp
                )
                Text(
                    text = "Devi",
                    color = Color(0xFF00509E), // devisoft_blue
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Soft",
                    color = Color(0xFFF58220), // devisoft_orange
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Scan Section
            Column(
                modifier = Modifier
                    .fillMaxWidth(), // Removed vertical padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Fix: Circular container with shadow/elevation
                // Logo Section: Enlarged as requested
                Image(
                    painter = painterResource(Res.drawable.sita_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(260.dp) // Slightly reduced to help with spacing and safety
                        .clickable {
                            isScanning = true
                            isDeleteMode = false
                            nfcManager.startScanning()
                        },
                    contentScale = ContentScale.Fit
                )

                // No Spacer here as marginTop is 0dp in XML

                Text(
                    text = if (isScanning) {
                        if (isDeleteMode) "Tap card to delete data..." else "Tap card now..."
                    } else "Tap logo to scan card",
                    color = brandBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-30).dp) // Moved even higher
                )

                if (isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Time Elapsed : ${remainingSeconds}s",
                        color = grayText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = brandBlue,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            isScanning = false
                            isDeleteMode = false
                            nfcManager.stopScanning()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = grayText)
                    ) {
                        Text("Stop Scanning")
                    }
                }
            }



            // Error Display Section
            if (verificationError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, errorRed)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back), // Using back icon as placeholder if error icon is missing, or preferably an alert icon if available
                            contentDescription = "Error",
                            tint = errorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = verificationError!!,
                            color = errorRed,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                // Reset all states
                                cardData = null
                                apiResponse = null
                                verificationError = null
                                currentAmount = "Loading..."
                                scanStatus = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = errorRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel / Reset", color = Color.White)
                        }
                    }
                }
            }
            // Member Details Card (Only show if no error)
            else if (cardData != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 8.dp,
                    color = brandBlue
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(brandBlue, Color(0xFF1A1C63))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (apiResponse?.cardType ?: cardData!!["cardType"] ?: "Member").uppercase(),
                                    color = Color(0xFFFFD700),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.logo),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Member ID",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        cardData!!["memberId"] ?: "N/A",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Company",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        cardData!!["companyName"] ?: "N/A",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Current Total",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                currentAmount ?: "₹0.00",
                                                color = Color(0xFFFFD700),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "Valid Upto",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                cardData!!["validUpto"] ?: "N/A",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Global Total",
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            globalAmount ?: "N/A",
                                            color = Color(0xFF4CAF50),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (apiResponse != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            apiResponse?.companyAddress?.let { address ->
                                                if (address.isNotBlank()) {
                                                    DashboardInfoRow(
                                                        Icons.Default.LocationOn,
                                                        address
                                                    ) {
                                                        try {
                                                            uriHandler.openUri("geo:0,0?q=$address")
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                            apiResponse?.phoneNumber?.let { phone ->
                                                if (phone.isNotBlank()) {
                                                    DashboardInfoRow(Icons.Default.Phone, phone) {
                                                        try {
                                                            uriHandler.openUri("tel:$phone")
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                            apiResponse?.email?.let { email ->
                                                if (email.isNotBlank()) {
                                                    DashboardInfoRow(Icons.Default.Email, email) {
                                                        try {
                                                            uriHandler.openUri("mailto:$email")
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                            apiResponse?.website?.let { website ->
                                                if (website.isNotBlank()) {
                                                    DashboardInfoRow(
                                                        Icons.Default.Language,
                                                        website
                                                    ) {
                                                        val url =
                                                            if (website.startsWith("http")) website else "https://$website"
                                                        try {
                                                            uriHandler.openUri(url)
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                            apiResponse?.whatsapp?.let { whatsapp ->
                                                if (whatsapp.isNotBlank()) {
                                                    DashboardInfoRow(
                                                        Icons.Default.Message,
                                                        whatsapp
                                                    ) {
                                                        val cleanWhatsapp =
                                                            whatsapp.replace(Regex("[^0-9]"), "")
                                                        try {
                                                            uriHandler.openUri("https://wa.me/$cleanWhatsapp")
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (scanStatus.isNotEmpty() && !isScanning) {
                Text(
                    text = scanStatus,
                    color = when {
                        scanStatus.contains(
                            "successfully",
                            ignoreCase = true
                        ) || scanStatus.contains("Success", ignoreCase = true) -> successGreen

                        scanStatus.contains("Error") || scanStatus.contains("failed") || scanStatus.contains(
                            "Failed"
                        ) || scanStatus.contains("not detected", ignoreCase = true) -> errorRed

                        else -> grayText
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Action Buttons: Exact match for Android layout heights and rounding
            Column(
                modifier = Modifier.fillMaxWidth(0.95f), // Matching Android padding
                verticalArrangement = Arrangement.spacedBy(8.dp) // Matching Android 4sdp spacing
            ) {
                // 1. Issue New Card (Full Width, Blue)
                Button(
                    onClick = onIssueCardClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp), // Approx 40sdp
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Issue New Card",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // 2. Write Logo URL (Full Width, Green)
                Button(
                    onClick = onWriteLogoUrlClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp), // Approx 40sdp
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = successGreen),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Write Logo URL",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // 3. Clear Page and Delete Card (Side-by-Side row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { resetDashboard() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "Clear Page",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Button(
                        onClick = {
                            isScanning = true
                            isDeleteMode = true
                            nfcManager.startScanning()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = errorRed),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "Delete Card",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
            }
    }
}


fun formatAmount(amount: Any?): String {
    if (amount == null) return "₹0.00"
    
    val doubleValue = when (amount) {
        is Double -> amount
        is Int -> amount.toDouble()
        is Long -> amount.toDouble()
        is String -> amount.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    
    val rounded = ((doubleValue * 100.0).toLong() / 100.0)
    val parts = rounded.toString().split(".")
    val integerPart = parts[0]
    var decimalPart = if (parts.size > 1) parts[1] else "00"
    
    // Ensure two decimal places
    if (decimalPart.length < 2) decimalPart += "0"
    else if (decimalPart.length > 2) decimalPart = decimalPart.substring(0, 2)
    
    // Add commas for thousands
    val regex = "(\\d)(?=(\\d{3})+(?!\\d))".toRegex()
    val formattedInteger = integerPart.replace(regex, "$1,")
    
    return "₹$formattedInteger.$decimalPart"
}


@Composable
fun DetailRow(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DashboardInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
