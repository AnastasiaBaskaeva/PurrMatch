package com.purrmatch.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.purrmatch.presentation.components.Theme
import com.purrmatch.presentation.favorites.FavoritesScreen
import com.purrmatch.presentation.listing.Listing
import com.purrmatch.presentation.profile.Profile
import com.purrmatch.presentation.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListScreensTest {
    @get:Rule val compose = createComposeRule()

    private val listings = List(200) { index ->
        Listing(id = "cat-$index", name = "Кошка $index", age = "2 года", city = "Москва")
    }
    private val profile = Profile(name = "Анна", city = "Москва", about = "Помогаю кошкам", phone = "+7 900 123-45-67")

    @Test fun favoritesComposeOnlyNearbyCardsAndKeepCallbacksAfterRemoval() {
        var current by mutableStateOf(listings)
        var opened: String? = null
        var removed: String? = null
        compose.setContent {
            Theme {
                FavoritesScreen(current, { opened = it.id }, {
                    removed = it.id
                    current = current.filterNot { listing -> listing.id == it.id }
                }, {})
            }
        }
        assertFewCardsComposed()
        compose.onNodeWithText("Кошка 199, 2 года").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Кошка 199, 2 года"))
        compose.onNodeWithText("Кошка 199, 2 года").performClick()
        compose.runOnIdle { assertEquals("cat-199", opened) }
        compose.onNodeWithContentDescription("Убрать Кошка 199 из Избранного").performClick()
        compose.runOnIdle { assertEquals("cat-199", removed) }
        compose.onNodeWithText("Кошка 199, 2 года").assertDoesNotExist()
        compose.onNodeWithText("Кошка 198, 2 года").performClick()
        compose.runOnIdle { assertEquals("cat-198", opened) }
        assertFewCardsComposed()
    }

    @Test fun favoritesRestoreScrollAndKeepAnchorWhenEarlierCardsAreRemoved() {
        var current by mutableStateOf(listings)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Theme { FavoritesScreen(current, {}, {}, {}) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(101)
        compose.onNodeWithText("Кошка 100, 2 года").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Кошка 100, 2 года").assertIsDisplayed()
        compose.runOnIdle { current = current.drop(20) }
        compose.onNodeWithText("Кошка 100, 2 года").assertIsDisplayed()
        assertFewCardsComposed()
    }

    @Test fun ownProfileVirtualizesCardsAndKeepsDeleteAndAddActions() {
        var opened: String? = null
        var deleted: String? = null
        var added = false
        compose.setContent {
            Theme {
                ProfileScreen(profile, listings, false, {}, {}, { opened = it.id },
                    { deleted = it.id }, { added = true }, {})
            }
        }
        assertFewCardsComposed()
        compose.onNodeWithText("Кошка 199").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Кошка 199"))
        compose.onNodeWithText("Кошка 199").performClick()
        compose.onNodeWithContentDescription("Удалить анкету Кошка 199").performClick()
        compose.runOnIdle {
            assertEquals("cat-199", opened)
            assertEquals("cat-199", deleted)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Добавить анкету"))
        compose.onNodeWithText("Добавить анкету").performClick()
        compose.runOnIdle { assertTrue(added) }
        assertFewCardsComposed()
    }

    @Test fun publicProfileVirtualizesCardsAndKeepsContactsAndBack() {
        var back = false
        var contact: String? = null
        var opened: String? = null
        compose.setContent {
            Theme {
                ProfileScreen(profile, listings, true, { back = true }, {}, { opened = it.id },
                    {}, {}, { contact = it })
            }
        }
        compose.onNodeWithText("Telegram").performClick()
        compose.runOnIdle { assertEquals("telegram", contact) }
        assertFewCardsComposed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Кошка 199"))
        compose.onNodeWithText("Кошка 199").performClick()
        compose.runOnIdle { assertEquals("cat-199", opened) }
        compose.onNodeWithContentDescription("Удалить анкету Кошка 199").assertDoesNotExist()
        compose.onNodeWithText("Добавить анкету").assertDoesNotExist()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.runOnIdle { assertTrue(back) }
        assertFewCardsComposed()
    }

    private fun assertFewCardsComposed() {
        // Offscreen eager Column children are still in semantics; a virtualized list keeps a small window.
        val count = compose.onAllNodes(hasText("Кошка ", substring = true)).fetchSemanticsNodes().size
        assertTrue("Expected a small composition window, found $count cards", count in 1..20)
    }
}
