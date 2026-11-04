package com.purrmatch.presentation.browse

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.BrowseFeed
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PendingSwipe(val listingId: String, val like: Boolean)

class BrowseViewModel(private val repository: MatchRepository, private val userId: String) : AsyncActionViewModel() {
    private val feed = BrowseFeed(repository, userId, viewModelScope)
    val state = feed.state
    private val mutablePendingSwipe = MutableStateFlow<PendingSwipe?>(null)
    val pendingSwipe = mutablePendingSwipe.asStateFlow()

    init {
        // The server picks cards by the saved filters, so a change of filters means a fresh feed.
        viewModelScope.launch {
            repository.data.map { it.selection(userId).filters }.distinctUntilChanged().collect { feed.restart() }
        }
    }

    fun swipe(listingId: String, like: Boolean, onComplete: (Boolean) -> Unit) {
        if (mutablePendingSwipe.value != null) {
            onComplete(false)
            return
        }
        mutablePendingSwipe.value = PendingSwipe(listingId, like)
        runAction {
            try {
                val result = feed.swipe(listingId, like)
                val advanced = result.isSuccess && feed.state.value.current?.id != listingId
                // Publish the replacement before releasing a restored screen's pending card.
                if (advanced) feed.state.first { it.current?.id != listingId }
                onComplete(advanced)
                result
            } finally {
                mutablePendingSwipe.value = null
            }
        }
    }

    fun reload() = feed.reload()
}
