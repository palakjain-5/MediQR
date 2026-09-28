package com.example.mediqr.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediqr.core.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

private const val NETWORK_ERROR = "No internet connection. Please check your network and try again."
private const val GENERIC_ERROR = "Something went wrong. Please try again."
private const val SESSION_ERROR = "Your session has expired. Please sign out and sign in again."

/**
 * Owns the medical profile form: loads the signed-in user's row from Supabase,
 * validates input, and saves via upsert. Scoped to the edit-profile navigation
 * entry, so every screen open performs a fresh load.
 *
 * All queries carry the auth uid in their filter and RLS enforces ownership
 * server-side: a user can only ever read or write their own profile.
 */
class ProfileViewModel : ViewModel() {

    private val _form = MutableStateFlow(ProfileFormState(isLoading = true))
    val form: StateFlow<ProfileFormState> = _form.asStateFlow()

    init {
        load()
    }

    /**
     * Loads profile + contacts. A missing profile row is not an error: the
     * form opens empty so the user can create it. A failed request keeps
     * [ProfileFormState.isLoaded] false, which hides the form (preventing an
     * accidental save over unknown data) and shows a Retry action instead.
     */
    fun load() {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid == null) {
            _form.value = ProfileFormState(bannerError = SESSION_ERROR)
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true, bannerError = null, successMessage = null) }
            try {
                val profile = supabase.from("profiles")
                    .select { filter { eq("id", uid) } }
                    .decodeList<MedicalProfileRow>()
                    .firstOrNull()
                val contactRows = supabase.from("emergency_contacts")
                    .select { filter { eq("user_id", uid) } }
                    .decodeList<EmergencyContactRow>()

                // Place each contact into its saved slot (1..3); extra/unknown
                // positions are ignored rather than crashing the load.
                val slots = Array(3) { ContactSlot() }
                contactRows.forEach { row ->
                    val position = row.position
                    if (position != null && position in 1..slots.size) {
                        slots[position - 1] = ContactSlot(
                            name = row.contactName.orEmpty(),
                            phone = row.phoneNumber.orEmpty(),
                        )
                    }
                }

                _form.value = ProfileFormState(
                    isLoaded = true,
                    fullName = profile?.fullName.orEmpty(),
                    bloodGroup = profile?.bloodGroup.orEmpty(),
                    allergies = profile?.allergies.orEmpty(),
                    medicalConditions = profile?.medicalConditions.orEmpty(),
                    currentMedicines = profile?.medications.orEmpty(),
                    additionalInfo = profile?.additionalInfo.orEmpty(),
                    cardId = profile?.cardId,
                    contacts = slots.toList(),
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _form.update {
                    it.copy(isLoading = false, isLoaded = false, bannerError = profileErrorMessage(t))
                }
            }
        }
    }

    /** Validates, then upserts the profile and its contact slots. */
    fun save() {
        val current = _form.value
        validate(current)?.let { invalid ->
            _form.value = invalid
            return
        }
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid == null) {
            _form.update { it.copy(bannerError = SESSION_ERROR) }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(isSaving = true, bannerError = null, successMessage = null) }
            try {
                // 1) Profile: upsert keyed on id (= auth uid). Blank optional
                //    fields go down as explicit nulls so clearing persists.
                supabase.from("profiles").upsert(
                    MedicalProfileRow(
                        id = uid,
                        fullName = current.fullName.trim(),
                        bloodGroup = current.bloodGroup.trim(),
                        allergies = current.allergies.trim().ifBlank { null },
                        medicalConditions = current.medicalConditions.trim().ifBlank { null },
                        medications = current.currentMedicines.trim().ifBlank { null },
                        additionalInfo = current.additionalInfo.trim().ifBlank { null },
                    ),
                ) { onConflict = "id" }

                // 2) Contacts: upsert filled slots on (user_id, position)...
                val filled = current.contacts.mapIndexedNotNull { index, slot ->
                    if (slot.isEmpty) null else EmergencyContactWrite(
                        userId = uid,
                        contactName = slot.name.trim(),
                        phoneNumber = slot.phone.trim(),
                        position = index + 1,
                    )
                }
                if (filled.isNotEmpty()) {
                    supabase.from("emergency_contacts").upsert(filled) {
                        onConflict = "user_id,position"
                    }
                }

                // ...then delete rows for slots the user cleared.
                val cleared = current.contacts.mapIndexedNotNull { index, slot ->
                    if (slot.isEmpty) index + 1 else null
                }
                if (cleared.isNotEmpty()) {
                    supabase.from("emergency_contacts").delete {
                        filter {
                            eq("user_id", uid)
                            isIn("position", cleared)
                        }
                    }
                }

                _form.update { it.copy(isSaving = false, successMessage = "Profile saved.") }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _form.update { it.copy(isSaving = false, bannerError = profileErrorMessage(t)) }
            }
        }
    }

    // --- field edits: update the value and clear its stale messages ---------

    fun onFullNameChange(value: String) = _form.update {
        it.copy(fullName = value, fullNameError = null, bannerError = null, successMessage = null)
    }

    fun onBloodGroupChange(value: String) = _form.update {
        it.copy(bloodGroup = value, bloodGroupError = null, bannerError = null, successMessage = null)
    }

    fun onAllergiesChange(value: String) = _form.update {
        it.copy(allergies = value, bannerError = null, successMessage = null)
    }

    fun onMedicalConditionsChange(value: String) = _form.update {
        it.copy(medicalConditions = value, bannerError = null, successMessage = null)
    }

    fun onCurrentMedicinesChange(value: String) = _form.update {
        it.copy(currentMedicines = value, bannerError = null, successMessage = null)
    }

    fun onAdditionalInfoChange(value: String) = _form.update {
        it.copy(additionalInfo = value, bannerError = null, successMessage = null)
    }

    fun onContactNameChange(index: Int, value: String) = _form.update { state ->
        state.copy(
            contacts = state.contacts.updateSlot(index) {
                it.copy(name = value, nameError = null).withoutStaleErrors()
            },
            bannerError = null,
            successMessage = null,
        )
    }

    fun onContactPhoneChange(index: Int, value: String) = _form.update { state ->
        state.copy(
            contacts = state.contacts.updateSlot(index) {
                it.copy(phone = value, phoneError = null).withoutStaleErrors()
            },
            bannerError = null,
            successMessage = null,
        )
    }

    /**
     * An unused slot can never be invalid - once the user empties both fields,
     * drop the other field's leftover error too (e.g. a name-only slot failed
     * validation, then the user deleted the name).
     */
    private fun ContactSlot.withoutStaleErrors(): ContactSlot =
        if (isEmpty) copy(nameError = null, phoneError = null) else this

    /**
     * Runs all validation rules. Returns the state with per-field errors set,
     * or null when everything is valid.
     */
    private fun validate(s: ProfileFormState): ProfileFormState? {
        val fullNameError = if (s.fullName.isBlank()) "Please enter your full name." else null
        val bloodGroupError = if (s.bloodGroup.isBlank()) {
            "Please select your blood group (or Unknown)."
        } else {
            null
        }

        val contacts = s.contacts.map { slot ->
            val used = !slot.isEmpty
            val nameError = if (used && slot.name.isBlank()) "Please enter a contact name." else null
            val phoneError = when {
                !used -> null
                slot.phone.isBlank() -> "Please enter a phone number."
                !isValidPhone(slot.phone) -> "Enter a valid phone number (7-15 digits)."
                else -> null
            }
            slot.copy(nameError = nameError, phoneError = phoneError)
        }

        val hasErrors = fullNameError != null || bloodGroupError != null ||
            contacts.any { it.nameError != null || it.phoneError != null }

        return if (!hasErrors) {
            null
        } else {
            s.copy(
                fullNameError = fullNameError,
                bloodGroupError = bloodGroupError,
                contacts = contacts,
                bannerError = null,
                successMessage = null,
            )
        }
    }

    private fun List<ContactSlot>.updateSlot(
        index: Int,
        transform: (ContactSlot) -> ContactSlot,
    ): List<ContactSlot> = mapIndexed { i, slot -> if (i == index) transform(slot) else slot }
}

