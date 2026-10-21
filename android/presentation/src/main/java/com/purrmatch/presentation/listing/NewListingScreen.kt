package com.purrmatch.presentation.listing

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.profile.Profile
import java.util.Calendar
import java.util.Locale

@Composable
fun NewListingScreen(
    state: NewListingState,
    onChange: (NewListingState) -> Unit,
    profile: Profile,
    photos: List<Painter>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ScreenLayout("Новая анкета", modifier, onBack, navigationIcon = Icon.Close, footer = {
        ActionFooter {
            Button(if (state.paid) "Опубликовать анкету" else "Опубликовать бесплатно", onPublish, Modifier.fillMaxWidth(), ButtonStyle.Warm)
        }
    }) {
        FormContent {
            PhotoPicker(photos, onAddPhoto, onRemovePhoto)
            if (state.submitted && photos.isEmpty()) Body("Добавьте хотя бы одну фотографию")
            Spacer(Modifier.height(13.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(state.name, { onChange(state.copy(name = it)) }, "Имя кошки", placeholder = "Например, Мия",
                    errorMessage = if (state.submitted && state.name.isBlank()) "Укажите имя кошки" else null)
                DropdownField("Порода", state.breed, breeds, { onChange(state.copy(breed = it)) })
                if (state.submitted && state.breed !in breeds) Body("Выберите породу")
                ChipGroup("Пол", listOf("Кошка", "Кот"), state.sex, { onChange(state.copy(sex = it)) })
                SelectField("Дата рождения", state.birthday.ifEmpty { "ДД.ММ.ГГГГ" }, {
                    val now = Calendar.getInstance()
                    DatePickerDialog(context, { _, year, month, day ->
                        onChange(state.copy(birthday = String.format(Locale.ROOT, "%02d.%02d.%04d", day, month + 1, year)))
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = now.timeInMillis
                    }.show()
                }, icon = Icon.Calendar)
                TextField(state.color, { onChange(state.copy(color = it)) }, "Окрас", placeholder = "Укажите окрас")
                TextField(state.eyes, { onChange(state.copy(eyes = it)) }, "Цвет глаз", placeholder = "Укажите цвет")
                TextField(state.character, { onChange(state.copy(character = it)) }, "Характер", placeholder = "Например, спокойная, ласковая")
                Column(Modifier.padding(top = 5.dp)) {
                    Heading("Совместимость", Modifier.padding(bottom = 10.dp))
                    SwitchRow("С собаками", state.dogs, { onChange(state.copy(dogs = it)) })
                    SwitchRow("С кошками", state.cats, { onChange(state.copy(cats = it)) })
                    SwitchRow("С детьми", state.children, { onChange(state.copy(children = it)) })
                }
                TextField(state.description, { onChange(state.copy(description = it)) }, "Описание", placeholder = "Расскажите о привычках кошки", multiline = true)
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Условия передачи", style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioOption("Бесплатно", !state.paid, { onChange(state.copy(paid = false)) })
                        RadioOption("Платно", state.paid, { onChange(state.copy(paid = true)) })
                    }
                }
                TextField(state.price, { onChange(state.copy(price = it.filter(Char::isDigit))) }, "Цена при платной передаче", placeholder = "0 ₽",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    errorMessage = if (state.submitted && state.paid && (state.price.toIntOrNull() ?: 0) <= 0) "Укажите цену" else null)
                Notice("Автор: ${profile.name}. Контакты и местоположение берутся из вашего профиля.")
            }
        }
    }
}
