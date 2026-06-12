package com.aerolite.lgplayer.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aerolite.lgplayer.data.MediaFile
import com.aerolite.lgplayer.data.MediaRepository
import com.aerolite.lgplayer.data.MediaType
import com.aerolite.lgplayer.data.database.LGPlayerDatabase
import com.aerolite.lgplayer.data.database.PlaybackProgress
import com.aerolite.lgplayer.data.database.PlaylistItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoListViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {
    private val database = LGPlayerDatabase.getDatabase(context)
    private val playlistDao = database.playlistDao()
    private val playbackDao = database.playbackDao()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val playlist: StateFlow<List<PlaylistItem>> = playlistDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _historyProgress = MutableStateFlow<List<PlaybackProgress>>(emptyList())
    // History now works independently of the playlist items
    val history: StateFlow<List<PlaylistItem>> = _historyProgress.map { progressList ->
        progressList.map { progress ->
            PlaylistItem(
                mediaUri = progress.originalUri, // Use the real URI for playback
                name = progress.name,
                duration = progress.duration,
                size = 0, // Not stored in progress
                type = progress.type,
                addedAt = progress.lastUpdated
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _historyProgress.value = playbackDao.getAllHistory()
            _isLoading.value = false
        }
    }

    fun addToPlaylist(uri: Uri) {
        viewModelScope.launch {
            val name = getFileName(context, uri)
            val size = getFileSize(context, uri)
            val type = if (name.lowercase().let { it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".wav") }) 
                MediaType.AUDIO else MediaType.VIDEO
            
            playlistDao.addItem(
                PlaylistItem(
                    mediaUri = uri.toString(),
                    name = name,
                    duration = 0,
                    size = size,
                    type = type
                )
            )
        }
    }

    fun removeFromPlaylist(item: PlaylistItem) {
        viewModelScope.launch {
            playlistDao.removeItem(item)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun getFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (e: Exception) {}
        return uri.lastPathSegment ?: "Unknown"
    }
    
    private fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                return afd.length
            }
        } catch (e: Exception) {}
        return 0
    }
}
