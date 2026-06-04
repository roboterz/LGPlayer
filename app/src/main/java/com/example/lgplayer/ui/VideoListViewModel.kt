package com.example.lgplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lgplayer.data.MediaFile
import com.example.lgplayer.data.MediaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoListViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val mediaFiles: StateFlow<List<MediaFile>> = combine(_mediaFiles, _searchQuery) { files, query ->
        if (query.isBlank()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keeping 'videos' alias for compatibility if needed, but 'mediaFiles' is the new source
    val videos = mediaFiles 

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _mediaFiles.value = repository.getAllMedia()
            _isLoading.value = false
        }
    }

    // Legacy function name for compatibility with existing UI calls if any
    fun loadVideos() = loadMedia()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
