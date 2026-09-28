package com.example.mediqr.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Blood group choices offered by the form. "Unknown" lets users whose group
 * isn't known still complete the profile without inventing a value.
 */
val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")

/**
 * Row of `public.profiles`, keyed by the Supabase auth uid so RLS guarantees
 * each user only ever reads/writes their own medical card.
 *
 * Fields intentionally declare NO default values: kotlinx-serialization always
 * encodes elements without a declared default (even with `encodeDefaults = false`),
 * so a field the user cleared is persisted as an explicit SQL NULL instead of
 * being silently omitted from the upsert. `created_at`/`updated_at` are not
 * declared at all - the client's default Json ignores unknown keys on read, and
 * on write the database keeps managing those timestamps.
 *
 * `card_id` is the exception: it has a default so save() (which never sets it)
 * omits it from the payload entirely - the database keeps generating/keeping
 * it, and a save can never rotate the id behind someone's printed QR code.
 */
@Serializable
data class MedicalProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String?,
    @SerialName("blood_group") val bloodGroup: String?,
    val allergies: String?,
    @SerialName("medical_conditions") val medicalConditions: String?,
    val medications: String?,
    @SerialName("additional_info") val additionalInfo: String?,
    @SerialName("card_id") val cardId: String? = null,
)

/**
 * Read model for `public.emergency_contacts`. Every field has a default so
 * decoding stays tolerant (a missing/odd column degrades to null instead of
 * failing the whole load).
 */
@Serializable
data class EmergencyContactRow(
    val id: Int? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val position: Int? = null,
)

/**
 * Write model for `public.emergency_contacts`. No defaults -> every field is
 * always encoded. Rows are upserted on the unique `(user_id, position)` index;
 * `id` is intentionally absent so new rows get the serial default and existing
 * rows keep their id.
 */
@Serializable
data class EmergencyContactWrite(
    @SerialName("user_id") val userId: String,
    @SerialName("contact_name") val contactName: String,
    @SerialName("phone_number") val phoneNumber: String,
    val position: Int,
)

/** One of the three emergency contact slots in the edit form. */
data class ContactSlot(
    val name: String = "",
    val phone: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
) {
    /** A slot counts as unused when the user typed nothing into it. */
    val isEmpty: Boolean get() = name.isBlank() && phone.isBlank()
}

/**
 * Full UI state of the edit-profile form.
 * Field errors come from local validation; bannerError/successMessage come from
 * the Supabase load/save call.
 */
data class ProfileFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** True once a load finished successfully (the profile row may still be absent). */
    val isLoaded: Boolean = false,
    val fullName: String = "",
    val fullNameError: String? = null,
    val bloodGroup: String = "",
    val bloodGroupError: String? = null,
    val allergies: String = "",
    val medicalConditions: String = "",
    val currentMedicines: String = "",
    val additionalInfo: String = "",
    /** Public card id of the loaded row (null until the row exists). */
    val cardId: String? = null,
    val contacts: List<ContactSlot> = List(3) { ContactSlot() },
    val bannerError: String? = null,
    val successMessage: String? = null,
)

private val PHONE_ALLOWED_CHARS = Regex("""^[+()\-\s\d.]+$""")
private val PHONE_DIGIT = Regex("""\d""")

/**
 * Phone validation for emergency contacts: only phone-ish characters are
 * allowed and the number must contain 7-15 digits (E.164 allows up to 15).
 */
internal fun isValidPhone(raw: String): Boolean {
    val trimmed = raw.trim()
    if (!PHONE_ALLOWED_CHARS.matches(trimmed)) return false
    val digits = PHONE_DIGIT.findAll(trimmed).count()
    return digits in 7..15
}
