package com.purrmatch.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginButtonTextIsCentered() {
        compose.setContent { SampleApp(Screen.Login) }
        val button = compose.onNode(hasText("Войти") and hasClickAction()).assertIsDisplayed().fetchSemanticsNode()
        val text = compose.onNodeWithText(
            "Войти",
            useUnmergedTree = true
        ).fetchSemanticsNode()

        assertEquals(
            button.boundsInRoot.center.x,
            text.boundsInRoot.center.x,
            1f
        )
        assertEquals(
            button.boundsInRoot.center.y,
            text.boundsInRoot.center.y,
            1f
        )
    }

    @Test
    fun registrationValidatesAndOpensProfileThenHome() {
        compose.setContent { SampleApp(Screen.Login) }
        compose.onNodeWithText("Создать аккаунт").performClick()
        compose.onNodeWithText("Зарегистрироваться").performClick()
        compose.onNodeWithText("Укажите имя").assertIsDisplayed()
        compose.onNodeWithContentDescription("Имя").performTextInput("Мария")
        compose.onNodeWithContentDescription("Логин").performTextInput("maria_cats")
        compose.onNodeWithContentDescription(
            "Пароль",
            useUnmergedTree = true
        ).performTextInput("password123")
        compose.onNodeWithContentDescription(
            "Повторите пароль",
            useUnmergedTree = true
        ).performTextInput("password123")
        compose.onNodeWithText("Зарегистрироваться").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Расскажите о себе").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Расскажите о себе").assertIsDisplayed()
        compose.onNodeWithText("Заполнить позже").performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test
    fun deleteKeepsUnavailableListingInFavorites() {
        compose.setContent { SampleApp(initialScreen = Screen.Profile) }
        compose.onNodeWithContentDescription("Удалить анкету Мия").performClick()
        compose.onNodeWithText(
            "Удалить",
            substring = false
        ).performClick()
        compose.onNodeWithText("1 действующие").assertIsDisplayed()
        compose.onNodeWithText(
            "Избранное",
            substring = false
        ).performClick()
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.onNodeWithText("Неактуально").assertIsDisplayed()
        compose.onNodeWithText("Убрать из Избранного").performScrollTo().performClick()
        compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
    }

    @Test
    fun filtersApplyAndReset() {
        compose.setContent { SampleApp(initialScreen = Screen.Home) }
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").performClick()
        compose.onNodeWithText("Сибирская").performClick()
        compose.onNodeWithText("Показать анкеты").performClick()
        compose.onNodeWithText("Новых анкет пока нет").assertIsDisplayed()
        compose.onNodeWithText("Изменить фильтры").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.onNodeWithText("Показать анкеты").performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test
    fun stateRestoresFavoritesAndProfileForm() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SampleApp(initialScreen = Screen.Favorites) }
        compose.onNodeWithContentDescription("Убрать Мия из Избранного").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Мия, 2 года").assertDoesNotExist()
        compose.onNodeWithText(
            "Профиль",
            substring = false
        ).performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithContentDescription("Имя").performTextReplacement("Мария")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Имя").assertTextContains("Мария")
        compose.onNodeWithText("Сохранить изменения").performClick()
        compose.onNodeWithText("Мария").assertIsDisplayed()
    }

    @Test
    fun publishingAddsListingAndRestoresIt() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SampleApp(initialScreen = Screen.Profile) }
        compose.onNodeWithText("Добавить анкету").performScrollTo().performClick()
        compose.onNodeWithText("Опубликовать анкету").assertIsDisplayed()
        compose.onNodeWithText("Платно", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Опубликовать анкету").assertIsDisplayed()
        compose.onNodeWithText("Бесплатно", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Опубликовать анкету").assertIsDisplayed()
        compose.onNodeWithContentDescription("Имя кошки").performScrollTo().performTextInput("Луна")
        compose.onNodeWithText("Выберите породу").performClick()
        compose.onNodeWithText("Метис").performClick()
        compose.onNodeWithText("Опубликовать анкету").performClick()
        compose.onNodeWithText("3 действующие").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Луна").performScrollTo().performClick()
        compose.onNodeWithText("Луна").assertIsDisplayed()
        compose.onNodeWithText("Метис · кошка").assertIsDisplayed()
    }

    @Test
    fun renderEveryFigmaScreenAt376By803() {
        var screen by mutableStateOf(Screen.Login)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    1f,
                    1f
                )
            ) {
                Box(
                    Modifier
                        .requiredSize(
                            376.dp,
                            803.dp
                        )
                        .testTag("screen")
                ) {
                    key(screen) { SampleApp(initialScreen = screen) }
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(
            output ?: instrumentation.targetContext.getExternalFilesDir(null)!!.path,
            "screen-screenshots"
        )
        directory.mkdirs()
        Screen.entries.forEach { current ->
            compose.runOnIdle { screen = current }
            compose.waitForIdle()
            val node = if (current == Screen.Delete || current == Screen.Logout) compose.onNode(isDialog())
            else compose.onNodeWithTag("screen")
            val bitmap = node.captureToImage().asAndroidBitmap()
            assertEquals(
                "${current.route} keeps the constrained host width",
                376,
                bitmap.width
            )
            File(
                directory,
                "${current.route}.png"
            ).outputStream().use {
                assertTrue(
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        it
                    )
                )
            }
        }
    }
}
