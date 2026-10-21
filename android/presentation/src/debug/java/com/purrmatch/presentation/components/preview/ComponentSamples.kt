package com.purrmatch.presentation.components.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R
import com.purrmatch.presentation.components.*

/** The sample crop comes from the image frame in Figma, not from a screen export. */
@Composable
internal fun sampleCatPhoto(detail: Boolean = false): Painter {
    val original = painterResource(R.drawable.sample_cat)
    return remember(original, detail) {
        object : Painter() {
            override val intrinsicSize = Size(if (detail) 336f else 332f, if (detail) 297f else 365f)
            override fun DrawScope.onDraw() {
                val scale = size.width / intrinsicSize.width
                clipRect {
                    translate(-57f * scale, -61.01f * scale) {
                        with(original) { draw(Size(419f * scale, 476f * scale)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun sampleUploadPhoto(): Painter {
    val original = painterResource(R.drawable.sample_upload)
    return remember(original) {
        object : Painter() {
            override val intrinsicSize = Size(102f, 102f)
            override fun DrawScope.onDraw() {
                val scale = size.width / 102f
                clipRect {
                    translate(-34.5f * scale, -13f * scale) {
                        with(original) { draw(Size(171f * scale, 128f * scale)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SampleCatCard(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    CatCard(
        "Мия, 2 года", "Британская короткошёрстная · серая", sampleCatPhoto(), "Москва",
        listOf(CatTag("Ласковая"), CatTag("Спокойная", TagStyle.Peach)),
        onClick, modifier,
    )
}

@Composable
internal fun SampleFilters(modifier: Modifier = Modifier) {
    var breed by remember { mutableStateOf("Любая порода") }
    var color by remember { mutableStateOf("Любой окрас") }
    var eyes by remember { mutableStateOf("Любой цвет") }
    var city by remember { mutableStateOf("Выберите город") }
    var sex by remember { mutableIntStateOf(0) }
    var terms by remember { mutableIntStateOf(0) }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField("Порода", breed, listOf("Любая порода", "Британская короткошёрстная", "Метис"), { breed = it })
        ChipGroup("Пол", listOf("Любой", "Кошка", "Кот"), sex, { sex = it })
        DropdownField("Окрас", color, listOf("Любой окрас", "Серый", "Рыжий"), { color = it })
        DropdownField("Цвет глаз", eyes, listOf("Любой цвет", "Янтарные", "Зелёные"), { eyes = it })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 5.dp)) {
            Text("Возраст", style = Typography.GroupLabel, color = Colors.GroupLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(min, { min = it }, "От", Modifier.weight(1f), "0 лет")
                TextField(max, { max = it }, "До", Modifier.weight(1f), "20 лет")
            }
        }
        DropdownField("Город", city, listOf("Москва", "Санкт-Петербург"), { city = it })
        ChipGroup("Условия передачи", listOf("Любые", "Бесплатно", "Платно"), terms, { terms = it })
        TextField(price, { price = it }, "Цена до", placeholder = "Любая цена ₽")
    }
}

@Composable
internal fun SampleFields(modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("12345678") }
    var visible by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(true) }
    var radio by remember { mutableStateOf(true) }
    var errorValue by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(value, { value = it }, "Логин", placeholder = "Введите логин")
        PasswordField(password, { password = it }, "Пароль", visible, { visible = it })
        TextField(value, { value = it }, "О себе", placeholder = "Пара слов о вас", multiline = true)
        ReadOnlyField("Логин", "anna_cats")
        SelectField("Дата рождения", "ДД.ММ.ГГГГ", {}, icon = Icon.Calendar)
        TextField("", {}, "Недоступное поле", placeholder = "Введите значение", enabled = false)
        TextField(errorValue, { errorValue = it }, "Имя", errorMessage = "Укажите имя")
        SwitchRow("С собаками", checked, { checked = it })
        Switch(checked, { checked = it })
        Row(horizontalArrangement = Arrangement.spacedBy(25.dp)) {
            RadioOption("Бесплатно", radio, { radio = true })
            RadioOption("Платно", !radio, { radio = false })
        }
    }
}

@Composable
internal fun SampleButtons(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Button("Войти", {}, Modifier.fillMaxWidth())
        Button("Опубликовать бесплатно", {}, Modifier.fillMaxWidth(), ButtonStyle.Warm)
        Button("Фильтры", {}, Modifier.fillMaxWidth(), ButtonStyle.Secondary, leadingIcon = Icon.Filters)
        Button("Убрать из Избранного", {}, Modifier.fillMaxWidth(), ButtonStyle.Outlined)
        Button("Создать аккаунт", {}, Modifier.fillMaxWidth(), ButtonStyle.Text)
        Button("Войти", {}, Modifier.fillMaxWidth(), enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Удалить", {}, style = ButtonStyle.Destructive)
            IconButton(Icon.Back, "Назад", {})
            IconButton(Icon.Settings, "Настройки", {})
            IconButton(Icon.FavoriteFilled, "Избранное", {})
            IconButton(Icon.Delete, "Удалить", {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ContactChip("Сайт", {}, compact = true)
            ContactChip("Telegram", {}, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContactChip("Сайт", {})
            ContactChip("Telegram", {})
            ContactChip("Позвонить", {})
        }
    }
}

@Composable
internal fun SampleCards(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ProfileHeader("Анна", "Москва")
        CatListItem("Мия, 2 года", "Британская · Москва", {}) {
            IconButton(Icon.FavoriteFilled, "Убрать из Избранного", {})
        }
        CatListItem("Соня, 4 года", "", {}, available = false) {
            IconButton(Icon.FavoriteFilled, "Убрать из Избранного", {})
        }
        CatListItem("Персик", "5 000 ₽", {}) {
            IconButton(Icon.Delete, "Удалить анкету", {})
        }
        TagRow(listOf(CatTag("Бесплатно"), CatTag("Спокойная", TagStyle.Peach)))
        SectionHeader("Мои анкеты") {
            Text("2 действующие", style = Typography.Caption, color = Colors.Body)
        }
        DetailRow("Дата рождения", "14 мая 2024")
        DetailRow("Окрас", "Серый")
    }
}

@Preview(name = "Buttons and contacts", widthDp = 376)
@Composable
private fun ButtonsPreview() {
    Theme { Background { SampleButtons(Modifier.padding(20.dp)) } }
}

@Preview(name = "Text fields and selection", widthDp = 376, heightDp = 900)
@Composable
private fun FieldsPreview() {
    Theme { Background { SampleFields(Modifier.padding(20.dp)) } }
}

@Preview(name = "Cards and profile", widthDp = 376)
@Composable
private fun CardsPreview() {
    Theme { Background { SampleCards(Modifier.padding(20.dp)) } }
}

@Preview(name = "Cat card", widthDp = 376)
@Composable
private fun CatCardPreview() {
    Theme { Background { SampleCatCard(Modifier.padding(20.dp)) } }
}

@Preview(name = "Navigation", widthDp = 376)
@Composable
private fun NavigationPreview() {
    Theme {
        Background {
            Column {
                TopBar("Анкета кошки", navigationIcon = {
                    IconButton(Icon.Back, "Назад", {})
                })
                BottomBar(Tab.Favorites, {})
            }
        }
    }
}

@Preview(name = "Delete confirmation", widthDp = 376)
@Composable
private fun ConfirmationPreview() {
    Theme { Background {
        ConfirmationCard("Удалить анкету?", "Она исчезнет из общего подбора и ваших действующих анкет.", "Удалить", {}, {}, Modifier.padding(24.dp))
    } }
}
