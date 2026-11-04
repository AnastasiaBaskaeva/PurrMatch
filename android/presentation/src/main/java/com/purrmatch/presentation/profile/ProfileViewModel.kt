package com.purrmatch.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileState(
    val profile: Profile?,
    val listings: List<com.purrmatch.domain.match.Listing>,
    /** The profile is not known yet and is being requested. */
    val loading: Boolean = false,
)

class ProfileViewModel(private val repository: MatchRepository, private val authorId: String) : AsyncActionViewModel() {
    private val loading = MutableStateFlow(repository.data.value.profile(authorId) == null)
    private fun state(data: com.purrmatch.domain.match.MatchData, loading: Boolean) = ProfileState(
        data.profile(authorId), data.listings.filter { it.authorId == authorId && it.available }, loading,
    )
    val state = combine(repository.data, loading, ::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value, loading.value))

    init {
        viewModelScope.launch {
            repository.getProfile(authorId)
            loading.value = false
            repository.getAuthorListings(authorId)
        }
    }
}

class EditProfileViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    userId: String,
) : AsyncActionViewModel() {
    val state = savedState.getStateFlow("profile", requireNotNull(repository.data.value.profile(userId)))
    fun edit(value: Profile) { savedState["profile"] = value }
    fun save(onSuccess: () -> Unit) = runAction(onSuccess) { repository.upsertProfile(state.value) }
}
