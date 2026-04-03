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

    var isScanning by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var cardData by remember { mutableStateOf<Map<String, String>?>(null) }
    var scanStatus by remember { mutableStateOf("") }


    var remainingSeconds by remember { mutableStateOf(60) }

    // API Integration
    val apiClient = remember { MemberApiClient() }


    val scope = rememberCoroutineScope()
    var currentAmount by remember { mutableStateOf<String?>("Loading...") }
    var globalAmount by remember { mutableStateOf<String?>("0.00") }

    val uriHandler = LocalUriHandler.current
    var apiResponse by remember {
        mutableStateOf<com.example.sitacardmaster.network.models.VerifyMemberResponse?>(
            null
        )
    }

    // Verified Member State
    var verificationError by remember { mutableStateOf<String?>(null) }

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
                    scanStatus = "Failed to open URL"
                }
                return@LaunchedEffect
            }

            if (isDeleteMode) {
                platformLog("Dashboard", "Processing card deletion...")
                scanStatus = "Deleting data..."
                nfcManager.clearCard { success, message ->
                    if (success) {
                        scanStatus = "Card data deleted successfully"
                        platformLog("Dashboard", "Card deletion success")
                    } else {
                        scanStatus = "Delete Failed: $message"
                        platformLog("Dashboard", "Card deletion failed: $message")
                    }
                    isScanning = false
                    isDeleteMode = false // Reset mode
                }
                return@LaunchedEffect
            }


            platformLog("Dashboard", "Reading card data...")
            nfcManager.readCard { success, data, message ->
                if (success) {
                    cardData = data
                    scanStatus =
                        if (data == null) "No data in the card" else "Card read successfully"
                    platformLog("Dashboard", "Card read success: ${data?.get("memberId")}")

                    // Fetch Amount from API
                    if (data != null) {
                        val memberId = data["memberId"] ?: ""
                        val companyName = data["companyName"] ?: ""
                        platformLog(
                            "Dashboard",
                            "Fetching Amount for ID: $memberId, Company: $companyName"
                        )

                        currentAmount = "Loading..."
                        verificationError = null // Reset error before new request

                        val password = data["password"] ?: ""
                        scope.launch {
                            val result = apiClient.verifyMember(memberId, companyName, password)
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
                    scanStatus = "Read error: $message"
                    platformLog("Dashboard", "Card read error: $message")
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
                scanStatus = "No card detected"
            }
        }
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
                        text = "Admin",
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
            PoweredBySection(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 8.dp)
            )
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
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Fix: Circular container with shadow/elevation
                Surface(
                    modifier = Modifier
                        .size(180.dp)
                        .padding(vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF0F0F0))
                ) {
                    Image(
                        painter = painterResource(Res.drawable.sita_logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .clickable {
                                isScanning = true
                                isDeleteMode = false
                                scanStatus = "Scanning... Tap card"
                                nfcManager.startScanning()
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isScanning) {
                        if (isDeleteMode) "TAP CARD TO DELETE DATA..." else "TAP CARD NOW..."
                    } else "TAP LOGO TO SCAN CARD",
                    color = brandBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
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
                                    "PREMIUM MEMBER",
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

            // Action Buttons Reordered as per Screenshot
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        isScanning = true
                        isDeleteMode = true
                        scanStatus = "TAP CARD TO DELETE DATA..."
                        nfcManager.startScanning()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = errorRed),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "DELETE CARD",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = onIssueCardClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "ISSUE CARD",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    cardData = null
                    apiResponse = null
                    verificationError = null
                    currentAmount = "Loading..."
                    globalAmount = "0.00"
                    scanStatus = ""
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "CLEAR PAGE",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Hidden Secondary Action
            if (false) { // Keep hidden as it wasn't requested for dashboard flow
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onWriteLogoUrlClick,
                    modifier = Modifier.fillMaxWidth(0.9f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = successGreen)
                ) {
                     Text("WRITE LOGO URL")
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
