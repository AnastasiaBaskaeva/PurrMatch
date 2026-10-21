package com.purrmatch.presentation.browse

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.BrowseListings
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.Listing
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BrowseState(val current: Listing?)

data class PendingSwipe(val listingId: String, val like: Boolean)

class BrowseViewModel(private val repository: MatchRepository, private val userId: String) : AsyncActionViewModel() {
    private val browse = BrowseListings()
    private val mutablePendingSwipe = MutableStateFlow<PendingSwipe?>(null)
    val pendingSwipe = mutablePendingSwipe.asStateFlow()
    private fun state(data: MatchData): BrowseState {
        val current = browse(data, userId)
        return BrowseState(current)
    }
    val state = repository.data.map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value))

    fun swipe(listingId: String, like: Boolean, onComplete: (Boolean) -> Unit) {
        if (mutablePendingSwipe.value != null) {
            onComplete(false)
            return
        }
        mutablePendingSwipe.value = PendingSwipe(listingId, like)
        runAction {
            try {
                val result = repository.swipe(userId, listingId, like)
                val advanced = result.isSuccess && browse(repository.data.value, userId)?.id != listingId
                // Publish the replacement before releasing a restored screen's pending card.
                if (advanced) state.first { it.current?.id != listingId }
                onComplete(advanced)
                result
            } finally {
                mutablePendingSwipe.value = null
            }
        }
    }
}
