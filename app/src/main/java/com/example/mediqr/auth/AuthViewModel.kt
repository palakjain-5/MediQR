package com.example.mediqr.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediqr.core.supabase
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Minimum password length enforced by Supabase Auth. */
const val MIN_PASSWORD_LENGTH = 6

private const val NETWORK_ERROR = "No internet connection. Please check your network and try again."
private const val GENERIC_ERROR = "Something went wrong. Please try again."

/** Upper bound for the server-side part of sign-out before falling back to a local clear. */
private const val SIGN_OUT_TIMEOUT_MS = 5_000L
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/**
 * UI state of a single authentication form (Login or Sign Up).
 * Field errors are produced by local validation, bannerError/notice by the Supabase call.
 */
data class AuthFormState(
    val isLoading: Boolean = false,
    val bannerError: String? = null,
    val notice: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
)

/** How a successful sign-up finished, so the UI can navigate accordingly. */
enum class SignUpOutcome {
    /** Supabase returned a session (email confirmation disabled) - go to Dashboard. */
    SIGNED_IN,

    /** Account created, but Supabase requires the confirmation email first - go to Login. */
    EMAIL_CONFIRMATION_REQUIRED,
}

/**
 * Owns every authentication operation (sign-in, sign-up, sign-out) and exposes the
 * live Supabase session status that drives navigation.
 *
 * The password is only ever handed to Supabase Auth over HTTPS; it is never written
 * to any database by this app.
 */
class AuthViewModel : ViewModel() {

    private val auth: Auth
        get() = supabase.auth

    /**
     * Live session status from Supabase. The SDK restores persisted sessions on
     * startup, so this also maintains authentication across app restarts.
     */
    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    private val _loginForm = MutableStateFlow(AuthFormState())
    val loginForm: StateFlow<AuthFormState> = _loginForm.asStateFlow()

    private val _signupForm = MutableStateFlow(AuthFormState())
    val signupForm: StateFlow<AuthFormState> = _signupForm.asStateFlow()

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    /** Signs in with email + password. Navigation happens via [sessionStatus]. */
    fun signIn(emailInput: String, password: String) {
        val email = emailInput.trim()
        validateCredentials(email, password, confirmPassword = null, requireStrongPassword = false)
            ?.let { invalid ->
                _loginForm.value = invalid
                return
            }

        viewModelScope.launch {
            _loginForm.update {
                it.copy(isLoading = true, bannerError = null, notice = null, emailError = null, passwordError = null)
            }
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                // Success: sessionStatus becomes Authenticated and navigation follows it.
                _loginForm.value = AuthFormState()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _loginForm.update {
                    it.copy(isLoading = false, bannerError = authErrorMessage(t))
                }
            }
        }
    }

    /**
     * Creates a Supabase account with email + password.
     * [onFinished] reports whether Supabase returned a session immediately
     * (auto-confirm enabled) or the user must confirm their email first.
     */
    fun signUp(
        emailInput: String,
        password: String,
        confirmPassword: String,
        onFinished: (SignUpOutcome) -> Unit,
    ) {
        val email = emailInput.trim()
        validateCredentials(email, password, confirmPassword, requireStrongPassword = true)
            ?.let { invalid ->
                _signupForm.value = invalid
                return
            }

        viewModelScope.launch {
            _signupForm.update {
                it.copy(
                    isLoading = true,
                    bannerError = null,
                    emailError = null,
                    passwordError = null,
                    confirmPasswordError = null,
                )
            }
            try {
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val signedIn = auth.currentSessionOrNull() != null
                _signupForm.value = AuthFormState()
                if (!signedIn) {
                    _loginForm.update {
                        it.copy(
                            notice = "Account created. Check your inbox for a confirmation link, " +
                                "then sign in here.",
                        )
                    }
                }
                onFinished(
                    if (signedIn) SignUpOutcome.SIGNED_IN
                    else SignUpOutcome.EMAIL_CONFIRMATION_REQUIRED
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _signupForm.update {
                    it.copy(isLoading = false, bannerError = authErrorMessage(t))
                }
            }
        }
    }

    /**
     * Signs out. Best effort revokes the session server-side first (global
     * sign-out), then ALWAYS clears the local session.
     *
     * The local clear is not redundant: in supabase-kt every signOut(scope)
     * variant POSTs /auth/v1/logout while a session exists and only wipes the
     * stored session when that call succeeds - so on an offline device
     * signOut() always throws and the user would be stuck signed in. The
     * public clearSession() is the local-only wipe (deletes the stored session
     * and flips sessionStatus to NotAuthenticated) and is idempotent after a
     * successful global sign-out. It also resets the auth forms so a stale
     * error banner from an earlier failed attempt can't reappear on the
     * login screen after signing out.
     */
    fun logout() {
        viewModelScope.launch {
            _isLoggingOut.value = true
            try {
                // Bounded so a hanging (e.g. captive-portal) network can never
                // trap the user behind the signing-out spinner.
                withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) {
                    runCatching { auth.signOut(SignOutScope.GLOBAL) }
                }
                runCatching { auth.clearSession() }
                _loginForm.value = AuthFormState()
                _signupForm.value = AuthFormState()
            } finally {
                _isLoggingOut.value = false
            }
        }
    }

    /** Clears validation/banner messages when the user edits the login form. */
    fun clearLoginMessages() {
        _loginForm.update {
            it.copy(bannerError = null, notice = null, emailError = null, passwordError = null)
        }
    }

    /** Clears validation/banner messages when the user edits the sign-up form. */
    fun clearSignupMessages() {
        _signupForm.update {
            it.copy(
                bannerError = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
            )
        }
    }

    private fun validateCredentials(
        email: String,
        password: String,
        confirmPassword: String?,
        requireStrongPassword: Boolean,
    ): AuthFormState? {
        val emailError = when {
            email.isEmpty() -> "Please enter your email address."
            !EMAIL_REGEX.matches(email) -> "Please enter a valid email address."
            else -> null
        }
        val passwordError = when {
            password.isEmpty() -> "Please enter your password."
            requireStrongPassword && password.length < MIN_PASSWORD_LENGTH ->
                "Password must be at least $MIN_PASSWORD_LENGTH characters."
            else -> null
        }
        val confirmPasswordError = when {
            confirmPassword == null -> null
            confirmPassword.isEmpty() -> "Please confirm your password."
            confirmPassword != password -> "Passwords do not match."
            else -> null
        }

        return if (emailError == null && passwordError == null && confirmPasswordError == null) {
            null
        } else {
            AuthFormState(
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
            )
        }
    }
}

