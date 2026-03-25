package com.example.sitacardmaster

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.example.sitacardmaster.R

class WriteUrlActivity : AppCompatActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private var isScanning = false
    private var logoUrlInput = ""

    private lateinit var urlInput: TextInputEditText
    private lateinit var startScanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var scanProgress: ProgressBar
    private lateinit var statusMessage: TextView
    private lateinit var timerText: TextView
    private lateinit var tapCardHint: TextView
    
    private var secondsElapsed = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (isScanning) {
            logAction("Scan timeout reached (60s)")
        
            stopScanMode()
            statusMessage.text = "No card detected (or multiple cards present)"
            statusMessage.setTextColor(getColor(R.color.error_red))
        }
    }
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                secondsElapsed++
                val remaining = 60 - secondsElapsed
                timerText.text = "Time Elapsed : ${remaining}s"
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_url)

        nfcManager = AndroidNfcManager(this)

        urlInput = findViewById(R.id.urlInput)
        startScanButton = findViewById(R.id.startScanButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        scanProgress = findViewById(R.id.scanProgress)
        statusMessage = findViewById(R.id.statusMessage)
        timerText = findViewById(R.id.timerText)
        tapCardHint = findViewById(R.id.tapCardHint)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val titleText = findViewById<TextView>(R.id.appBarTitle)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        titleText.text = "Write Logo URL"
        logoutButton.visibility = View.GONE

        backButton.setOnClickListener {
            logAction("User clicked back button")
            finish()
        }

        startScanButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                urlInput.error = "Please enter a URL"
                logAction("Start scan failed: URL is empty")
                return@setOnClickListener
            }
            logoUrlInput = url
            logAction("User initiated scan for URL: $logoUrlInput")
            startScanMode()
        }

        stopScanButton.setOnClickListener {
            logAction("User stopped scanning")
            stopScanMode()
        }

        findViewById<View>(R.id.llPoweredBy).setOnClickListener {
            logAction("User clicked Powered by Devisoft")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://devisoft.co.in"))
            startActivity(intent)
        }
    }

    private fun startScanMode() {
        isScanning = true
        startScanButton.visibility = View.GONE
        stopScanButton.visibility = View.VISIBLE
        scanProgress.visibility = View.VISIBLE
        tapCardHint.visibility = View.VISIBLE
        statusMessage.text = "Ready to write"
        statusMessage.setTextColor(getColor(R.color.brand_blue))
        timerText.text = "Time Elapsed: 60s"
        timerText.visibility = View.VISIBLE
        secondsElapsed = 0
        urlInput.isEnabled = false
        nfcManager.startScanning()
        
        handler.postDelayed(timeoutRunnable, 60000)
        handler.postDelayed(timerRunnable, 1000)
        
        logAction("NFC scan mode started with 60s timeout")
    }

    private fun stopScanMode() {
        isScanning = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(timerRunnable)
        
        startScanButton.visibility = View.VISIBLE
        stopScanButton.visibility = View.GONE
        scanProgress.visibility = View.GONE
        tapCardHint.visibility = View.GONE
        timerText.visibility = View.GONE
        
        statusMessage.text = "Ready to write"
        statusMessage.setTextColor(getColor(R.color.gray_text))
        urlInput.isEnabled = true
        nfcManager.stopScanning()
        logAction("NFC scan mode stopped")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            nfcManager.onNewIntent(intent)
            
            if (nfcManager.isMultipleTagsDetected.value) {
                runOnUiThread {
                    stopScanMode()
                    statusMessage.text = "Multiple cards detected! Please hold one card only."
                    statusMessage.setTextColor(getColor(R.color.error_red))
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Multiple Cards Detected")
                        .setMessage("Multiple cards detected! Please hold one card only.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                return
            }

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                logAction("NFC Tag detected, starting write process")
                processWrite()
            }
        }
    }

    private fun processWrite() {
        runOnUiThread {
            statusMessage.text = "Writing Logo URL..."
        }
        nfcManager.writeLogoUrl(logoUrlInput) { success, message ->
            runOnUiThread {
                stopScanMode()
                statusMessage.text = message
                if (success) {
                    statusMessage.setTextColor(getColor(R.color.brand_blue))
                    logAction("Logo URL write success: $logoUrlInput")
                } else {
                    statusMessage.setTextColor(getColor(R.color.error_red))
                    logAction("Logo URL write failed: $message")
                }
            }
        }
    }

    private fun logAction(action: String) {
        platformLog("SITACardMaster", "WriteUrl: $action")
    }

    override fun onResume() {
        super.onResume()
        if (isScanning) nfcManager.startScanning()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.stopScanning()
    }
}
