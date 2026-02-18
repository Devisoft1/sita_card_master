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
import com.example.sitacardmaster.NfcManager
import com.example.sitacardmaster.network.MemberApiClient
import com.example.sitacardmaster.platformLog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@Composable
fun DashboardScreen(
    nfcManager: NfcManager,
    onIssueCardClick: () -> Unit,
    onVerifyMemberClick: () -> Unit,
    onLogsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val grayText = Color(0xFF666666)
    val errorRed = Color(0xFFE53935)

    var isScanning by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var cardData by remember { mutableStateOf<Map<String, String>?>(null) }
    var scanStatus by remember { mutableStateOf("") }
    
    // API Integration
    val apiClient = remember { MemberApiClient() }
    val scope = rememberCoroutineScope()
    var currentAmount by remember { mutableStateOf<String?>("Loading...") }

    // Verified Member State
    var verificationError by remember { mutableStateOf<String?>(null) }
    
    // Logic to handle scan results
    val detectedTag by nfcManager.detectedTag
    LaunchedEffect(detectedTag) {
        if (isScanning && detectedTag != null) {
            
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
                    scanStatus = if (data == null) "No data in the card" else "Card read successfully"
                    platformLog("Dashboard", "Card read success: ${data?.get("memberId")}")
                    
                    // Fetch Amount from API
                    if (data != null) {
                        val memberId = data["memberId"] ?: ""
                        val companyName = data["companyName"] ?: ""
                        platformLog("Dashboard", "Fetching Amount for ID: $memberId, Company: $companyName")
                        
                        currentAmount = "Loading..."
                        verificationError = null // Reset error before new request
                        
                        val password = data["password"] ?: ""
                        scope.launch {
                             val result = apiClient.verifyMember(memberId, companyName, password)
                             result.fold(
                                 onSuccess = { response ->
                                     currentAmount = "₹${response.currentTotal}"
                                     platformLog("Dashboard", "Amount fetched: ${response.currentTotal}")
                                 },
                                 onFailure = { error ->
                                     currentAmount = "N/A"
                                     verificationError = error.message ?: "Member verification failed"
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

    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 2.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(40.dp)
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLogout) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "Back",
                            tint = brandBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandBlue,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = errorRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = surfaceGray
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
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .clickable {
                            isScanning = true
                            // If user just clicks logo, default to read mode
                            isDeleteMode = false
                            scanStatus = "Scanning... Tap card"
                            nfcManager.startScanning()
                        },
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isScanning) {
                        if (isDeleteMode) "TAP CARD TO DELETE DATA..." else "Scanning... Tap card"
                    } else "Tap logo to scan card",
                    color = brandBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isScanning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = brandBlue,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { isScanning = false; nfcManager.stopScanning() },
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
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
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
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Total Buy",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                "₹${cardData!!["totalBuy"] ?: "0.00"}",
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
                                            "Amount (Current Total)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            currentAmount ?: "N/A",
                                            color = Color(0xFF4CAF50),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (scanStatus.isNotEmpty() && !isScanning) {
                Text(
                    text = scanStatus,
                    color = grayText,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Action Buttons
            Button(
                onClick = onIssueCardClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandBlue)
            ) {
                Text("Issue New Card", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
             Button(
                onClick = {
                    isScanning = true
                    isDeleteMode = true
                    scanStatus = "TAP CARD TO DELETE DATA..."
                    nfcManager.startScanning()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = errorRed)
            ) {
                Text("Delete Card Data", fontWeight = FontWeight.Bold)
            }
            

        }
    }
}



@Composable
fun DetailRow(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
