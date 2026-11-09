package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.MediaType
import com.example.ui.HomeScreen
import com.example.ui.LibraryScreen
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LanguageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val app by lazy { application as SafePlayerApp }

    override fun attachBaseContext(newBase: Context) {
        val code = LanguageManager.getSavedLanguageCode(newBase)
        if (code != LanguageManager.CODE_SYSTEM && code.isNotBlank()) {
            super.attachBaseContext(LanguageManager.createLocalizedContext(newBase, code))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onResume() {
        super.onResume()
        app.securityManager.checkAutoLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.init(this)
        val code = LanguageManager.getSavedLanguageCode(this)
        val targetLocale = LanguageManager.getTargetLocale(this, code)
        Locale.setDefault(targetLocale)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentLangCode by LanguageManager.currentLanguageCode.collectAsStateWithLifecycle()
            val targetLocaleState = remember(currentLangCode) {
                LanguageManager.getTargetLocale(this, currentLangCode)
            }
            val layoutDirection = remember(currentLangCode) {
                LanguageManager.getLayoutDirection(this, currentLangCode)
            }
            val localizedConfiguration = remember(targetLocaleState) {
                Configuration(resources.configuration).apply {
                    setLocale(targetLocaleState)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setLocales(LocaleList(targetLocaleState))
                    }
                    setLayoutDirection(targetLocaleState)
                }
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection
            ) {
                val themeColor by com.example.util.ThemeManager.themeColor.collectAsStateWithLifecycle()
                val themeMode by com.example.util.ThemeManager.themeMode.collectAsStateWithLifecycle()
                MyApplicationTheme(themeColor = themeColor, themeMode = themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(app.repository, app.scanner)
                    )

                    val playerViewModel: PlayerViewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return PlayerViewModel(app, app.repository) as T
                            }
                        }
                    )

                    val securityConfig by app.securityManager.securityConfig.collectAsStateWithLifecycle(initialValue = null)
                    val isUnlocked by app.securityManager.isUnlocked.collectAsStateWithLifecycle()
                    val protectedMediaIds by app.securityManager.protectedMediaIds.collectAsStateWithLifecycle(initialValue = emptySet())

                    // Interrupted Task Recovery
                    val interruptedTasks by app.database.taskRecordDao().getTasksByStatusFlow("INTERRUPTED").collectAsStateWithLifecycle(initialValue = emptyList())
                    var dismissedTaskIds by remember { mutableStateOf(setOf<Long>()) }
                    val pendingRecoveryTask = interruptedTasks.firstOrNull { it.id !in dismissedTaskIds }

                    // Screen capture protection
                    LaunchedEffect(securityConfig?.isScreenshotProtectionEnabled, isUnlocked, securityConfig?.isAppLockEnabled) {
                        val isLocked = securityConfig?.isAppLockEnabled == true && !isUnlocked
                        if (securityConfig?.isScreenshotProtectionEnabled == true || isLocked) {
                            window.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    val navController = rememberNavController()

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                val scope = rememberCoroutineScope()
                                HomeScreen(
                                    viewModel = mainViewModel,
                                    playerViewModel = playerViewModel,
                                    protectedMediaIds = protectedMediaIds,
                                    onNavigateToPlayer = { item ->
                                        navController.navigate("player/${item.id}")
                                    },
                                    onNavigateToLibrary = { type ->
                                        navController.navigate("library/${type.name}")
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onNavigateToTaskCenter = {
                                        navController.navigate("task_center")
                                    },
                                    onNavigateToSecurity = {
                                        navController.navigate("security")
                                    },
                                    onNavigateToStorage = {
                                        navController.navigate("storage")
                                    },
                                    onNavigateToPlaylists = {
                                        navController.navigate("playlists")
                                    },
                                    onNavigateToSources = {
                                        navController.navigate("media_sources")
                                    },
                                    onNavigateToMediaDetails = { item ->
                                        navController.navigate("media_details/${item.id}")
                                    },
                                    onNavigateToEditing = { mediaId ->
                                        navController.navigate("editing/$mediaId")
                                    },
                                    onToggleVaultProtection = { mediaId ->
                                        scope.launch {
                                            app.securityManager.toggleItemProtection("MEDIA", mediaId)
                                        }
                                    },
                                    onNavigateToIptv = {
                                        navController.navigate("iptv_stream")
                                    },
                                    onNavigateToWifiShare = {
                                        navController.navigate("wifi_transfer")
                                    },
                                    onNavigateToAnalytics = {
                                        navController.navigate("watch_analytics")
                                    }
                                )
                            }
                            composable("iptv_stream") {
                                com.example.ui.streaming.IptvStreamScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() },
                                    onPlayStreamUrl = { url, title ->
                                        playerViewModel.loadStreamUrl(url, title)
                                        navController.navigate("player/-1")
                                    }
                                )
                            }
                            composable("wifi_transfer") {
                                com.example.ui.streaming.WifiTransferScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("watch_analytics") {
                                com.example.ui.analytics.WatchAnalyticsScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() },
                                    onPlayMedia = { mediaId ->
                                        navController.navigate("player/$mediaId")
                                    }
                                )
                            }
                            composable("settings") {
                                com.example.ui.settings.SettingsScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToAiModels = { navController.navigate("ai_models") },
                                    onNavigateToSources = { navController.navigate("media_sources") },
                                    onNavigateToPlaylists = { navController.navigate("playlists") },
                                    onNavigateToTasks = { navController.navigate("task_center") },
                                    onNavigateToSecurity = { navController.navigate("security") },
                                    onNavigateToStorage = { navController.navigate("storage") },
                                    onNavigateToIptv = { navController.navigate("iptv_stream") },
                                    onNavigateToWifiShare = { navController.navigate("wifi_transfer") },
                                    onNavigateToAnalytics = { navController.navigate("watch_analytics") }
                                )
                            }
                            composable("media_sources") {
                                com.example.ui.sources.MediaSourcesScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("playlists") {
                                com.example.ui.playlists.PlaylistsScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() },
                                    onPlayMedia = { mediaId ->
                                        navController.navigate("player/$mediaId")
                                    }
                                )
                            }
                            composable("storage") {
                                com.example.ui.storage.StorageDashboardScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("task_center") {
                                com.example.ui.tasks.TaskCenterScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("security") {
                                com.example.ui.security.SecurityScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() },
                                    onPlayMedia = { mediaId ->
                                        navController.navigate("player/$mediaId")
                                    }
                                )
                            }
                            composable("ai_models") {
                                com.example.ui.AiModelsScreen(
                                    app = app,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("library/{type}") { backStackEntry ->
                            val typeString = backStackEntry.arguments?.getString("type") ?: "VIDEO"
                            val type = MediaType.valueOf(typeString)
                            LibraryScreen(
                                viewModel = mainViewModel,
                                mediaType = type,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPlayer = { item ->
                                    navController.navigate("player/${item.id}")
                                }
                            )
                        }
                        composable("media_details/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                            com.example.ui.MediaDetailsScreen(
                                app = app,
                                mediaId = id,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayMedia = { mediaId ->
                                    navController.navigate("player/$mediaId")
                                },
                                onPlayScene = { mediaId, startMs ->
                                    navController.navigate("player/$mediaId?startMs=$startMs")
                                },
                                onNavigateToAiReview = { mediaId ->
                                    navController.navigate("ai_review/$mediaId")
                                },
                                onNavigateToEditing = { mediaId ->
                                    navController.navigate("editing/$mediaId")
                                }
                            )
                        }
                        composable("ai_review/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                            com.example.ui.AiReviewScreen(
                                app = app,
                                mediaId = id,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayScene = { mediaId, startMs ->
                                    navController.navigate("player/$mediaId?startMs=$startMs")
                                }
                            )
                        }
                        
                        composable(
                            route = "player/{id}?startMs={startMs}",
                            arguments = listOf(
                                androidx.navigation.navArgument("startMs") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                }
                            )
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                            val startMs = backStackEntry.arguments?.getString("startMs")?.toLongOrNull()
                            
                            LaunchedEffect(id, startMs) {
                                if (id > 0) {
                                    val item = app.repository.getMediaById(id).first()
                                    if (item != null) {
                                        playerViewModel.loadMediaItem(item, startMs)
                                        mainViewModel.markAsNotNew(id)
                                    }
                                }
                            }
                            
                            PlayerScreen(
                                viewModel = playerViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEditing = { mediaId ->
                                    navController.navigate("editing/$mediaId")
                                }
                            )
                        }
                        composable("editing/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                            com.example.ui.editing.EditingScreen(
                                app = app,
                                mediaId = id,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    com.example.ui.security.AppLockOverlay(
                        securityManager = app.securityManager,
                        onUnlocked = {}
                    )

                    if (pendingRecoveryTask != null) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { dismissedTaskIds = dismissedTaskIds + pendingRecoveryTask.id },
                            title = { androidx.compose.material3.Text("Interrupted Task Detected", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                            text = {
                                androidx.compose.material3.Text("The background task '${pendingRecoveryTask.title}' was interrupted. Would you like to resume it now?")
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        app.taskManager.retryTask(pendingRecoveryTask.id)
                                        dismissedTaskIds = dismissedTaskIds + pendingRecoveryTask.id
                                        navController.navigate("task_center")
                                    }
                                ) {
                                    androidx.compose.material3.Text("Resume Task")
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        app.taskManager.cancelTask(pendingRecoveryTask.id)
                                        dismissedTaskIds = dismissedTaskIds + pendingRecoveryTask.id
                                    }
                                ) {
                                    androidx.compose.material3.Text("Discard")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}
}
