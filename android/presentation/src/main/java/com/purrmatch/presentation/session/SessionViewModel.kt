package com.purrmatch.presentation.session

import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.presentation.AsyncActionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : AsyncActionViewModel() {
    fun signOut() = runAction { sessions.signOut() }
}
