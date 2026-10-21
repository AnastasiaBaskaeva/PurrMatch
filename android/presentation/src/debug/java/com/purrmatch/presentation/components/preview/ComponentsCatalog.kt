package com.purrmatch.presentation.components.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R
import com.purrmatch.presentation.components.*

private val samples = linkedMapOf(
    "login" to "Вход", "buttons" to "Кнопки и контакты", "fields" to "Поля и переключатели",
    "filters" to "Фильтры", "home" to "Карточка кошки", "swipe-right" to "Свайп вправо",
    "swipe-left" to "Свайп влево", "cards" to "Карточки списка", "profile" to "Профиль",
    "photos" to "Фотографии", "empty" to "Пустое состояние", "notices" to "Уведомления",
    "delete" to "Подтверждение удаления", "logout" to "Подтверждение выхода",
)

/** Debug-only, interactive catalogue. It is not part of the production library API. */
@Composable
fun ComponentsCatalog(initialSample: String = "catalog") {
    var sample by remember { mutableStateOf(initialSample) }
    var tab by remember { mutableStateOf(if (initialSample == "profile") Tab.Profile else Tab.Home) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    Theme {
        Background(Modifier.fillMaxSize()) {
            when (sample) {
                "login" -> SampleLogin(Modifier.fillMaxSize(), onSubmit = { sample = "home" }, onRegister = { sample = "fields" })
                else -> Column(Modifier.fillMaxSize()) {
                    val home = sample in listOf("home", "empty", "swipe-right", "swipe-left")
                    TopBar(
                        if (home) "Кошки рядом" else samples[sample] ?: "Компоненты",
                        navigationIcon = if (home || sample == "profile" || sample == "catalog") null else {
                            { IconButton(Icon.Back, "Назад", { sample = "catalog" }) }
                        },
                        actions = {
                            if (sample == "filters") Button("Сбросить", { sample = "catalog" }, style = ButtonStyle.Text)
                            if (sample == "profile") IconButton(Icon.Settings, "Настройки", { sample = "fields" })
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (sample) {
                            "home", "swipe-right", "swipe-left" -> {
                                Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f)) {
                                        when (sample) {
                                            "home" -> SwipeCard(
                                                onLike = { sample = "empty" }, onSkip = { sample = "empty" },
                                                modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                                            ) { SampleCatCard(Modifier.padding(horizontal = 20.dp)) { sample = "photos" } }
                                            else -> SwipeCardFrame(if (sample == "swipe-right") .33f else -.33f) {
                                                SampleCatCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
                                            }
                                        }
                                    }
                                    Button("Фильтры", { sample = "filters" },
                                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                                        ButtonStyle.Secondary, leadingIcon = Icon.Filters)
                                }
                            }
                            "filters" -> Column(Modifier.fillMaxSize()) {
                                SampleFilters(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 17.dp))
                                ActionFooter {
                                    Button("Показать анкеты", { sample = "home" }, Modifier.fillMaxWidth())
                                }
                            }
                            "empty" -> EmptyState(
                                "Новых анкет пока нет",
                                "Вы посмотрели всех кошек по выбранным условиям. Попробуйте расширить поиск.",
                                "Изменить фильтры", { sample = "filters" },
                                Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                            )
                            "delete", "logout" -> Column(Modifier.padding(20.dp)) {
                                SampleCards()
                                Button("Открыть диалог", {
                                    confirmation = if (sample == "delete") Confirmation.DeleteListing else Confirmation.Logout
                                }, Modifier.padding(top = 24.dp).fillMaxWidth())
                            }
                            "profile" -> SampleProfile(Modifier.padding(start = 20.dp, end = 20.dp, top = 17.dp),
                                onDelete = { confirmation = Confirmation.DeleteListing })
                            else -> Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (sample) {
                                    "buttons" -> SampleButtons()
                                    "fields" -> SampleFields()
                                    "cards" -> SampleCards()
                                    "photos" -> SamplePhotos()
                                    "notices" -> {
                                        Notice("Профиль поможет другим пользователям связаться с вами по поводу кошек.")
                                        Notice("Автор удалил анкету. Она сохранена только в истории вашего Избранного.", title = "Неактуально", unavailable = true)
                                        Mascot(Modifier.align(Alignment.CenterHorizontally))
                                    }
                                    else -> samples.forEach { (key, title) ->
                                        Button(title, { sample = key }, Modifier.fillMaxWidth(), ButtonStyle.Outlined)
                                    }
                                }
                            }
                        }
                    }
                    if (home || sample == "profile") BottomBar(
                        tab, {
                            tab = it
                            sample = when (it) {
                                Tab.Home -> "home"
                                Tab.Favorites -> "cards"
                                Tab.Profile -> "profile"
                            }
                        }, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                }
            }
            confirmation?.let { current ->
                ConfirmationDialog(current, onConfirm = {
                    confirmation = null
                    sample = if (current == Confirmation.Logout) "login" else "profile"
                }, onDismiss = { confirmation = null })
            }
        }
    }
}

