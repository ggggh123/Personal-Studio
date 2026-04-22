package com.example.personal_studio.core.di

import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.OpenAiCompatibleProvider
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
        OpenAiCompatibleProvider(
            prefs = prefs,
            bundledDefaultKey = BuildConfig.DEFAULT_API_KEY,
            bundledDefaultBaseUrl = BuildConfig.DEFAULT_API_BASE_URL,
            bundledDefaultModel = BuildConfig.DEFAULT_LLM_MODEL,
        )
}
