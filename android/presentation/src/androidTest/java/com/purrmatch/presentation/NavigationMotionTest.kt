package com.purrmatch.presentation

import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.presentation.navigation.Favorites
import com.purrmatch.presentation.navigation.PurrMatchApp
import kotlinx.coroutines.runBlocking

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun screensSlideForwardAndBackToTheCurrentCard() {
        compose.setContent { SampleApp(Screen.Home) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Фильтры").performClick()
        compose.mainClock.advanceTimeBy(100)
        val enteringX = compose.onNodeWithText("Любая порода").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settledX = compose.onNodeWithText("Любая порода").fetchSemanticsNode().boundsInRoot.left
        assertTrue("The next screen enters from the end edge", enteringX > settledX)

        compose.mainClock.autoAdvance = false
        pressBack()
        compose.mainClock.advanceTimeBy(100)
        val exitingX = compose.onNodeWithText("Любая порода").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Back reverses the screen transition", exitingX > settledX)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Любая порода").assertDoesNotExist()
    }

    @Test fun registrationBackRestoresTheLoginDraft() {
        compose.setContent { SampleApp(Screen.Login) }
        compose.onNodeWithContentDescription("Логин").performTextInput("motion_user")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Повторите пароль", useUnmergedTree = true).assertExists()
        pressBack()
        compose.onNodeWithContentDescription("Логин").assertTextContains("motion_user")
        compose.onNodeWithText("Войти", substring = false).assertIsDisplayed()
    }

    @Test fun tabChangeCrossfadesWithoutSlidingAndKeepsTheCurrentCard() {
        val matches = MockMatchRepository()
        val sessions = LocalSessionRepository().also { runBlocking { it.signIn("viewer") } }
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = Favorites) }
        compose.onNodeWithText("Главная", substring = false).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.mainClock.advanceTimeBy(100)
        val enteringX = compose.onNodeWithText("Пока пусто").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settledX = compose.onNodeWithText("Пока пусто").fetchSemanticsNode().boundsInRoot.left
        assertEquals("Tab content stays in place during the crossfade", settledX, enteringX, 1f)
        compose.onNodeWithText("Главная", substring = false).performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun predictiveBackCanCancelThenCompleteWithoutPoppingTwice() {
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SampleApp(Screen.Home)
        }
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(.5f))
        }
        compose.onNodeWithText("Мия, 2 года").assertExists()
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("Профиль автора и контакты").assertIsDisplayed()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(.7f))
            dispatcher.onBackPressed()
        }
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Профиль автора и контакты").assertDoesNotExist()
    }

    @Test fun dismissingConfirmationKeepsSettingsAndItsBackDestination() {
        compose.setContent { SampleApp(Screen.Settings) }
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.onNodeWithText("Выйти", substring = false).assertIsDisplayed()
        // Back targets the focused Dialog window, not Espresso's underlying Activity root.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Выйти", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Сохранить изменения").assertIsDisplayed()
        pressBack()
        compose.onNodeWithContentDescription("Настройки").assertIsDisplayed()
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 100f, progress, BackEventCompat.EDGE_LEFT)
}
