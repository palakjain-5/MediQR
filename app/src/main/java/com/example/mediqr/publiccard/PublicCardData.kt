package com.example.mediqr.publiccard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Canonical UUID shape of a public card id (what `/card/{id}` URLs carry). */
private val CARD_ID_REGEX = Regex(
    """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""",
)

/**
 * True when [raw] is shaped like a card id. Malformed links are rejected
 * locally before any network call, so junk URLs get an instant "not found"
 * instead of a database round-trip and a 400.
 */
internal fun isValidCardId(raw: String): Boolean = CARD_ID_REGEX.matches(raw.trim())

/**
 * Emergency contact as shown on the public card. Read-only projection of what
 * the `get_public_card` SQL function returns; server-side whitelisting means
 * other columns can never appear here in the first place.
 */
@Serializable
data class PublicEmergencyContact(
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val position: Int? = null,
)

/**
 * The public emergency medical card: ONLY the fields the card owner chose to
 * share (the same whitelist the SQL function enforces).
 *
 * Deliberately declares no email, no auth uid, no timestamps: kotlinx
 * serialization drops anything this model doesn't declare, so even if a server
 * ever returned extra keys they could not reach the screen.
 */
@Serializable
data class PublicMedicalCard(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    @SerialName("medical_conditions") val medicalConditions: String? = null,
    val medications: String? = null,
    @SerialName("additional_info") val additionalInfo: String? = null,
    @SerialName("emergency_contacts")
    val emergencyContacts: List<PublicEmergencyContact> = emptyList(),
)