/** Maps any throwable from PostgREST to a user-friendly message. */
internal fun profileErrorMessage(t: Throwable): String = when (t) {
    is PostgrestRestException -> postgrestErrorMessage(t)

    is RestException ->
        t.description.takeIf { !it.isNullOrBlank() }
            ?: t.message?.takeIf { it.isNotBlank() }
            ?: GENERIC_ERROR

    is HttpRequestException, is IOException -> NETWORK_ERROR

    is SerializationException -> "Couldn't read your saved profile. Please try again."

    else -> GENERIC_ERROR
}

private fun postgrestErrorMessage(e: PostgrestRestException): String {
    val context = buildString {
        append(e.description ?: "")
        append(' ')
        append(e.message ?: "")
    }.lowercase()

    return when {
        // RLS denial / foreign row.
        e.code == "42501" || context.contains("row-level security") ->
            "You don't have permission to change this data."

        // Schema out of sync with the app (migration not run).
        e.code == "42703" || e.code == "PGRST204" ->
            "The app's database is out of date, so this can't be saved yet. " +
                "Please update the app or contact support."

        // NOT NULL violation - defensive; validation should prevent this.
        e.code == "23502" || context.contains("null value") ->
            "Please fill in the required fields."

        e.statusCode == 401 || e.statusCode == 403 ->
            "You don't have permission to do that. Please sign in again."

        else -> GENERIC_ERROR
    }
}
