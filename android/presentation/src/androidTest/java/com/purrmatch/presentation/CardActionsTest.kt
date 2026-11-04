package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.navigation.PurrMatchApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardActionsTest {
    @get:Rule val compose = createComposeRule()

    private fun launch(matches: MatchRepository) {
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    private fun action(like: Boolean) = compose.onNode(
        hasContentDescription(if (like) "Нравится" else "Не нравится") and hasClickAction(),
    )

    @Test fun accessibleButtonsLikeAndSkipPersistAndDoubleTapDoesNotSelectNextCard() {
        val matches = MockMatchRepository()
        launch(matches)
        action(true).assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        action(false).assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        action(true).performTouchInput { doubleClick(delayMillis = 80) }
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen.isNotEmpty() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("mia"), matches.data.value.selection("viewer").seen)
            assertEquals(listOf("mia"), matches.data.value.selection("viewer").favorites)
        }
        // A semantic click is the same activation exposed to TalkBack.
        action(false).performClick()
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen.size == 2 }
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("mia"), matches.data.value.selection("viewer").favorites) }
    }

    @Test fun secondPressAfterFastWriteAndCardReplacementIsStillIgnored() {
        val matches = MockMatchRepository()
        launch(matches)
        action(true)
        compose.mainClock.autoAdvance = false
        action(true).performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen == listOf("mia") }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        action(true).assertIsNotEnabled().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(listOf("mia"), matches.data.value.selection("viewer").seen)
            assertEquals(listOf("mia"), matches.data.value.selection("viewer").favorites)
        }
        action(false).assertIsEnabled().performClick()
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen == listOf("mia", "peach") }
    }

    @Test fun buttonsAndFiltersRemainReachableOnShortScreen() {
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        val height = mutableStateOf(803.dp)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(376.dp, height.value).testTag("home")) {
                    PurrMatchApp(sessions, matches)
                }
            }
        }
        action(true).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("card-actions-376x803")
        compose.runOnIdle { height.value = 600.dp }
        action(true).assertIsDisplayed().assertIsEnabled()
        action(false).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Фильтры").assertIsDisplayed()
        compose.onNodeWithTag("home").performTouchInput {
            swipe(Offset(180f, 310f), Offset(180f, 100f), 300)
        }
        compose.onNodeWithText("Ласковая", useUnmergedTree = true).assertIsDisplayed()
        capture("card-actions-376x600-scrolled")
        action(false).performClick()
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen == listOf("mia") }
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val output = File(directory, "$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use {
            compose.onNodeWithTag("home").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun buttonWaitsForExitAndWriteAndLocksBothDirections() {
        val backing = MockMatchRepository()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                gate.await()
                return backing.swipe(userId, listingId, like)
            }
        }
        launch(matches)
        action(true)
        compose.mainClock.autoAdvance = false
        action(true).performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertEquals(0, writes)
            assertTrue(backing.data.value.selection("viewer").seen.isEmpty())
        }
        action(true).assertIsNotEnabled()
        action(false).assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("Нравится").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Не нравится").assertCountEquals(1)
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle { assertEquals(1, writes) }
        action(false).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, writes); gate.complete(Unit) }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
    }

    @Test fun shortAndCancelledGesturesLeaveHistoryUntouchedAndFullSwipesUseSameSelection() {
        val matches = MockMatchRepository()
        launch(matches)
        val card = compose.onNodeWithText("Мия, 2 года")
        card.performTouchInput { swipe(center, center + Offset(width * .1f, 0f), 300) }
        compose.waitForIdle()
        card.assertIsDisplayed()
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(width * .4f, 0f), 300)
            cancel()
        }
        compose.waitForIdle()
        card.assertIsDisplayed()
        compose.runOnIdle { assertTrue(matches.data.value.selection("viewer").seen.isEmpty()) }
        card.performTouchInput { swipeRight(durationMillis = 300) }
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen == listOf("mia") }
        compose.onNodeWithText("Персик, 7 мес.").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitUntil(5_000) { matches.data.value.selection("viewer").seen.size == 2 }
        compose.runOnIdle { assertEquals(listOf("mia"), matches.data.value.selection("viewer").favorites) }
    }

    @Test fun pendingButtonRemainsLockedAcrossFiltersAndTabReentry() {
        val backing = MockMatchRepository()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                gate.await()
                return backing.swipe(userId, listingId, like)
            }
        }
        launch(matches)
        action(true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Фильтры").performClick()
        pressBack()
        action(true).assertIsNotEnabled()
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Главная", substring = false).performClick()
        action(false).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, writes); gate.complete(Unit) }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        action(false).assertIsEnabled()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
    }

    @Test fun filteredReplacementRejectedDuringWriteReturnsAndCanBeChosenLater() {
        val backing = MockMatchRepository()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                gate.await()
                return backing.swipe(userId, listingId, like)
            }
        }
        launch(matches)
        action(true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").performClick()
        compose.onNodeWithText("Метис").performClick()
        compose.onNodeWithText("Показать анкеты").performClick()
        action(false).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        action(false).assertIsEnabled()
        compose.runOnIdle { assertEquals(1, writes); gate.complete(Unit) }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        action(false).performClick()
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia", "peach") }
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
    }

    @Test fun failureAfterReturningHomeRestoresButtonsAndRetry() {
        val backing = MockMatchRepository()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                if (writes == 1) { gate.await(); return Result.failure(IllegalStateException("Storage unavailable")) }
                return backing.swipe(userId, listingId, like)
            }
        }
        launch(matches)
        action(true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Фильтры").performClick()
        pressBack()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Понятно").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        action(true).assertIsEnabled().performClick()
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        compose.runOnIdle { assertEquals(2, writes) }
    }
}
