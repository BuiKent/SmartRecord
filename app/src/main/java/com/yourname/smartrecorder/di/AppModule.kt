package com.yourname.smartrecorder.di

import android.content.Context
import androidx.room.Room
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.audio.AudioPlayerImpl
import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.audio.AudioRecorderImpl
import com.yourname.smartrecorder.data.local.dao.NoteDao
import com.yourname.smartrecorder.data.local.dao.RecordingDao
import com.yourname.smartrecorder.data.local.dao.TranscriptDao
import com.yourname.smartrecorder.data.local.db.SmartRecorderDatabase
import com.yourname.smartrecorder.data.repository.NoteRepositoryImpl
import com.yourname.smartrecorder.data.repository.RecordingRepositoryImpl
import com.yourname.smartrecorder.data.repository.TranscriptRepositoryImpl
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
        return Room.databaseBuilder(
            context,
            SmartRecorderDatabase::class.java,
            SmartRecorderDatabase.DATABASE_NAME
        ).build()
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
    fun provideAudioRecorder(): AudioRecorder {
        return AudioRecorderImpl()
    }
    
    @Provides
    @Singleton
    fun provideAudioPlayer(): AudioPlayer {
        return AudioPlayerImpl()
    }
}

