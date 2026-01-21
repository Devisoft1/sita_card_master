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


class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            goToDashboard()
            return
        }

        setContentView(R.layout.activity_login)

        val adminIdInput = findViewById<EditText>(R.id.adminId)
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

            if (adminId == "admin" && password == "admin") {
                val editor = sharedPref.edit()
                if (rememberMe.isChecked) {
                    editor.putBoolean("isLoggedIn", true)
                    editor.putString("adminId", adminId)
                    editor.putString("password", password)
                    editor.putBoolean("rememberMe", true)
                } else {
                    // If not remembered, clear everything or just the session?
                    // Usually, if user unchecks it, we should clear the saved credentials too.
                    editor.putBoolean("isLoggedIn", false)
                    editor.putBoolean("rememberMe", false)
                    editor.remove("adminId")
                    editor.remove("password")
                }
                editor.apply()

                logAction("Admin logged in")
                goToDashboard()
            } else {
                errorText.text = "Invalid ID or Password"
                errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun goToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
