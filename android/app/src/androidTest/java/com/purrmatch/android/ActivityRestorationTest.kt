package com.purrmatch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the real Hilt host and Activity recreation, not only Compose's saveable registry. */
class ActivityRestorationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun formsRoutesAndLogoutSurviveActivityRecreation() {
        clearAppFiles()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Логин").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Логин").performTextInput("activity_test")
            compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextInput("password")
            scenario.recreate()
            compose.onNodeWithContentDescription("Логин").assertTextContains("activity_test")
            compose.onNodeWithText("Создать аккаунт").performClick()
            compose.onNodeWithContentDescription("Имя").performTextInput("Тест")
            compose.onNodeWithContentDescription("Логин").performTextInput("activity_test")
            compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextInput("password")
            compose.onNodeWithContentDescription("Повторите пароль", useUnmergedTree = true).performTextInput("password")
            compose.onNodeWithText("Зарегистрироваться").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Расскажите о себе").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Заполнить позже").performClick()
            compose.onNodeWithText("Фильтры").performClick()
            compose.onNodeWithContentDescription("От").performTextInput("1")
            scenario.recreate()
            compose.onNodeWithContentDescription("От").assertTextContains("1")
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithText("Мия, 2 года").performClick()
            compose.onNodeWithText("Профиль автора и контакты").performClick()
            scenario.recreate()
            compose.onNodeWithText("Анна", substring = false).assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("Мия", substring = false).assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
            compose.onNodeWithText("Профиль", substring = false).performClick()
            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithContentDescription("Имя").performTextReplacement("Мария")
            scenario.recreate()
            compose.onNodeWithContentDescription("Имя").assertTextContains("Мария")
            compose.onNodeWithText("Сохранить изменения").performClick()
            compose.onNodeWithText("Мария", substring = false).assertIsDisplayed()
            compose.onNodeWithText("Добавить анкету").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Имя кошки").performTextInput("Луна")
            scenario.recreate()
            compose.onNodeWithContentDescription("Имя кошки").assertTextContains("Луна")
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
            scenario.recreate()
            compose.onNodeWithText("Выйти", substring = false).performClick()
            scenario.recreate()
            compose.onNodeWithText("Войти", substring = false).assertIsDisplayed()
            pressBackUnconditionally()
            compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
        } finally {
            clearAppFiles()
        }
    }

    private fun clearAppFiles() {
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir.listFiles()
            ?.forEach { it.deleteRecursively() }
    }
}
