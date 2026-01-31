package com.example.sitacardmaster

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.sitacardmaster.screens.DashboardScreen
import com.example.sitacardmaster.screens.IssueCardScreen
import com.example.sitacardmaster.screens.LoginScreen
import com.example.sitacardmaster.screens.MemberVerificationScreen

enum class Screen {
    Login, Dashboard, IssueCard, MemberVerification, Logs
}

@Composable
fun App(nfcManager: NfcManager = rememberNfcManager()) {
    var currentScreen by remember { mutableStateOf(Screen.Login) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                Screen.Login -> LoginScreen(onLoginSuccess = { currentScreen = Screen.Dashboard })
                Screen.Dashboard -> DashboardScreen(
                    nfcManager = nfcManager,
                    onIssueCardClick = { currentScreen = Screen.IssueCard },
                    onVerifyMemberClick = { currentScreen = Screen.MemberVerification },
                    onLogsClick = { /* Handle logs */ },
                    onLogout = { currentScreen = Screen.Login }
                )
                Screen.IssueCard -> IssueCardScreen(
                    onBack = { currentScreen = Screen.Dashboard },
                    nfcManager = nfcManager
                )
                Screen.MemberVerification -> MemberVerificationScreen(
                    onBack = { currentScreen = Screen.Dashboard }
                )
                Screen.Logs -> { /* Fallback for logs */ }
            }
        }
    }
}