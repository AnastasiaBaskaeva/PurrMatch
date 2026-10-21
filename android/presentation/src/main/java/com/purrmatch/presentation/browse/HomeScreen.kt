package com.purrmatch.presentation.browse

import com.purrmatch.presentation.listing.ageText
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.purrmatch.presentation.R
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
    onSwipe: ((Boolean, (Boolean) -> Unit) -> Unit)? = null,
    pendingLike: Boolean? = null,
) {
    ScreenLayout("Кошки рядом", modifier) {
        if (listing == null) EmptyBrowseState(false, onFilters)
        else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val card: @Composable BoxScope.() -> Unit = {
                    CatCard(
                        "${listing.name}${if (listing.ageText.isEmpty()) "" else ", ${listing.ageText}"}",
                        "${listing.breed} · ${listing.color.lowercase()}", photo, listing.city,
                        listing.character.split(",").take(2).mapIndexed { index, tag ->
                            CatTag(tag.trim(), if (index == 1) TagStyle.Peach else TagStyle.Lavender)
                        }, onOpen, Modifier.padding(horizontal = 20.dp),
                    )
                }
                if (swipeProgress == null) SwipeCard(onLike, onSkip,
                    Modifier.fillMaxSize().padding(top = 17.dp), cardKey = listing.id,
                    onSwipe = onSwipe, pendingLike = pendingLike,
                    actions = { enabled, select ->
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
                            IconButton(Icon.SwipeSkip, stringResource(R.string.swipe_skip),
                                { select(false) }, Modifier.size(54.dp), enabled)
                            IconButton(Icon.SwipeLike, stringResource(R.string.swipe_like),
                                { select(true) }, Modifier.size(54.dp), enabled)
                        }
                    }, content = card)
                else SwipeCardFrame(swipeProgress, Modifier.padding(top = 6.dp), card)
            }
            Button("Фильтры", onFilters,
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                ButtonStyle.Secondary, leadingIcon = Icon.Filters)
        }
    }
}
