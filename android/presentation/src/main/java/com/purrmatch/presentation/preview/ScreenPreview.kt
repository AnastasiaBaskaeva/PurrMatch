package com.purrmatch.presentation.preview

import androidx.compose.ui.res.stringResource
import com.purrmatch.presentation.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.Background
import com.purrmatch.presentation.components.BottomBar
import com.purrmatch.presentation.components.Tab
import com.purrmatch.presentation.components.Theme
import com.purrmatch.presentation.listing.Listing
import com.purrmatch.presentation.profile.Profile
import java.time.LocalDate

/** Static UI fixtures: previews need neither repositories nor a navigation host. */
internal object ScreenPreviewData {
    val profile: Profile
        @Composable get() = Profile(
            id = "preview-author",
            name = stringResource(R.string.preview_anna),
            login = stringResource(R.string.preview_login),
            city = stringResource(R.string.city_moscow),
            about = stringResource(R.string.preview_profile_about),
            telegram = stringResource(R.string.preview_telegram),
            phone = stringResource(R.string.preview_phone),
            website = stringResource(R.string.preview_website),
        )

    val listing: Listing
        @Composable get() = Listing(
            id = "preview-cat",
            name = stringResource(R.string.preview_mia),
            authorId = profile.id,
            breed = stringResource(R.string.breed_british),
            birthday = LocalDate.of(2024, 2, 10),
            color = stringResource(R.string.color_grey),
            eyes = stringResource(R.string.eyes_amber),
            character = stringResource(R.string.preview_character),
            description = stringResource(R.string.preview_description),
            city = stringResource(R.string.city_moscow),
        )

    val listings: List<Listing>
        @Composable get() = listOf(
            listing,
            listing.copy(
                id = "preview-second-cat",
                name = stringResource(R.string.preview_peach),
                breed = stringResource(R.string.breed_mixed),
            ),
        )
}

@Composable
internal fun ScreenPreview(tab: Tab? = null, content: @Composable () -> Unit) {
    Theme {
        Background(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize()) { content() }
                if (tab != null) BottomBar(tab, {}, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
            }
        }
    }
}
