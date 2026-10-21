package com.purrmatch.presentation.listing

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.Profile
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ListingState(val listing: Listing?, val author: Profile?, val favorite: Boolean)

class ListingViewModel(
    private val repository: MatchRepository,
    private val userId: String,
    private val listingId: String,
) : AsyncActionViewModel() {
    private fun state(data: com.purrmatch.domain.match.MatchData): ListingState {
        val listing = data.listing(listingId)
        return ListingState(listing, listing?.let { data.profile(it.authorId) },
            listingId in data.selection(userId).favorites)
    }
    val state = repository.data.map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value))
    fun toggleFavorite(onSuccess: () -> Unit = {}) = runAction(onSuccess) { repository.toggleFavorite(userId, listingId) }
    fun delete(onSuccess: () -> Unit) = runAction(onSuccess) { repository.delete(userId, listingId) }
}
