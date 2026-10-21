package com.purrmatch.presentation.listing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun ListingScreen(
    listing: Listing, authorName: String, photos: List<Painter>, favorite: Boolean,
    onBack: () -> Unit, onToggleFavorite: () -> Unit, onAuthor: () -> Unit,
    onContact: (String) -> Unit, modifier: Modifier = Modifier,
) {
    ScreenLayout("Анкета кошки", modifier, onBack, actions = {
        if (listing.available) IconButton(if (favorite) Icon.FavoriteFilled else Icon.Favorite,
            if (favorite) "Убрать из Избранного" else "В Избранное", onToggleFavorite)
    }, footer = {
        if (listing.available) ActionFooter {
            Button("Профиль автора и контакты", onAuthor, Modifier.fillMaxWidth())
        }
    }) {
        if (!listing.available) {
            FormContent {
                Notice("Автор удалил анкету. Она сохранена только в истории вашего Избранного.",
                    title = "Неактуально", unavailable = true)
                Spacer(Modifier.height(310.dp))
                Text(listing.name, style = Typography.Name, color = Colors.Ink)
                Body("${listing.breed} · ${listing.ageText} · ${listing.color.lowercase()}", Modifier.padding(top = 14.dp, bottom = 30.dp))
                Heading("Описание")
                Body(listing.description, Modifier.padding(top = 14.dp, bottom = 30.dp))
                Divider()
                Button("Убрать из Избранного", onToggleFavorite,
                    Modifier.fillMaxWidth().padding(top = 20.dp), ButtonStyle.Outlined)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PhotoGallery(photos)
                Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(listing.name, style = Typography.Name, color = Colors.Ink)
                    Tag(listing.priceText)
                }
                Body("${listing.breed} · ${listing.sex}${if (listing.ageText.isEmpty()) "" else " · ${listing.ageText}"}",
                    Modifier.padding(top = 14.dp, bottom = 30.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Дата рождения", listing.birthdayText)
                    DetailRow("Окрас", listing.color)
                    DetailRow("Глаза", listing.eyes)
                }
                Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(45.dp))
                Heading("Характер")
                TagRow(listing.character.split(',').filter { it.isNotBlank() }.map { CatTag(it.trim()) }, Modifier.padding(top = 8.dp))
                Body(listing.description, Modifier.padding(top = 13.dp, bottom = 30.dp))
                Divider(); Spacer(Modifier.height(16.dp)); Heading("Ладит с")
                TagRow(listOf(CatTag("Собаками · ${if (listing.dogs) "да" else "нет"}"),
                    CatTag("Кошками · ${if (listing.cats) "да" else "нет"}"),
                    CatTag("Детьми · ${if (listing.children) "да" else "нет"}")), Modifier.padding(top = 8.dp, bottom = 16.dp))
                Divider(); Spacer(Modifier.height(16.dp)); Heading("Передача и автор")
                Body("${listing.city} · ${listing.priceText.lowercase()}", Modifier.padding(top = 14.dp))
                Text("$authorName · открыть профиль", Modifier.padding(top = 14.dp)
                    .clickable(role = Role.Button, onClick = onAuthor), style = Typography.Body.copy(textDecoration = TextDecoration.Underline), color = Colors.Accent)
                Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp)); Heading("Связаться")
                FlowRow(Modifier.padding(top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Telegram" to "telegram", "WhatsApp" to "whatsapp", "Телефон" to "phone", "Сайт" to "website").forEach { (label, type) ->
                        ContactChip(label, { onContact(type) })
                    }
                }
            }
        }
    }
}
