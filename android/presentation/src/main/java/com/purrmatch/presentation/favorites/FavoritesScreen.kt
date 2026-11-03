package com.purrmatch.presentation.favorites

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
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
    ScreenLayout(stringResource(R.string.favorites), modifier) {
        if (listings.isEmpty()) EmptyBrowseState(true, onBrowse)
        else FormContent {
            Spacer(Modifier.height(13.34.dp))
            Body(stringResource(R.string.favorites_description))
            Spacer(Modifier.height(13.99.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listings.forEach { listing ->
                    CatListItem(stringResource(R.string.name_age, listing.name, listing.ageText),
                        if (listing.id == "peach") stringResource(R.string.mixed_free) else stringResource(R.string.detail_pair, listing.breed.substringBefore(' '), listing.city),
                        { onOpen(listing) }, available = listing.available) {
                        IconButton(
                            Icon.FavoriteFilled,
                            stringResource(R.string.remove_named_favorite, listing.name),
                            { onRemove(listing) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Избранное", widthDp = 376, heightDp = 803)
@Composable
private fun FavoritesScreenPreview() {
    ScreenPreview(Tab.Favorites) {
        FavoritesScreen(ScreenPreviewData.listings, {}, {}, {})
    }
}

@Preview(name = "Пустое Избранное", widthDp = 376, heightDp = 803)
@Composable
private fun EmptyFavoritesScreenPreview() {
    ScreenPreview(Tab.Favorites) {
        FavoritesScreen(emptyList(), {}, {}, {})
    }
}
