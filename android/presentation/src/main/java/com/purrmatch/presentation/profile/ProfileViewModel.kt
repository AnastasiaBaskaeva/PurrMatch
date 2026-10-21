package com.purrmatch.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileState(val profile: Profile?, val listings: List<com.purrmatch.domain.match.Listing>)

class ProfileViewModel(repository: MatchRepository, private val authorId: String) : AsyncActionViewModel() {
    private fun state(data: com.purrmatch.domain.match.MatchData) = ProfileState(
        data.profile(authorId), data.listings.filter { it.authorId == authorId && it.available },
    )
    val state = repository.data.map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value))
}

class EditProfileViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    userId: String,
) : AsyncActionViewModel() {
    val state = savedState.getStateFlow("profile", requireNotNull(repository.data.value.profile(userId)))
    fun edit(value: Profile) { savedState["profile"] = value }
    fun save(onSuccess: () -> Unit) = runAction(onSuccess) { repository.saveProfile(state.value) }
}
