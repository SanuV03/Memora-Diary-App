package com.chronicle.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE userId = :uid ORDER BY timestamp DESC")
    fun getEntriesByUser(uid: String): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE userId = :uid AND isSynced = 0")
    suspend fun getUnsyncedEntries(uid: String): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LogEntry)

    @Delete
    suspend fun deleteEntry(entry: LogEntry)

    @Query("UPDATE log_entries SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}