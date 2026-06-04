package com.example.lgplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.lgplayer.ui.PlayerViewModel
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isInPipMode: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val isBuffering by viewModel.isBuffering.collectAsState()
    
    // Brightness state (0.0 to 1.0)
    var brightness by remember { 
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: -1f)
    }

    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }
    
    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    playerViewInstance?.showController()
                }
            )
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures { change, dragAmount ->
                change.consume()
                val isLeftSide = change.position.x < size.width / 2
                if (isLeftSide) {
                    // Brightness control
                    activity?.let { act ->
                        val currentBrightness = if (brightness < 0) 0.5f else brightness
                        val dragDelta = if (size.height > 0) dragAmount / size.height else 0f
                        val newBrightness = (currentBrightness - dragDelta).coerceIn(0f, 1f)
                        brightness = newBrightness
                        val lp = act.window.attributes
                        lp.screenBrightness = newBrightness
                        act.window.attributes = lp
                    }
                } else {
                    // Volume control
                    val direction = if (dragAmount > 0) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        direction,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
            }
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = !isInPipMode
                    playerViewInstance = this
                }
            },
            update = { view ->
                view.player = viewModel.player
                view.useController = !isInPipMode
                playerViewInstance = view
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && !isInPipMode) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
