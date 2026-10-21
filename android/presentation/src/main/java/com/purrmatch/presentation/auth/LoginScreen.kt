package com.purrmatch.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun LoginScreen(state: AuthState, onChange: (AuthState) -> Unit, onLogin: () -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, error: String? = null) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val topSpace = (maxHeight / 2 - 311.5.dp).coerceAtLeast(16.dp)
        Icon(Icon.Paw, null,
            Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 14.dp).size(30.dp).alpha(.25f)
                .graphicsLayer { rotationZ = 24f }, tint = Colors.PawDecoration)
        Icon(Icon.Paw, null,
            Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 104.dp).size(43.dp).alpha(.2f)
                .graphicsLayer { rotationZ = -20f }, tint = Colors.PawDecoration)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(topSpace))
            Mascot()
            Text("Кошки рядом", style = Typography.Hero, color = Colors.Ink)
            Text("Найдите своего нового друга", Modifier.padding(top = 22.dp, bottom = 40.dp),
                style = Typography.Subtitle, color = Colors.Body, textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(state.login, { onChange(state.copy(login = it)) }, "Логин", placeholder = "Введите логин",
                    errorMessage = if (state.submitted && state.login.isBlank()) "Введите логин" else null)
                PasswordField(state.password, { onChange(state.copy(password = it)) }, "Пароль", state.visible, { onChange(state.copy(visible = it)) },
                    placeholder = "••••••••", errorMessage = if (state.submitted && state.password.isEmpty()) "Введите пароль" else null)
                if (error != null) Text(error, color = Colors.DestructiveText, style = Typography.Body)
                Button(if (loading) "Входим…" else "Войти", onLogin, Modifier.fillMaxWidth(), enabled = !loading)
                Button("Создать аккаунт", onRegister, Modifier.fillMaxWidth(), ButtonStyle.Text, enabled = !loading)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
