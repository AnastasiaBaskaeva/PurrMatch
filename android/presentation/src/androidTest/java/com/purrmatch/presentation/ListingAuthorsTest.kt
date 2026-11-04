package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.Profile
import com.purrmatch.domain.match.RequestFailedException
import com.purrmatch.presentation.navigation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Uses the repository and real destinations so callbacks cannot hide an ID mix-up. */
class ListingAuthorsTest {
    @get:Rule val compose = createComposeRule()

    /** Anna and Olga get different phones, so a mix-up of authors shows in the contacted profile. */
    private fun dependencies(fixtureName: String = "default"): MockRepositories {
        val dependencies = MockRepositories(fixtureName, userId = "anna")
        runBlocking {
            for ((id, phone) in listOf("anna" to "+79991111111", "olga" to "+79992222222")) {
                val profile = dependencies.matches.getProfile(id).getOrThrow()
                dependencies.matches.upsertProfile(profile.copy(phone = phone)).getOrThrow()
            }
        }
        return dependencies
    }

    @Test fun differentListingsUseTheirOwnAuthorAndContacts() {
        val dependencies = dependencies()
        val matches = dependencies.matches
        runBlocking { matches.toggleFavorite("anna", "barsik").getOrThrow() }
        var contacted: Pair<String, Profile>? = null
        compose.setContent {
            PurrMatchApp(dependencies.sessions, matches, initialDestination = Favorites,
                onContact = { type, author -> contacted = type to author })
        }
        compose.waitForText("Барсик, 2 года")
        for ((name, id) in listOf("Мия, 2 года" to "anna", "Барсик, 2 года" to "olga")) {
            val author = runBlocking { matches.getProfile(id).getOrThrow() }
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.waitForText("Телефон")
            compose.onNodeWithText("Телефон", substring = false).performScrollTo().performClick()
            compose.runOnIdle { assertEquals("phone" to author, contacted) }
            compose.onNodeWithText("Профиль автора и контакты").performClick()
            compose.onNodeWithText(author.name, substring = false).assertIsDisplayed()
            compose.onNodeWithText("Telegram", substring = false).performClick()
            compose.runOnIdle { assertEquals("telegram" to author, contacted) }
            pressBack()
            compose.onNodeWithText(name.substringBefore(',')).assertExists()
            pressBack()
            compose.onNodeWithText(name).assertExists()
        }
    }

    @Test fun editedContactsReachOpenListingAndAuthorProfile() {
        val dependencies = dependencies()
        val matches = dependencies.matches
        var contacted: Profile? = null
        compose.setContent {
            PurrMatchApp(dependencies.sessions, matches, initialDestination = ListingDetails("barsik"),
                onContact = { _, author -> contacted = author })
        }
        compose.waitForText("Телефон")
        compose.onNodeWithText("Барсик", substring = false).assertExists()
        val updated = runBlocking { matches.getProfile("olga").getOrThrow() }.copy(name = "Ольга Новая",
            phone = "+79993333333", telegram = "@new_olga", website = "new.example.com")
        runBlocking { matches.upsertProfile(updated).getOrThrow() }
        compose.onNodeWithText("Телефон", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(updated, contacted) }
        compose.onNodeWithText("Ольга Новая · открыть профиль").assertExists()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Ольга Новая", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Сайт", substring = false).performClick()
        compose.runOnIdle { assertEquals(updated, contacted) }
        val editedAgain = updated.copy(telegram = "@latest_olga")
        runBlocking { matches.upsertProfile(editedAgain).getOrThrow() }
        compose.onNodeWithText("Telegram", substring = false).performClick()
        compose.runOnIdle { assertEquals(editedAgain, contacted) }
    }

    @Test fun publicListUsesActualBreedAndPriceAndRemovesDeletedListings() {
        val dependencies = dependencies("catalog")
        val matches = dependencies.matches
        compose.setContent { PurrMatchApp(dependencies.sessions, matches, initialDestination = AuthorProfile("anna")) }
        compose.waitForText("Действующие анкеты · 2")
        compose.onNodeWithText("Соня", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Барсик", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Метис · 5 000 ₽").performScrollTo().assertIsDisplayed()
        runBlocking { matches.delete("anna", "peach").getOrThrow() }
        compose.onNodeWithText("Действующие анкеты · 1").assertExists()
        compose.onNodeWithText("Персик", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Мия", substring = false).assertExists()
    }

    @Test fun missingPublicAuthorShowsExplicitStateAndBackWorks() {
        val dependencies = MockRepositories(newUser = "reader")
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches, initialDestination = AuthorProfile("missing")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Автор не найден").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("Анна", substring = false).assertDoesNotExist()
        pressBack()
        compose.waitForText("Мия, 2 года")
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun listingWithMissingAuthorShowsExplicitStateInsteadOfEmptyContacts() {
        val dependencies = MockRepositories(newUser = "reader")
        // The mock server cannot hold a listing without its author, so the author's request fails.
        val matches = object : MatchRepository by dependencies.matches {
            override suspend fun getProfile(id: String): Result<Profile> =
                if (id == "olga") Result.failure(RequestFailedException(404, "PROFILE_NOT_FOUND", "Profile not found"))
                else dependencies.matches.getProfile(id)
        }
        compose.setContent { PurrMatchApp(dependencies.sessions, matches, initialDestination = ListingDetails("barsik")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Автор не найден").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("Телефон", substring = false).assertDoesNotExist()
        pressBack()
        compose.waitForText("Мия, 2 года")
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }
}
