package com.example.sitacardmaster.screens

import androidx.compose.foundation.Image
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
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.Res
import sitacardmaster.composeapp.generated.resources.logo

@Composable
fun DashboardScreen(
    nfcManager: NfcManager,
    onIssueCardClick: () -> Unit,
    onLogsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val grayText = Color(0xFF666666)
    val errorRed = Color(0xFFE53935)

    var isScanning by remember { mutableStateOf(false) }
    var cardData by remember { mutableStateOf<Map<String, String>?>(null) }
    var scanStatus by remember { mutableStateOf("") }

    // Logic to handle scan results
    val detectedTag by nfcManager.detectedTag
    LaunchedEffect(detectedTag) {
        if (isScanning && detectedTag != null) {
            nfcManager.readCard { success, data, message ->
                if (success) {
                    cardData = data
                    scanStatus = if (data == null) "Blank card detected" else "Card read successfully"
                } else {
                    scanStatus = "Read error: $message"
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
                        .windowInsetsPadding(WindowInsets.safeContent)
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = brandBlue
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
            horizontalAlignment = Alignment.CenterHorizontally
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
                            scanStatus = "Scanning... Tap card"
                            nfcManager.startScanning()
                        },
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isScanning) "Scanning... Tap card" else "Tap logo to scan card",
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

            // Member Details Card
            if (cardData != null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "MEMBER DETAILS",
                            color = brandBlue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        DetailRow("Member ID", cardData!!["memberId"] ?: "N/A", Color.Black)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                        DetailRow("Company", cardData!!["companyName"] ?: "N/A", Color.Black)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                        DetailRow("Valid Upto", cardData!!["validUpto"] ?: "N/A", Color.Black)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                        DetailRow("Total Buy", cardData!!["totalBuy"] ?: "0.00", brandBlue)
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onLogsClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Action Logs")
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
