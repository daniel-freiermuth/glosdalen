package com.glosdalen.app.di

import com.glosdalen.app.data.repository.AnkiRepository
import com.glosdalen.app.data.repository.impl.AnkiRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnkiModule {

    @Binds
    @Singleton
    abstract fun bindAnkiRepository(
        ankiRepositoryImpl: AnkiRepositoryImpl
    ): AnkiRepository
}
