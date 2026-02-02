package com.example.sitacardmaster.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sitacardmaster.network.models.AddAmountResponse
import com.example.sitacardmaster.network.MemberApiClient
import com.example.sitacardmaster.network.models.VerifyMemberResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberVerificationScreen(
    onBack: () -> Unit
) {
    val client = remember { MemberApiClient() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var memberId by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var memberData by remember { mutableStateOf<VerifyMemberResponse?>(null) }
    
    // Add Amount State
    var amountToAdd by remember { mutableStateOf("") }
    var isAddingAmount by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Verify Member") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Verification Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Member Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = memberId,
                        onValueChange = { memberId = it },
                        label = { Text("Member ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (memberId.isNotBlank() && companyName.isNotBlank()) {
                                isLoading = true
                                resultMessage = ""
                                memberData = null
                                scope.launch {
                                    val result = client.verifyMember(memberId, companyName)
                                    result.fold(
                                        onSuccess = {
                                            memberData = it
                                            resultMessage = "Member verified successfully"
                                        },
                                        onFailure = {
                                            resultMessage = "Error: ${it.message ?: "Unknown error"}"
                                        }
                                    )
                                    isLoading = false
                                }
                            } else {
                                resultMessage = "Please fill all fields"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Verify")
                        }
                    }
                    
                    if (resultMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resultMessage,
                            color = if (memberData != null) Color(0xFF4CAF50) else Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Result Section
            memberData?.let { data ->
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                           Column {
                               Text("Member ID", style = MaterialTheme.typography.labelSmall)
                               Text(data.memberId.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                           }
                           Column(horizontalAlignment = Alignment.End) {
                               Text("Membership Validity", style = MaterialTheme.typography.labelSmall)
                               Text(data.validity?.take(10) ?: "N/A", style = MaterialTheme.typography.bodyLarge)
                               Spacer(modifier = Modifier.height(4.dp))
                               Text("Card Validity", style = MaterialTheme.typography.labelSmall)
                               Text(data.cardValidity?.take(10) ?: "N/A", style = MaterialTheme.typography.bodyLarge)
                           }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Company", style = MaterialTheme.typography.labelSmall)
                        Text(data.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Column {
                               Text("Current Balance:", style = MaterialTheme.typography.labelMedium)
                               Text(
                                   "₹${data.currentTotal}",
                                   style = MaterialTheme.typography.headlineSmall,
                                   color = Color(0xFF2D2F91),
                                   fontWeight = FontWeight.Bold
                               )
                           }
                           Spacer(modifier = Modifier.width(16.dp))
                           Column {
                               Text("Global Balance:", style = MaterialTheme.typography.labelMedium)
                               Text(
                                   "₹${data.globalTotal}",
                                   style = MaterialTheme.typography.headlineSmall,
                                   color = Color(0xFF006400), // Dark Green
                                   fontWeight = FontWeight.Bold
                               )
                           }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Add Amount Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                   Column(modifier = Modifier.padding(16.dp)) {
                       Text(
                           "Add Balance",
                           style = MaterialTheme.typography.titleMedium,
                           fontWeight = FontWeight.Bold
                       )
                       Spacer(modifier = Modifier.height(16.dp))
                       
                       OutlinedTextField(
                           value = amountToAdd,
                           onValueChange = { 
                               if (it.all { char -> char.isDigit() || char == '.' }) {
                                   amountToAdd = it 
                               }
                           },
                           label = { Text("Amount (₹)") },
                           modifier = Modifier.fillMaxWidth(),
                           singleLine = true,
                           keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                       )
                       
                       Spacer(modifier = Modifier.height(16.dp))
                       
                       Button(
                           onClick = {
                               val amount = amountToAdd.toDoubleOrNull()
                               if (amount != null && amount > 0) {
                                   isAddingAmount = true
                                   scope.launch {
                                      val result = client.addAmount(data.memberId.toString(), amount)
                                      result.fold(
                                          onSuccess = { res ->
                                              // Update local display
                                              memberData = memberData?.copy(currentTotal = res.newTotal)
                                              amountToAdd = ""
                                              resultMessage = "Added ₹${res.addedAmount} successfully. New Total: ₹${res.newTotal}"
                                          },
                                          onFailure = {
                                              resultMessage = "Failed to add amount: ${it.message}"
                                          }
                                      )
                                      isAddingAmount = false
                                   }
                               }
                           },
                           modifier = Modifier.fillMaxWidth(),
                           colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                           enabled = !isAddingAmount && amountToAdd.isNotEmpty()
                       ) {
                           if (isAddingAmount) {
                               CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                           } else {
                               Text("Add Amount")
                           }
                       }
                   }
                }
            }
        }
    }
}
