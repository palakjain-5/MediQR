package com.example.mediqr.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediqr.publiccard.PublicCardUiState
import com.example.mediqr.publiccard.PublicEmergencyContact
import com.example.mediqr.publiccard.PublicMedicalCard
import com.example.mediqr.publiccard.normalizePhoneNumber
import com.example.mediqr.ui.components.BackButton
import com.example.mediqr.ui.components.CenteredLoading
import com.example.mediqr.ui.components.FormMessage
import com.example.mediqr.ui.components.MediQRLogo
import com.example.mediqr.ui.components.PrimaryButton
import com.example.mediqr.ui.theme.MedicalRed

/**
 * Public Medical Card - what a QR-code scanner sees, with NO login.
 *
 * Reached either by scanning the QR (the public `/card/{cardId}` URL opens the
 * app via the manifest intent filter) or from the dashboard's "Preview Card"
 * button. Data comes from the whitelisted `get_public_card` function while
 * signed out, so the screen only ever renders the permitted emergency fields -
 * never email, auth ids or other private account data.
 *
 * Layout is deliberately plain and ordered by emergency priority: name and
 * blood group first and biggest, then allergies/conditions/medicines, then
 * the emergency contact block (red-tinted cards, Call button opens a
 * confirmation dialog before the system dialer), then extra notes. No
 * decorative elements.
 */
@Composable
fun PublicMedicalCardScreen(
    state: PublicCardUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediQRLogo(size = 26.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "MediQR",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedicalRed,
                    )
                    Spacer(Modifier.weight(1f))
                    BackButton(onClick = onBack)
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "EMERGENCY MEDICAL CARD",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MedicalRed,
                )

                Spacer(Modifier.height(12.dp))

                when (state) {
                    PublicCardUiState.Loading ->
                        CenteredLoading("Loading medical card...")

                    is PublicCardUiState.NotFound -> {
                        FormMessage(message = state.message, isError = true)
                    }

                    is PublicCardUiState.Error -> {
                        FormMessage(message = state.message, isError = true)
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(text = "Try again", onClick = onRetry)
                    }

                    is PublicCardUiState.Found -> PublicCardContent(card = state.card)
                }
            }
        }
    }
}

