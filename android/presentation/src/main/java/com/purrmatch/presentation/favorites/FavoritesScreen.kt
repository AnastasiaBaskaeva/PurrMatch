package com.purrmatch.presentation.favorites

import com.purrmatch.presentation.listing.ageText
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.Listing

@Composable
fun FavoritesScreen(listings: List<Listing>, onOpen: (Listing) -> Unit,
    onRemove: (Listing) -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    ScreenLayout("Избранное", modifier) {
        if (listings.isEmpty()) EmptyBrowseState(true, onBrowse)
        else FormContent {
            Spacer(Modifier.height(13.34.dp))
            Body("Здесь сохраняются все анкеты, которым вы поставили «Нравится».")
            Spacer(Modifier.height(13.99.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listings.forEach { listing ->
                    CatListItem("${listing.name}, ${listing.ageText}",
                        if (listing.id == "peach") "Метис · бесплатно" else "${listing.breed.substringBefore(' ')} · ${listing.city}",
                        { onOpen(listing) }, available = listing.available) {
                        IconButton(Icon.FavoriteFilled, "Убрать ${listing.name} из Избранного", { onRemove(listing) })
                    }
                }
            }
        }
    }
}
