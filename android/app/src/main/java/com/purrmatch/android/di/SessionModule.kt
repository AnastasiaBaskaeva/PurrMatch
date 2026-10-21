package com.purrmatch.android.di

import android.content.Context
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.LocalSessionRepository
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
    fun provideSessionRepository(@ApplicationContext context: Context): SessionRepository =
        LocalSessionRepository(File(context.filesDir, "session"))

    @Provides
    @Singleton
    fun provideMatchRepository(@ApplicationContext context: Context): MatchRepository =
        MockMatchRepository(File(context.filesDir, "mock-matches-v1"))
}
