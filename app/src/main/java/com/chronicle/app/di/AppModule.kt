package com.chronicle.app.di

import android.content.Context
import androidx.room.Room
import com.chronicle.app.data.local.ChronicleDatabase
import com.chronicle.app.data.local.LogEntryDao
import com.google.firebase.firestore.FirebaseFirestore
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
    //manual for building your phone's internal storage//
    fun provideDatabase(@ApplicationContext context: Context): ChronicleDatabase {
        return Room.databaseBuilder(
            context,
            ChronicleDatabase::class.java,
            "chronicle_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDao(db: ChronicleDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}