package com.example.mediqr.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediqr.qr.QrCodeState
import com.example.mediqr.qr.hasWritePermission
import com.example.mediqr.qr.saveQrCode
import com.example.mediqr.qr.shareQrCode
import com.example.mediqr.ui.components.BackButton
import com.example.mediqr.ui.components.CenteredLoading
import com.example.mediqr.ui.components.FormMessage
import com.example.mediqr.ui.components.PrimaryButton
import com.example.mediqr.ui.components.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Auto-hide delay for the save/copy/error banner. */
private const val BANNER_DURATION_MS = 4_500L

private data class Banner(val message: String, val isError: Boolean)

/**
 * My QR Code - shows the QR image generated from the user's stable public card
 * id, explains what scanning it does, and offers copy/save/share actions.
 *
 * The QR code encodes ONLY the public card URL (`{base}/card/{cardId}`);
 * no medical information and no Supabase credentials ever go into the image.
 *
 * Missing card id cases are handled gracefully: [QrCodeState.NoProfile] offers
 * a shortcut to create the medical profile, while [QrCodeState.NoCardId] and
 * [QrCodeState.Error] show a friendly message with a retry action.
 */
@Composable
fun QrCodeScreen(
    state: QrCodeState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var banner by remember { mutableStateOf<Banner?>(null) }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(BANNER_DURATION_MS)
            banner = null
        }
    }

    /** Saves the given image on IO and reports the outcome via the banner. */
    fun launchSave(qrImage: Bitmap) {
        scope.launch {
            try {
                val location = saveQrCode(context, qrImage)
                banner = Banner("QR code saved to $location.", isError = false)
            } catch (t: Throwable) {
                banner = Banner("Couldn't save the QR code. Please try again.", isError = true)
            }
        }
    }

    // Only used below Android 10, where saving needs WRITE_EXTERNAL_STORAGE.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            (state as? QrCodeState.Ready)?.let { launchSave(it.qrImage) }
        } else {
            banner = Banner("Storage permission is required to save the image.", isError = true)
        }
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
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            BackButton(onClick = onBack)

            Text(
                text = "My QR Code",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(16.dp))

            banner?.let { current ->
                FormMessage(message = current.message, isError = current.isError)
                Spacer(Modifier.height(12.dp))
            }

            when (state) {
                QrCodeState.Loading -> CenteredLoading("Preparing your QR code...")

                is QrCodeState.NoProfile -> {
                    FormMessage(message = state.message, isError = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = "Set up medical profile", onClick = onNavigateToEditProfile)
                }

                is QrCodeState.NoCardId -> {
                    FormMessage(message = state.message, isError = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = "Try again", onClick = onRetry)
                }

                is QrCodeState.Error -> {
                    FormMessage(message = state.message, isError = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = "Try again", onClick = onRetry)
                }

                is QrCodeState.Ready -> {
                    // --- What this code does --------------------------------
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = "Your emergency QR code",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Anyone who scans this code is taken to your public " +
                                    "medical card at the link shown below. The code contains " +
                                    "only that link - never your medical details, password or " +
                                    "any other private data.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- The QR image itself --------------------------------
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = state.qrImage.asImageBitmap(),
                                contentDescription = "QR code linking to your public medical card",
                                modifier = Modifier.size(264.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- Card id + public link ------------------------------
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        SelectionContainer {
                            Column(Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Card identifier",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            copyToClipboard(context, state.cardId)
                                            banner = Banner("Card identifier copied.", isError = false)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(
                                    text = state.cardId,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = "Public link",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = state.cardUrl,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- Save / share ---------------------------------------
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton(
                            text = "Save image",
                            onClick = {
                                if (hasWritePermission(context)) {
                                    launchSave(state.qrImage)
                                } else {
                                    storagePermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Download,
                        )
                        SecondaryButton(
                            text = "Share",
                            onClick = {
                                scope.launch {
                                    try {
                                        shareQrCode(context, state.qrImage, state.cardUrl)
                                    } catch (t: Throwable) {
                                        banner = Banner(
                                            "Couldn't share the QR code. Please try again.",
                                            isError = true,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Share,
                        )
                    }
                }
            }
        }
    }
}

/** Puts the card id on the clipboard (framework API, no Compose version skew). */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MediQR card identifier", text))
}
