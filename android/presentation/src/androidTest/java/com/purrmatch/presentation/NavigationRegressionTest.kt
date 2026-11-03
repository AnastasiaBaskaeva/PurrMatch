package com.purrmatch.presentation

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import com.purrmatch.presentation.navigation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun logoutKeepsTheRealUnsavedFormAndScrollThroughRestorationAndDismissal() {
        val matches = MockMatchRepository()
        val sessions = signedIn()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PurrMatchApp(
                sessions,
                matches,
                modifier = Modifier.requiredSize(
                    376.dp,
                    600.dp
                ),
                initialDestination = Settings
            )
        }
        compose.onNodeWithContentDescription("Имя").performTextReplacement("Несохранённое имя")
        closeSoftKeyboard()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo()
        val scroll = verticalScroll()
        assertTrue(scroll > 0f)
        compose.onNodeWithText("Выйти из аккаунта").performClick()
        compose.onNode(isDialog()).assertExists()
        // The form must remain the actual preceding entry, rather than a reconstructed background.
        compose.onNodeWithContentDescription("Имя").assertTextContains("Несохранённое имя")
        screenshot("logout-with-unsaved-settings")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(
            "Выйти",
            substring = false
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Имя").assertTextContains("Несохранённое имя")
        compose.onNodeWithText(
            "Отмена",
            substring = false
        ).performClick()
        assertEquals(
            scroll,
            verticalScroll(),
            1f
        )
        compose.onNodeWithText("Выйти из аккаунта").assertIsDisplayed()
        compose.onNodeWithContentDescription("Имя").performScrollTo().assertTextContains("Несохранённое имя")
        assertEquals(
            "Анна",
            matches.data.value.profile("anna")!!.name
        )
        pressBack()
        compose.onNodeWithContentDescription("Настройки").assertIsDisplayed()
    }

    @Test
    fun deleteRestoresTheSelectedListingAndCanRetryAfterStorageFailure() {
        val backing = MockMatchRepository()
        var attempts = 0
        val matches = object : MatchRepository by backing {
            override suspend fun delete(
                userId: String,
                listingId: String
            ): Result<Unit> {
                attempts++
                return if (attempts == 1) Result.failure(IllegalStateException("storage unavailable"))
                else backing.delete(
                    userId,
                    listingId
                )
            }
        }
        val sessions = signedIn()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PurrMatchApp(
                sessions,
                matches,
                initialDestination = Profile
            )
        }
        compose.onNodeWithContentDescription("Удалить анкету Персик").performScrollTo().performClick()
        screenshot("delete-selected-listing")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(
            "Удалить",
            substring = false
        ).performClick()
        compose.onNodeWithText(
            "Не удалось сохранить",
            substring = false
        ).assertIsDisplayed()
        assertTrue(backing.data.value.listing("peach")!!.available)
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText(
            "Удалить",
            substring = false
        ).assertIsDisplayed().performClick()
        compose.waitUntil { !backing.data.value.listing("peach")!!.available }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Удалить анкету Мия").assertExists()
        compose.onNodeWithContentDescription("Удалить анкету Персик").assertDoesNotExist()
        assertTrue(backing.data.value.listing("mia")!!.available)
        assertEquals(
            2,
            attempts
        )
    }

    @Test
    fun cancellingDeleteAfterRestorationKeepsTheProfileScroll() {
        val matches = MockMatchRepository()
        val sessions = signedIn()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PurrMatchApp(
                sessions,
                matches,
                modifier = Modifier.requiredSize(
                    376.dp,
                    500.dp
                ),
                initialDestination = Profile
            )
        }
        compose.onNodeWithText("Добавить анкету").performScrollTo()
        val scroll = verticalScroll()
        assertTrue(scroll > 0f)
        compose.onNodeWithContentDescription("Удалить анкету Персик").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(
            "Отмена",
            substring = false
        ).performClick()
        assertEquals(
            scroll,
            verticalScroll(),
            1f
        )
        compose.onNodeWithText("Добавить анкету").assertIsDisplayed()
        assertTrue(matches.data.value.listing("peach")!!.available)
    }

    @Test
    fun logoutCanRetryAndSigningInAgainDoesNotRestoreTheOldDraft() {
        val matches = MockMatchRepository()
        val backing = signedIn()
        var attempts = 0
        val sessions = object : SessionRepository by backing {
            override suspend fun signOut(): Result<Unit> {
                attempts++
                return if (attempts == 1) Result.failure(IllegalStateException("storage unavailable"))
                else backing.signOut()
            }
        }
        compose.setContent {
            PurrMatchApp(
                sessions,
                matches
            )
        }
        compose.onNodeWithText(
            "Профиль",
            substring = false
        ).performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithContentDescription("Имя").performTextReplacement("Старый черновик")
        closeSoftKeyboard()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.onNodeWithText(
            "Выйти",
            substring = false
        ).performClick()
        compose.onNodeWithText(
            "Не удалось сохранить",
            substring = false
        ).assertIsDisplayed()
        assertTrue(backing.session.value is SessionState.SignedIn)
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText(
            "Выйти",
            substring = false
        ).performClick()
        compose.onNodeWithText(
            "Войти",
            substring = false
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Логин").performTextInput("anna_cats")
        compose.onNodeWithContentDescription(
            "Пароль",
            useUnmergedTree = true
        ).performTextInput("purrmatch-demo")
        compose.onNodeWithText(
            "Войти",
            substring = false
        ).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(
                "Главная",
                substring = false
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(
            "Профиль",
            substring = false
        ).performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithContentDescription("Имя").assertTextContains("Анна")
        compose.onNodeWithText("Старый черновик").assertDoesNotExist()
        assertEquals(
            2,
            attempts
        )
    }

    @Test
    fun repeatedListingEntriesRestoreTheirOwnPhotoAndScrollAndBackHistory() {
        val sessions = signedIn()
        val matches = MockMatchRepository()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PurrMatchApp(
                sessions,
                matches
            )
        }
        compose.onNodeWithText("Мия, 2 года").performClick()
        val gallery = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        gallery.performTouchInput { swipeLeft() }
        val firstPhoto = horizontalScroll()
        assertTrue(firstPhoto > 0f)
        compose.onNodeWithText(
            "Связаться",
            substring = false
        ).performScrollTo()
        val firstScroll = verticalScroll()
        assertTrue(firstScroll > 0f)
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText(
            "Мия",
            substring = false
        ).performScrollTo().performClick()
        assertEquals(
            0f,
            horizontalScroll(),
            .01f
        )
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(
            0f,
            horizontalScroll(),
            .01f
        )
        pressBack()
        compose.onNodeWithText(
            "Анна",
            substring = false
        ).assertExists()
        pressBack()
        assertEquals(
            firstScroll,
            verticalScroll(),
            1f
        )
        assertEquals(
            firstPhoto,
            horizontalScroll(),
            .01f
        )
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test
    fun tabRootsReturnHomeAndProfileScrollSurvivesSwitchAndRestoration() {
        val sessions = signedIn()
        val matches = MockMatchRepository()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PurrMatchApp(
                sessions,
                matches
            )
        }
        compose.onNodeWithText(
            "Избранное",
            substring = false
        ).performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText(
            "Профиль",
            substring = false
        ).performClick()
        compose.onNodeWithText("Добавить анкету").performScrollTo()
        val scroll = verticalScroll()
        compose.onNodeWithText(
            "Главная",
            substring = false
        ).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(
            "Профиль",
            substring = false
        ).performClick()
        assertEquals(
            scroll,
            verticalScroll(),
            1f
        )
        compose.onNodeWithText("Добавить анкету").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    private fun signedIn() = LocalSessionRepository().also { runBlocking { it.signIn("anna").getOrThrow() } }

    private fun verticalScroll() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
    ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun horizontalScroll() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
    ).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: instrumentation.targetContext.getExternalFilesDir(null)!!.path
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(
                output,
                "$name.png"
            ).outputStream().use {
                assertTrue(
                    bitmap.compress(
                        android.graphics.Bitmap.CompressFormat.PNG,
                        100,
                        it
                    )
                )
            }
        } finally {
            bitmap.recycle()
        }
    }
}
