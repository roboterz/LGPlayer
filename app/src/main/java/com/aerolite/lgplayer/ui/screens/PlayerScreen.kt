package com.aerolite.lgplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.aerolite.lgplayer.ui.PlayerViewModel
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
    val playbackError by viewModel.playbackError.collectAsState()
    val player by viewModel.player.collectAsState()
    val title by viewModel.displayTitle.collectAsState()

    // Manage system bars visibility for immersive playback
    DisposableEffect(isInPipMode) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (!isInPipMode) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    
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
            var startVolume = 0
            var startBrightness = 0f
            var totalDragY = 0f

            detectVerticalDragGestures(
                onDragStart = {
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                    if (startBrightness < 0) startBrightness = 0.5f
                    totalDragY = 0f
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    // 累计滑动位移（向上滑 dragAmount 为负，我们取负值使其向上为正）
                    totalDragY -= dragAmount
                    val isLeftSide = change.position.x < size.width / 2
                    
                    // 计算位移相对于屏幕高度的比例
                    val dragRatio = if (size.height > 0) totalDragY / size.height else 0f
                    
                    if (isLeftSide) {
                        // 亮度控制 (0.0 - 1.0)
                        activity?.let { act ->
                            val newBrightness = (startBrightness + dragRatio).coerceIn(0f, 1f)
                            brightness = newBrightness
                            val lp = act.window.attributes
                            lp.screenBrightness = newBrightness
                            act.window.attributes = lp
                        }
                    } else {
                        // 音量控制 (基于最大音量级数)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val volumeDelta = (dragRatio * maxVol).toInt()
                        val newVol = (startVolume + volumeDelta).coerceIn(0, maxVol)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            newVol,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                }
            )
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = !isInPipMode
                    playerViewInstance = this
                }
            },
            update = { view ->
                view.player = player
                view.useController = !isInPipMode
                playerViewInstance = view
            },
            modifier = Modifier
                .fillMaxSize()
                .then(if (isInPipMode) Modifier else Modifier.safeDrawingPadding())
        )

        if ((isBuffering || player == null) && !isInPipMode && playbackError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (playbackError != null && !isInPipMode) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playbackError!!,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                androidx.compose.material3.Button(
                    onClick = { 
                        player?.let {
                            it.prepare()
                            it.play()
                        }
                    }
                ) {
                    Text("Retry")
                }
            }
        }

        if (!isInPipMode && title != null) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
