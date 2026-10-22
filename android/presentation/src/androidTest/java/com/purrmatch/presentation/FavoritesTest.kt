package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
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
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = Favorites) }
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
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("reader") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
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
            assertEquals(listOf("mia"), matches.data.value.selection("reader").seen)
            assertTrue(matches.data.value.selection("reader").favorites.isEmpty())
            assertEquals(listOf("mia", "peach", "sonya"), matches.data.value.selection("anna").favorites)
        }
    }

    @Test fun deletedOtherAuthorsFavoriteSurvivesReloadAndCanBeRemovedWithoutClearingSeen() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "favorites-${System.nanoTime()}").apply { mkdirs() }
        try {
            val file = File(directory, "matches")
            runBlocking {
                val original = MockMatchRepository(file)
                original.ensureProfile("owner", "owner").getOrThrow()
                original.publish("owner", Listing("luna", "Луна", birthday = LocalDate.now().minusYears(1))).getOrThrow()
                original.swipe("reader", "luna", true).getOrThrow()
                original.delete("owner", "luna").getOrThrow()
            }
            val matches = MockMatchRepository(file).also {
                runBlocking { it.ensureProfile("reader", "reader").getOrThrow() }
            }
            val sessions = LocalSessionRepository().also { runBlocking { it.signIn("reader") } }
            compose.setContent { PurrMatchApp(sessions, matches, initialDestination = Favorites) }
            compose.onNodeWithText("Луна, 1 год").assertIsDisplayed()
            compose.onNodeWithText("Неактуально").assertIsDisplayed()
            screenshot("favorites-deleted-listing")
            compose.onNodeWithText("Луна, 1 год").performClick()
            compose.onNodeWithText("Неактуально").assertIsDisplayed()
            compose.onNodeWithText("Профиль автора и контакты").assertDoesNotExist()
            compose.onNodeWithText("Убрать из Избранного").performScrollTo().performClick()
            compose.onNodeWithText("Пока пусто").assertIsDisplayed()
            runBlocking {
                val restored = MockMatchRepository(file)
                restored.ensureProfile("reader", "reader").getOrThrow()
                assertTrue(restored.data.value.selection("reader").favorites.isEmpty())
                assertEquals(listOf("luna"), restored.data.value.selection("reader").seen)
                assertTrue(restored.data.value.selection("owner").favorites.isEmpty())
            }
        } finally { directory.deleteRecursively() }
    }

    private fun screenshot(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(output, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
