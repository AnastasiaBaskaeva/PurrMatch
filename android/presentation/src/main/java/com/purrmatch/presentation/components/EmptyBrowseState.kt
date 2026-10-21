package com.purrmatch.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyBrowseState(favorites: Boolean, onAction: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = (maxHeight / 2 - 175.dp).coerceAtLeast(16.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = top, bottom = 24.dp)) {
            EmptyState(
                if (favorites) "Пока пусто" else "Новых анкет пока нет",
                if (favorites) "Отмечайте понравившихся кошек на Главной. Они появятся здесь."
                else "Вы посмотрели всех кошек по выбранным условиям. Попробуйте расширить поиск.",
                if (favorites) "Смотреть кошек" else "Изменить фильтры", onAction,
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
