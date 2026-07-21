package com.chronicle.app.presentation.addentry

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronicle.app.data.local.LogEntry
import com.chronicle.app.data.repository.EntryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val repository: EntryRepository,
    @ApplicationContext private val context: Context // Inject context to handle URI permissions
) : ViewModel() {

    fun saveEntry(title: String, body: String, category: String, mood: Int, imagePath: String?, audioPath: String?) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

            // --- PERMISSION PERSISTENCE LOGIC ---
            // This ensures images load even after the phone restarts
            imagePath?.let { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    // We check if it's a gallery/content URI (not a raw file path)
                    if (uriString.startsWith("content://")) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // Log error if permission cannot be persisted
                }
            }

            // --- SAVE TO REPOSITORY ---
            repository.addEntry(
                LogEntry(
                    userId    = uid,
                    title     = title,
                    body      = body,
                    category  = category,
                    mood      = mood,
                    timestamp = System.currentTimeMillis(),
                    imagePath = imagePath,
                    audioPath = audioPath,
                    isSynced  = false
                )
            )
        }
    }
}