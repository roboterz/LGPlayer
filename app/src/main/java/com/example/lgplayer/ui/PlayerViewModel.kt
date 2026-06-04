package com.example.lgplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.lgplayer.PlaybackService
import com.example.lgplayer.data.database.LGPlayerDatabase
import com.example.lgplayer.data.database.PlaybackProgress
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LGPlayerDatabase.getDatabase(application)
    private val playbackDao = database.playbackDao()
    private var progressJob: Job? = null
    
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _displayTitle = MutableStateFlow("Loading...")
    val displayTitle: StateFlow<String> = _displayTitle.asStateFlow()

    private var currentUri: String? = null
    private var currentPlaybackKey: String? = null
    private var hasResumedProgress = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            mediaMetadata.title?.let { _displayTitle.value = it.toString() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            
            val p = _player.value
            val key = currentPlaybackKey
            if (playbackState == Player.STATE_READY && !hasResumedProgress && p != null && key != null) {
                hasResumedProgress = true
                viewModelScope.launch {
                    val savedProgress = playbackDao.getProgress(key)
                    savedProgress?.let {
                        if (it.position < p.duration && it.position > 0) {
                            p.seekTo(it.position)
                        }
                    }
                }
            }
        }
    }

    init {
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _player.value = controller
                controller.addListener(playerListener)
                currentUri?.let { uri -> 
                    performLoad(controller, uri) 
                }
            } catch (e: Exception) {
                _player.value = null
            }
        }, MoreExecutors.directExecutor())

        startProgressSaving()
    }

    fun load(uri: String, title: String?) {
        // Remove the return check to force reload/resume when requested
        currentUri = uri
        _displayTitle.value = title ?: getFileName(getApplication(), Uri.parse(uri))
        currentPlaybackKey = getFileFingerprint(getApplication(), Uri.parse(uri))
        hasResumedProgress = false
        
        val p = _player.value
        if (p != null) {
            performLoad(p, uri)
        }
    }

    private fun performLoad(player: Player, uri: String) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(currentPlaybackKey ?: uri)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(_displayTitle.value)
                    .build()
            )
            .build()
        
        player.setMediaItem(mediaItem, true)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

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

    private fun getFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (e: Exception) {}
        return uri.lastPathSegment ?: "Unknown"
    }

    private fun startProgressSaving() {
        progressJob = viewModelScope.launch {
            while (isActive) {
                val p = _player.value
                val key = currentPlaybackKey
                if (p != null && p.isPlaying && key != null) {
                    saveProgress(p, key)
                }
                delay(2000)
            }
        }
    }

    private suspend fun saveProgress(player: Player, key: String) {
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration > 0) {
            playbackDao.saveProgress(
                PlaybackProgress(
                    mediaUri = key,
                    position = currentPosition,
                    duration = duration
                )
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        _player.value?.let { p ->
            val key = currentPlaybackKey
            if (key != null) {
                val currentPosition = p.currentPosition
                val duration = p.duration
                if (duration > 0) {
                    val progress = PlaybackProgress(
                        mediaUri = key,
                        position = currentPosition,
                        duration = duration
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        playbackDao.saveProgress(progress)
                    }
                }
            }
            p.removeListener(playerListener)
        }
        _player.value = null
        controllerFuture?.cancel(true)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
