package com.example.sitacardmaster.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.Res
import sitacardmaster.composeapp.generated.resources.logo

@Composable
fun DashboardScreen(onIssueCardClick: () -> Unit, onLogsClick: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(60.dp)
            )
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Admin Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 16.dp),
            onClick = onIssueCardClick
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "New Card Issue",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        OutlinedButton(
            onClick = onLogsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Action Logs")
        }
    }
}
