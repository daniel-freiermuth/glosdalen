package com.glosdalen.app.di

import com.glosdalen.app.libs.copilot.util.SystemTimeProvider
import com.glosdalen.app.libs.copilot.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {
    
    @Binds
    @Singleton
    abstract fun bindTimeProvider(
        systemTimeProvider: SystemTimeProvider
    ): TimeProvider
}
