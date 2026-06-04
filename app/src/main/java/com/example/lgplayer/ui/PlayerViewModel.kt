package com.example.lgplayer.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
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
class PlayerViewModel(
    context: Context,
    private val videoUri: String,
    initialTitle: String? = null
) : ViewModel() {
    private val database = LGPlayerDatabase.getDatabase(context)
    private val playbackDao = database.playbackDao()
    private var progressJob: Job? = null
    
    // Stable fingerprint: Name + Size
    private val playbackKey = getFileFingerprint(context, Uri.parse(videoUri))

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _displayTitle = MutableStateFlow(initialTitle ?: getFileName(context, Uri.parse(videoUri)))
    val displayTitle: StateFlow<String> = _displayTitle.asStateFlow()

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
            if (playbackState == Player.STATE_READY && !hasResumedProgress) {
                hasResumedProgress = true
                viewModelScope.launch {
                    val savedProgress = playbackDao.getProgress(playbackKey)
                    savedProgress?.let {
                        val p = _player.value
                        if (p != null && it.position < p.duration && it.position > 0) {
                            p.seekTo(it.position)
                        }
                    }
                }
            }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _player.value = controller
                setupPlayer(controller)
            } catch (e: Exception) {
                _player.value = null
            }
        }, MoreExecutors.directExecutor())

        startProgressSaving()
    }

    private fun getFileFingerprint(context: Context, uri: Uri): String {
        var name = ""
        var size = -1L
        
        try {
            // Use AssetFileDescriptor for a more reliable size across all URI types
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                size = afd.length
            }
        } catch (e: Exception) {}

        try {
            // Get Display Name
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: ""
                }
            }
        } catch (e: Exception) {}

        if (name.isEmpty()) name = uri.lastPathSegment ?: "unknown"
        
        // Handle file scheme specifically if size is still not found
        if (size <= 0 && uri.scheme == "file") {
            try {
                size = java.io.File(uri.path!!).length()
            } catch (e: Exception) {}
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

    private fun setupPlayer(player: Player) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(playbackKey)
            .setUri(Uri.parse(videoUri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(_displayTitle.value)
                    .build()
            )
            .build()
        
        // Forced reset sequence
        player.pause()
        player.stop()
        player.clearMediaItems()
        
        player.setMediaItem(mediaItem)
        player.prepare()
        player.addListener(playerListener)
        player.playWhenReady = true
        player.play()
    }

    private fun startProgressSaving() {
        progressJob = viewModelScope.launch {
            while (isActive) {
                val p = _player.value
                if (p != null && p.isPlaying) {
                    saveProgress(p)
                }
                delay(2000)
            }
        }
    }

    private suspend fun saveProgress(player: Player) {
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration > 0) {
            playbackDao.saveProgress(
                PlaybackProgress(
                    mediaUri = playbackKey,
                    position = currentPosition,
                    duration = duration
                )
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        _player.value?.let { p ->
            val currentPosition = p.currentPosition
            val duration = p.duration
            if (duration > 0) {
                val progress = PlaybackProgress(
                    mediaUri = playbackKey,
                    position = currentPosition,
                    duration = duration
                )
                CoroutineScope(Dispatchers.IO).launch {
                    playbackDao.saveProgress(progress)
                }
            }
            p.removeListener(playerListener)
        }
        _player.value = null
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
