package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.presentation.navigation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class NavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun specificListingOpensItsAuthorAndBackRestoresBothRoutes() {
        val matches = MockMatchRepository().also { runBlocking { it.toggleFavorite("anna", "barsik") } }
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        val restoration = StateRestorationTester(compose)
        var contactAuthor: String? = null
        restoration.setContent {
            PurrMatchApp(sessions, matches, initialDestination = Favorites, onContact = { _, author -> contactAuthor = author.id })
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Барсик, 2 года"))
        compose.onNodeWithText("Барсик, 2 года").performClick()
        compose.onNodeWithText("Барсик", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Ольга", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Мия", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Telegram", substring = false).performClick()
        compose.runOnIdle { assertEquals("olga", contactAuthor) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Ольга", substring = false).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Барсик", substring = false).assertIsDisplayed()
        pressBack()
        compose.onAllNodesWithText("Избранное", substring = false).assertCountEquals(2)
        compose.onNodeWithText("Барсик, 2 года").assertExists()
    }

    @Test fun detailsAndTabSwitchPreserveCurrentCardAndFiltersBackCancelsDraft() {
        compose.setContent { SampleApp(Screen.Home) }
        compose.onNodeWithText("Мия, 2 года").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").performClick()
        compose.onNodeWithText("Сибирская").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Главная", substring = false).performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun missingListingHasExplicitStateInsteadOfFirstCat() {
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = ListingDetails("missing")) }
        compose.onAllNodesWithText("Анкета не найдена").assertCountEquals(2)
        compose.onNodeWithText("Мия", substring = false).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun logoutClearsProtectedStackAndNextUserDoesNotSeeFavorites() {
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Профиль", substring = false).performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.onNodeWithText("Выйти", substring = false).performClick()
        compose.onNodeWithText("Войти", substring = false).assertIsDisplayed()
        // A new session gets new tab stacks and entry-scoped ViewModels.
        compose.onNodeWithContentDescription("Логин").performTextInput("anna")
        compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextInput("password")
        compose.onNodeWithText("Войти", substring = false).performClick()
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Пока пусто").assertExists()
    }

    @Test fun reopeningListingKeepsIntermediateAuthorInBackHistory() {
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        compose.setContent { PurrMatchApp(sessions, MockMatchRepository()) }
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Мия", substring = false).performClick()
        pressBack()
        compose.onNodeWithText("Анна", substring = false).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Мия", substring = false).assertIsDisplayed()
    }
}
