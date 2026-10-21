package com.purrmatch.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.*

@Composable
fun RegistrationScreen(onBack: () -> Unit, onRegister: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    ScreenLayout("Регистрация", modifier, onBack, footer = {
        ActionFooter(contentPadding = PaddingValues(start = 20.dp, top = 11.dp, end = 20.dp, bottom = 40.dp)) {
            Button("Зарегистрироваться", {
                submitted = true
                if (name.isNotBlank() && login.isNotBlank() && password.length >= 8 && repeat == password) onRegister(name.trim(), login.trim())
            }, Modifier.fillMaxWidth())
        }
    }) {
        FormContent {
            Text("Создайте аккаунт", style = Typography.Title, color = Colors.Ink)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, "Имя", placeholder = "Ваше имя",
                    errorMessage = if (submitted && name.isBlank()) "Укажите имя" else null)
                TextField(login, { login = it }, "Логин", placeholder = "Придумайте уникальный логин",
                    errorMessage = if (submitted && login.isBlank()) "Укажите логин" else null)
                PasswordField(password, { password = it }, "Пароль", visible, { visible = it }, placeholder = "Не менее 8 символов",
                    errorMessage = if (submitted && password.length < 8) "Не менее 8 символов" else null)
                TextField(repeat, { repeat = it }, "Повторите пароль", placeholder = "••••••••",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation('•'),
                    errorMessage = if (submitted && repeat != password) "Пароли не совпадают" else null)
            }
        }
    }
}

@Preview(name = "Регистрация", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun RegistrationScreenPreview() {
    App(initialScreen = Screen.Registration)
}
