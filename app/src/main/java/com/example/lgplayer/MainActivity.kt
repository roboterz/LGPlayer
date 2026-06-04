package com.example.lgplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
                                        return VideoListViewModel(repository) as T
                                    }
                                }
                            )
                            VideoListScreen(
                                viewModel = viewModel,
                                onVideoClick = { mediaId ->
                                    val media = viewModel.mediaFiles.value.find { it.id == mediaId }
                                    media?.let {
                                        val route = Route.Player(it.uri.toString())
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
                                key = route.videoUri,
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return PlayerViewModel(applicationContext, route.videoUri) as T
                                    }
                                }
                            )
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
        enterPip()
    }

    private fun handleIntent(intent: android.content.Intent?): Route? {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                return Route.Player(uri.toString())
            }
        }
        return null
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
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
