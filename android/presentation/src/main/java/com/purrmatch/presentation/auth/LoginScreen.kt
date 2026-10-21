package com.purrmatch.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.*

@Composable
fun LoginScreen(onLogin: () -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
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
                TextField(login, { login = it }, "Логин", placeholder = "Введите логин",
                    errorMessage = if (submitted && login.isBlank()) "Введите логин" else null)
                PasswordField(password, { password = it }, "Пароль", visible, { visible = it },
                    placeholder = "••••••••", errorMessage = if (submitted && password.isEmpty()) "Введите пароль" else null)
                Button("Войти", { submitted = true; if (login.isNotBlank() && password.isNotEmpty()) onLogin() }, Modifier.fillMaxWidth())
                Button("Создать аккаунт", onRegister, Modifier.fillMaxWidth(), ButtonStyle.Text)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(name = "Вход", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun LoginScreenPreview() {
    App(initialScreen = Screen.Login)
}
