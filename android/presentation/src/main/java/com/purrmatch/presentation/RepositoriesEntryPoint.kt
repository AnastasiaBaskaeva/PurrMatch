package com.purrmatch.presentation

import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the composition root read the singleton repositories without routing them through a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoriesEntryPoint {
    fun sessions(): SessionRepository
    fun matches(): MatchRepository
}
