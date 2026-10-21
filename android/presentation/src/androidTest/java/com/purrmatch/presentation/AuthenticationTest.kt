package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.auth.AuthState
import com.purrmatch.presentation.auth.AuthViewModel
import com.purrmatch.presentation.navigation.PurrMatchApp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthenticationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invalidLoginAndOccupiedRegistrationKeepFormsAndSuccessfulLoginOpensHome() {
        compose.setContent { PurrMatchApp(LocalSessionRepository(), MockMatchRepository()) }
        waitFor("Войти")
        compose.onNodeWithContentDescription("Логин").performTextInput("missing")
        password("wrong-password")
        compose.onNodeWithText("Войти", substring = false).performClick()
        waitFor("Неверный логин или пароль")
        compose.onNodeWithContentDescription("Логин").assertTextContains("missing")
        compose.onNodeWithContentDescription("Логин").performTextReplacement("anna_cats")
        compose.onNodeWithText("Войти", substring = false).performClick()
        waitFor("Неверный логин или пароль")
        compose.onNodeWithText("Создать аккаунт").performClick()
        compose.onNodeWithContentDescription("Имя").performTextInput("Новая")
        compose.onNodeWithContentDescription("Логин").performTextInput("anna_cats")
        password("new-password")
        compose.onNodeWithContentDescription("Повторите пароль", useUnmergedTree = true).performTextInput("new-password")
        compose.onNodeWithText("Зарегистрироваться").performClick()
        waitFor("Этот логин уже занят")
        compose.onNodeWithContentDescription("Имя").assertTextContains("Новая")
        compose.onNodeWithContentDescription("Назад").performClick()
        password("purrmatch-demo")
        compose.onNodeWithText("Войти", substring = false).performClick()
        waitFor("Главная")
        compose.onNodeWithText("Расскажите о себе").assertDoesNotExist()
    }

    @Test fun loginRetriesRecoveredStorageWithoutRetypingOrLosingAccountData() = runBlocking {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "auth-retry")
        dir.deleteRecursively()
        check(dir.mkdirs())
        try {
            val file = File(dir, "matches")
            val writer = MockMatchRepository(file)
            val id = writer.register("retry", "retry-password", "Retry").getOrThrow()
            writer.swipe(id, "mia", true).getOrThrow()
            val expected = writer.data.value
            val snapshot = file.readBytes()
            file.writeText("temporarily unreadable")
            val repository = MockMatchRepository(file)
            compose.setContent { PurrMatchApp(LocalSessionRepository(), repository) }
            waitFor("Войти")
            compose.onNodeWithContentDescription("Логин").performTextInput("retry")
            password("retry-password")
            compose.onNodeWithText("Войти", substring = false).performClick()
            waitFor("Не удалось прочитать или сохранить аккаунт. Повторите попытку.")
            compose.onNodeWithContentDescription("Логин").assertTextContains("retry")
            file.writeBytes(snapshot)
            compose.onNodeWithText("Войти", substring = false).performClick()
            waitFor("Главная")
            assertEquals(expected, repository.data.value)
        } finally { dir.deleteRecursively() }
    }

    @Test fun auth18SnapshotLogsInWithOriginalPasswordAndKeepsTypedDataAfterProfileSaveAndRestart() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.cacheDir, "auth18-migration-${java.util.UUID.randomUUID()}")
        check(dir.mkdirs())
        try {
            val file = File(dir, "matches")
            instrumentation.context.assets.open("auth18-wrapped-v1.bin").use { input -> file.outputStream().use(input::copyTo) }
            val repository = MockMatchRepository(file)
            compose.setContent { PurrMatchApp(LocalSessionRepository(), repository) }
            waitFor("Войти")
            compose.onNodeWithContentDescription("Логин").performTextInput("auth18_owner")
            password("auth18-password-🐈")
            compose.onNodeWithText("Войти", substring = false).performClick()
            waitFor("Главная")
            val expected = repository.data.value
            assertEquals(java.time.LocalDate.of(2024, 2, 29), expected.listing("compat-cat")!!.birthday)
            assertEquals(12_345L, expected.listing("compat-cat")!!.price)
            assertEquals(listOf("sample:upload", "picked:1", "picked:3"), expected.listing("compat-cat")!!.photos.map { it.reference })
            assertEquals(listOf("peach", "sonya"), expected.selection("auth18-owner").seen)
            compose.onNodeWithText("Избранное", substring = false).performClick()
            compose.onNodeWithText("Мия,", substring = true).assertExists()
            compose.onNodeWithText("Луна,", substring = true).assertExists()
            compose.onNodeWithText("Профиль", substring = false).performClick()
            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithContentDescription("О себе").performScrollTo().performTextReplacement("После обновления")
            compose.onNodeWithText("Сохранить изменения", substring = false).performClick()
            compose.waitUntil(15_000) { repository.data.value.profile("auth18-owner")?.about == "После обновления" }
            val registered = repository.register("after-android-upgrade", "new-android-password", "Новый").getOrThrow()
            val restored = MockMatchRepository(file)
            assertEquals("auth18-owner", restored.authenticate("auth18_owner", "auth18-password-🐈").getOrThrow())
            assertTrue(restored.authenticate("auth18_owner", "purrmatch-demo").isFailure)
            assertEquals(registered, restored.authenticate("after-android-upgrade", "new-android-password").getOrThrow())
            assertEquals(repository.data.value, restored.data.value)
            assertEquals(expected.listings, restored.data.value.listings)
            assertEquals(expected.selections, restored.data.value.selections)
        } finally { dir.deleteRecursively() }
    }

    @Test fun loadingRejectsRepeatsAndFailedWritePreservesInputWithoutPersistingPassword() {
        val gate = CompletableDeferred<Result<String>>()
        var calls = 0
        val repository = object : MatchRepository by MockMatchRepository() {
            override suspend fun register(login: String, password: String, name: String): Result<String> {
                calls++
                return gate.await()
            }
        }
        val saved = SavedStateHandle()
        lateinit var model: AuthViewModel
        compose.runOnIdle {
            model = AuthViewModel(saved, repository, LocalSessionRepository())
            model.edit(AuthState(name = "Alice", login = "alice", password = "alice-pass", repeat = "alice-pass"))
            model.submit(true)
            model.submit(true)
            assertTrue(model.loading.value)
        }
        compose.waitUntil { calls == 1 }
        compose.runOnIdle {
            assertEquals("", saved.get<AuthState>("auth")!!.password)
            gate.complete(Result.failure(IOException("test write failure")))
        }
        compose.waitUntil { !model.loading.value }
        compose.runOnIdle {
            assertEquals("alice-pass", model.state.value.password)
            assertNotNull(model.error.value)
            val restored = AuthViewModel(saved, repository, LocalSessionRepository())
            assertFalse(restored.loading.value)
            assertEquals("alice", restored.state.value.login)
            assertEquals("", restored.state.value.password)
            val legacySaved = SavedStateHandle(mapOf("auth" to AuthState(login = "legacy", password = "old-bundle-pass")))
            AuthViewModel(legacySaved, repository, LocalSessionRepository())
            assertEquals("", legacySaved.get<AuthState>("auth")!!.password)
        }
    }

    private fun password(value: String) = compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextReplacement(value)
    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
    }
}
