package com.example.lgplayer.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lgplayer.data.MediaFile
import com.example.lgplayer.data.MediaRepository
import com.example.lgplayer.data.database.LGPlayerDatabase
import com.example.lgplayer.data.database.PlaybackProgress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoListViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {
    private val playbackDao = LGPlayerDatabase.getDatabase(context).playbackDao()
    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    private val _history = MutableStateFlow<List<PlaybackProgress>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val history: StateFlow<List<MediaFile>> = combine(_history, _mediaFiles) { historyItems, allFiles ->
        historyItems.mapNotNull { historyItem ->
            allFiles.find { file -> 
                file.uri.toString() == historyItem.mediaUri || 
                getFileFingerprint(context, file.uri) == historyItem.mediaUri
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaFiles: StateFlow<List<MediaFile>> = combine(_mediaFiles, _searchQuery) { files, query ->
        if (query.isBlank()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun getFileFingerprint(context: Context, uri: Uri): String {
        var name = ""
        var size = -1L
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                size = afd.length
            }
        } catch (e: Exception) {}
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: ""
            }
        } catch (e: Exception) {}
        if (name.isEmpty()) name = uri.lastPathSegment ?: "unknown"
        if (size <= 0 && uri.scheme == "file") {
            try { size = java.io.File(uri.path!!).length() } catch (e: Exception) {}
        }
        return "stable_${name}_$size"
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            val files = repository.getAllMedia()
            _mediaFiles.value = files
            _history.value = playbackDao.getAllHistory()
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