@Composable
private fun SampleLogin(modifier: Modifier, onSubmit: () -> Unit, onRegister: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("12345678") }
    var visible by remember { mutableStateOf(false) }
    Box(modifier) {
        Icon(Icon.Paw, null,
            Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 14.dp).size(30.dp).alpha(.25f).graphicsLayer { rotationZ = 24f }, tint = Colors.PawDecoration)
        Icon(Icon.Paw, null,
            Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 104.dp).size(43.dp).alpha(.2f).graphicsLayer { rotationZ = -20f }, tint = Colors.PawDecoration)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(90.dp))
            Mascot()
            Text("Кошки рядом", Modifier.padding(top = 0.dp), style = Typography.Hero, color = Colors.Ink)
            Text("Найдите своего нового друга", Modifier.padding(top = 22.dp, bottom = 40.dp),
                style = Typography.Subtitle, color = Colors.Body, textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(login, { login = it }, "Логин", placeholder = "Введите логин")
                PasswordField(password, { password = it }, "Пароль", visible, { visible = it })
                Button("Войти", onSubmit, Modifier.fillMaxWidth())
                Button("Создать аккаунт", onRegister, Modifier.fillMaxWidth(), ButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun SampleProfile(modifier: Modifier = Modifier, onDelete: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        ProfileHeader("Анна", "Москва")
        Text("Помогаю кошкам находить заботливый дом. Отвечаю на сообщения каждый день.",
            style = Typography.Body, color = Colors.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(23.dp)) {
            ContactChip("Сайт", {}, compact = true)
            ContactChip("Telegram", {}, compact = true)
            ContactChip("+7 (•••) •••-••-••", {}, compact = true)
        }
        SectionHeader("Мои анкеты") {
            Text("2 действующие", style = Typography.Caption, color = Colors.Body)
        }
        CatListItem("Мия", "Бесплатно", {}) {
            IconButton(Icon.Delete, "Удалить анкету", onDelete)
        }
        CatListItem("Персик", "5 000 ₽", {}) {
            IconButton(Icon.Delete, "Удалить анкету", onDelete)
        }
        Button("Добавить анкету", {}, Modifier.padding(top = 10.dp).fillMaxWidth(), ButtonStyle.Warm)
    }
}

@Composable
private fun SamplePhotos() {
    val photo = sampleUploadPhoto()
    var photos by remember { mutableStateOf(listOf(photo)) }
    PhotoPicker(photos, { if (photos.size < 4) photos = photos + photo }, { index -> photos = photos.filterIndexed { i, _ -> i != index } })
    Spacer(Modifier.height(12.dp))
    PhotoGallery(listOf(sampleCatPhoto(detail = true), photo, sampleCatPhoto(detail = true)))
}

@Preview(name = "Filters", widthDp = 376, heightDp = 803)
@Composable
private fun FiltersPreview() { ComponentsCatalog("filters") }

@Preview(name = "Home", widthDp = 376, heightDp = 803)
@Composable
private fun HomePreview() { ComponentsCatalog("home") }

@Preview(name = "Photos", widthDp = 376, heightDp = 803)
@Composable
private fun PhotosPreview() { ComponentsCatalog("photos") }

@Preview(name = "Empty", widthDp = 376, heightDp = 803)
@Composable
private fun EmptyPreview() { ComponentsCatalog("empty") }

@Preview(name = "Login", widthDp = 376, heightDp = 803)
@Composable
private fun LoginPreview() { ComponentsCatalog("login") }
