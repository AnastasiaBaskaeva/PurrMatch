package com.purrmatch.presentation.browse

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.BrowseListings
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.Listing
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BrowseState(val current: Listing?)

class BrowseViewModel(private val repository: MatchRepository, private val userId: String) : AsyncActionViewModel() {
    private val browse = BrowseListings()
    private fun state(data: MatchData): BrowseState {
        val current = browse(data, userId)
        return BrowseState(current)
    }
    val state = repository.data.map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value))

    fun swipe(listingId: String, like: Boolean) = runAction { repository.swipe(userId, listingId, like) }
}
