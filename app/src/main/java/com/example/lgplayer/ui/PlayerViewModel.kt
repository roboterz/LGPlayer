package com.example.lgplayer.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.lgplayer.data.database.LGPlayerDatabase
import com.example.lgplayer.data.database.PlaybackProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlayerViewModel(
    context: Context,
    private val videoUri: String
) : ViewModel() {
    private val database = LGPlayerDatabase.getDatabase(context)
    private val playbackDao = database.playbackDao()
    private var progressJob: Job? = null

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var hasResumedProgress = false

    val player = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
        prepare()
        playWhenReady = true
        
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && !hasResumedProgress) {
                    hasResumedProgress = true
                    viewModelScope.launch {
                        val savedProgress = playbackDao.getProgress(videoUri)
                        savedProgress?.let {
                            if (it.position < duration && it.position > 0) {
                                seekTo(it.position)
                            }
                        }
                    }
                }
            }
        })
    }

    init {
        startProgressSaving()
    }

    private fun startProgressSaving() {
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    saveProgress()
                }
                delay(2000) // Save every 2 seconds
            }
        }
    }

    private suspend fun saveProgress() {
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration > 0) {
            playbackDao.saveProgress(
                PlaybackProgress(
                    mediaUri = videoUri,
                    position = currentPosition,
                    duration = duration
                )
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        // Final save
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration > 0) {
            val progress = PlaybackProgress(
                mediaUri = videoUri,
                position = currentPosition,
                duration = duration
            )
            CoroutineScope(Dispatchers.IO).launch {
                playbackDao.saveProgress(progress)
            }
        }
        player.release()
        super.onCleared()
    }
}
