package com.purrmatch.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.purrmatch.presentation.R

/** Figma shapes shared across sizes and solid-color variants through [Icon]'s tint. */
enum class Icon(@param:DrawableRes val resource: Int) {
    Back(R.drawable.ic_back),
    Close(R.drawable.ic_close),
    Settings(R.drawable.ic_settings),
    PasswordHidden(R.drawable.ic_password_hidden),
    ChevronDown(R.drawable.ic_chevron_down),
    Calendar(R.drawable.ic_calendar),
    Plus(R.drawable.ic_plus),
    Filters(R.drawable.ic_filters),
    Delete(R.drawable.ic_delete),
    Logout(R.drawable.ic_logout),
    Lock(R.drawable.ic_lock),
    Home(R.drawable.ic_home),
    HomeSelected(R.drawable.ic_home_selected),
    Favorite(R.drawable.ic_favorite),
    FavoriteSelected(R.drawable.ic_favorite_selected),
    FavoriteFilled(R.drawable.ic_favorite_filled),
    Profile(R.drawable.ic_profile),
    ProfileSelected(R.drawable.ic_profile_selected),
    Paw(R.drawable.ic_paw),
    SwipeLike(R.drawable.ic_swipe_like),
    SwipeSkip(R.drawable.ic_swipe_skip),
}

@Composable
fun Icon(
    icon: Icon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Image(
        painter = painterResource(icon.resource),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}
