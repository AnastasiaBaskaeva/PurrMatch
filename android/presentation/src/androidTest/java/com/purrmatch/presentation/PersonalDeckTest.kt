package com.purrmatch.presentation

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.presentation.navigation.PurrMatchApp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalDeckTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownAndDeletedCardsAreExcludedAndOnlyCompletedSwipeRecordsHistory() {
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("anna") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
        compose.onNodeWithText("Соня, 4 года").assertDoesNotExist()
        compose.onNodeWithText("Барсик, 2 года").performTouchInput {
            swipe(Offset(width * .5f, height * .5f), Offset(width * .55f, height * .5f), 300)
        }
        compose.onNodeWithText("Барсик, 2 года").performClick()
        pressBack()
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertTrue(matches.data.value.selection("anna").seen.isEmpty()) }
        compose.onNodeWithText("Барсик, 2 года").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitUntil(5_000) { matches.data.value.selection("anna").seen == listOf("barsik") }
        compose.onNodeWithText("Новых анкет пока нет").assertIsDisplayed()
        compose.onNodeWithText("Изменить фильтры").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("barsik" in matches.data.value.selection("anna").favorites)
            runBlocking { sessions.signOut(); sessions.signIn("viewer") }
        }
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertTrue(matches.data.value.selection("viewer").seen.isEmpty()) }
    }

    @Test fun skippedCardStaysExcludedAfterFiltersAndDiskReload() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "personal-deck-test")
        directory.deleteRecursively()
        directory.mkdirs()
        try {
            val file = File(directory, "matches")
            val repository = mutableStateOf(MockMatchRepository(file))
            val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
            compose.setContent { key(repository.value) { PurrMatchApp(sessions, repository.value) } }
            compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeLeft(durationMillis = 400) }
            compose.waitUntil(5_000) { repository.value.data.value.selection("viewer").seen == listOf("mia") }
            compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
            compose.runOnIdle {
                runBlocking { repository.value.applyFilters("viewer", SearchFilters(breed = "Сибирская")).getOrThrow() }
            }
            compose.onNodeWithText("Новых анкет пока нет").assertIsDisplayed()
            compose.onNodeWithText("Изменить фильтры").performClick()
            compose.onNodeWithText("Сбросить").performClick()
            compose.onNodeWithText("Показать анкеты").performClick()
            compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(repository.value.data.value.selection("viewer").favorites.isEmpty())
                repository.value = MockMatchRepository(file, initialData = MatchData(emptyList(), emptyList()))
            }
            compose.waitUntil(5_000) { repository.value.data.value.selection("viewer").seen == listOf("mia") }
            compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
            compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
        } finally { directory.deleteRecursively() }
    }
}
