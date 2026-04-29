package com.example.personal_studio.core.di

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.data.repository.KnowledgeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {
    @Binds @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository
}
