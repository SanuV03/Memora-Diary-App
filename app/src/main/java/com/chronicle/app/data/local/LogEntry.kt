package com.chronicle.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val body: String,
    val category: String,
    val mood: Int,
    val timestamp: Long,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val isSynced: Boolean = false
)