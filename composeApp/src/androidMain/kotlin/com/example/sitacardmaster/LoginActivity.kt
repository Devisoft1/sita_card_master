package com.example.sitacardmaster

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sitacardmaster.R


import kotlinx.coroutines.launch
import com.example.sitacardmaster.network.AuthApiClient

class LoginActivity : AppCompatActivity() {

    private fun logAction(action: String) {
        android.util.Log.i("SITACardMaster", "Login: $action")
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    private val authApiClient = com.example.sitacardmaster.network.AuthApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            goToDashboard()
            return
        }

        setContentView(R.layout.activity_login)

        val adminIdInput = findViewById<EditText>(R.id.Username)
        val passwordInput = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val errorText = findViewById<TextView>(R.id.errorText)
        val rememberMe = findViewById<CheckBox>(R.id.rememberMe)

        // Pre-fill saved credentials
        val savedAdminId = sharedPref.getString("adminId", "")
        val savedPassword = sharedPref.getString("password", "")
        val wasRemembered = sharedPref.getBoolean("rememberMe", false)
        
        adminIdInput.setText(savedAdminId)
        passwordInput.setText(savedPassword)
        rememberMe.isChecked = wasRemembered

        loginButton.setOnClickListener {
            val adminId = adminIdInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (adminId.isBlank() || password.isBlank()) {
                 errorText.text = "Please enter ID and Password"
                 errorText.visibility = View.VISIBLE
                 return@setOnClickListener
            }

            errorText.visibility = View.GONE
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."

            scope.launch {
                // Using "App" as source
                val loginResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                      authApiClient.login(adminId, password, "App")
                }

               loginResult.fold(
                    onSuccess = { response ->
                        val editor = sharedPref.edit()
                        if (rememberMe.isChecked) {
                            editor.putBoolean("isLoggedIn", true)
                            editor.putString("adminId", adminId)
                            editor.putString("password", password)
                            editor.putBoolean("rememberMe", true)
                        } else {
                            editor.putBoolean("isLoggedIn", true) // Still logged in for session
                            editor.putBoolean("rememberMe", false)
                            // don't save creds if not remembered, but session needs to be active
                        }
                        // Save token
                        editor.putString("authToken", response.token)
                        editor.putString("role", response.role)
                        editor.apply()

                        logAction("Admin logged in: ${response.username}")
                        goToDashboard()
                    },
                    onFailure = { error ->
                        loginButton.isEnabled = true
                        loginButton.text = "LOGIN"
                        errorText.text = error.message ?: "Login Failed"
                        errorText.visibility = View.VISIBLE
                    }
                )
            }
        }
    }

    private fun goToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
