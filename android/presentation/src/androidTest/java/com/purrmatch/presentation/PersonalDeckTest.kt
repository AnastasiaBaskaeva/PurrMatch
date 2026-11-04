package com.purrmatch.presentation

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.presentation.navigation.PurrMatchApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalDeckTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownAndDeletedCardsAreExcludedAndOnlyCompletedSwipeRecordsHistory() {
        val dependencies = MockRepositories("catalog", userId = "olga")
        val matches = dependencies.matches
        val sessions = dependencies.sessions
        runBlocking {
            matches.publish("olga", Listing("olga-cat", "Олечка")).getOrThrow()
            matches.delete("anna", "peach").getOrThrow()
        }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.waitForText("Мия, 2 года")
        compose.onNodeWithText("Олечка").assertDoesNotExist()
        compose.onNodeWithText("Персик, 7 мес.").assertDoesNotExist()
        compose.onNodeWithText("Соня, 4 года").assertDoesNotExist()
        compose.onNodeWithText("Мия, 2 года").performTouchInput {
            swipe(Offset(width * .5f, height * .5f), Offset(width * .55f, height * .5f), 300)
        }
        compose.onNodeWithText("Мия, 2 года").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertTrue(matches.data.value.selection("olga").seen.isEmpty()) }
        compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitUntil(5_000) { matches.data.value.selection("olga").seen == listOf("mia") }
        compose.onNodeWithText("Новых анкет пока нет").assertIsDisplayed()
        compose.onNodeWithText("Изменить фильтры").assertIsDisplayed()
        compose.runOnIdle { assertTrue("mia" in matches.data.value.selection("olga").favorites) }
        val viewer = runBlocking {
            val id = matches.register("viewer", "password123", "viewer").getOrThrow()
            sessions.signOut()
            sessions.signIn(id)
            id
        }
        compose.waitForText("Мия, 2 года")
        compose.runOnIdle { assertTrue(matches.data.value.selection(viewer).seen.isEmpty()) }
    }

    @Test fun skippedCardStaysExcludedAfterFiltersAndRestart() {
        val dependencies = MockRepositories("catalog", newUser = "viewer")
        val sessions = dependencies.sessions
        val viewer = dependencies.user!!
        val repository = mutableStateOf(dependencies.matches)
        compose.setContent { key(repository.value) { PurrMatchApp(sessions, repository.value) } }
        compose.waitForText("Мия, 2 года")
        compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitUntil(5_000) { repository.value.data.value.selection(viewer).seen == listOf("mia") }
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle {
            runBlocking { repository.value.applyFilters(viewer, SearchFilters(breed = "Сибирская")).getOrThrow() }
        }
        compose.onNodeWithText("Новых анкет пока нет").assertIsDisplayed()
        compose.onNodeWithText("Изменить фильтры").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.onNodeWithText("Показать анкеты").performClick()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(repository.value.data.value.selection(viewer).favorites.isEmpty())
            // A restarted app has an empty cache and learns the history from the same server.
            repository.value = MockMatchRepository(dependencies.backend)
        }
        compose.waitUntil(5_000) { repository.value.data.value.selection(viewer).seen == listOf("mia") }
        compose.waitForText("Персик, 7 мес.")
        compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
    }
}
