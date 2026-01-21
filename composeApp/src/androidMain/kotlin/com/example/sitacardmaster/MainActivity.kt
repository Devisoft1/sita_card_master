package com.example.sitacardmaster

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview


class MainActivity : ComponentActivity() {
    private var nfcManager: AndroidNfcManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val manager = rememberNfcManager() as AndroidNfcManager
            nfcManager = manager
            App(manager)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcManager?.onNewIntent(intent)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}