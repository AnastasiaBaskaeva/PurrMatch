package com.purrmatch.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.Listing

@Composable
fun ProfileScreen(
    profile: Profile, listings: List<Listing>, public: Boolean,
    onBack: () -> Unit, onSettings: () -> Unit, onOpen: (Listing) -> Unit,
    onDelete: (Listing) -> Unit, onNewListing: () -> Unit, onContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenLayout(if (public) "Автор анкеты" else "Профиль", modifier,
        onBack = if (public) onBack else null, actions = {
            if (!public) IconButton(Icon.Settings, "Настройки", onSettings)
        }) {
        FormContent {
            ProfileHeader(profile.name, profile.city)
            Spacer(Modifier.height(13.3.dp))
            Body(profile.about)
            Spacer(Modifier.height(if (public) 14.dp else 13.3.dp))
            if (public) {
                Heading("Контакты")
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.padding(vertical = if (public) 0.dp else 1.dp), horizontalArrangement = Arrangement.spacedBy(if (public) 12.dp else 23.dp)) {
                ContactChip("Сайт", { onContact("website") }, compact = !public)
                ContactChip("Telegram", { onContact("telegram") }, compact = !public)
                ContactChip(if (public) "Позвонить" else profile.phone,
                    { onContact("phone") }, compact = !public)
            }
            if (public) {
                Heading("Действующие анкеты · ${listings.size}", Modifier.padding(top = 20.dp, bottom = 8.dp))
            } else {
                Spacer(Modifier.height(13.3.dp)); Divider(); Spacer(Modifier.height(20.dp))
                SectionHeader("Мои анкеты") { Body("${listings.size} действующие", caption = true) }
                Spacer(Modifier.height(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listings.forEach { listing ->
                    CatListItem(listing.name, if (public && listing.id == "peach") "Метис · ${listing.price}" else listing.price,
                        { onOpen(listing) }, trailingContent = if (public) null else {
                            { IconButton(Icon.Delete, "Удалить анкету ${listing.name}", { onDelete(listing) }) }
                        })
                }
            }
            if (!public) Button("Добавить анкету", onNewListing,
                Modifier.fillMaxWidth().padding(top = 23.dp), ButtonStyle.Warm)
        }
    }
}
