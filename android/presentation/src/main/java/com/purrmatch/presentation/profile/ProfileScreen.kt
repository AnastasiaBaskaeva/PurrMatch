package com.purrmatch.presentation.profile

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
import com.purrmatch.presentation.listing.priceText
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
    ScreenLayout(if (public) stringResource(R.string.author_title) else stringResource(R.string.profile), modifier,
        onBack = if (public) onBack else null, actions = {
            if (!public) IconButton(Icon.Settings, stringResource(R.string.settings), onSettings)
        }) {
        FormContent {
            ProfileHeader(profile.displayName, profile.city)
            Spacer(Modifier.height(13.3.dp))
            Body(profile.about)
            val hasContacts = profile.website.isNotBlank() || profile.telegram.isNotBlank() || profile.phone.isNotBlank()
            if (hasContacts) {
                Spacer(Modifier.height(if (public) 14.dp else 13.3.dp))
                if (public) {
                    Heading(stringResource(R.string.contacts))
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier.padding(vertical = if (public) 0.dp else 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (public) 12.dp else 23.dp),
                ) {
                    if (profile.website.isNotBlank()) {
                        ContactChip(stringResource(R.string.website_label), { onContact("website") })
                    }
                    if (profile.telegram.isNotBlank()) {
                        ContactChip(stringResource(R.string.telegram_label), { onContact("telegram") })
                    }
                    if (profile.phone.isNotBlank()) {
                        ContactChip(
                            if (public) stringResource(R.string.call) else profile.phone,
                            { onContact("phone") },
                        )
                    }
                }
            }
            if (public) {
                Heading(
                    stringResource(R.string.active_listings_title, listings.size),
                    Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(13.3.dp)); Divider(); Spacer(Modifier.height(20.dp))
                SectionHeader(stringResource(R.string.my_listings)) { Body(stringResource(R.string.active_listings_count, listings.size), caption = true) }
                Spacer(Modifier.height(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listings.forEach { listing ->
                    CatListItem(listing.name, if (public && listing.id == "peach") stringResource(R.string.mixed_price, listing.priceText) else listing.priceText,
                        { onOpen(listing) }, trailingContent = if (public) null else {
                            { IconButton(Icon.Delete, stringResource(R.string.delete_named_listing, listing.name), { onDelete(listing) }) }
                        })
                }
            }
            if (!public) Button(stringResource(R.string.add_cat), onNewListing,
                Modifier.fillMaxWidth().padding(top = 23.dp), ButtonStyle.Warm)
        }
    }
}

@Preview(name = "Мой профиль", widthDp = 376, heightDp = 803)
@Composable
private fun ProfileScreenPreview() {
    ScreenPreview(Tab.Profile) { ProfilePreviewContent(public = false) }
}

@Preview(name = "Профиль автора", widthDp = 376, heightDp = 803)
@Composable
private fun AuthorProfileScreenPreview() {
    ScreenPreview { ProfilePreviewContent(public = true) }
}

@Composable
private fun ProfilePreviewContent(public: Boolean, withoutContacts: Boolean = false) {
    ProfileScreen(
        profile = if (withoutContacts) ScreenPreviewData.profile.copy(website = "", telegram = "", phone = "")
        else ScreenPreviewData.profile,
        listings = ScreenPreviewData.listings,
        public = public,
        onBack = {},
        onSettings = {},
        onOpen = {},
        onDelete = {},
        onNewListing = {},
        onContact = {},
    )
}

@Preview(name = "Профиль без контактов", widthDp = 376, heightDp = 803)
@Composable
private fun ProfileWithoutContactsPreview() {
    ScreenPreview(Tab.Profile) { ProfilePreviewContent(public = false, withoutContacts = true) }
}

@Preview(name = "Автор без контактов", widthDp = 376, heightDp = 803)
@Composable
private fun AuthorWithoutContactsPreview() {
    ScreenPreview { ProfilePreviewContent(public = true, withoutContacts = true) }
}
