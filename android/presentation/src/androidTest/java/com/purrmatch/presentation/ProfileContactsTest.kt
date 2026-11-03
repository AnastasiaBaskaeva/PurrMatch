package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.profile.Profile
import com.purrmatch.presentation.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileContactsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ownProfileShowsOnlyFilledContacts() = checkContacts(public = false)

    @Test
    fun publicProfileShowsOnlyFilledContacts() = checkContacts(public = true)

    private fun checkContacts(public: Boolean) {
        val profile = mutableStateOf(Profile(name = "Анна"))
        val clicked = mutableListOf<String>()
        compose.setContent {
            ScreenPreview {
                ProfileScreen(
                    profile = profile.value,
                    listings = emptyList(),
                    public = public,
                    onBack = {},
                    onSettings = {},
                    onOpen = {},
                    onDelete = {},
                    onNewListing = {},
                    onContact = clicked::add,
                )
            }
        }
        // Check every combination, including removing contacts from an already displayed profile.
        (0..7).forEach { mask ->
            val website = mask and 1 != 0
            val telegram = mask and 2 != 0
            val phone = mask and 4 != 0
            compose.runOnIdle {
                profile.value = profile.value.copy(
                    website = if (website) "example.com" else if (mask % 2 == 0) "" else " \t ",
                    telegram = if (telegram) "@anna" else if (mask % 2 == 0) " \n " else "",
                    phone = if (phone) "+7 999 123-45-67" else if (mask % 2 == 0) "" else "   ",
                )
                clicked.clear()
            }
            listOf("Сайт" to website, "Telegram" to telegram, (if (public) "Позвонить" else "+7 999 123-45-67") to phone)
                .forEach { (label, visible) ->
                    val node = compose.onNodeWithText(label, substring = false)
                    if (visible) node.assertIsDisplayed().performClick() else node.assertDoesNotExist()
                }
            compose.onAllNodes(hasText("", substring = false) and hasClickAction()).assertCountEquals(0)
            if (public && mask != 0) compose.onNodeWithText("Контакты").assertIsDisplayed()
            else compose.onNodeWithText("Контакты").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(
                    listOfNotNull("website".takeIf { website }, "telegram".takeIf { telegram }, "phone".takeIf { phone }),
                    clicked,
                )
            }
            if (mask == 0 || mask == 7) saveScreenshot("${if (public) "public" else "own"}-$mask")
        }
        compose.runOnIdle { profile.value = Profile(name = "Анна", website = "  ", telegram = "  ", phone = "  ") }
        compose.onNodeWithText("Сайт").assertDoesNotExist()
        compose.onNodeWithText("Telegram").assertDoesNotExist()
        compose.onNodeWithText("Позвонить").assertDoesNotExist()
        compose.onAllNodes(hasText("  ", substring = false) and hasClickAction()).assertCountEquals(0)
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(output ?: instrumentation.targetContext.getExternalFilesDir(null)!!.path, "profile-contacts")
        directory.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
