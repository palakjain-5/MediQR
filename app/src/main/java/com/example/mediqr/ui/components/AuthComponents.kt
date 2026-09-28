package com.example.mediqr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediqr.ui.theme.MedicalRed
import com.example.mediqr.ui.theme.MedicalRedDark
import com.example.mediqr.ui.theme.MedicalRedLight
import com.example.mediqr.ui.theme.SuccessGreen
import com.example.mediqr.ui.theme.SuccessGreenLight

/**
 * Simple medical cross drawn with Canvas - no icon dependencies needed.
 */
@Composable
fun MediQRLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MedicalRed,
) {
    Box(
        modifier = modifier
            .size(size)
            // Decorative shape, but TalkBack users should know what it is.
            .semantics { contentDescription = "MediQR medical cross logo" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val thickness = this.size.minDimension * 0.26f
            val length = this.size.minDimension * 0.88f
            val radius = thickness / 2f
            // Vertical bar
            drawRoundRect(
                color = color,
                topLeft = Offset((this.size.width - thickness) / 2f, (this.size.height - length) / 2f),
                size = Size(thickness, length),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
            // Horizontal bar
            drawRoundRect(
                color = color,
                topLeft = Offset((this.size.width - length) / 2f, (this.size.height - thickness) / 2f),
                size = Size(length, thickness),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
            // Subtle inner outline for a modern look
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset((this.size.width - thickness) / 2f + 2.dp.toPx(), (this.size.height - length) / 2f + 2.dp.toPx()),
                size = Size(thickness - 4.dp.toPx(), length - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/**
 * Rounded outlined text field used by the auth forms.
 * Shows an inline validation error below the field and a Show/Hide toggle for passwords.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        // singleLine forces a one-line field; minLines only applies to text areas.
        minLines = if (singleLine) 1 else minLines,
        isError = error != null,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = if (passwordVisible) "Hide" else "Show",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
        supportingText = if (error != null) {
            {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onGo = { onImeAction() },
            onNext = { onImeAction() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * Full-width primary action button in the medical red accent,
 * with an optional built-in loading indicator.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Banner shown above the form: red tint for errors, green tint for notices
 * (e.g. "check your inbox to confirm your account"). A status icon makes the
 * severity scannable without reading the text first.
 */
@Composable
fun FormMessage(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (isError) MedicalRedLight else SuccessGreenLight
    val contentColor = if (isError) MedicalRedDark else SuccessGreen
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * Back navigation control used by every pushed screen: arrow + label, with a
 * 48dp minimum touch target (the default TextButton height is below the
 * accessibility guideline).
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Back",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Secondary action button (outlined, brand red) with a 48dp touch target and
 * an optional leading icon. Pairs with [PrimaryButton] so every screen has
 * exactly two button weights.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Centered loading indicator with a plain-language label, so a spinner never
 * appears without saying what the app is doing.
 */
@Composable
fun CenteredLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
