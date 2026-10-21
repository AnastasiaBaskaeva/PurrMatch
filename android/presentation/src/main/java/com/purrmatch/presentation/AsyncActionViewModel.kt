package com.purrmatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Runs storage work away from UI callbacks and keeps the current form available for retry. */
open class AsyncActionViewModel : ViewModel() {
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    protected fun runAction(onSuccess: () -> Unit = {}, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onSuccess { onSuccess() }
                .onFailure { mutableError.value = "Не удалось сохранить изменения. Повторите попытку." }
        }
    }

    fun dismissError() { mutableError.value = null }
}
