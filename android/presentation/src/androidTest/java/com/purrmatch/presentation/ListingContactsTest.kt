package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.sampleMatchData
import com.purrmatch.presentation.listing.ListingScreen
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ListingContactsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun listingShowsOnlyFilledAuthorContactsAndUpdatesWhenTheyAreRemoved() {
        val author = mutableStateOf<Profile?>(null)
        val listing = sampleMatchData().listings.first()
        val clicked = mutableListOf<String>()
        compose.setContent {
            ScreenPreview {
                ListingScreen(
                    listing = listing,
                    author = author.value,
                    photos = emptyList(),
                    favorite = false,
                    onBack = {},
                    onToggleFavorite = {},
                    onAuthor = {},
                    onContact = clicked::add,
                )
            }
        }
        assertNoContacts()
        (15 downTo 0).forEach { mask ->
            val telegram = mask and 1 != 0
            val whatsapp = mask and 2 != 0
            val phone = mask and 4 != 0
            val website = mask and 8 != 0
            val blank = if (mask % 2 == 0) " \t " else ""
            compose.runOnIdle {
                author.value = Profile(
                    name = "Анна",
                    telegram = if (telegram) "@anna" else blank,
                    whatsapp = if (whatsapp) "+79991234567" else blank,
                    phone = if (phone) "+79997654321" else blank,
                    website = if (website) "example.com" else blank,
                )
                clicked.clear()
            }
            val contacts = listOf(
                Triple("Telegram", "telegram", telegram),
                Triple("WhatsApp", "whatsapp", whatsapp),
                Triple("Телефон", "phone", phone),
                Triple("Сайт", "website", website),
            )
            contacts.forEach { (label, _, visible) ->
                val node = compose.onNodeWithText(label, substring = false)
                if (visible) node.performScrollTo().assertIsDisplayed().performClick() else node.assertDoesNotExist()
            }
            compose.runOnIdle { assertEquals(contacts.filter { it.third }.map { it.second }, clicked) }
            if (mask == 0) assertNoContacts()
            else compose.onNodeWithText("Связаться").assertExists()
            if (mask == 15 || mask == 0) {
                compose.onNode(hasText("Анна", substring = true) and hasClickAction()).performScrollTo()
                saveScreenshot("contacts-$mask")
            }
        }
        compose.runOnIdle { author.value = Profile() }
        assertNoContacts()
    }

    private fun assertNoContacts() {
        listOf("Telegram", "WhatsApp", "Телефон", "Сайт", "Связаться").forEach {
            compose.onNodeWithText(it, substring = false).assertDoesNotExist()
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(output ?: instrumentation.targetContext.getExternalFilesDir(null)!!.path, "listing-contacts")
        directory.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
