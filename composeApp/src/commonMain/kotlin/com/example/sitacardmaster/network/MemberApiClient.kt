package com.example.sitacardmaster.network

import com.example.sitacardmaster.network.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class MemberApiClient {
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

    private val baseUrl = "https://apisita.shanti-pos.com/api"

    suspend fun getApprovedMembers(search: String? = null): Result<List<VerifyMemberResponse>> {
        return try {
            val response: MemberListResponse = client.get("$baseUrl/members") {
                if (search != null) {
                    parameter("search", search)
                }
            }.body()
            // The API returns all members, so we filter for status 1 (Approved) locally.
            Result.success(response.members.filter { it.status == 1 })
        } catch (e: Exception) {
            // Log the error for easier debugging
            println("API Error in getApprovedMembers: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyMember(
        memberId: String,
        companyName: String,
        password: String,
        cardMfid: String = "",
        cardValidity: String = "",
        cardType: String = "Membership" // Default to Membership if not provided
    ): Result<VerifyMemberResponse> {
        return try {
            val response: VerifyMemberResponse = client.post("$baseUrl/members/verify") {
                contentType(ContentType.Application.Json)
                setBody(
                    VerifyMemberRequest(
                        memberId = memberId,
                        companyName = companyName,
                        password = password,
                        card_mfid = cardMfid,
                        cardValidity = cardValidity,
                        cardType = cardType
                    )
                )
            }.body()

            if ((response.verified == false) || (response.expired == true)) {
                 Result.failure(Exception(response.message ?: "Card verification failed"))
            } else if (response.message != null && response.message.lowercase().contains("not found")) {
                 Result.failure(Exception(response.message))
            } else {
                 Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAmount(memberId: String, amount: Double): Result<AddAmountResponse> {
        // Keeping this as is/mock for now as user request didn't ask to change this logic specifically, 
        // but ensuring it remains compatible if any model changes affected it (none did).
        return Result.success(
            AddAmountResponse(
                message = "Amount added successfully",
                memberId = memberId.toLongOrNull() ?: 0L,
                addedAmount = amount,
                newTotal = amount
            )
        )
    }
}
