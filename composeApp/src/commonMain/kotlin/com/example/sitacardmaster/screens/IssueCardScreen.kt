package com.example.sitacardmaster.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sitacardmaster.NfcManager
import kotlinx.coroutines.launch

@Composable
fun IssueCardScreen(nfcManager: NfcManager, onBack: () -> Unit) {
    var memberId by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var validUpto by remember { mutableStateOf("") }
    var totalBuy by remember { mutableStateOf("") }
    var lastBuyDate by remember { mutableStateOf("") }

    var statusMessage by remember { mutableStateOf("Ready to write") }
    var isScanning by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val tag by nfcManager.detectedTag

    LaunchedEffect(tag) {
        if (isScanning && tag != null) {
            statusMessage = "Card detected! Writing..."
            nfcManager.writeCard(
                memberId = memberId,
                companyName = companyName,
                validUpto = validUpto,
                totalBuy = totalBuy,
                onResult = { success, message ->
                    statusMessage = message
                    isScanning = false
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Success: $message")
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    isScanning = false
                    onBack()
                }) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Issue New Card",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = memberId,
                onValueChange = { memberId = it },
                label = { Text("Member ID") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = { Text("Company Name") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = validUpto,
                onValueChange = { validUpto = it },
                label = { Text("Valid Upto (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                placeholder = { Text("YYYY-MM-DD") }
            )

            OutlinedTextField(
                value = totalBuy,
                onValueChange = { totalBuy = it },
                label = { Text("Total Buy") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                text = statusMessage,
                color = if (statusMessage.contains("Error") || statusMessage.contains("failed")) Color.Red else Color.Gray,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text("TAP CARD NOW...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { isScanning = false }) {
                    Text("Cancel Scan")
                }
            } else {
                Button(
                    onClick = {
                        if (memberId.isEmpty() || companyName.isEmpty()) {
                            statusMessage = "Error: Please fill all fields"
                            return@Button
                        }
                        isScanning = true
                        statusMessage = "Scanning... Tap Card"
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Start Scan & Write")
                }
            }
        }
    }
}
