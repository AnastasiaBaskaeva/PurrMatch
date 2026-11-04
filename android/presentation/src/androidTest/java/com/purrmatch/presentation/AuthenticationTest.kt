package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.purrmatch.data.session.MockSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.auth.AuthState
import com.purrmatch.presentation.auth.AuthViewModel
import com.purrmatch.presentation.navigation.PurrMatchApp
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthenticationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invalidLoginAndOccupiedRegistrationKeepFormsAndSuccessfulLoginOpensHome() {
        val dependencies = MockRepositories()
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches) }
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

    @Test fun serverFailureKeepsLoginDraftAndAllowsSuccessfulRetry() {
        val dependencies = MockRepositories()
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches) }
        waitFor("Войти")
        compose.onNodeWithContentDescription("Логин").performTextInput("server_error")
        password("purrmatch-demo")
        compose.onNodeWithText("Войти", substring = false).performClick()
        waitFor("Сервис временно недоступен. Повторите попытку.")
        compose.onNodeWithContentDescription("Логин").assertTextContains("server_error")
        compose.onNodeWithContentDescription("Логин").performTextReplacement("anna_cats")
        compose.onNodeWithText("Войти", substring = false).performClick()
        waitFor("Главная")
    }

    @Test fun registrationSharesAccountWithSessionAndNewBackendStartsFresh() {
        val dependencies = MockRepositories()
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches) }
        waitFor("Войти")
        compose.onNodeWithText("Создать аккаунт").performClick()
        compose.onNodeWithContentDescription("Имя").performTextInput("Новая")
        compose.onNodeWithContentDescription("Логин").performTextInput("new_account")
        password("new-password")
        compose.onNodeWithContentDescription("Повторите пароль", useUnmergedTree = true).performTextInput("new-password")
        compose.onNodeWithText("Зарегистрироваться").performClick()
        waitFor("Расскажите о себе")
        runBlocking {
            val id = dependencies.matches.authenticate("new_account", "new-password").getOrThrow()
            assertEquals(id, (dependencies.sessions.session.value as com.purrmatch.domain.session.SessionState.SignedIn).userId)
            val fresh = MockRepositories()
            assertTrue(fresh.matches.authenticate("new_account", "new-password").isFailure)
            assertEquals(com.purrmatch.domain.session.SessionState.Guest, fresh.sessions.session.value)
        }
    }

    @Test fun loadingRejectsRepeatsAndFailedRequestPreservesInputWithoutPersistingPassword() {
        val gate = CompletableDeferred<Result<String>>()
        var calls = 0
        val dependencies = MockRepositories()
        val repository = object : MatchRepository by dependencies.matches {
            override suspend fun register(login: String, password: String, name: String): Result<String> {
                calls++
                return gate.await()
            }
        }
        val saved = SavedStateHandle()
        lateinit var model: AuthViewModel
        compose.runOnIdle {
            model = AuthViewModel(saved, repository, MockSessionRepository(dependencies.backend))
            model.edit(AuthState(name = "Alice", login = "alice", password = "alice-pass", repeat = "alice-pass"))
            model.submit(true)
            model.submit(true)
            assertTrue(model.loading.value)
        }
        compose.waitUntil { calls == 1 }
        compose.runOnIdle {
            assertEquals("", saved.get<AuthState>("auth")!!.password)
            gate.complete(Result.failure(IOException("test server failure")))
        }
        compose.waitUntil { !model.loading.value }
        compose.runOnIdle {
            assertEquals("alice-pass", model.state.value.password)
            assertNotNull(model.error.value)
            val restored = AuthViewModel(saved, repository, MockSessionRepository(dependencies.backend))
            assertFalse(restored.loading.value)
            assertEquals("alice", restored.state.value.login)
            assertEquals("", restored.state.value.password)
            val legacySaved = SavedStateHandle(mapOf("auth" to AuthState(login = "legacy", password = "old-bundle-pass")))
            AuthViewModel(legacySaved, repository, MockSessionRepository(dependencies.backend))
            assertEquals("", legacySaved.get<AuthState>("auth")!!.password)
        }
    }

    private fun password(value: String) = compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextReplacement(value)
    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
    }
}
