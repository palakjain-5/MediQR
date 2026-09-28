package com.example.mediqr.publiccard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediqr.core.supabase
import com.example.mediqr.profile.profileErrorMessage
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The scanned link itself is bad (not a uuid) - retrying cannot help. */
private const val INVALID_LINK_MESSAGE =
    "This card link isn't valid. Ask the person to scan their QR code again."

/** Well-formed id, but no card behind it: unknown, rotated or deleted. */
private const val CARD_GONE_MESSAGE =
    "This medical card doesn't exist or is no longer available. " +
        "The link may be outdated, or the card may have been removed."

/**
 * UI state of the public medical card screen.
 *
 * [NotFound] covers every "no card behind this id" case (invalid id, rotated
 * id, deleted card) as a terminal state; [Error] is the retryable kind
 * (network/server problems).
 */
sealed interface PublicCardUiState {
    /** The card id is being looked up. */
    data object Loading : PublicCardUiState

    /** Card found: exactly the whitelisted emergency fields. */
    data class Found(val card: PublicMedicalCard) : PublicCardUiState

    /** Invalid / unknown / deleted card id - nothing to show, don't retry. */
    data class NotFound(val message: String) : PublicCardUiState

    /** Network or server failure - [message] is user-friendly, retry helps. */
    data class Error(val message: String) : PublicCardUiState
}

/**
 * Loads a public medical card by its card id, with NO authentication: the
 * Supabase client runs as the anon role and the `get_public_card` SQL function
 * returns only the whitelisted emergency fields (or JSON null for an id that
 * has no card behind it).
 *
 * Security properties:
 *  - the request carries no user session data beyond the anonymous API key;
 *  - a malformed id never leaves the device (validated locally first);
 *  - the response model ([PublicMedicalCard]) cannot represent email, auth ids
 *    or any other private field even if a server returned them.
 */
class PublicCardViewModel : ViewModel() {

    private val _state = MutableStateFlow<PublicCardUiState>(PublicCardUiState.Loading)
    val state: StateFlow<PublicCardUiState> = _state.asStateFlow()

    fun load(cardId: String) {
        val normalized = cardId.trim()
        if (!isValidCardId(normalized)) {
            _state.value = PublicCardUiState.NotFound(INVALID_LINK_MESSAGE)
            return
        }
        viewModelScope.launch {
            _state.value = PublicCardUiState.Loading
            try {
                // Whitelisted server-side lookup; JSON null => no such card.
                val result = supabase.postgrest.rpc(
                    "get_public_card",
                    buildJsonObject { put("p_card_id", JsonPrimitive(normalized)) },
                )
                val card = result.decodeAsOrNull<PublicMedicalCard>()
                _state.value = card?.let { PublicCardUiState.Found(it) }
                    ?: PublicCardUiState.NotFound(CARD_GONE_MESSAGE)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w("MediQR", "public card load failed for id ${normalized.take(8)}...", t)
                _state.value = PublicCardUiState.Error(profileErrorMessage(t))
            }
        }
    }
}
