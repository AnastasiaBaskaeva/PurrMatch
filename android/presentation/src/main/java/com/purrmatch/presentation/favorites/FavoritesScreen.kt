package com.purrmatch.presentation.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
        else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 24.dp),
        ) {
            item(key = "intro", contentType = "intro") {
                Column {
                    Spacer(Modifier.height(13.34.dp))
                    Body("Здесь сохраняются все анкеты, которым вы поставили «Нравится».")
                    Spacer(Modifier.height(13.99.dp))
                }
            }
            itemsIndexed(listings, key = { _, listing -> "listing:${listing.id}" }, contentType = { _, _ -> "listing" }) { index, listing ->
                CatListItem("${listing.name}, ${listing.age}",
                    "${listing.breed.substringBefore(' ')} · ${listing.city}",
                    { onOpen(listing) },
                    modifier = Modifier.padding(bottom = if (index < listings.lastIndex) 10.dp else 0.dp),
                    available = listing.available) {
                    IconButton(Icon.FavoriteFilled, "Убрать ${listing.name} из Избранного", { onRemove(listing) })
                }
            }
        }
    }
}
