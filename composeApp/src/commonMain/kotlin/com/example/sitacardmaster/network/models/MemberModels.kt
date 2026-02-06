package com.example.sitacardmaster.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class VerifyMemberRequest(
    val memberId: String,
    val companyName: String,
    val card_mfid: String,
    val cardValidity: String
)

@Serializable
data class MemberListResponse(
    val members: List<VerifyMemberResponse>
)

@Serializable
data class VerifyMemberResponse(
    @Serializable(with = StringOrIntSerializer::class)
    val memberId: String? = null,
    val companyName: String? = null,
    val card_mfid: String? = null,
    val cardValidity: String? = null,
    val message: String? = null, // For error case
    val currentTotal: Double = 0.0, // Restored for backward compatibility
    val globalTotal: Double = 0.0,
    val validity: String? = null, // Restored for backward compatibility
    val verified: Boolean? = null,
    val expired: Boolean? = null,
    val companyAddress: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val website: String? = null,
    @SerialName("communicatorWhatsapp")
    val whatsapp: String? = null,
    val status: Int? = null
)

object StringOrIntSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrInt", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
    override fun deserialize(decoder: Decoder): String? {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                element.contentOrNull
            } else {
                element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

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
