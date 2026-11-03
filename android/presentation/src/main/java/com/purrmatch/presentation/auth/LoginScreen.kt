package com.purrmatch.presentation.auth

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun LoginScreen(
    state: AuthState,
    onChange: (AuthState) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val topSpace = (maxHeight / 2 - 311.5.dp).coerceAtLeast(16.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(topSpace))
            Mascot()
            Text(
                stringResource(R.string.app_title),
                style = Typography.Hero,
                color = Colors.Ink
            )
            Text(
                stringResource(R.string.login_subtitle),
                Modifier.padding(
                    top = 22.dp,
                    bottom = 40.dp
                ),
                style = Typography.Subtitle,
                color = Colors.Body,
                textAlign = TextAlign.Center
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    state.login,
                    { onChange(state.copy(login = it)) },
                    stringResource(R.string.login_label),
                    placeholder = stringResource(R.string.login_hint),
                    errorMessage = if (state.submitted && state.login.isBlank()) stringResource(R.string.login_hint) else null
                )
                PasswordField(
                    state.password,
                    { onChange(state.copy(password = it)) },
                    stringResource(R.string.password_label),
                    state.visible,
                    { onChange(state.copy(visible = it)) },
                    errorMessage = if (state.submitted && state.password.isEmpty()) stringResource(R.string.password_hint) else null
                )
                if (error != null) Text(
                    error,
                    color = Colors.DestructiveText,
                    style = Typography.Body
                )
                Button(
                    if (loading) stringResource(R.string.signing_in) else stringResource(R.string.sign_in),
                    onLogin,
                    Modifier.fillMaxWidth(),
                    enabled = !loading,
                )
                Button(
                    stringResource(R.string.create_account),
                    onRegister,
                    Modifier.fillMaxWidth(),
                    ButtonStyle.Text,
                    enabled = !loading
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(
    name = "Вход",
    widthDp = 376,
    heightDp = 803
)
@Composable
private fun LoginScreenPreview() {
    var state by remember { mutableStateOf(AuthState()) }
    ScreenPreview {
        LoginScreen(
            state = state,
            onChange = { state = it },
            onLogin = {},
            onRegister = {})
    }
}
