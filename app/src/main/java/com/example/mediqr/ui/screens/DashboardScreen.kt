package com.example.mediqr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediqr.ui.components.FormMessage
import com.example.mediqr.ui.components.MediQRLogo
import com.example.mediqr.ui.components.PrimaryButton
import com.example.mediqr.ui.components.SecondaryButton
import com.example.mediqr.ui.theme.MedicalRed
import com.example.mediqr.ui.theme.MedicalRedLight
import kotlinx.coroutines.delay

private const val NOTICE_DURATION_MS = 4_000L

/**
 * Dashboard - the private landing screen after authentication.
 *
 * Contains the three entry points of the app:
 *  - Medical Profile -> the existing Edit Medical Profile screen.
 *  - My QR Code -> the My QR Code screen (Phase 4).
 *  - Public View -> opens the same Public Medical Card a scanner of the QR
 *    code sees, so the owner can preview it; without a card id yet the button
 *    explains what to do first instead of being a dead control.
 *
 * Requires an authenticated session; the navigation guard keeps unauthenticated
 * users out and logging out rebuilds the back stack on the login screen.
 */
@Composable
fun DashboardScreen(
    userEmail: String?,
    userName: String?,
    publicCardId: String?,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit,
    onViewQrCode: () -> Unit,
    onPreviewPublicCard: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-hiding notice shown when a future-phase action is tapped.
    var pendingNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingNotice) {
        if (pendingNotice != null) {
            delay(NOTICE_DURATION_MS)
            pendingNotice = null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // Keep the layout comfortable on tablets while staying
                    // full-width on phones.
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    // Edge-to-edge: keep content out of the status bar, where
                    // touches are consumed by the system.
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediQRLogo(size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "MediQR",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedicalRed,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onLogout,
                        enabled = !isLoggingOut,
                        // 48dp minimum touch target (a11y).
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Signing out...", fontSize = 14.sp)
                        } else {
                            Text(
                                text = "Sign out",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Welcome",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (userName.isNullOrBlank()) {
                    // No profile (yet) or it has no name: show the account.
                    Text(
                        text = userEmail ?: "Signed in",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                EmergencyCard(
                    icon = Icons.Outlined.Person,
                    title = "MEDICAL PROFILE",
                    description = "Keep your emergency medical information updated.",
                    actionLabel = "Edit Profile",
                    emphasized = true,
                    onAction = onEditProfile,
                )

                Spacer(Modifier.height(16.dp))

                EmergencyCard(
                    icon = Icons.Outlined.QrCode,
                    title = "MY QR CODE",
                    description = "Display, save or share your emergency QR code.",
                    actionLabel = "View QR Code",
                    emphasized = false,
                    onAction = onViewQrCode,
                )

                Spacer(Modifier.height(16.dp))

                EmergencyCard(
                    icon = Icons.Outlined.Visibility,
                    title = "PUBLIC VIEW",
                    description = "See exactly what someone scanning your QR code will see.",
                    actionLabel = "Preview Card",
                    emphasized = false,
                    onAction = {
                        val cardId = publicCardId
                        if (cardId.isNullOrBlank()) {
                            pendingNotice =
                                "Set up your medical profile first - your public card gets " +
                                    "its identifier as soon as your profile is saved."
                        } else {
                            onPreviewPublicCard(cardId)
                        }
                    },
                )

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Only people you share your QR code with can view your " +
                                "emergency information. Your password is never part of the " +
                                "shared card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Auto-hiding notice for the not-yet-available actions; anchored to
            // the bottom so it is visible no matter where the list is scrolled.
            pendingNotice?.let { message ->
                FormMessage(
                    message = message,
                    isError = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .widthIn(max = 640.dp),
                )
            }
        }
    }
}

/** One dashboard section: icon badge, title, description and an action button. */
@Composable
private fun EmergencyCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    emphasized: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MedicalRedLight
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MedicalRed, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(18.dp))

            if (emphasized) {
                PrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                )
            } else {
                SecondaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
