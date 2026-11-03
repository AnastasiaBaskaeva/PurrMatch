package com.purrmatch.presentation.filters

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.presentation.AsyncActionViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias Filters = SearchFilters

class FiltersViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    private val userId: String,
) : AsyncActionViewModel() {
    private val mutableState = MutableStateFlow(
        savedState.get<Bundle>("filters_draft")?.restoreFiltersDraft()
            ?: repository.data.value.selection(userId).filters,
    )
    val state = mutableState.asStateFlow()
    fun edit(value: Filters) {
        savedState["filters_draft"] = value.saveFiltersDraft()
        mutableState.value = value
    }
    fun reset() = edit(Filters())
    fun apply(onSuccess: () -> Unit) = runAction(onSuccess) { repository.applyFilters(userId, state.value) }
}

private fun Filters.saveFiltersDraft() = Bundle().apply {
    putString("breed", breed)
    putString("color", color)
    putString("eyes", eyes)
    putString("minAge", minAge)
    putString("maxAge", maxAge)
    putString("city", city)
    putString("price", price)
    putInt("sex", sex)
    putInt("terms", terms)
}

private fun Bundle.restoreFiltersDraft() = Filters(
    breed = getString("breed").orEmpty(),
    color = getString("color").orEmpty(),
    eyes = getString("eyes").orEmpty(),
    minAge = getString("minAge").orEmpty(),
    maxAge = getString("maxAge").orEmpty(),
    city = getString("city").orEmpty(),
    price = getString("price").orEmpty(),
    sex = getInt("sex"),
    terms = getInt("terms"),
)
