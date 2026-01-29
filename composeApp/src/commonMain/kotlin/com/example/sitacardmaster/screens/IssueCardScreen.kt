package com.example.sitacardmaster.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
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

    val brandBlue = Color(0xFF2D2F91)
    val surfaceGray = Color(0xFFF5F7FA)
    val white = Color.White
    val errorRed = Color(0xFFD32F2F)
    val grayText = Color(0xFF757575)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val datePickerState = rememberDatePickerState()

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
        topBar = {
            Surface(
                shadowElevation = 4.dp,
                color = white
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp) // Updated to match standard Material TopBar height (~@dimen/_35sdp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isScanning = false
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
                modifier = Modifier.fillMaxWidth(),
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

                    OutlinedTextField(
                        value = memberId,
                        onValueChange = { memberId = it },
                        label = { Text("Member ID") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_person),
                                contentDescription = null,
                                tint = brandBlue
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_business),
                                contentDescription = null,
                                tint = brandBlue
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Date Picker Field
                    OutlinedTextField(
                        value = validUpto,
                        onValueChange = { },
                        label = { Text("Valid Upto") },
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
                            disabledPlaceholderColor = grayText,
                            disabledLeadingIconColor = brandBlue,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_event),
                                contentDescription = null,
                                tint = brandBlue
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Select Date"
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        validUpto = formatDateToDDMMYYYY(millis)
                                    }
                                    showDatePicker = false
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                        DatePicker(
                            state = datePickerState,
                            title = null
                        )
                        }
                    }

                    OutlinedTextField(
                        value = totalBuy,
                        onValueChange = { totalBuy = it },
                        label = { Text("Total Buy") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_payments),
                                contentDescription = null,
                                tint = brandBlue
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
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
                            color = if (statusMessage.contains("Error") || statusMessage.contains("failed")) errorRed else grayText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(bottom = 12.dp),
                                color = brandBlue
                            )
                            Text(
                                text = "TAP CARD NOW...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = brandBlue,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { isScanning = false },
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
                                    if (memberId.isEmpty() || companyName.isEmpty()) {
                                        statusMessage = "Error: Please fill all fields"
                                        return@Button
                                    }
                                    isScanning = true
                                    statusMessage = "Scanning... Tap Card"
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

@Suppress("DEPRECATION")
fun formatDateToDDMMYYYY(millis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = localDate.dayOfMonth.toString().padStart(2, '0')
    val month = localDate.monthNumber.toString().padStart(2, '0')
    val year = localDate.year
    return "$day/$month/$year"
}
