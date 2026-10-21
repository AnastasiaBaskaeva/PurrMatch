package com.purrmatch.presentation.auth

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
fun RegistrationScreen(state: AuthState, onChange: (AuthState) -> Unit, onBack: () -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier) {
    ScreenLayout("Регистрация", modifier, onBack, footer = {
        ActionFooter(contentPadding = PaddingValues(start = 20.dp, top = 11.dp, end = 20.dp, bottom = 40.dp)) {
            Button("Зарегистрироваться", onRegister, Modifier.fillMaxWidth())
        }
    }) {
        FormContent {
            Text("Создайте аккаунт", style = Typography.Title, color = Colors.Ink)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(state.name, { onChange(state.copy(name = it)) }, "Имя", placeholder = "Ваше имя",
                    errorMessage = if (state.submitted && state.name.isBlank()) "Укажите имя" else null)
                TextField(state.login, { onChange(state.copy(login = it)) }, "Логин", placeholder = "Придумайте уникальный логин",
                    errorMessage = if (state.submitted && state.login.isBlank()) "Укажите логин" else null)
                PasswordField(state.password, { onChange(state.copy(password = it)) }, "Пароль", state.visible, { onChange(state.copy(visible = it)) }, placeholder = "Не менее 8 символов",
                    errorMessage = if (state.submitted && state.password.length < 8) "Не менее 8 символов" else null)
                TextField(state.repeat, { onChange(state.copy(repeat = it)) }, "Повторите пароль", placeholder = "••••••••",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation('•'),
                    errorMessage = if (state.submitted && state.repeat != state.password) "Пароли не совпадают" else null)
            }
        }
    }
}