/** Maps any throwable from Supabase Auth to a user-friendly message. */
internal fun authErrorMessage(t: Throwable): String = when (t) {
    is AuthWeakPasswordException ->
        "Password is too weak. Use at least $MIN_PASSWORD_LENGTH characters and mix letters with numbers."

    is AuthRestException -> authRestErrorMessage(t)

    is RestException ->
        t.description.takeIf { !it.isNullOrBlank() }
            ?: t.message?.takeIf { it.isNotBlank() }
            ?: GENERIC_ERROR

    is HttpRequestException, is IOException -> NETWORK_ERROR

    else -> GENERIC_ERROR
}

private fun authRestErrorMessage(e: AuthRestException): String {
    val details = buildString {
        append(e.description ?: "")
        append(' ')
        append(e.errorDescription ?: "")
        append(' ')
        append(e.error ?: "")
    }.lowercase()

    return when (e.errorCode) {
        AuthErrorCode.InvalidCredentials -> "Incorrect email or password."

        AuthErrorCode.UserAlreadyExists ->
            "An account with this email already exists. Please sign in instead."

        AuthErrorCode.EmailNotConfirmed ->
            "Please confirm your email address before signing in. Check your inbox for the confirmation link."

        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.EmailAddressNotAuthorized ->
            "This email address can't be used. Please enter a different one."

        AuthErrorCode.ValidationFailed ->
            if (details.contains("email")) "Please enter a valid email address."
            else "Please check your details and try again."

        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
            "Too many attempts. Please wait a few minutes and try again."

        AuthErrorCode.SignupDisabled ->
            "Sign-up is currently disabled. Please try again later."

        AuthErrorCode.UserBanned ->
            "This account has been disabled. Please contact support."

        else ->
            e.errorDescription?.takeIf { it.isNotBlank() }
                ?: e.description?.takeIf { it.isNotBlank() }
                ?: e.error?.takeIf { it.isNotBlank() }
                ?: GENERIC_ERROR
    }
}
