package com.purrmatch.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun EditProfileScreen(
    profile: Profile,
    onboarding: Boolean,
    onChange: (Profile) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenLayout(if (onboarding) "Расскажите о себе" else "Настройки", modifier,
        onBack = if (onboarding) null else onBack, footer = {
            ActionFooter {
                Button(if (onboarding) "Сохранить профиль" else "Сохранить изменения", onSave, Modifier.fillMaxWidth(), ButtonStyle.Warm)
                if (onboarding) Button("Заполнить позже", onBack,
                    Modifier.fillMaxWidth().padding(top = 11.dp), ButtonStyle.Text)
            }
        }) {
        FormContent {
            if (onboarding) {
                Notice("Профиль поможет другим пользователям связаться с вами по поводу кошек.",
                    backgroundColor = Colors.Like)
                Spacer(Modifier.height(24.dp))
            } else {
                Heading("Данные аккаунта")
                Spacer(Modifier.height(9.dp))
                ReadOnlyField("Логин", profile.login, backgroundColor = Colors.SettingsReadOnly)
                Spacer(Modifier.height(12.dp))
                TextField(profile.name, { onChange(profile.copy(name = it)) }, "Имя", textColor = Colors.EnteredText)
                Spacer(Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(profile.city, { onChange(profile.copy(city = it)) }, if (onboarding) "Город и район" else "Город", placeholder = "Где вы находитесь",
                    textColor = if (onboarding) Colors.Muted else Colors.EnteredText)
                TextField(profile.about, { onChange(profile.copy(about = it)) }, "О себе", placeholder = "Пара слов о вас", multiline = true)
            }
            if (!onboarding) {
                Heading("Контакты автора", Modifier.padding(top = 24.dp, bottom = 9.dp))
            } else Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(profile.telegram, { onChange(profile.copy(telegram = it)) }, "Telegram", placeholder = "@username")
                TextField(profile.phone, { onChange(profile.copy(phone = it)) }, "Телефон", placeholder = "Номер телефона", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                TextField(profile.website, { onChange(profile.copy(website = it)) }, "Сайт", placeholder = "Адрес сайта", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
            Spacer(Modifier.height(85.dp))
            Body(if (onboarding) "Контакты можно изменить позже в Настройках." else
                "Актуальные контакты показываются во всех ваших опубликованных анкетах.", caption = true)
            if (!onboarding) Button("Выйти из аккаунта", onLogout,
                Modifier.fillMaxWidth().padding(top = 36.dp), ButtonStyle.Text)
        }
    }
}
