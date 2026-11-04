package com.purrmatch.android.di

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.MockSessionRepository
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    fun provideMockBackend(): MockBackend = MockBackend()

    @Provides
    @Singleton
    fun provideSessionRepository(backend: MockBackend): SessionRepository = MockSessionRepository(backend)

    @Provides
    @Singleton
    fun provideMatchRepository(backend: MockBackend): MatchRepository = MockMatchRepository(backend)
}
