package com.purrmatch.presentation.components

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyBrowseState(favorites: Boolean, onAction: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = (maxHeight / 2 - 175.dp).coerceAtLeast(16.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = top, bottom = 24.dp)) {
            EmptyState(
                if (favorites) stringResource(R.string.favorites_empty_title) else stringResource(R.string.browse_empty_title),
                if (favorites) stringResource(R.string.favorites_empty_message)
                else stringResource(R.string.browse_empty_message),
                if (favorites) stringResource(R.string.browse_cats) else stringResource(R.string.change_filters), onAction,
                illustration = if (favorites) { { EmptyFavoritesIllustration() } } else {
                    { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.purrmatch.presentation.R.drawable.empty_search), null, Modifier.size(115.dp, 120.dp)) }
                },
                actionStyle = if (favorites) ButtonStyle.Warm else ButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun EmptyFavoritesIllustration() {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.purrmatch.presentation.R.drawable.empty_favorites),
        null, Modifier.size(115.dp, 120.dp),
    )
}

@Preview(name = "EmptyBrowse", widthDp = 376, heightDp = 803)
@Composable
private fun EmptyBrowsePreview() {
    Theme {
        Background {
            EmptyBrowseState(favorites = false, onAction = {})
        }
    }
}

@Preview(name = "EmptyFavorites", widthDp = 376, heightDp = 803)
@Composable
private fun EmptyFavoritesPreview() {
    Theme {
        Background {
            EmptyBrowseState(favorites = true, onAction = {})
        }
    }
}
