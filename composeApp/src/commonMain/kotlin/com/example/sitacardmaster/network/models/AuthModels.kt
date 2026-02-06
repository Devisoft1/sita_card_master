package com.example.sitacardmaster.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val source: String // "Web" or "App"
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("_id") val id: String,
    val username: String,
    val role: String,
    val allowedSource: String? = null,
    val logo: String? = null
) {
    fun toUser(): User = User(id, username, role, allowedSource, logo)
}

@Serializable
data class User(
    @SerialName("_id") val id: String,
    val username: String,
    val role: String,
    val allowedSource: String? = null,
    val logo: String? = null
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val role: String,
    val allowedSource: String // "Web", "App", "Both"
)

@Serializable
data class RegisterResponse(
    val message: String,
    val user: User
)

@Serializable
data class AdminListResponse(
    val admins: List<User>
)
