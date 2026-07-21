package com.chronicle.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.chronicle.app.data.local.LogEntry
import com.chronicle.app.data.local.LogEntryDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val dao: LogEntryDao,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//We register a
//NetworkCallback with ConnectivityManager.
    init {
        // ── Watch for network to come back and auto-sync ───────────────────────
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network just came back — push any unsynced entries
                repoScope.launch { syncPendingEntries() }
            }
        })
    }

    // ── Room operations ───────────────────────────────────────────────────────
   //Multi-user isolation.
    fun getAllEntries(): Flow<List<LogEntry>> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return dao.getAllEntries()
        return dao.getEntriesByUser(uid)
    }

    fun getAllTimestamps(): Flow<List<Long>> = getAllEntries().map { list -> list.map { it.timestamp } }
//offline-first architecture.
    suspend fun addEntry(entry: LogEntry) {
        // 1. Always save to Room first (works offline)
        dao.insertEntry(entry)

        // 2. Try Firestore immediately if online
        if (isOnline()) {
            pushToFirestore(entry)
        }
        // If offline, the NetworkCallback will sync when connectivity returns
    }

    suspend fun deleteEntry(entry: LogEntry) {
        dao.deleteEntry(entry)
        // Best-effort delete from Firestore too
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            firestore.collection("users").document(uid)
                .collection("entries").document(entry.id)
                .delete().await()
        } catch (_: Exception) {}
    }

    // ── Sync all Room entries that are not yet in Firestore ───────────────────
    suspend fun syncPendingEntries() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val unsynced = dao.getUnsyncedEntries(uid)
            unsynced.forEach { entry -> pushToFirestore(entry) }
        } catch (_: Exception) {}
    }

    // ── Push one entry to Firestore and mark it synced in Room ────────────────
    private suspend fun pushToFirestore(entry: LogEntry) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val data = hashMapOf(
                "id"        to entry.id,
                "userId"    to entry.userId,
                "title"     to entry.title,
                "body"      to entry.body,
                "category"  to entry.category,
                "mood"      to entry.mood,
                "timestamp" to entry.timestamp,
                "imagePath" to entry.imagePath,
                "audioPath" to entry.audioPath,
                "isSynced"  to true
            )
            firestore.collection("users").document(uid)
                .collection("entries").document(entry.id)
                .set(data).await()

            // Mark as synced in Room
            dao.markAsSynced(entry.id)
        } catch (_: Exception) {
            // Will retry next time network is available
        }
    }

    // ── Check connectivity ────────────────────────────────────────────────────
    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}