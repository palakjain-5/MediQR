package com.example.mediqr.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.navDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mediqr.BuildConfig
import com.example.mediqr.auth.AuthViewModel
import com.example.mediqr.auth.SignUpOutcome
import com.example.mediqr.core.supabase
import com.example.mediqr.profile.ProfileViewModel
import com.example.mediqr.publiccard.PublicCardViewModel
import com.example.mediqr.qr.QrCodeViewModel
import com.example.mediqr.ui.components.MediQRLogo
import com.example.mediqr.ui.screens.DashboardScreen
import com.example.mediqr.ui.screens.EditProfileScreen
import com.example.mediqr.ui.screens.PublicMedicalCardScreen
import com.example.mediqr.ui.screens.QrCodeScreen
import com.example.mediqr.ui.screens.auth.LoginScreen
import com.example.mediqr.ui.screens.auth.SignupScreen
import com.example.mediqr.ui.theme.MedicalRed
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

/** All navigation routes of the app. */
object Routes {
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val DASHBOARD = "dashboard"
    const val EDIT_PROFILE = "edit_profile"
    const val QR_CODE = "qr_code"

    /**
     * Public medical card, addressed by its card id. Intentionally NOT part of
     * [PRIVATE]: this screen must open without a session (the whole point of
     * the QR flow) and only ever renders whitelisted emergency fields.
     */
    const val PUBLIC_CARD = "card/{cardId}"

    /** Route (and deep-link path) for one user's public medical card. */
    fun publicCard(cardId: String) = "card/$cardId"

    /** Screens that require an authenticated session (requirement: protected navigation). */
    val PRIVATE = setOf(DASHBOARD, EDIT_PROFILE, QR_CODE)
}

/** Marks the launch intent as already turned into navigation (survives rotation). */
private const val DEEP_LINK_HANDLED = "com.example.mediqr.deep_link_handled"

/** Log tag for navigation safety warnings (back-stack recovery). */
private const val MEDIQR_LOG_TAG = "MediQR"

/**
 * Root of the app's navigation.
 *
 * Auth handling:
 *  - While Supabase does its FIRST session restore ([SessionStatus.Initializing]
 *    with no decision yet) a splash is shown, which keeps authentication across
 *    app restarts (the restored session decides the start screen). Later
 *    Initializing blips (session reload on resume) keep the current decision so
 *    the nav tree - and the user's back stack - survive a trip to another app
 *    (e.g. the phone dialer opened from the public card's Call button).
 *  - The whole NavHost is keyed on the authentication state: flipping between
 *    signed-in/signed-out rebuilds the back stack with the correct root
 *    (Dashboard vs Login). Private routes therefore cannot be reached while signed
 *    out, and logging out wipes every private entry from the back stack.
 */
