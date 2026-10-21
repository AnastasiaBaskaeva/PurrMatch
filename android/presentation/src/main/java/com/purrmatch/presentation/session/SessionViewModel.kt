package com.purrmatch.presentation.session

import androidx.lifecycle.ViewModel
import com.purrmatch.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
) : ViewModel() {
    val session = repository.session

    fun enterDemoSession() = repository.enterDemoSession()
    fun signOut() = repository.signOut()
}
