package com.example.mediqr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mediqr.profile.BLOOD_GROUPS
import com.example.mediqr.profile.ContactSlot
import com.example.mediqr.profile.ProfileFormState
import com.example.mediqr.ui.components.AppTextField
import com.example.mediqr.ui.components.BackButton
import com.example.mediqr.ui.components.CenteredLoading
import com.example.mediqr.ui.components.FormMessage
import com.example.mediqr.ui.components.PrimaryButton
import com.example.mediqr.ui.theme.MedicalRed
import com.example.mediqr.ui.theme.MedicalRedContainer
import com.example.mediqr.ui.theme.OnMedicalRedContainer

/**
 * Edit Medical Profile - creates or updates the signed-in user's emergency
 * medical card in Supabase.
 *
 * While [ProfileFormState.isLoading] the form is hidden (spinner shown); if a
 * load failed ([ProfileFormState.isLoaded] stays false) the form stays hidden
 * behind an error + Retry so the user can never save blank data over an
 * existing profile they couldn't see. Saving shows [ProfileFormState.isSaving]
 * on the button with a success/error banner afterwards.
 */
@Composable
fun EditProfileScreen(
    form: ProfileFormState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
    onFullNameChange: (String) -> Unit,
    onBloodGroupChange: (String) -> Unit,
    onAllergiesChange: (String) -> Unit,
    onMedicalConditionsChange: (String) -> Unit,
    onCurrentMedicinesChange: (String) -> Unit,
    onAdditionalInfoChange: (String) -> Unit,
    onContactNameChange: (Int, String) -> Unit,
    onContactPhoneChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val inputsEnabled = !form.isSaving
    // Keyboard flow: Full name (Next) jumps to the first medical field, and
    // within a contact card Name (Next) moves to Phone, Phone (Next) to the
    // next contact's name.
    val allergiesFocus = remember { FocusRequester() }
    val contactNameFocus = remember(form.contacts.size) {
        List(form.contacts.size) { FocusRequester() }
    }
    val contactPhoneFocus = remember(form.contacts.size) {
        List(form.contacts.size) { FocusRequester() }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            BackButton(onClick = onBack, enabled = !form.isSaving)

            Text(
                text = "Edit Medical Profile",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(16.dp))

            when {
                form.isLoading -> CenteredLoading("Loading your profile...")

                !form.isLoaded -> {
                    // Load failed: never render the (unknown) form contents.
                    FormMessage(
                        message = form.bannerError ?: "Couldn't load your profile.",
                        isError = true,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = "Try again", onClick = onRetryLoad)
                }

                else -> {
                    form.bannerError?.let { message ->
                        FormMessage(message = message, isError = true)
                        Spacer(Modifier.height(12.dp))
                    }
                    form.successMessage?.let { message ->
                        FormMessage(message = message, isError = false)
                        Spacer(Modifier.height(12.dp))
                    }

                    // --- Personal information --------------------------------
                    SectionHeader(title = "Personal Information")

                    AppTextField(
                        value = form.fullName,
                        onValueChange = onFullNameChange,
                        label = "Full Name *",
                        error = form.fullNameError,
                        enabled = inputsEnabled,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        onImeAction = { allergiesFocus.requestFocus() },
                    )

                    Spacer(Modifier.height(16.dp))

                    BloodGroupPicker(
                        selected = form.bloodGroup,
                        onSelect = onBloodGroupChange,
                        error = form.bloodGroupError,
                        enabled = inputsEnabled,
                    )

                    Spacer(Modifier.height(22.dp))

                    // --- Medical information --------------------------------
                    SectionHeader(
                        title = "Medical Information",
                        subtitle = "Optional - list anything a doctor should know first.",
                    )

                    AppTextField(
                        value = form.allergies,
                        onValueChange = onAllergiesChange,
                        modifier = Modifier.focusRequester(allergiesFocus),
                        label = "Allergies",
                        error = null,
                        enabled = inputsEnabled,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 3,
                        keyboardType = KeyboardType.Text,
                    )

                    Spacer(Modifier.height(16.dp))

                    AppTextField(
                        value = form.medicalConditions,
                        onValueChange = onMedicalConditionsChange,
                        label = "Medical Conditions",
                        error = null,
                        enabled = inputsEnabled,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 3,
                        keyboardType = KeyboardType.Text,
                    )

                    Spacer(Modifier.height(16.dp))

                    AppTextField(
                        value = form.currentMedicines,
                        onValueChange = onCurrentMedicinesChange,
                        label = "Current Medicines",
                        error = null,
                        enabled = inputsEnabled,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 3,
                        keyboardType = KeyboardType.Text,
                    )

                    Spacer(Modifier.height(22.dp))

                    // --- Emergency contacts ---------------------------------
                    SectionHeader(
                        title = "Emergency Contacts",
                        subtitle = "Up to 3 people. If you fill a slot, name and phone are both required.",
                    )

                    form.contacts.forEachIndexed { index, slot ->
                        ContactCard(
                            index = index,
                            slot = slot,
                            onNameChange = { onContactNameChange(index, it) },
                            onPhoneChange = { onContactPhoneChange(index, it) },
                            enabled = inputsEnabled,
                            nameFocus = contactNameFocus[index],
                            phoneFocus = contactPhoneFocus[index],
                            onNextContact = if (index < form.contacts.lastIndex) {
                                { contactNameFocus[index + 1].requestFocus() }
                            } else {
                                null
                            },
                        )
                        if (index < form.contacts.lastIndex) {
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    // --- Additional information -----------------------------
                    SectionHeader(
                        title = "Additional Emergency Information",
                        subtitle = "Optional. Anything else responders should know.",
                    )

                    AppTextField(
                        value = form.additionalInfo,
                        onValueChange = onAdditionalInfoChange,
                        label = "Additional information",
                        error = null,
                        enabled = inputsEnabled,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 4,
                        keyboardType = KeyboardType.Text,
                    )

                    Spacer(Modifier.height(22.dp))

                    PrimaryButton(
                        text = "Save Profile",
                        onClick = {
                            focusManager.clearFocus()
                            onSave()
                        },
                        loading = form.isSaving,
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Red section title with an optional gray helper line underneath. */
@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MedicalRed,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
}

/**
 * Blood group selector as chip rows (3 rows of 3) - avoids flow-layout
 * experimental APIs while never overflowing the screen width.
 */
@Composable
private fun BloodGroupPicker(
    selected: String,
    onSelect: (String) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    Column {
        Text(
            text = "Blood Group *",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        BLOOD_GROUPS.chunked(3).forEach { rowGroups ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowGroups.forEach { group ->
                    FilterChip(
                        selected = selected == group,
                        onClick = { onSelect(group) },
                        label = { Text(group) },
                        enabled = enabled,
                        // Tint the selected state with the app's red accent
                        // instead of Material3's default purple container.
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalRedContainer,
                            selectedLabelColor = OnMedicalRedContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * One emergency contact slot: grouped card with name + phone fields.
 * [nameFocus]/[phoneFocus] power the keyboard flow inside the card, and
 * [onNextContact] moves from this card's phone to the next contact's name.
 */
@Composable
private fun ContactCard(
    index: Int,
    slot: ContactSlot,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    enabled: Boolean,
    nameFocus: FocusRequester,
    phoneFocus: FocusRequester,
    onNextContact: (() -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Contact ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MedicalRed,
            )

            Spacer(Modifier.height(12.dp))

            AppTextField(
                value = slot.name,
                onValueChange = onNameChange,
                modifier = Modifier.focusRequester(nameFocus),
                label = "Name",
                error = slot.nameError,
                enabled = enabled,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                onImeAction = { phoneFocus.requestFocus() },
            )

            Spacer(Modifier.height(12.dp))

            AppTextField(
                value = slot.phone,
                onValueChange = onPhoneChange,
                modifier = Modifier.focusRequester(phoneFocus),
                label = "Phone number",
                error = slot.phoneError,
                enabled = enabled,
                imeAction = if (onNextContact != null) ImeAction.Next else ImeAction.Done,
                onImeAction = {
                    if (onNextContact != null) {
                        onNextContact()
                    } else {
                        focusManager.clearFocus()
                    }
                },
                keyboardType = KeyboardType.Phone,
            )
        }
    }
}
