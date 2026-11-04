package com.purrmatch.presentation.filters

import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias Filters = SearchFilters

class FiltersViewModel(
    private val repository: MatchRepository,
    private val userId: String,
) : AsyncActionViewModel() {
    private val mutableState = MutableStateFlow(repository.data.value.selection(userId).filters)
    val state = mutableState.asStateFlow()
    fun edit(value: Filters) { mutableState.value = value }
    fun reset() = edit(Filters())
    fun apply(onSuccess: () -> Unit) = runAction(onSuccess) { repository.applyFilters(userId, state.value) }
}
