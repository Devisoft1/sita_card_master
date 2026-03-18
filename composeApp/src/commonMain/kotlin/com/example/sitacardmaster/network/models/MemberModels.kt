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
    val password: String,
    val card_mfid: String,
    val cardValidity: String,
    val cardType: String // "Membership", "Add-on", "Event"
)

@Serializable
data class MemberListResponse(
    val members: List<VerifyMemberResponse>
)

@Serializable
data class CardDetails(
    val card_mfid: String? = null,
    val cardTotal: Double = 0.0,
    val amount: Double = 0.0,
    val status: String? = null
)

@Serializable
data class VerifyMemberResponse(
    @Serializable(with = StringOrIntSerializer::class)
    val memberId: String? = null,
    val companyName: String? = null,
    val card_mfid: String? = null,
    val cardValidity: String? = null,
    val message: String? = null, // For error case
    @SerialName("amount")
    val currentTotal: Double = 0.0,
    @SerialName("total")
    val globalTotal: Double = 0.0,
    val cardTotal: Double = 0.0,
    val password: String? = null,
    val validity: String? = null, // Restored for backward compatibility
    val verified: Boolean? = null,
    val expired: Boolean? = null,
    val companyAddress: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val website: String? = null,
    val whatsapp: String? = null,
    val status: Int? = null,
    @Serializable(with = CardListSerializer::class)
    val cards: List<CardDetails>? = null
)

object CardListSerializer : KSerializer<List<CardDetails>?> {
    private val delegate = kotlinx.serialization.builtins.ListSerializer(CardDetails.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<CardDetails>?) {
        if (value == null) encoder.encodeNull() else delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<CardDetails>? {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        
        val element = decoder.decodeJsonElement()
        if (element !is kotlinx.serialization.json.JsonArray) return null
        
        return element.map {
            if (it is kotlinx.serialization.json.JsonPrimitive) {
                // Handle list of strings (card IDs)
                CardDetails(card_mfid = it.contentOrNull)
            } else {
                // Handle list of objects (CardDetails)
                decoder.json.decodeFromJsonElement(CardDetails.serializer(), it)
            }
        }
    }
}

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
