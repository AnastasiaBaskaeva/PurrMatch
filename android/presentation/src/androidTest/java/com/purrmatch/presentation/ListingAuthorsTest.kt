package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.match.sampleMatchData
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.Profile
import com.purrmatch.presentation.navigation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Uses the repository and real destinations so callbacks cannot hide an ID mix-up. */
class ListingAuthorsTest {
    @get:Rule val compose = createComposeRule()

    private val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
    private val fixture = sampleMatchData().let { data ->
        data.copy(profiles = data.profiles.map { profile ->
            profile.copy(phone = if (profile.id == "anna") "+79991111111" else "+79992222222")
        })
    }

    @Test fun differentListingsUseTheirOwnAuthorAndContacts() {
        val matches = MockMatchRepository(initialData = fixture)
        runBlocking { matches.toggleFavorite("anna", "barsik").getOrThrow() }
        var contacted: Pair<String, Profile>? = null
        compose.setContent {
            PurrMatchApp(sessions, matches, initialDestination = Favorites,
                onContact = { type, author -> contacted = type to author })
        }
        for ((name, id) in listOf("Мия, 2 года" to "anna", "Барсик, 2 года" to "olga")) {
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("Телефон", substring = false).performScrollTo().performClick()
            compose.runOnIdle { assertEquals("phone" to fixture.profile(id), contacted) }
            compose.onNodeWithText("Профиль автора и контакты").performClick()
            compose.onNodeWithText(fixture.profile(id)!!.name, substring = false).assertIsDisplayed()
            compose.onNodeWithText("Telegram", substring = false).performClick()
            compose.runOnIdle { assertEquals("telegram" to fixture.profile(id), contacted) }
            pressBack()
            compose.onNodeWithText(name.substringBefore(',')).assertExists()
            pressBack()
            compose.onNodeWithText(name).assertExists()
        }
    }

    @Test fun editedContactsReachOpenListingAndAuthorProfile() {
        val matches = MockMatchRepository(initialData = fixture)
        var contacted: Profile? = null
        compose.setContent {
            PurrMatchApp(sessions, matches, initialDestination = ListingDetails("barsik"),
                onContact = { _, author -> contacted = author })
        }
        compose.onNodeWithText("Барсик", substring = false).assertExists()
        val updated = fixture.profile("olga")!!.copy(name = "Ольга Новая", phone = "+79993333333",
            telegram = "@new_olga", website = "new.example.com")
        runBlocking { matches.saveProfile(updated).getOrThrow() }
        compose.onNodeWithText("Телефон", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(updated, contacted) }
        compose.onNodeWithText("Ольга Новая · открыть профиль").assertExists()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Ольга Новая", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Сайт", substring = false).performClick()
        compose.runOnIdle { assertEquals(updated, contacted) }
        val editedAgain = updated.copy(telegram = "@latest_olga")
        runBlocking { matches.saveProfile(editedAgain).getOrThrow() }
        compose.onNodeWithText("Telegram", substring = false).performClick()
        compose.runOnIdle { assertEquals(editedAgain, contacted) }
    }

    @Test fun publicListUsesActualBreedAndPriceAndRemovesDeletedListings() {
        val data = fixture.copy(listings = fixture.listings.map {
            if (it.id == "peach") it.copy(breed = "Сибирская", price = 12000L) else it
        })
        val matches = MockMatchRepository(initialData = data)
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = AuthorProfile("anna")) }
        compose.onNodeWithText("Действующие анкеты · 2").assertExists()
        compose.onNodeWithText("Соня", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Барсик", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Сибирская · 12 000 ₽").performScrollTo().assertIsDisplayed()
        runBlocking { matches.delete("anna", "peach").getOrThrow() }
        compose.onNodeWithText("Действующие анкеты · 1").assertExists()
        compose.onNodeWithText("Персик", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Мия", substring = false).assertExists()
    }

    @Test fun missingPublicAuthorShowsExplicitStateAndBackWorks() {
        compose.setContent { PurrMatchApp(LocalSessionRepository().also { runBlocking { it.signIn("reader") } }, MockMatchRepository(), initialDestination = AuthorProfile("missing")) }
        compose.onAllNodesWithText("Автор не найден").assertCountEquals(2)
        compose.onNodeWithText("Анна", substring = false).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun listingWithMissingAuthorShowsExplicitStateInsteadOfEmptyContacts() {
        val data = fixture.copy(profiles = fixture.profiles.filterNot { it.id == "olga" })
        compose.setContent { PurrMatchApp(LocalSessionRepository().also { runBlocking { it.signIn("reader") } }, MockMatchRepository(initialData = data), initialDestination = ListingDetails("barsik")) }
        compose.onAllNodesWithText("Автор не найден").assertCountEquals(2)
        compose.onNodeWithText("Телефон", substring = false).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }
}
