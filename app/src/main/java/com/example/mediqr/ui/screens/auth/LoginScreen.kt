package com.example.mediqr.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediqr.auth.AuthFormState
import com.example.mediqr.ui.components.AppTextField
import com.example.mediqr.ui.components.FormMessage
import com.example.mediqr.ui.components.MediQRLogo
import com.example.mediqr.ui.components.PrimaryButton
import com.example.mediqr.ui.theme.MedicalRed

/**
 * Login screen: authenticates with Supabase Auth (email + password).
 * There is exactly one login screen in the app; navigation to Sign Up happens
 * through [onNavigateToSignup].
 */
@Composable
fun LoginScreen(
    form: AuthFormState,
    onLogin: (email: String, password: String) -> Unit,
    onNavigateToSignup: () -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    // Keyboard flow: Email (Next) moves straight to Password.
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        focusManager.clearFocus()
        onLogin(email, password)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            MediQRLogo(size = 48.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "MediQR",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MedicalRed,
            )
            Text(
                text = "Emergency Medical ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = "Welcome back",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Sign in to manage your emergency medical card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                form.bannerError?.let {
                    FormMessage(message = it, isError = true)
                    Spacer(Modifier.height(12.dp))
                }
                form.notice?.let {
                    FormMessage(message = it, isError = false)
                    Spacer(Modifier.height(12.dp))
                }

                AppTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        onInputChanged()
                    },
                    label = "Email",
                    error = form.emailError,
                    enabled = !form.isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = { passwordFocus.requestFocus() },
                )

                Spacer(Modifier.height(14.dp))

                AppTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onInputChanged()
                    },
                    modifier = Modifier.focusRequester(passwordFocus),
                    label = "Password",
                    isPassword = true,
                    error = form.passwordError,
                    enabled = !form.isLoading,
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                )

                Spacer(Modifier.height(20.dp))

                PrimaryButton(
                    text = "Log In",
                    onClick = { submit() },
                    loading = form.isLoading,
                )

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { onNavigateToSignup() },
                    enabled = !form.isLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Don't have an account? Sign up",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
