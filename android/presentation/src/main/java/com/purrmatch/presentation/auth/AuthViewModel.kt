package com.purrmatch.presentation.auth

import androidx.lifecycle.SavedStateHandle
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.presentation.AsyncActionViewModel
import java.io.Serializable

data class AuthState(
    val name: String = "", val login: String = "", val password: String = "",
    val repeat: String = "", val visible: Boolean = false, val submitted: Boolean = false,
) : Serializable

class AuthViewModel(
    private val savedState: SavedStateHandle,
    private val matches: MatchRepository,
    private val sessions: SessionRepository,
) : AsyncActionViewModel() {
    val state = savedState.getStateFlow("auth", AuthState())
    fun edit(value: AuthState) { savedState["auth"] = value }
    fun submit(registration: Boolean) {
        edit(state.value.copy(submitted = true))
        val form = state.value
        if (form.login.isBlank() || form.password.isEmpty()) return
        if (registration && (form.name.isBlank() || form.password.length < 8 || form.repeat != form.password)) return
        val login = form.login.trim()
        val id = matches.data.value.profiles.firstOrNull { it.login == login }?.id ?: "local:$login"
        runAction {
            matches.ensureProfile(id, login, if (registration) form.name.trim() else login)
                .fold(onSuccess = { sessions.signIn(id, onboarding = registration) }, onFailure = { Result.failure(it) })
        }
    }
}
