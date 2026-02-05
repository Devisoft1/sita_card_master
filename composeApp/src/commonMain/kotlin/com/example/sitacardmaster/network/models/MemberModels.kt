package com.example.sitacardmaster.network.models

import kotlinx.serialization.Serializable

@Serializable
data class VerifyMemberRequest(
    val memberId: String,
    val companyName: String,
    val card_mfid: String,
    val cardValidity: String
)

@Serializable
data class VerifyMemberResponse(
    val memberId: String? = null,
    val companyName: String? = null,
    val card_mfid: String? = null,
    val cardValidity: String? = null,
    val message: String? = null, // For error case
    val currentTotal: Double = 0.0, // Restored for backward compatibility
    val globalTotal: Double = 0.0,
    val validity: String? = null, // Restored for backward compatibility
    val verified: Boolean? = null,
    val expired: Boolean? = null
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
