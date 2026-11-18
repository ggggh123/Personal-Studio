package com.example.personal_studio.core.di

import com.example.personal_studio.core.llm.BuiltInLlm
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.ConfigurableLlmProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmProvider(prefs: UserPreferencesRepository): LLMProvider =
        ConfigurableLlmProvider(prefs = prefs, bundled = BuiltInLlm.bundled)
}
