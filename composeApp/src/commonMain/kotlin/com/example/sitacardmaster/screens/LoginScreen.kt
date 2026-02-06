package com.example.sitacardmaster.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*
import sitacardmaster.composeapp.generated.resources.Res
import sitacardmaster.composeapp.generated.resources.logo
import com.example.sitacardmaster.SettingsStorage
import com.example.sitacardmaster.logAction
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {

    val settings = remember { SettingsStorage() }
    
    var adminId by remember { mutableStateOf(settings.getString("adminId", "")) }
    var password by remember { mutableStateOf(settings.getString("password", "")) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(settings.getBoolean("rememberMe", false)) }
    var errorText by remember { mutableStateOf("") }

    val brandBlue = Color(0xFF2D2F91)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {

            // Logo
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    // Admin ID
                    OutlinedTextField(
                        value = adminId,
                        onValueChange = { adminId = it },
                        label = { Text("Admin ID") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_person),
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_lock),
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible }
                            ) {
                                Icon(
                                    imageVector =
                                        if (passwordVisible)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        visualTransformation =
                            if (passwordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorText,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember me
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it }
                        )
                        Text(
                            text = "Keep me logged in",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val scope = rememberCoroutineScope()
                    val authClient = remember { com.example.sitacardmaster.network.AuthApiClient() }
                    var isLoading by remember { mutableStateOf(false) }

                    // Login Button
                    Button(
                        onClick = {
                            if (adminId.isBlank() || password.isBlank()) {
                                errorText = "Please enter ID and Password"
                                return@Button
                            }
                            
                            isLoading = true
                            errorText = ""
                            
                            scope.launch {
                                val result = authClient.login(adminId, password, "App")
                                
                                result.fold(
                                    onSuccess = { response ->
                                        logAction("Admin logged in: ${response.username}")
                                        
                                        // Save Session
                                        settings.putString("authToken", response.token)
                                        settings.putString("role", response.role)
                                        
                                        if (rememberMe) {
                                            settings.putString("adminId", adminId)
                                            settings.putString("password", password)
                                            settings.putBoolean("rememberMe", true)
                                        } else {
                                            // Keep adminId for convenience? Or clear? 
                                            // Logic in Android was clear. Here let's clear if unchecked.
                                            // Actually typically "Remember Me" means remember creds for next time.
                                            settings.remove("password")
                                            settings.putBoolean("rememberMe", false)
                                            // We usually keep the ID or maybe clear it too.
                                            // Let's stick to previous logic:
                                            settings.remove("adminId")
                                        }
                                        
                                        isLoading = false
                                        onLoginSuccess()
                                    },
                                    onFailure = { error ->
                                        isLoading = false
                                        errorText = error.message ?: "Invalid ID or Password"
                                    }
                                )
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandBlue
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "LOGIN",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}



