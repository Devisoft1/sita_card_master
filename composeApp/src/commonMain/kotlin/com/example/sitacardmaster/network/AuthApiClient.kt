package com.example.sitacardmaster.network

import com.example.sitacardmaster.network.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

import com.example.sitacardmaster.platformLog

class AuthApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }

    private val baseUrl = "https://apisita.shanti-pos.com/api/auth"

    suspend fun login(username: String, password: String, source: String = "App"): Result<LoginResponse> {
        platformLog("AuthAPI", "Attempting login - Username: $username, Password: $password, Source: $source")
        return try {
            val response = client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password, source))
            }

            platformLog("AuthAPI", "Login Response: Status=${response.status}, Body=${response.bodyAsText()}")

            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
                Result.failure(Exception("Invalid ID or Password"))
            } else {
                Result.failure(Exception("Login failed: ${response.status}"))
            }
        } catch (e: Exception) {
            platformLog("AuthAPI", "Login Exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun register(
        token: String,
        username: String,
        password: String,
        role: String,
        allowedSource: String
    ): Result<RegisterResponse> {
        return try {
            val response = client.post("$baseUrl/register") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(RegisterRequest(username, password, role, allowedSource))
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Register failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdmins(token: String): Result<List<User>> {
        return try {
            val response = client.get("$baseUrl/admins") {
                header("Authorization", "Bearer $token")
            }
            if (response.status == HttpStatusCode.OK) {
                val adminListResponse: AdminListResponse = response.body()
                Result.success(adminListResponse.admins)
            } else {
                Result.failure(Exception("Get admins failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMe(token: String): Result<User> {
         return try {
            val response = client.get("$baseUrl/me") {
                header("Authorization", "Bearer $token")
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Get profile failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
