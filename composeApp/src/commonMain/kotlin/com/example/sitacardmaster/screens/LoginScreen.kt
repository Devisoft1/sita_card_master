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
    val brandBlueDark = Color(0xFF1A1B4B)
    val brandOrange = Color(0xFFF57C00)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(brandBlue, brandBlueDark),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        // Geometric Shapes
        GeometricBackground()

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
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Welcome Back!",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sign in to continue",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 24.dp),
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
                            cursorColor = brandOrange
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
                                    contentDescription = "Toggle Password"
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
                            cursorColor = brandOrange
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
                            colors = CheckboxDefaults.colors(checkedColor = brandOrange)
                        )
                        Text(
                            text = "Keep me logged in",
                            color = Color(0xFF666666), // gray_text
                            fontSize = 12.sp
                        )
                    }

                    // Login Button
                    val scope = rememberCoroutineScope()
                    val authClient = remember { com.example.sitacardmaster.network.AuthApiClient() }
                    var isLoading by remember { mutableStateOf(false) }

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
                                        settings.putString("authToken", response.token)
                                        settings.putString("role", response.role)
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
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Footer
            Spacer(modifier = Modifier.weight(1f))
            val uriHandler = LocalUriHandler.current
            Text(
                text = "Powered by DeviSoft",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .alpha(0.7f)
                    .padding(top = 24.dp, bottom = 24.dp)
                    .clickable { uriHandler.openUri("https://devisoft.co.in") }
            )
        }
    }
}

@Composable
fun GeometricBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Right Diamond
        Box(
            modifier = Modifier
                .offset(x = 250.dp, y = (-80).dp)
                .rotate(45f)
                .size(200.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )

        // Bottom Left Square
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = 500.dp)
                .rotate(30f)
                .size(300.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )

        // Middle Right Accent
        Box(
            modifier = Modifier
                .offset(x = 300.dp, y = 250.dp)
                .rotate(60f)
                .size(150.dp)
                .background(Color.White.copy(alpha = 0.04f))
        )

        // Top Left circle
        Box(
            modifier = Modifier
                .offset(x = 20.dp, y = 50.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
    }
}



