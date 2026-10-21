package com.purrmatch.presentation.filters

import androidx.lifecycle.SavedStateHandle
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.presentation.AsyncActionViewModel

typealias Filters = SearchFilters

class FiltersViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    private val userId: String,
) : AsyncActionViewModel() {
    val state = savedState.getStateFlow("filters", repository.data.value.selection(userId).filters)
    fun edit(value: Filters) { savedState["filters"] = value }
    fun reset() = edit(Filters())
    fun apply(onSuccess: () -> Unit) = runAction(onSuccess) { repository.applyFilters(userId, state.value) }
}
