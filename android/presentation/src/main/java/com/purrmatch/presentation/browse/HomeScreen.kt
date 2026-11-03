package com.purrmatch.presentation.browse

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
import com.purrmatch.presentation.listing.ageText
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.Listing

@Composable
fun HomeScreen(
    listing: Listing?, photo: Painter,
    onOpen: () -> Unit, onLike: () -> Unit, onSkip: () -> Unit, onFilters: () -> Unit,
    modifier: Modifier = Modifier, swipeProgress: Float? = null,
) {
    ScreenLayout(stringResource(R.string.app_title), modifier) {
        if (listing == null) EmptyBrowseState(false, onFilters)
        else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val card: @Composable BoxScope.() -> Unit = {
                    CatCard(
                        if (listing.ageText.isEmpty()) listing.name else stringResource(R.string.name_age, listing.name, listing.ageText),
                        stringResource(R.string.detail_pair, listing.breed, listing.color.lowercase()), photo, listing.city,
                        listing.character.split(",").take(2).mapIndexed { index, tag ->
                            CatTag(tag.trim(), if (index == 1) TagStyle.Peach else TagStyle.Lavender)
                        }, onOpen, Modifier.padding(horizontal = 20.dp),
                    )
                }
                if (swipeProgress == null) SwipeCard(onLike, onSkip,
                    Modifier.fillMaxSize().padding(top = 17.dp), card)
                else SwipeCardFrame(swipeProgress, Modifier.padding(top = 6.dp), card)
            }
            Button(stringResource(R.string.filters), onFilters,
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                ButtonStyle.Secondary, leadingIcon = Icon.Filters)
        }
    }
}

@Preview(name = "Главная", widthDp = 376, heightDp = 803)
@Composable
private fun HomeScreenPreview() {
    HomePreviewContent()
}

@Preview(name = "Нет новых анкет", widthDp = 376, heightDp = 803)
@Composable
private fun EmptyHomeScreenPreview() {
    HomePreviewContent(empty = true)
}

@Preview(name = "Свайп вправо", widthDp = 376, heightDp = 803)
@Composable
private fun SwipeRightScreenPreview() {
    HomePreviewContent(swipeProgress = .33f)
}

@Preview(name = "Свайп влево", widthDp = 376, heightDp = 803)
@Composable
private fun SwipeLeftScreenPreview() {
    HomePreviewContent(swipeProgress = -.33f)
}

@Composable
private fun HomePreviewContent(empty: Boolean = false, swipeProgress: Float? = null) {
    ScreenPreview(Tab.Home) {
        HomeScreen(
            listing = if (empty) null else ScreenPreviewData.listing,
            photo = designCatPhoto(),
            onOpen = {},
            onLike = {},
            onSkip = {},
            onFilters = {},
            swipeProgress = swipeProgress,
        )
    }
}
