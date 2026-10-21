package com.purrmatch.presentation.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.InvalidCredentialsException
import com.purrmatch.domain.match.LoginOccupiedException
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import java.io.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val name: String = "", val login: String = "", val password: String = "",
    val repeat: String = "", val visible: Boolean = false, val submitted: Boolean = false,
) : Serializable

class AuthViewModel(
    private val savedState: SavedStateHandle,
    private val matches: MatchRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        (savedState.get<AuthState>("auth") ?: AuthState()).copy(password = "", repeat = "", visible = false)
            .also { savedState["auth"] = it },
    )
    val state = mutableState.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun edit(value: AuthState) {
        if (loading.value) return
        mutableState.value = value
        // Keep passwords in this ViewModel only, never in a saved-state Bundle on disk.
        savedState["auth"] = value.copy(password = "", repeat = "", visible = false)
        dismissError()
    }

    fun dismissError() { mutableError.value = null }

    fun submit(registration: Boolean) {
        if (loading.value) return
        edit(state.value.copy(submitted = true))
        val form = state.value
        if (form.login.isBlank() || form.password.isEmpty()) return
        if (registration && (form.name.isBlank() || form.password.length < 8 || form.repeat != form.password)) return
        mutableLoading.value = true
        viewModelScope.launch {
            try {
                val account = if (registration) matches.register(form.login, form.password, form.name)
                    else matches.authenticate(form.login, form.password)
                account.fold(onSuccess = { id ->
                    sessions.signIn(id, onboarding = registration).onFailure {
                        mutableError.value = if (registration)
                            "Аккаунт создан, но не удалось сохранить вход. Войдите с указанными логином и паролем."
                        else "Не удалось сохранить вход. Повторите попытку."
                    }
                }, onFailure = {
                    mutableError.value = when (it) {
                        is LoginOccupiedException -> "Этот логин уже занят"
                        is InvalidCredentialsException -> "Неверный логин или пароль"
                        else -> "Не удалось прочитать или сохранить аккаунт. Повторите попытку."
                    }
                })
            } finally { mutableLoading.value = false }
        }
    }
}
