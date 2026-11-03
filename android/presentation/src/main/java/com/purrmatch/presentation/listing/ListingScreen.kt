package com.purrmatch.presentation.listing

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.profile.Profile
import com.purrmatch.presentation.profile.displayName

@Composable
fun ListingScreen(
    listing: Listing,
    author: Profile?,
    photos: List<Painter>,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAuthor: () -> Unit,
    onContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (listing.available) {
        AvailableListingScreen(
            listing = listing,
            author = author,
            photos = photos,
            favorite = favorite,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
            onAuthor = onAuthor,
            onContact = onContact,
            modifier = modifier,
        )
    } else {
        UnavailableListingScreen(
            listing = listing,
            onBack = onBack,
            onRemoveFavorite = onToggleFavorite,
            modifier = modifier,
        )
    }
}

@Composable
private fun AvailableListingScreen(
    listing: Listing,
    author: Profile?,
    photos: List<Painter>,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAuthor: () -> Unit,
    onContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)
    ScreenLayout(
        title = stringResource(R.string.cat_details),
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(
                icon = if (favorite) Icon.FavoriteFilled else Icon.Favorite,
                contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                onClick = onToggleFavorite,
            )
        },
        footer = {
            ActionFooter {
                Button(stringResource(R.string.author_profile_contacts), onAuthor, Modifier.fillMaxWidth())
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            PhotoGallery(photos)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(listing.name, style = Typography.Name, color = Colors.Ink)
                Tag(listing.priceText)
            }
            val summary = if (listing.ageText.isEmpty()) {
                stringResource(R.string.detail_pair, listing.breed, listing.sex)
            } else {
                stringResource(R.string.detail_triple, listing.breed, listing.sex, listing.ageText)
            }
            Body(summary, Modifier.padding(top = 14.dp, bottom = 30.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.birthday_label), listing.birthdayText)
                DetailRow(stringResource(R.string.color_label), listing.color)
                DetailRow(stringResource(R.string.eyes_label), listing.eyes)
            }
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(45.dp))

            Heading(stringResource(R.string.character_label))
            TagRow(
                listing.character.split(',').filter { it.isNotBlank() }.map { CatTag(it.trim()) },
                Modifier.padding(top = 8.dp),
            )
            Body(listing.description, Modifier.padding(top = 13.dp, bottom = 30.dp))

            Divider()
            Spacer(Modifier.height(16.dp))
            Heading(stringResource(R.string.gets_along_with))
            TagRow(
                listOf(
                    CatTag(stringResource(R.string.dogs_compatibility, if (listing.dogs) yes else no)),
                    CatTag(stringResource(R.string.cats_compatibility, if (listing.cats) yes else no)),
                    CatTag(stringResource(R.string.children_compatibility, if (listing.children) yes else no)),
                ),
                Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Divider()
            Spacer(Modifier.height(16.dp))
            Heading(stringResource(R.string.transfer_author))

            Body(
                stringResource(R.string.detail_pair, listing.city, listing.priceText.lowercase()),
                Modifier.padding(top = 14.dp),
            )
            Text(
                text = stringResource(R.string.open_named_author, author?.displayName.orEmpty()),
                modifier = Modifier.padding(top = 14.dp).clickable(role = Role.Button, onClick = onAuthor),
                style = Typography.Body.copy(textDecoration = TextDecoration.Underline),
                color = Colors.Accent,
            )
            val contacts = listOfNotNull(
                author?.telegram?.takeIf { it.isNotBlank() }?.let { R.string.telegram_label to "telegram" },
                author?.whatsapp?.takeIf { it.isNotBlank() }?.let { R.string.whatsapp_label to "whatsapp" },
                author?.phone?.takeIf { it.isNotBlank() }?.let { R.string.phone_label to "phone" },
                author?.website?.takeIf { it.isNotBlank() }?.let { R.string.website_label to "website" },
            )
            if (contacts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))
                Heading(stringResource(R.string.contact))

                FlowRow(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    contacts.forEach { (label, type) ->
                        ContactChip(stringResource(label), { onContact(type) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableListingScreen(
    listing: Listing,
    onBack: () -> Unit,
    onRemoveFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenLayout(
        title = stringResource(R.string.cat_details),
        modifier = modifier,
        onBack = onBack,
    ) {
        FormContent {
            Notice(
                stringResource(R.string.unavailable_listing_message),
                title = stringResource(R.string.unavailable),
                unavailable = true,
            )
            Spacer(Modifier.height(310.dp))
            Text(listing.name, style = Typography.Name, color = Colors.Ink)
            Body(
                stringResource(R.string.detail_triple, listing.breed, listing.ageText, listing.color.lowercase()),
                Modifier.padding(top = 14.dp, bottom = 30.dp),
            )
            Heading(stringResource(R.string.description_label))
            Body(listing.description, Modifier.padding(top = 14.dp, bottom = 30.dp))
            Divider()
            Button(
                stringResource(R.string.remove_favorite),
                onRemoveFavorite,
                Modifier.fillMaxWidth().padding(top = 20.dp),
                ButtonStyle.Outlined,
            )
        }
    }
}

@Preview(name = "Анкета кошки", widthDp = 376, heightDp = 803)
@Composable
private fun ListingScreenPreview() {
    var favorite by remember { mutableStateOf(false) }
    ScreenPreview {
        ListingScreen(
            listing = ScreenPreviewData.listing,
            author = ScreenPreviewData.profile,
            photos = listOf(designCatPhoto(detail = true)),
            favorite = favorite,
            onBack = {},
            onToggleFavorite = { favorite = !favorite },
            onAuthor = {},
            onContact = {},
        )
    }
}

@Preview(name = "Неактуальная анкета", widthDp = 376, heightDp = 803)
@Composable
private fun UnavailableListingScreenPreview() {
    ScreenPreview {
        ListingScreen(
            listing = ScreenPreviewData.listing.copy(available = false),
            author = ScreenPreviewData.profile,
            photos = emptyList(),
            favorite = true,
            onBack = {},
            onToggleFavorite = {},
            onAuthor = {},
            onContact = {},
        )
    }
}

@Preview(name = "Анкета без контактов автора", widthDp = 376, heightDp = 803)
@Composable
private fun ListingWithoutContactsPreview() {
    ScreenPreview {
        ListingScreen(
            listing = ScreenPreviewData.listing,
            author = ScreenPreviewData.profile.copy(telegram = "", whatsapp = "", phone = "", website = ""),
            photos = listOf(designCatPhoto(detail = true)),
            favorite = false,
            onBack = {},
            onToggleFavorite = {},
            onAuthor = {},
            onContact = {},
        )
    }
}
