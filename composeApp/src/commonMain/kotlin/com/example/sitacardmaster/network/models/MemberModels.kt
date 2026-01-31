package com.example.sitacardmaster.network.models

import kotlinx.serialization.Serializable

@Serializable
data class VerifyMemberRequest(
    val memberId: String,
    val companyName: String
)

@Serializable
data class VerifyMemberResponse(
    val memberId: Long,
    val companyName: String,
    val validity: String,
    val currentTotal: Double
)

@Serializable
data class AddAmountRequest(
    val memberId: String,
    val amount: Double
)

@Serializable
data class AddAmountResponse(
    val message: String,
    val memberId: Long,
    val addedAmount: Double,
    val newTotal: Double
)
