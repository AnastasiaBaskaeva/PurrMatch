package com.purrmatch.presentation.favorites

import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(private val repository: MatchRepository, private val userId: String) : AsyncActionViewModel() {
    val listings = repository.data.map { data -> data.listings.filter { it.id in data.selection(userId).favorites } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.data.value.let { data ->
            data.listings.filter { it.id in data.selection(userId).favorites }
        })
    fun toggle(id: String) = runAction { repository.toggleFavorite(userId, id) }
}
