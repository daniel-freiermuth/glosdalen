package com.glosdalen.app.di

import android.content.Context
import com.glosdalen.app.BuildConfig
import com.glosdalen.app.libs.copilot.CopilotChat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CopilotModule {
    
    @Provides
    @Singleton
    fun provideCopilotChat(
        @ApplicationContext context: Context
    ): CopilotChat {
        return CopilotChat.builder(context)
            .enableDebugLogging(BuildConfig.DEBUG)
            .setConnectionTimeout(30)
            .setReadTimeout(60)
            .build()
    }
}
