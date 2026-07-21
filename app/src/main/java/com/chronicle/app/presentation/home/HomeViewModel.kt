package com.chronicle.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronicle.app.data.local.LogEntry
import com.chronicle.app.data.repository.EntryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class SortOrder { NEWEST, OLDEST, HIGHEST_MOOD, LOWEST_MOOD }
enum class SyncStatus { ALL_SYNCED, PENDING, SYNCING, ERROR }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: EntryRepository
) : ViewModel() {

    private val _isDarkMode       = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery      = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder        = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _syncStatus       = MutableStateFlow(SyncStatus.ALL_SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ── Filtered entries ──────────────────────────────────────────────────────
    val filteredEntries: StateFlow<List<LogEntry>> = combine(
        repository.getAllEntries(), _selectedCategory, _searchQuery, _sortOrder
    ) { entries, category, query, sort ->
        var result = entries
        if (category != "All") result = result.filter { it.category == category }
        if (query.isNotBlank()) result = result.filter {
            it.title.contains(query, true) || it.body.contains(query, true)
        }
        when (sort) {
            SortOrder.NEWEST       -> result.sortedByDescending { it.timestamp }
            SortOrder.OLDEST       -> result.sortedBy { it.timestamp }
            SortOrder.HIGHEST_MOOD -> result.sortedByDescending { it.mood }
            SortOrder.LOWEST_MOOD  -> result.sortedBy { it.mood }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Drive sync status from unsynced count ─────────────────────────────────
    init {
        viewModelScope.launch {
            repository.getAllEntries().collect { entries ->
                val hasUnsynced = entries.any { !it.isSynced }
                if (_syncStatus.value != SyncStatus.SYNCING) {
                    _syncStatus.value = if (hasUnsynced) SyncStatus.PENDING else SyncStatus.ALL_SYNCED
                }
            }
        }
    }

    // ── On This Day ───────────────────────────────────────────────────────────
    val onThisDayEntries: StateFlow<List<LogEntry>> = repository.getAllEntries().map { entries ->
        val cal   = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0);  set(Calendar.MINUTE, 0)  }.timeInMillis
        val end   = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        entries.filter { it.timestamp in start..end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Stats ─────────────────────────────────────────────────────────────────
    val totalWordsLogged: StateFlow<Int> = repository.getAllEntries().map { entries ->
        entries.sumOf { it.body.split("\\s+".toRegex()).count { w -> w.isNotBlank() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
//chart
    val weeklyMoodData: StateFlow<List<Float>> = repository.getAllEntries().map { entries ->
        (0..6).map { offset ->
            val cal   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0);  set(Calendar.MINUTE, 0)  }.timeInMillis
            val end   = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
            val day   = entries.filter { it.timestamp in start..end }
            if (day.isEmpty()) 0f else day.map { it.mood }.average().toFloat()
        }.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { 0f })

    val currentStreak: StateFlow<Int> = repository.getAllTimestamps().map { timestamps ->
        if (timestamps.isEmpty()) return@map 0
        val days  = timestamps.map { it / 86400000L }.distinct().sortedDescending()
        val today = System.currentTimeMillis() / 86400000L
        if (days.isEmpty() || days[0] < today - 1) return@map 0
        var streak = 0; var expected = days[0]
        for (d in days) { if (d == expected) { streak++; expected-- } else break }
        streak
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val moodByCategory: StateFlow<Map<String, Float>> = repository.getAllEntries().map { entries ->
        entries.groupBy { it.category }
            .mapValues { (_, v) -> v.map { it.mood }.average().toFloat() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Manual sync ───────────────────────────────────────────────────────────
    fun syncNow() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                repository.syncPendingEntries()
                _syncStatus.value = SyncStatus.ALL_SYNCED
            } catch (_: Exception) {
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }

    // ── Entry by ID (for detail screen navigation) ────────────────────────────
    fun getEntryById(id: String): Flow<LogEntry?> =
        repository.getAllEntries().map { it.find { e -> e.id == id } }

    // ── Demo data ─────────────────────────────────────────────────────────────
    fun seedDemoData() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val now = System.currentTimeMillis(); val day = 86400000L
            listOf(
                LogEntry(userId = uid, title = "Viva Prep",       body = "Practicing the Memora demo for the panel.",            category = "Study",  mood = 4, timestamp = now - day),
                LogEntry(userId = uid, title = "Rainy Day Ramen", body = "Cozy hostel vibes with the girls.",                    category = "Food",   mood = 5, timestamp = now - day * 2),
                LogEntry(userId = uid, title = "Project Panic",   body = "Debugging Firebase took all night. Eyes burning.",     category = "Study",  mood = 2, timestamp = now - day * 3),
                LogEntry(userId = uid, title = "Gym PR 🏋️",       body = "Hit a new deadlift record. 80 kg. Legs like jelly.",  category = "Gym",    mood = 5, timestamp = now - day * 4),
                LogEntry(userId = uid, title = "Shopping Trip",   body = "Sarah and I found the best outfits for the social.",  category = "Social", mood = 5, timestamp = now - day * 5)
            ).forEach { repository.addEntry(it) }
        }
    }

    fun toggleDarkMode()           { _isDarkMode.value = !_isDarkMode.value }
    fun setCategory(cat: String)   { _selectedCategory.value = cat }
    fun setSearchQuery(q: String)  { _searchQuery.value = q }
    fun setSortOrder(o: SortOrder) { _sortOrder.value = o }
    fun deleteEntry(e: LogEntry)   { viewModelScope.launch { repository.deleteEntry(e) } }
}