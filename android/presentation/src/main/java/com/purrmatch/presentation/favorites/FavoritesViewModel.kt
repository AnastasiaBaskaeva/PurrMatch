package com.purrmatch.presentation.favorites

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesState(val listings: List<Listing>, val loading: Boolean)

class FavoritesViewModel(private val repository: MatchRepository, private val userId: String) : AsyncActionViewModel() {
    private val loading = MutableStateFlow(true)
    private fun listings(data: MatchData) = data.listings.filter { it.id in data.selection(userId).favorites }

    val state = combine(repository.data, loading) { data, loading -> FavoritesState(listings(data), loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            FavoritesState(listings(repository.data.value), loading.value))

    init {
        // Favorites may have been added by a swipe on the main screen, so cards are fetched whenever the ids change.
        viewModelScope.launch {
            repository.data.map { it.selection(userId).favorites }.distinctUntilChanged().collect {
                repository.getFavorites(userId)
                loading.value = false
            }
        }
    }

    fun toggle(id: String) = runAction { repository.toggleFavorite(userId, id) }
}
