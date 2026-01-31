package com.example.sitacardmaster.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import com.example.sitacardmaster.network.models.AddAmountRequest
import com.example.sitacardmaster.network.models.AddAmountResponse
import com.example.sitacardmaster.network.models.VerifyMemberRequest
import com.example.sitacardmaster.network.models.VerifyMemberResponse

class MemberApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
            /*
            // Optional: Filter specific headers if needed, otherwise it logs everything
            sanitizeHeader { header -> header == HttpHeaders.Authorization } 
            */
        }
    }

    private val baseUrl = "https://apisita.shanti-pos.com/api"

    suspend fun verifyMember(memberId: String, companyName: String): Result<VerifyMemberResponse> {
        return try {
            val response: HttpResponse = client.post("$baseUrl/members/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyMemberRequest(memberId, companyName))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAmount(memberId: String, amount: Double): Result<AddAmountResponse> {
        return try {
            val response: HttpResponse = client.post("$baseUrl/members/add-amount") {
                contentType(ContentType.Application.Json)
                setBody(AddAmountRequest(memberId, amount))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
