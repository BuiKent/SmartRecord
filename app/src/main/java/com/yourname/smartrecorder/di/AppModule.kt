package com.yourname.smartrecorder.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.audio.AudioPlayerImpl
import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.audio.AudioRecorderImpl
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_DATABASE
import com.yourname.smartrecorder.data.local.dao.BookmarkDao
import com.yourname.smartrecorder.data.local.dao.FlashcardDao
import com.yourname.smartrecorder.data.local.dao.NoteDao
import com.yourname.smartrecorder.data.local.dao.RecordingDao
import com.yourname.smartrecorder.data.local.dao.TranscriptDao
import com.yourname.smartrecorder.data.local.db.SmartRecorderDatabase
import com.yourname.smartrecorder.data.local.db.SmartRecorderDatabaseMigration
import com.yourname.smartrecorder.data.repository.BookmarkRepositoryImpl
import com.yourname.smartrecorder.data.repository.FlashcardRepositoryImpl
import com.yourname.smartrecorder.data.repository.NoteRepositoryImpl
import com.yourname.smartrecorder.data.repository.RecordingRepositoryImpl
import com.yourname.smartrecorder.data.repository.TranscriptRepositoryImpl
import com.yourname.smartrecorder.data.stt.AudioConverter
import com.yourname.smartrecorder.data.stt.WhisperAudioTranscriber
import com.yourname.smartrecorder.data.stt.WhisperEngine
import com.yourname.smartrecorder.data.stt.WhisperModelManager
import com.yourname.smartrecorder.data.stt.WhisperModelProvider
import com.yourname.smartrecorder.domain.repository.BookmarkRepository
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import com.yourname.smartrecorder.domain.repository.NoteRepository
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmartRecorderDatabase {
        AppLogger.d(TAG_DATABASE, "Initializing Room database -> name: %s", SmartRecorderDatabase.DATABASE_NAME)
        val startTime = System.currentTimeMillis()
        
        val database = Room.databaseBuilder(
            context,
            SmartRecorderDatabase::class.java,
            SmartRecorderDatabase.DATABASE_NAME
        )
            .addMigrations(
                SmartRecorderDatabaseMigration.MIGRATION_1_2,
                SmartRecorderDatabaseMigration.MIGRATION_2_3,
                SmartRecorderDatabaseMigration.MIGRATION_1_3
            )
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true) // For development only - remove in production
            .build()
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logDatabase(TAG_DATABASE, "DATABASE_INIT", "SmartRecorderDatabase", "duration=${duration}ms")
        AppLogger.logPerformance(TAG_DATABASE, "Database initialization", duration)
        
        return database
    }
    
    @Provides
    fun provideRecordingDao(database: SmartRecorderDatabase): RecordingDao {
        return database.recordingDao()
    }
    
    @Provides
    fun provideTranscriptDao(database: SmartRecorderDatabase): TranscriptDao {
        return database.transcriptDao()
    }
    
    @Provides
    fun provideNoteDao(database: SmartRecorderDatabase): NoteDao {
        return database.noteDao()
    }
    
    @Provides
    fun provideBookmarkDao(database: SmartRecorderDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
    
    @Provides
    fun provideFlashcardDao(database: SmartRecorderDatabase): FlashcardDao {
        return database.flashcardDao()
    }
    
    @Provides
    @Singleton
    fun provideRecordingRepository(dao: RecordingDao): RecordingRepository {
        return RecordingRepositoryImpl(dao)
    }
    
    @Provides
    @Singleton
    fun provideTranscriptRepository(dao: TranscriptDao): TranscriptRepository {
        return TranscriptRepositoryImpl(dao)
    }
    
    @Provides
    @Singleton
    fun provideNoteRepository(dao: NoteDao): NoteRepository {
        return NoteRepositoryImpl(dao)
    }
    
    @Provides
    @Singleton
    fun provideBookmarkRepository(dao: BookmarkDao): BookmarkRepository {
        return BookmarkRepositoryImpl(dao)
    }
    
    @Provides
    @Singleton
    fun provideFlashcardRepository(dao: FlashcardDao): FlashcardRepository {
        return FlashcardRepositoryImpl(dao)
    }
    
    @Provides
    @Singleton
    fun provideAudioRecorder(): AudioRecorder {
        return AudioRecorderImpl()
    }
    
    @Provides
    @Singleton
    fun provideAudioPlayer(): AudioPlayer {
        return AudioPlayerImpl()
    }
    
    // Whisper STT providers
    @Provides
    @Singleton
    fun provideWhisperModelManager(
        @ApplicationContext context: Context
    ): WhisperModelManager {
        return WhisperModelManager(context)
    }
    
    @Provides
    @Singleton
    fun provideWhisperEngine(): WhisperEngine {
        return WhisperEngine()
    }
    
    @Provides
    @Singleton
    fun provideWhisperModelProvider(
        @ApplicationContext context: Context,
        modelManager: WhisperModelManager,
        engine: WhisperEngine
    ): WhisperModelProvider {
        return WhisperModelProvider(context, modelManager, engine)
    }
    
    @Provides
    @Singleton
    fun provideAudioConverter(
        @ApplicationContext context: Context
    ): AudioConverter {
        return AudioConverter(context)
    }
    
    @Provides
    @Singleton
    fun provideWhisperAudioTranscriber(
        @ApplicationContext context: Context,
        converter: AudioConverter,
        modelProvider: WhisperModelProvider,
        modelManager: WhisperModelManager,
        engine: WhisperEngine
    ): WhisperAudioTranscriber {
        return WhisperAudioTranscriber(context, converter, modelProvider, modelManager, engine)
    }
}

