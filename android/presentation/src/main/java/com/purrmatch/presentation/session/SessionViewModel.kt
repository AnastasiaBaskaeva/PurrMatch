package com.purrmatch.presentation.session

import androidx.lifecycle.ViewModel
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.presentation.AsyncActionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    val sessions: SessionRepository,
    val matches: MatchRepository,
) : AsyncActionViewModel() {
    fun signOut() = runAction { sessions.signOut() }
}
