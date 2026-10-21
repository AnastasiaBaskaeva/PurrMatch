package com.purrmatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.components.SwipeCard
import com.purrmatch.presentation.components.Theme
import com.purrmatch.presentation.navigation.PurrMatchApp
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SwipeMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exitPrecedesCommitAndPendingCardCannotBeSwipedTwice() {
        var calls = 0
        var complete: ((Boolean) -> Unit)? = null
        val card = mutableStateOf("first")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Theme {
                SwipeCard({}, {}, Modifier.requiredSize(300.dp, 400.dp).testTag("swipe"),
                    cardKey = card.value, onSwipe = { _, result -> calls++; complete = result }) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        assertRedCardVisible(true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertEquals(0, calls) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(1, calls) }
        assertRedCardVisible(false)
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight(durationMillis = 300) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, calls) }
        assertRedCardVisible(false)
        compose.runOnIdle { complete!!(true) }
        compose.mainClock.advanceTimeBy(300)
        assertRedCardVisible(false)
        compose.runOnIdle { card.value = "second" }
        compose.mainClock.advanceTimeBy(80)
        val entering = centerColor()
        assertTrue(entering.red < .95f || entering.green > .05f || entering.blue > .05f)
        compose.mainClock.advanceTimeBy(300)
        assertRedCardVisible(true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight(durationMillis = 300) }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun shortAndCancelledDragsReturnAndFailedWriteCanBeRetried() {
        var calls = 0
        var complete: ((Boolean) -> Unit)? = null
        compose.setContent {
            Theme {
                SwipeCard({}, {}, Modifier.requiredSize(300.dp, 400.dp).testTag("swipe"),
                    onSwipe = { _, result -> calls++; complete = result }) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput {
            swipe(center, center + Offset(width * .1f, 0f), 300)
        }
        compose.waitForIdle()
        assertRedCardVisible(true)
        compose.onNodeWithTag("swipe").performTouchInput {
            down(center)
            moveTo(center + Offset(width * .4f, 0f), 300)
            cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, calls) }
        assertRedCardVisible(true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, calls); complete!!(false) }
        compose.waitForIdle()
        assertRedCardVisible(true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun staleCompletionDoesNotResetReplacementCard() {
        val card = mutableStateOf("first")
        val completions = mutableListOf<(Boolean) -> Unit>()
        compose.setContent {
            Theme {
                SwipeCard({}, {}, Modifier.requiredSize(300.dp, 400.dp).testTag("swipe"),
                    cardKey = card.value, onSwipe = { _, result -> completions += result }) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { card.value = "replacement" }
        compose.waitForIdle()
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, completions.size); completions.first()(false) }
        compose.waitForIdle()
        assertRedCardVisible(false)
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, completions.size) }
    }

    @Test fun actualHomeStoresLikeAndSkipOnlyAfterExitAndAdvancesDeck() {
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
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertTrue(backing.data.value.selection("viewer").seen.isEmpty())
            gate.complete(Unit)
        }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("mia"), backing.data.value.selection("viewer").favorites) }
        compose.onNodeWithText("Персик, 7 мес.").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen.size == 2 }
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("mia"), backing.data.value.selection("viewer").favorites) }
    }

    @Test fun successfulNoOpRestoresCardForAnotherAttempt() {
        val backing = MockMatchRepository()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                return Result.success(Unit)
            }
        }
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        repeat(2) {
            compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
            compose.waitForIdle()
            compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(2, writes) }
    }

    @Test fun actualStorageErrorRestoresCardAndRetrySucceeds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "swipe-motion-${System.nanoTime()}")
        val matches = MockMatchRepository(File(directory, "matches.bin"))
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        try {
            compose.setContent { PurrMatchApp(sessions, matches) }
            compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Понятно").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Понятно").performClick()
            compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(matches.data.value.selection("viewer").seen.isEmpty())
                assertTrue(directory.mkdirs())
            }
            compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
            compose.waitUntil(5_000) { matches.data.value.selection("viewer").favorites == listOf("mia") }
            compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun changedCardRejectedDuringEarlierWriteReturnsAndCanBeRetried() {
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
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").performClick()
        compose.onNodeWithText("Метис").performClick()
        compose.onNodeWithText("Показать анкеты").performClick()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.onNodeWithText("Персик, 7 мес.").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertTrue(backing.data.value.selection("viewer").seen.isEmpty())
            gate.complete(Unit)
        }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia") }
        compose.onNodeWithText("Персик, 7 мес.").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitUntil(5_000) { backing.data.value.selection("viewer").seen == listOf("mia", "peach") }
        compose.onNodeWithText("Барсик, 2 года").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, writes) }
    }

    @Test fun pendingSwipeStaysOffscreenAfterFiltersAndBack() = pendingSwipeAfterHomeDisposal(false)

    @Test fun pendingSwipeFailureAfterFiltersAndBackRestoresRetry() = pendingSwipeAfterHomeDisposal(true)

    private fun pendingSwipeAfterHomeDisposal(failFirstWrite: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "pending-swipe-${System.nanoTime()}")
        val backing = MockMatchRepository(if (failFirstWrite) File(directory, "matches.bin") else null)
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val matches = object : MatchRepository by backing {
            override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
                writes++
                gate.await()
                return backing.swipe(userId, listingId, like)
            }
        }
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        try {
            compose.setContent { PurrMatchApp(sessions, matches) }
            compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, writes) }
            compose.onNodeWithText("Фильтры").performClick()
            compose.onNodeWithText("Сбросить").assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("Мия, 2 года").assertIsNotDisplayed()
            compose.onRoot().performTouchInput {
                swipe(Offset(width * .8f, height * .35f), Offset(width * .2f, height * .35f), 300)
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(1, writes)
                assertTrue(backing.data.value.selection("viewer").seen.isEmpty())
                gate.complete(Unit)
            }
            if (failFirstWrite) {
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Понятно").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Понятно").performClick()
                compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
                compose.runOnIdle { assertTrue(directory.mkdirs()) }
                compose.onNodeWithText("Мия, 2 года").performTouchInput { swipeRight(durationMillis = 300) }
            }
            compose.waitUntil(5_000) { backing.data.value.selection("viewer").favorites == listOf("mia") }
            compose.onNodeWithText("Персик, 7 мес.").assertIsDisplayed()
            compose.runOnIdle { assertEquals(if (failFirstWrite) 2 else 1, writes) }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun centerColor(): Color {
        val image = compose.onNodeWithTag("swipe").captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2]
    }

    private fun assertRedCardVisible(visible: Boolean) {
        val color = centerColor()
        assertEquals(visible, color.red > .95f && color.green < .05f && color.blue < .05f)
    }
}
