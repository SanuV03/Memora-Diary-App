package com.chronicle.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
            //core configuration for local Room database.
@Database(
    entities = [LogEntry::class],
    version = 2,
    exportSchema = false
)
abstract class ChronicleDatabase : RoomDatabase() {
    abstract fun logEntryDao(): LogEntryDao
}