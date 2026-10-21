package com.purrmatch.presentation.listing

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.profile.Profile
import java.util.Calendar
import java.util.Locale

@Composable
fun NewListingScreen(
    profile: Profile,
    photos: List<Painter>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onBack: () -> Unit,
    onPublish: (Listing) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var breed by rememberSaveable { mutableStateOf("Выберите породу") }
    var sex by rememberSaveable { mutableIntStateOf(0) }
    var birthday by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var eyes by rememberSaveable { mutableStateOf("") }
    var character by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var dogs by rememberSaveable { mutableStateOf(true) }
    var cats by rememberSaveable { mutableStateOf(true) }
    var children by rememberSaveable { mutableStateOf(true) }
    var paid by rememberSaveable { mutableStateOf(false) }
    var price by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    ScreenLayout("Новая анкета", modifier, onBack, navigationIcon = Icon.Close, footer = {
        ActionFooter {
            Button(if (paid) "Опубликовать анкету" else "Опубликовать бесплатно", {
                submitted = true
                if (name.isNotBlank() && breed in breeds && photos.isNotEmpty() && (!paid || (price.toIntOrNull() ?: 0) > 0)) {
                    onPublish(Listing("local-${System.currentTimeMillis()}", name.trim(), breed,
                        if (sex == 0) "кошка" else "кот", age = "", birthday = birthday, color = color, eyes = eyes,
                        character = character, description = description, city = profile.city,
                        price = if (paid) "$price ₽" else "Бесплатно", dogs = dogs, cats = cats, children = children))
                }
            }, Modifier.fillMaxWidth(), ButtonStyle.Warm)
        }
    }) {
        FormContent {
            PhotoPicker(photos, onAddPhoto, onRemovePhoto)
            if (submitted && photos.isEmpty()) Body("Добавьте хотя бы одну фотографию")
            Spacer(Modifier.height(13.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, "Имя кошки", placeholder = "Например, Мия",
                    errorMessage = if (submitted && name.isBlank()) "Укажите имя кошки" else null)
                DropdownField("Порода", breed, breeds, { breed = it })
                if (submitted && breed !in breeds) Body("Выберите породу")
                ChipGroup("Пол", listOf("Кошка", "Кот"), sex, { sex = it })
                SelectField("Дата рождения", birthday.ifEmpty { "ДД.ММ.ГГГГ" }, {
                    val now = Calendar.getInstance()
                    DatePickerDialog(context, { _, year, month, day ->
                        birthday = String.format(Locale.ROOT, "%02d.%02d.%04d", day, month + 1, year)
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = now.timeInMillis
                    }.show()
                }, icon = Icon.Calendar)
                TextField(color, { color = it }, "Окрас", placeholder = "Укажите окрас")
                TextField(eyes, { eyes = it }, "Цвет глаз", placeholder = "Укажите цвет")
                TextField(character, { character = it }, "Характер", placeholder = "Например, спокойная, ласковая")
                Column(Modifier.padding(top = 5.dp)) {
                    Heading("Совместимость", Modifier.padding(bottom = 10.dp))
                    SwitchRow("С собаками", dogs, { dogs = it })
                    SwitchRow("С кошками", cats, { cats = it })
                    SwitchRow("С детьми", children, { children = it })
                }
                TextField(description, { description = it }, "Описание", placeholder = "Расскажите о привычках кошки", multiline = true)
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Условия передачи", style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioOption("Бесплатно", !paid, { paid = false })
                        RadioOption("Платно", paid, { paid = true })
                    }
                }
                TextField(price, { price = it.filter(Char::isDigit) }, "Цена при платной передаче", placeholder = "0 ₽",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    errorMessage = if (submitted && paid && (price.toIntOrNull() ?: 0) <= 0) "Укажите цену" else null)
                Notice("Автор: ${profile.name}. Контакты и местоположение берутся из вашего профиля.")
            }
        }
    }
}

@Preview(name = "Новая анкета", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun NewListingScreenPreview() {
    App(initialScreen = Screen.NewListing)
}
