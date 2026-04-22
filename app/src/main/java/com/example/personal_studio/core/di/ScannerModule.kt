package com.example.personal_studio.core.di

import android.content.Context
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.data.repository.ScanRepositoryImpl
import com.example.personal_studio.data.scanner.EdgeDetector
import com.example.personal_studio.data.scanner.EnhancePipeline
import com.example.personal_studio.data.scanner.OpenCvEdgeDetector
import com.example.personal_studio.data.scanner.OpenCvEnhancePipeline
import com.example.personal_studio.data.scanner.PdfExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    @Provides @Singleton
    fun provideScanRepository(
        @ApplicationContext context: Context,
        docDao: ScanDocumentDao,
        pageDao: ScanPageDao,
    ): ScanRepository {
        val scansRoot = File(context.filesDir, "scans").apply { mkdirs() }
        return ScanRepositoryImpl(docDao, pageDao, scansRoot)
    }

    @Provides @Singleton
    fun provideEnhancePipeline(impl: OpenCvEnhancePipeline): EnhancePipeline = impl

    @Provides @Singleton
    fun provideEdgeDetector(impl: OpenCvEdgeDetector): EdgeDetector = impl

    @Provides @Singleton
    fun providePdfExporter(@ApplicationContext ctx: Context): PdfExporter = PdfExporter(ctx)
}
