package com.example.sitacardmaster.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
                CalendarDatePickerDialog(
                    onDismiss = { showDatePicker = false },
                    onDateSelected = { day, month, year ->
                        validUpto =
                            "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
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
fun CalendarDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (day: Int, month: Int, year: Int) -> Unit
) {
    var selectedYear by remember { mutableStateOf(2026) }
    var selectedMonth by remember { mutableStateOf(1) } // 1–12
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun daysInMonth(month: Int, year: Int): Int =
        when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0) 29 else 28
            else -> 30
        }

    val days = daysInMonth(selectedMonth, selectedYear)
    val weekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Month & Year selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = {
                        if (selectedMonth == 1) {
                            selectedMonth = 12
                            selectedYear--
                        } else selectedMonth--
                        selectedDay = null
                    }) { Text("‹") }

                    Text(
                        "${monthNames[selectedMonth - 1]} $selectedYear",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    IconButton(onClick = {
                        if (selectedMonth == 12) {
                            selectedMonth = 1
                            selectedYear++
                        } else selectedMonth++
                        selectedDay = null
                    }) { Text("›") }
                }
            }
        },
        text = {
            Column {

                // Weekday headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEach {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(300.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(days) { index ->
                        val day = index + 1
                        val isSelected = selectedDay == day

                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clickable { selectedDay = day }
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDay != null,
                onClick = {
                    selectedDay?.let {
                        onDateSelected(it, selectedMonth, selectedYear)
                    }
                }
            ) {
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
