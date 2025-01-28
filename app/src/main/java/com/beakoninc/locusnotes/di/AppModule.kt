package com.beakoninc.locusnotes.di

import android.content.Context
import androidx.room.Room
import com.beakoninc.locusnotes.data.local.AppDatabase
import com.beakoninc.locusnotes.data.local.NoteDao
import com.beakoninc.locusnotes.data.repository.NoteRepository
import com.beakoninc.locusnotes.data.repository.NoteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.beakoninc.locusnotes.data.location.ActivityRecognitionService
import com.beakoninc.locusnotes.data.location.LocationService

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add the tags column with a default value
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''"
            )
        }
    }
    // Migration from version 2 to 3 (adding location fields)
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN locationName TEXT DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN latitude REAL DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN longitude REAL DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN address TEXT DEFAULT NULL"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "notes_database"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // Add migration strategy
            // Alternatively for development only:
            // .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideNoteRepository(noteRepositoryImpl: NoteRepositoryImpl): NoteRepository {
        return noteRepositoryImpl
    }
    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService {
        return LocationService(context)
    }
    @Provides
    @Singleton
    fun provideActivityRecognitionService(@ApplicationContext context: Context): ActivityRecognitionService {
        return ActivityRecognitionService(context)
    }

}