/** The rendered card: name + blood group up top, then the medical sections. */
@Composable
private fun PublicCardContent(card: PublicMedicalCard) {
    val context = LocalContext.current

    // --- Name (prominent) -------------------------------------------------
    Text(
        text = card.fullName?.trim().orEmpty().ifBlank { "Name not provided" },
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        color = if (card.fullName.isNullOrBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )

    Spacer(Modifier.height(12.dp))

    // --- Blood group (prominent) ------------------------------------------
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MedicalRed.copy(alpha = 0.08f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "BLOOD GROUP",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MedicalRed,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.bloodGroup?.trim().orEmpty().ifBlank { "Unknown" },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MedicalRed,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    // --- Critical medical fields (allergies highlighted) -------------------
    MedicalSection(title = "ALLERGIES", value = card.allergies, highlight = true)
    Spacer(Modifier.height(12.dp))
    MedicalSection(title = "MEDICAL CONDITIONS", value = card.medicalConditions)
    Spacer(Modifier.height(12.dp))
    MedicalSection(title = "CURRENT MEDICINES", value = card.medications)

    Spacer(Modifier.height(16.dp))

    // --- Emergency contacts (tap-to-call with confirmation) ----------------
    // pendingCall holds the contact whose confirmation dialog is open;
    // callErrorMessage surfaces failures such as a missing phone app.
    var pendingCall by remember { mutableStateOf<PublicEmergencyContact?>(null) }
    var callErrorMessage by remember { mutableStateOf<String?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Phone,
            contentDescription = null,
            tint = MedicalRed,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "EMERGENCY CONTACTS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MedicalRed,
        )
    }
    Spacer(Modifier.height(8.dp))

    callErrorMessage?.let { message ->
        FormMessage(message = message, isError = true)
        Spacer(Modifier.height(10.dp))
    }

    val contacts = card.emergencyContacts
    if (contacts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = "No emergency contacts listed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(20.dp),
            )
        }
    } else {
        contacts.forEach { contact ->
            ContactRow(contact = contact, onCallClick = { pendingCall = contact })
            Spacer(Modifier.height(10.dp))
        }
    }

    pendingCall?.let { contact ->
        ConfirmCallDialog(
            contact = contact,
            onDismiss = { pendingCall = null },
            onConfirm = {
                pendingCall = null
                callErrorMessage = placeCall(context, contact)
            },
        )
    }

    Spacer(Modifier.height(4.dp))

    MedicalSection(
        title = "ADDITIONAL EMERGENCY INFORMATION",
        value = card.additionalInfo,
    )

    Spacer(Modifier.height(18.dp))

    Text(
        text = "Information provided by the card holder for emergencies. " +
            "If in doubt, call emergency services.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One emergency contact: name, phone number and a Call button that only
 * appears for a dialable number. The red-tinted card matches the blood-group
 * block so responders can spot contacts at a glance. Missing or invalid
 * numbers degrade to an explanation instead of a dead control:
 *  - no number  -> "No phone number provided." (no Call button)
 *  - bad number -> shown with an "Invalid phone number - can't call." note
 *  - blank name -> falls back to "Emergency contact" (never "none")
 */
@Composable
private fun ContactRow(contact: PublicEmergencyContact, onCallClick: () -> Unit) {
    val name = contact.contactName?.trim().orEmpty().ifBlank { "Emergency contact" }
    val rawPhone = contact.phoneNumber?.trim().orEmpty()
    val dialableNumber = normalizePhoneNumber(rawPhone)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MedicalRed.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                when {
                    rawPhone.isEmpty() -> Text(
                        text = "No phone number provided.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    dialableNumber == null -> {
                        Text(
                            text = rawPhone,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Invalid phone number - can't call.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MedicalRed,
                        )
                    }

                    else -> Text(
                        text = rawPhone,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (dialableNumber != null) {
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = onCallClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedicalRed,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(text = "Call", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Confirmation step before anything leaves the app: shows exactly who and
 * what number will be dialed. Only an explicit "Call" tap fires ACTION_DIAL,
 * and the system dialer then asks the user to press the call key - so a call
 * is never placed automatically.
 */
@Composable
private fun ConfirmCallDialog(
    contact: PublicEmergencyContact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = contact.contactName?.trim().orEmpty().ifBlank { "this contact" }
    val number = normalizePhoneNumber(contact.phoneNumber).orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Phone,
                contentDescription = null,
                tint = MedicalRed,
            )
        },
        title = { Text(text = "Call $name?") },
        text = {
            Text(
                text = "The phone app will open with $number so you can " +
                    "confirm the call. Nothing dials automatically.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Call",
                    color = MedicalRed,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}

/**
 * Opens the system dialer (ACTION_DIAL) with the number pre-filled - the
 * user still presses the call key there, so no call is ever placed by the
 * app itself. Returns an error message when the device can't open a phone
 * app, or null when the dialer opened successfully.
 */
private fun placeCall(context: Context, contact: PublicEmergencyContact): String? {
    val number = normalizePhoneNumber(contact.phoneNumber)
        ?: return INVALID_NUMBER_MESSAGE
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        NO_PHONE_APP_MESSAGE
    } catch (e: SecurityException) {
        CALL_BLOCKED_MESSAGE
    }
}

private const val INVALID_NUMBER_MESSAGE =
    "This contact's phone number can't be dialed."
private const val NO_PHONE_APP_MESSAGE =
    "No phone app is available on this device, so the call can't be started."
private const val CALL_BLOCKED_MESSAGE =
    "This device blocked opening the phone app, so the call can't be started."

/**
 * A labeled block of medical text. Empty fields show "Not provided" (never
 * "none") so responders can't mistake missing data for a negative answer.
 * [highlight] gives allergies the strongest visual contrast on the card.
 */
@Composable
private fun MedicalSection(title: String, value: String?, highlight: Boolean = false) {
    val text = value?.trim().orEmpty().ifBlank { null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MedicalRed.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MedicalRed else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text ?: "Not provided",
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp,
                fontStyle = if (text == null) FontStyle.Italic else FontStyle.Normal,
                color = if (text == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
