package com.example.sitacardmaster.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sitacardmaster.NfcManager
import com.example.sitacardmaster.PlatformDatePicker
import com.example.sitacardmaster.platformLog
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun IssueCardScreen(nfcManager: NfcManager, onBack: () -> Unit) {
    var memberId by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var validUpto by remember { mutableStateOf("") }
    var totalBuy by remember { mutableStateOf("") }
    var lastBuyDate by remember { mutableStateOf("") }

    var statusMessage by remember { mutableStateOf("Ready to write") }
    var isScanning by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeContent),
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

            // Date Picker Field
            OutlinedTextField(
                value = validUpto,
                onValueChange = { },
                label = { Text("Valid Upto (DD/MM/YYYY)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { 
                        platformLog("IssueCard", "Date field clicked!")
                        showDatePicker = true 
                    },
                placeholder = { Text("DD/MM/YYYY") },
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Select Date"
                    )
                }
            )

            if (showDatePicker) {
                platformLog("IssueCard", "Showing date picker dialog")
                CustomDatePickerDialog(
                    onDismiss = { showDatePicker = false },
                    onDateSelected = { day, month, year ->
                        validUpto = "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
                        showDatePicker = false
                    }
                )
            }

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

@Suppress("DEPRECATION")
fun formatDateToDDMMYYYY(millis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = localDate.dayOfMonth.toString().padStart(2, '0')
    val month = localDate.monthNumber.toString().padStart(2, '0')
    val year = localDate.year
    return "$day/$month/$year"
}

@Composable
fun CustomDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (day: Int, month: Int, year: Int) -> Unit
) {
    var selectedDay by remember { mutableStateOf(1) }
    var selectedMonth by remember { mutableStateOf(1) }
    var selectedYear by remember { mutableStateOf(2026) }
    
    val currentYear = 2026
    val years = (currentYear..currentYear + 10).toList()
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Day Selector
                Text("Day", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (selectedDay > 1) selectedDay-- }) {
                        Text("-")
                    }
                    Text(
                        selectedDay.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = { if (selectedDay < 31) selectedDay++ }) {
                        Text("+")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Month Selector
                Text("Month", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (selectedMonth > 1) selectedMonth-- }) {
                        Text("-")
                    }
                    Text(
                        selectedMonth.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = { if (selectedMonth < 12) selectedMonth++ }) {
                        Text("+")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Year Selector
                Text("Year", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (selectedYear > currentYear) selectedYear-- }) {
                        Text("-")
                    }
                    Text(
                        selectedYear.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = { if (selectedYear < currentYear + 10) selectedYear++ }) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(selectedDay, selectedMonth, selectedYear)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
