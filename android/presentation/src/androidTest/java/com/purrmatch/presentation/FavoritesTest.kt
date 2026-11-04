package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.domain.match.Listing
import com.purrmatch.presentation.navigation.Favorites
import com.purrmatch.presentation.navigation.PurrMatchApp
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoritesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paidPeachUsesModelTextAndDetailsShareFavoritesWithList() {
        val dependencies = MockRepositories(userId = "anna")
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches, initialDestination = Favorites) }
        compose.waitForText("Метис · Москва")
        compose.onNodeWithText("Метис · Москва").assertIsDisplayed()
        compose.onNodeWithText("Метис · бесплатно").assertDoesNotExist()
        screenshot("favorites-model-text")
        compose.onNodeWithText("Персик, 7 мес.").performClick()
        compose.onNodeWithText("5 000 ₽").assertIsDisplayed()
        compose.onNodeWithContentDescription("Убрать из Избранного").performClick()
        compose.onNodeWithContentDescription("В Избранное").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Персик, 7 мес.").assertDoesNotExist()
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.onNodeWithContentDescription("Убрать из Избранного").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
        compose.onNodeWithContentDescription("Убрать Соня из Избранного").performClick()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
    }

    @Test fun rightSwipeSharesFavoriteWithDetailsAndListRemovalKeepsSeen() {
        val dependencies = MockRepositories(newUser = "reader")
        val matches = dependencies.matches
        val reader = dependencies.user!!
        compose.setContent { PurrMatchApp(dependencies.sessions, matches) }
        compose.waitForText("Мия, 2 года")
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.onNodeWithContentDescription("Убрать из Избранного").assertIsDisplayed()
        pressBack()
        compose.onNodeWithContentDescription("Убрать Мия из Избранного").performClick()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
        compose.onNodeWithText("Главная", substring = false).performClick()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("mia"), matches.data.value.selection(reader).seen)
            assertTrue(matches.data.value.selection(reader).favorites.isEmpty())
            assertEquals(listOf("mia", "peach", "sonya"), dependencies.backend.let { runBlocking { it.getSelection("anna") } }.body!!.favorites)
        }
    }

    @Test fun deletedOtherAuthorsFavoriteSurvivesReloadAndCanBeRemovedWithoutClearingSeen() {
        val dependencies = MockRepositories(newUser = "reader")
        val reader = dependencies.user!!
        runBlocking {
            val owner = dependencies.matches.register("owner", "password123", "owner").getOrThrow()
            dependencies.matches.publish(owner, Listing("owner-luna", "Луна", birthday = LocalDate.now().minusYears(1))).getOrThrow()
            dependencies.matches.swipe(reader, "owner-luna", true).getOrThrow()
            dependencies.matches.delete(owner, "owner-luna").getOrThrow()
        }
        // A fresh repository over the same server stands for an app restart: nothing is cached yet.
        val matches = MockMatchRepository(dependencies.backend)
        compose.setContent { PurrMatchApp(dependencies.sessions, matches, initialDestination = Favorites) }
        compose.waitForText("Луна, 1 год")
        compose.onNodeWithText("Луна, 1 год").assertIsDisplayed()
        compose.onNodeWithText("Неактуально").assertIsDisplayed()
        screenshot("favorites-deleted-listing")
        compose.onNodeWithText("Луна, 1 год").performClick()
        compose.onNodeWithText("Неактуально").assertIsDisplayed()
        compose.onNodeWithText("Профиль автора и контакты").assertDoesNotExist()
        compose.onNodeWithText("Убрать из Избранного").performScrollTo().performClick()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
        val selection = runBlocking { dependencies.backend.getSelection(reader) }.body!!
        assertTrue(selection.favorites.isEmpty())
        assertEquals(listOf("owner-luna"), selection.seen)
    }

    private fun screenshot(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(output, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
