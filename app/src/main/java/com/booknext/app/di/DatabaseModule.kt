package com.booknext.app.di

import android.content.Context
import androidx.room.Room
import com.booknext.app.data.local.db.AppDatabase
import com.booknext.app.data.local.db.BookDao
import com.booknext.app.data.local.db.AnnotationDao
import com.booknext.app.data.local.db.ReadingSessionDao
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.local.prefs.ReaderPrefs
import com.booknext.app.data.local.prefs.UiPrefs
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.data.remote.MetadataService
import com.booknext.app.data.repository.AnnotationRepository
import com.booknext.app.data.repository.BookRepository
import com.booknext.app.data.repository.ReadingSessionRepository
import com.booknext.app.data.service.BookFileService
import com.booknext.app.data.service.CoverService
import com.booknext.app.data.service.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "booknext.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
    @Provides fun provideAnnotationDao(db: AppDatabase): AnnotationDao = db.annotationDao()
    @Provides fun provideReadingSessionDao(db: AppDatabase): ReadingSessionDao = db.readingSessionDao()

    // ── Prefs ─────────────────────────────────────────────

    @Provides @Singleton
    fun provideAccountPrefs(@ApplicationContext ctx: Context): AccountPrefs = AccountPrefs(ctx)

    @Provides @Singleton
    fun provideUiPrefs(@ApplicationContext ctx: Context): UiPrefs = UiPrefs(ctx)

    @Provides @Singleton
    fun provideReaderPrefs(@ApplicationContext ctx: Context): ReaderPrefs = ReaderPrefs(ctx)

    // ── Repositories ──────────────────────────────────────

    @Provides @Singleton
    fun provideBookRepository(
        bookDao: BookDao,
        apiClient: ApiClient,
        accountPrefs: AccountPrefs,
        metadataService: MetadataService,
        bookFileService: BookFileService,
        coverService: CoverService,
        downloadManager: DownloadManager,
        @ApplicationContext ctx: Context,
    ): BookRepository = BookRepository(bookDao, apiClient, accountPrefs, metadataService, bookFileService, coverService, downloadManager, ctx)

    @Provides @Singleton
    fun provideAnnotationRepository(dao: AnnotationDao): AnnotationRepository =
        AnnotationRepository(dao)

    @Provides @Singleton
    fun provideReadingSessionRepository(dao: ReadingSessionDao): ReadingSessionRepository =
        ReadingSessionRepository(dao)
}