@Composable
fun AppNavHost(authViewModel: AuthViewModel = viewModel()) {
    val sessionStatus by authViewModel.sessionStatus.collectAsState()

    // The auth decision is sticky. Supabase re-emits Initializing when it
    // reloads the stored session (token refresh / resuming the app - e.g.
    // returning from the phone app after a Call). Rebuilding on that blip
    // would dispose the auth-keyed nav tree below and wipe the user's back
    // stack (they would lose the open public card mid-emergency), so only
    // the very first, still-undecided state counts as "initializing".
    var stickyAuth by remember { mutableStateOf<Boolean?>(null) }
    when (sessionStatus) {
        is SessionStatus.Authenticated -> stickyAuth = true

        // A stored session exists but its refresh failed (e.g. offline): stay in.
        is SessionStatus.RefreshFailure ->
            stickyAuth = supabase.auth.currentSessionOrNull() != null

        is SessionStatus.Initializing -> Unit // keep the previous decision
        else -> stickyAuth = false
    }

    val isInitializing = sessionStatus is SessionStatus.Initializing && stickyAuth == null

    if (isInitializing) {
        SplashScreen()
        return
    }

    val isAuthenticated = stickyAuth == true

    key(isAuthenticated) {
        val navController = rememberNavController()

        // A screen's Back button must never pop the LAST stack entry: an empty
        // back stack makes NavHost emit no content at all - a blank screen that
        // only a task kill can leave. Two activations of the same Back button
        // landing within one frame (e.g. injected key events) could otherwise
        // call popBackStack() twice in a row, so refuse the pop when there is
        // no parent entry to go back to. System back is unaffected: NavHost's
        // own back callback is disabled at the root and falls through to the
        // activity, which finishes normally.
        val navigateBack: () -> Unit = {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        }

        // Safety net for the same invariant: should the stack ever end up empty
        // anyway (e.g. a Back activation racing a system back within one frame,
        // where the system callback's enabled flag only recomposes on the next
        // frame), rebuild the start destination instead of leaving NavHost with
        // nothing to render. currentBackStack is a conflated StateFlow collected
        // on the UI dispatcher, so the transient empty state of an in-flight
        // popUpTo(...)+navigate() batch is never observed here - only a stack
        // that actually stays empty. seenEntries also proves the graph was set
        // before any recovery navigate() is issued.
        LaunchedEffect(navController) {
            var seenEntries = false
            navController.currentBackStack.collect { stack ->
                if (stack.isNotEmpty()) {
                    seenEntries = true
                } else if (seenEntries && navController.currentBackStack.value.isEmpty()) {
                    val start = if (isAuthenticated) Routes.DASHBOARD else Routes.LOGIN
                    Log.w(MEDIQR_LOG_TAG, "back stack emptied unexpectedly, recovering $start")
                    navController.navigate(start) { launchSingleTop = true }
                }
            }
        }

        // A QR-scanned public card URL (https://.../card/{id}) arrives as an
        // ACTION_VIEW intent resolved by the manifest intent filter; turn it
        // into navigation. We first wait for the initial back-stack entry so
        // the NavHost graph is guaranteed to be set - calling handleDeepLink
        // before that would silently fail and leave the start destination on
        // screen. The extra guard keeps a configuration change (the same
        // intent) from resetting the back stack, while a fresh scan delivers
        // a fresh intent and works again.
        val deepLinkContext = LocalContext.current
        LaunchedEffect(Unit) {
            val intent = deepLinkContext.findActivity()?.intent
            val alreadyHandled = intent?.getBooleanExtra(DEEP_LINK_HANDLED, false) == true
            if (intent == null || intent.action != Intent.ACTION_VIEW) return@LaunchedEffect
            if (alreadyHandled) {
                // Consuming a deep link also means taking its URI away: the
                // DEEP_LINK_HANDLED extra only guards OUR handling, while
                // NavController itself re-runs activity.getIntent() through its
                // checkDeepLinkHandled() on every controller it creates - and
                // the auth-keyed rebuild above creates a fresh one on every
                // sign-in/sign-out. Without stripping the data, that internal
                // check would re-open the stale public card after signing out
                // (sign-out would land on the card instead of Login). The
                // action stays ACTION_VIEW, so this guard keeps working; a new
                // scan delivers a new intent with fresh data as before.
                intent.data = null
                return@LaunchedEffect
            }
            navController.currentBackStackEntryFlow.first()
            if (navController.handleDeepLink(intent)) {
                intent.putExtra(DEEP_LINK_HANDLED, true)
                intent.data = null // consumed; see the note in the branch above
            }
        }

        NavHost(
            navController = navController,
            startDestination = if (isAuthenticated) Routes.DASHBOARD else Routes.LOGIN,
        ) {
            composable(Routes.LOGIN) {
                val form by authViewModel.loginForm.collectAsState()
                LoginScreen(
                    form = form,
                    onLogin = { email, password -> authViewModel.signIn(email, password) },
                    onNavigateToSignup = { navController.navigate(Routes.SIGN_UP) },
                    onInputChanged = authViewModel::clearLoginMessages,
                )
            }

            composable(Routes.SIGN_UP) {
                val form by authViewModel.signupForm.collectAsState()
                SignupScreen(
                    form = form,
                    onSignup = { email, password, confirmPassword ->
                        authViewModel.signUp(email, password, confirmPassword) { outcome ->
                            if (outcome == SignUpOutcome.EMAIL_CONFIRMATION_REQUIRED) {
                                // No session yet: go to Login, where the confirmation
                                // notice is already staged by the view model.
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            }
                            // SIGNED_IN: the auth-keyed NavHost switches to Dashboard.
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onInputChanged = authViewModel::clearSignupMessages,
                )
            }

            composable(Routes.DASHBOARD) {
                val isLoggingOut by authViewModel.isLoggingOut.collectAsState()

                // Own ProfileViewModel instance for this entry: loads the user's
                // name for the welcome header (falls back to the email when no
                // profile exists yet). The extra load() below only fires when
                // returning from the editor, so a renamed profile shows up.
                val dashboardProfileViewModel: ProfileViewModel = viewModel()
                val dashboardProfile by dashboardProfileViewModel.form.collectAsState()
                LaunchedEffect(Unit) {
                    if (!dashboardProfile.isLoading) dashboardProfileViewModel.load()
                }

                DashboardScreen(
                    userEmail = supabase.auth.currentUserOrNull()?.email,
                    userName = dashboardProfile.fullName.trim().ifBlank { null },
                    publicCardId = dashboardProfile.cardId,
                    isLoggingOut = isLoggingOut,
                    onLogout = authViewModel::logout,
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onViewQrCode = { navController.navigate(Routes.QR_CODE) },
                    onPreviewPublicCard = { cardId ->
                        navController.navigate(Routes.publicCard(cardId))
                    },
                )
            }

            composable(Routes.EDIT_PROFILE) {
                // Scoped to this nav entry: a fresh ViewModel (and fresh load)
                // every time the screen opens; cleared when the user leaves.
                val profileViewModel: ProfileViewModel = viewModel()
                val profileForm by profileViewModel.form.collectAsState()
                EditProfileScreen(
                    form = profileForm,
                    onBack = navigateBack,
                    onSave = profileViewModel::save,
                    onRetryLoad = profileViewModel::load,
                    onFullNameChange = profileViewModel::onFullNameChange,
                    onBloodGroupChange = profileViewModel::onBloodGroupChange,
                    onAllergiesChange = profileViewModel::onAllergiesChange,
                    onMedicalConditionsChange = profileViewModel::onMedicalConditionsChange,
                    onCurrentMedicinesChange = profileViewModel::onCurrentMedicinesChange,
                    onAdditionalInfoChange = profileViewModel::onAdditionalInfoChange,
                    onContactNameChange = profileViewModel::onContactNameChange,
                    onContactPhoneChange = profileViewModel::onContactPhoneChange,
                )
            }

            composable(Routes.QR_CODE) {
                // The ViewModel is scoped to this nav entry (kept while the user
                // is on the profile editor and popped when they leave), but the
                // load is re-triggered by the LaunchedEffect below so returning
                // from a freshly created profile picks up the new card id.
                val qrViewModel: QrCodeViewModel = viewModel()
                val qrState by qrViewModel.state.collectAsState()
                LaunchedEffect(Unit) { qrViewModel.load() }
                QrCodeScreen(
                    state = qrState,
                    onBack = navigateBack,
                    onRetry = qrViewModel::load,
                    onNavigateToEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                )
            }

            composable(
                route = Routes.PUBLIC_CARD,
                deepLinks = listOf(
                    // Same URL shape the QR code encodes; opened by the manifest
                    // intent filter when someone taps/scans the public link.
                    navDeepLink {
                        uriPattern = "${BuildConfig.PUBLIC_CARD_BASE_URL}/card/{cardId}"
                    },
                ),
            ) {
                // Works signed-in (dashboard preview) and signed out (QR scan):
                // the data comes from the anon-callable whitelisted function.
                val publicCardViewModel: PublicCardViewModel = viewModel()
                val publicCardState by publicCardViewModel.state.collectAsState()
                val cardId = it.arguments?.getString("cardId").orEmpty()
                LaunchedEffect(cardId) { publicCardViewModel.load(cardId) }
                PublicMedicalCardScreen(
                    state = publicCardState,
                    onRetry = { publicCardViewModel.load(cardId) },
                    onBack = navigateBack,
                )
            }
        }
    }
}

/** Walks the context wrappers to the hosting Activity (for its launch intent). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Shown while Supabase restores the persisted session on app start. */
@Composable
private fun SplashScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MediQRLogo()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "MediQR",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedicalRed,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Starting securely...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MedicalRed,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}
