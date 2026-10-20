package com.purrmatch.android.di

import com.purrmatch.data.session.InMemorySessionRepository
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
    fun provideSessionRepository(): SessionRepository = InMemorySessionRepository()
}
