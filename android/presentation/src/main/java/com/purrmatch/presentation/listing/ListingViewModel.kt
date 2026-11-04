package com.purrmatch.presentation.listing

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.Profile
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListingState(
    val listing: Listing?, val author: Profile?, val favorite: Boolean,
    /** The listing or its author is not known yet and is being requested. */
    val loading: Boolean = false,
)

class ListingViewModel(
    private val repository: MatchRepository,
    private val userId: String,
    private val listingId: String,
) : AsyncActionViewModel() {
    private val loading = MutableStateFlow(repository.data.value.let { data ->
        data.listing(listingId)?.let { data.profile(it.authorId) } == null
    })
    private fun state(data: com.purrmatch.domain.match.MatchData, loading: Boolean): ListingState {
        val listing = data.listing(listingId)
        return ListingState(listing, listing?.let { data.profile(it.authorId) },
            listingId in data.selection(userId).favorites, loading)
    }
    val state = combine(repository.data, loading, ::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value, loading.value))

    init {
        viewModelScope.launch {
            val listing = repository.getListing(listingId).getOrNull()
            // The listing shows as soon as it is cached; "no author" is only reported once the author was requested.
            listing?.let { repository.getProfile(it.authorId) }
            loading.value = false
        }
    }

    fun toggleFavorite(onSuccess: () -> Unit = {}) = runAction(onSuccess) { repository.toggleFavorite(userId, listingId) }
    fun delete(onSuccess: () -> Unit) = runAction(onSuccess) { repository.delete(userId, listingId) }
}
