package com.example.mediqr.qr

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediqr.BuildConfig
import com.example.mediqr.core.supabase
import com.example.mediqr.profile.profileErrorMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SESSION_ERROR = "Your session has expired. Please sign out and sign in again."

private const val NO_PROFILE_MESSAGE =
    "Your medical card hasn't been set up yet. Create your medical profile first, " +
        "then your QR code will be ready here."

private const val NO_CARD_ID_MESSAGE =
    "Your QR code isn't ready yet: your card ID couldn't be loaded. Please try " +
        "again, and if this keeps happening update your medical profile or contact support."

/**
 * UI state of the My QR Code screen.
 *
 * [NoProfile]/[NoCardId] are graceful "missing card id" outcomes rather than
 * crashes: a fresh account has no profile row yet, and a row created before
 * the Phase 4 migration still lacks its card_id.
 */
sealed interface QrCodeState {
    /** The card id is being fetched and the QR image generated. */
    data object Loading : QrCodeState

    /**
     * QR ready to show. [cardId] is the user's stable public identifier and
     * [cardUrl] the public URL encoded in [qrImage] - both stay identical on
     * every visit for as long as the card id is not intentionally rotated.
     */
    data class Ready(
        val cardId: String,
        val cardUrl: String,
        val qrImage: Bitmap,
    ) : QrCodeState

    /** No profiles row exists yet: the user still has to create a medical profile. */
    data class NoProfile(val message: String) : QrCodeState

    /** A profile exists but carries no card id (migration not applied yet). */
    data class NoCardId(val message: String) : QrCodeState

    /** Loading failed (network, session, schema); [message] is user-friendly. */
    data class Error(val message: String) : QrCodeState
}

/**
 * Projection of the signed-in user's profile row. Only the public card id is
 * decoded; every other column the server returns is ignored. Nullable with a
 * default so a database without the Phase 4 column decodes to null (handled
 * as [QrCodeState.NoCardId]) instead of failing the whole read.
 */
@Serializable
private data class CardIdRow(
    @SerialName("card_id") val cardId: String? = null,
)

/**
 * Loads the signed-in user's stable public card id from Supabase and derives
 * the QR image from it.
 *
 * The card id always comes from the database - it is never generated per screen
 * open - so the same user's QR code points at the same public URL until the id
 * is deliberately rotated server-side.
 *
 * Loading is triggered by the navigation graph (LaunchedEffect on the QR
 * destination) rather than `init`, so returning to this screen from the profile
 * editor re-runs it and picks up a newly created profile without double-loading
 * on first entry.
 */
class QrCodeViewModel : ViewModel() {

    private val _state = MutableStateFlow<QrCodeState>(QrCodeState.Loading)
    val state: StateFlow<QrCodeState> = _state.asStateFlow()

    fun load() {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid == null) {
            _state.value = QrCodeState.Error(SESSION_ERROR)
            return
        }
        viewModelScope.launch {
            _state.value = QrCodeState.Loading
            try {
                // RLS guarantees this can only ever be the caller's own row.
                val row = supabase.from("profiles")
                    .select { filter { eq("id", uid) } }
                    .decodeList<CardIdRow>()
                    .firstOrNull()

                val cardId = row?.cardId
                when {
                    row == null -> _state.value = QrCodeState.NoProfile(NO_PROFILE_MESSAGE)

                    cardId.isNullOrBlank() -> {
                        // The only expected cause is the Phase 4 migration not
                        // being applied yet; visible in logcat for developers.
                        Log.w("MediQR", "profiles.card_id missing - is the Phase 4 migration applied?")
                        _state.value = QrCodeState.NoCardId(NO_CARD_ID_MESSAGE)
                    }

                    else -> {
                        val cardUrl = buildPublicCardUrl(BuildConfig.PUBLIC_CARD_BASE_URL, cardId)
                        val qrImage = withContext(Dispatchers.Default) {
                            encodeQrMatrix(cardUrl).toQrBitmap()
                        }
                        _state.value = QrCodeState.Ready(
                            cardId = cardId,
                            cardUrl = cardUrl,
                            qrImage = qrImage,
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.value = QrCodeState.Error(profileErrorMessage(t))
            }
        }
    }
}
