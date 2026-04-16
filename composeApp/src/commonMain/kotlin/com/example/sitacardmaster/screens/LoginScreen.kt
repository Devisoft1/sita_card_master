package com.example.sitacardmaster.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*
import sitacardmaster.composeapp.generated.resources.Res
import sitacardmaster.composeapp.generated.resources.logo
import com.example.sitacardmaster.isNetworkAvailable
import com.example.sitacardmaster.SettingsStorage
import com.example.sitacardmaster.logAction
import com.example.sitacardmaster.PoweredBySection
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val settings = remember { SettingsStorage() }
    var adminId by remember { mutableStateOf(settings.getString("adminId", "")) }
    var password by remember { mutableStateOf(settings.getString("password", "")) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(settings.getBoolean("rememberMe", false)) }
    var errorText by remember { mutableStateOf("") }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    val brandBlue = Color(0xFF2D2F91)
    val brandBlueDark = Color(0xFF1A1B4B)
    val brandOrange = Color(0xFFF57C00)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Shared Background Image (matches Android background1)
        Image(
            painter = painterResource(Res.drawable.background1),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
        )

        if (showNoInternetDialog) {
            AlertDialog(
                onDismissRequest = { showNoInternetDialog = false },
                title = { Text("No Internet") },
                text = { Text("No Internet Connection. Please connect to the internet to proceed.") },
                confirmButton = {
                    TextButton(onClick = { showNoInternetDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Removed Geometric Shapes as they are part of background1 image or it is replaced by background1


        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            
            // Header Section
            Spacer(modifier = Modifier.height(60.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome Back!",
                    color = brandBlue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sign in to continue",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Login Section (Replacing Card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = "SITA Logo",
                        modifier = Modifier
                            .width(140.dp)
                            .padding(bottom = 24.dp)
                    )

                    // Admin ID / Username
                    OutlinedTextField(
                        value = adminId,
                        onValueChange = { adminId = it },
                        label = { Text("Username") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_person),
                                contentDescription = null,
                                tint = brandOrange
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandOrange,
                            focusedLabelColor = brandOrange,
                            cursorColor = brandOrange,
                            unfocusedLabelColor = Color.Gray,
                            focusedLeadingIconColor = brandOrange,
                            unfocusedLeadingIconColor = Color.Gray
                        )
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_lock),
                                contentDescription = null,
                                tint = brandOrange
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Password",
                                    tint = Color.Gray
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandOrange,
                            focusedLabelColor = brandOrange,
                            cursorColor = brandOrange,
                            unfocusedLabelColor = Color.Gray,
                            focusedLeadingIconColor = brandOrange,
                            unfocusedLeadingIconColor = Color.Gray
                        )
                    )

                    // Forgot Password
                    Text(
                        text = "Forgot Password?",
                        color = brandBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 16.dp)
                            .clickable { /* Handle forgot password */ }
                    )

                    if (errorText.isNotEmpty()) {
                        Text(
                            text = errorText,
                            color = Color(0xFFE53935), // error_red
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Remember Me
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = brandOrange,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Keep me logged in",
                            color = Color(0xFF666666),
                            fontSize = 12.sp
                        )
                    }

                    // Login Button
                    val scope = rememberCoroutineScope()
                    val authClient = remember { com.example.sitacardmaster.network.AuthApiClient() }
                    var isLoading by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (!isNetworkAvailable()) {
                                showNoInternetDialog = true
                                return@Button
                            }
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
                                        settings.putString("authToken", response.token)
                                        settings.putString("role", response.role)
                                        settings.putString("loginTimestamp", com.example.sitacardmaster.getCurrentTimeMillis().toString())
                                        if (rememberMe) {
                                            settings.putString("adminId", adminId)
                                            settings.putString("password", password)
                                            settings.putBoolean("rememberMe", true)
                                        } else {
                                            settings.remove("password")
                                            settings.putBoolean("rememberMe", false)
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
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandBlue),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Powered By
                    PoweredBySection(
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


// GeometricBackground removed as it's replaced by the background1 image.
