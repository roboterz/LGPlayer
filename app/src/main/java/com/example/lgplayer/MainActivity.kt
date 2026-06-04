package com.example.lgplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.lgplayer.data.MediaRepository
import com.example.lgplayer.ui.PlayerViewModel
import com.example.lgplayer.ui.VideoListViewModel
import com.example.lgplayer.ui.navigation.Route
import com.example.lgplayer.ui.screens.PlayerScreen
import com.example.lgplayer.ui.screens.VideoListScreen
import com.example.lgplayer.ui.theme.LgplayerTheme

class MainActivity : ComponentActivity() {
    private var currentPlayerViewModel: PlayerViewModel? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val player = currentPlayerViewModel?.player?.value
            when (intent.action) {
                ACTION_PLAY -> player?.play()
                ACTION_PAUSE -> player?.pause()
            }
        }
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = MediaRepository(applicationContext)

        setContent {
            LgplayerTheme {
                val backStack = remember { 
                    val initialRoute = handleIntent(intent) ?: Route.VideoList
                    mutableStateListOf<Route>(initialRoute) 
                }

                // Handle new intents when activity is already running
                DisposableEffect(Unit) {
                    val listener = androidx.core.util.Consumer<android.content.Intent> { newIntent ->
                        handleIntent(newIntent)?.let { route ->
                            if (backStack.lastOrNull() != route) {
                                backStack.removeAll { it is Route.Player }
                                backStack.add(route)
                            }
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val isInPipMode = rememberIsInPipMode()

                val windowAdaptiveInfo = currentWindowAdaptiveInfo()
                val directive = remember(windowAdaptiveInfo) {
                    calculatePaneScaffoldDirective(windowAdaptiveInfo)
                        .copy(horizontalPartitionSpacerSize = 0.dp)
                }
                
                val listDetailStrategy = rememberListDetailSceneStrategy<Route>(directive = directive)

                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    sceneStrategy = listDetailStrategy,
                    entryProvider = entryProvider {
                        entry<Route.VideoList>(
                            metadata = ListDetailSceneStrategy.listPane(
                                detailPlaceholder = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Select a video to play")
                                    }
                                }
                            )
                        ) {
                            val viewModel: VideoListViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return VideoListViewModel(applicationContext, repository) as T
                                    }
                                }
                            )
                            VideoListScreen(
                                viewModel = viewModel,
                                onVideoClick = { mediaId ->
                                    val media = viewModel.mediaFiles.value.find { it.id == mediaId }
                                    media?.let {
                                        val route = Route.Player(it.uri.toString(), it.name)
                                        backStack.removeAll { r -> r is Route.Player }
                                        backStack.add(route)
                                    }
                                }
                            )
                        }
                        entry<Route.Player>(
                            metadata = ListDetailSceneStrategy.detailPane()
                        ) { route ->
                            val playerViewModel: PlayerViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return PlayerViewModel(applicationContext) as T
                                    }
                                }
                            )
                            
                            // Reactively load the media whenever the route changes
                            LaunchedEffect(route.videoUri) {
                                playerViewModel.load(route.videoUri, route.title)
                            }

                            DisposableEffect(playerViewModel) {
                                currentPlayerViewModel = playerViewModel
                                onDispose {
                                    if (currentPlayerViewModel == playerViewModel) {
                                        currentPlayerViewModel = null
                                    }
                                }
                            }

                            val isPlaying by playerViewModel.isPlaying.collectAsState()
                            LaunchedEffect(isPlaying) {
                                if (isInPipMode) {
                                    updatePipParams(isPlaying)
                                }
                            }
                            PlayerScreen(
                                viewModel = playerViewModel,
                                isInPipMode = isInPipMode
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (currentPlayerViewModel != null) {
            enterPip()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PLAY)
                addAction(ACTION_PAUSE)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                pipReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipReceiver)
    }

    private fun handleIntent(intent: android.content.Intent?): Route? {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                // Try to get the display name from the intent or URI
                val displayName = intent.getStringExtra(Intent.EXTRA_TITLE)
                    ?: getFileNameFromUri(uri)
                return Route.Player(uri.toString(), displayName)
            }
        }
        return null
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0)
                    }
                }
            } catch (e: Exception) {
                // fallback to lastPathSegment
            }
        }
        return uri.lastPathSegment
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = currentPlayerViewModel?.player?.value?.isPlaying ?: false
            enterPictureInPictureMode(buildPipParams(isPlaying))
        }
    }

    private fun updatePipParams(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(buildPipParams(isPlaying))
        }
    }

    private fun buildPipParams(isPlaying: Boolean): PictureInPictureParams {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(
                    listOf(
                        if (isPlaying) {
                            android.app.RemoteAction(
                                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                                "Pause",
                                "Pause",
                                PendingIntent.getBroadcast(
                                    this, REQUEST_PAUSE,
                                    Intent(ACTION_PAUSE).setPackage(packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        } else {
                            android.app.RemoteAction(
                                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                                "Play",
                                "Play",
                                PendingIntent.getBroadcast(
                                    this, REQUEST_PLAY,
                                    Intent(ACTION_PLAY).setPackage(packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    )
                )
                .build()
        } else {
            error("PiP not supported")
        }
    }

    companion object {
        private const val ACTION_PLAY = "com.example.lgplayer.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.example.lgplayer.ACTION_PAUSE"
        private const val REQUEST_PLAY = 1
        private const val REQUEST_PAUSE = 2
    }
}

@Composable
fun rememberIsInPipMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is ComponentActivity) break
            c = c.baseContext
        }
        c as? ComponentActivity
    } ?: return false

    var pipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
            pipMode = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(observer)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
    }
    return pipMode
}
