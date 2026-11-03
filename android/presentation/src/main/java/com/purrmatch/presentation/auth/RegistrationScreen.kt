package com.purrmatch.presentation.auth

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun RegistrationScreen(state: AuthState, onChange: (AuthState) -> Unit, onBack: () -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, error: String? = null) {
    ScreenLayout(stringResource(R.string.registration), modifier, onBack, footer = {
        ActionFooter(contentPadding = PaddingValues(start = 20.dp, top = 11.dp, end = 20.dp, bottom = 40.dp)) {
            if (error != null) Text(error, color = Colors.DestructiveText, style = Typography.Body)
            Button(
                if (loading) stringResource(R.string.creating_account) else stringResource(R.string.register),
                onRegister,
                Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }
    }) {
        FormContent {
            Text(stringResource(R.string.create_account_title), style = Typography.Title, color = Colors.Ink)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    state.name,
                    { onChange(state.copy(name = it)) },
                    stringResource(R.string.name_label),
                    placeholder = stringResource(R.string.name_hint),
                    errorMessage = if (state.submitted && state.name.isBlank()) stringResource(R.string.name_required) else null,
                )
                TextField(
                    state.login,
                    { onChange(state.copy(login = it)) },
                    stringResource(R.string.login_label),
                    placeholder = stringResource(R.string.unique_login_hint),
                    errorMessage = if (state.submitted && state.login.isBlank()) stringResource(R.string.login_required) else null,
                )
                PasswordField(
                    state.password,
                    { onChange(state.copy(password = it)) },
                    stringResource(R.string.password_label),
                    state.visible,
                    { onChange(state.copy(visible = it)) },
                    placeholder = stringResource(R.string.password_min_length),
                    errorMessage = if (state.submitted && state.password.length < 8) stringResource(R.string.password_min_length) else null,
                )
                TextField(
                    state.repeat,
                    { onChange(state.copy(repeat = it)) },
                    stringResource(R.string.repeat_password),
                    placeholder = stringResource(R.string.password_mask),
                    placeholderColor = Colors.Muted.copy(alpha = .5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation('•'),
                    errorMessage = if (state.submitted && state.repeat != state.password) stringResource(R.string.password_mismatch) else null,
                )
            }
        }
    }
}

@Preview(name = "Регистрация", widthDp = 376, heightDp = 803)
@Composable
private fun RegistrationScreenPreview() {
    var state by remember { mutableStateOf(AuthState()) }
    ScreenPreview {
        RegistrationScreen(state = state, onChange = { state = it }, onBack = {}, onRegister = {})
    }
}
