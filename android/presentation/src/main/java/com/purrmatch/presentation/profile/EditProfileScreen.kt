package com.purrmatch.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.*

@Composable
fun EditProfileScreen(
    profile: Profile,
    onboarding: Boolean,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    initialAbout: String = profile.about,
) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var city by rememberSaveable { mutableStateOf(if (onboarding) "" else profile.city) }
    var about by rememberSaveable { mutableStateOf(if (onboarding) "" else initialAbout) }
    var telegram by rememberSaveable { mutableStateOf(if (onboarding) "" else profile.telegram) }
    var phone by rememberSaveable { mutableStateOf(if (onboarding) "" else profile.phone) }
    var website by rememberSaveable { mutableStateOf(if (onboarding) "" else profile.website) }
    ScreenLayout(if (onboarding) "Расскажите о себе" else "Настройки", modifier,
        onBack = if (onboarding) null else onBack, footer = {
            ActionFooter {
                Button(if (onboarding) "Сохранить профиль" else "Сохранить изменения", {
                    onSave(profile.copy(name = name, city = city, about = about, telegram = telegram, phone = phone, website = website))
                }, Modifier.fillMaxWidth(), ButtonStyle.Warm)
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
                TextField(name, { name = it }, "Имя", textColor = Colors.EnteredText)
                Spacer(Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(city, { city = it }, if (onboarding) "Город и район" else "Город", placeholder = "Где вы находитесь",
                    textColor = if (onboarding) Colors.Muted else Colors.EnteredText)
                TextField(about, { about = it }, "О себе", placeholder = "Пара слов о вас", multiline = true)
            }
            if (!onboarding) {
                Heading("Контакты автора", Modifier.padding(top = 24.dp, bottom = 9.dp))
            } else Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(telegram, { telegram = it }, "Telegram", placeholder = "@username")
                TextField(phone, { phone = it }, "Телефон", placeholder = "Номер телефона", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                TextField(website, { website = it }, "Сайт", placeholder = "Адрес сайта", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
            Spacer(Modifier.height(85.dp))
            Body(if (onboarding) "Контакты можно изменить позже в Настройках." else
                "Актуальные контакты показываются во всех ваших опубликованных анкетах.", caption = true)
            if (!onboarding) Button("Выйти из аккаунта", onLogout,
                Modifier.fillMaxWidth().padding(top = 36.dp), ButtonStyle.Text)
        }
    }
}

@Preview(name = "Заполнение профиля", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun CompleteProfileScreenPreview() {
    App(initialScreen = Screen.CompleteProfile)
}

@Preview(name = "Настройки", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    App(initialScreen = Screen.Settings)
}
