package com.booknext.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.local.prefs.UiPrefs
import com.booknext.app.ui.bookshelf.BookshelfScreen
import com.booknext.app.ui.category.CategoryScreen
import com.booknext.app.ui.cloud.CloudScreen
import com.booknext.app.ui.common.BookNextTheme
import com.booknext.app.ui.common.LocalAppTheme
import com.booknext.app.ui.drawer.DrawerContent
import com.booknext.app.ui.drawer.DrawerPage
import com.booknext.app.ui.local.LocalScreen
import com.booknext.app.ui.onlinelibrary.OnlineLibraryScreen
import com.booknext.app.ui.recent.RecentScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppState { WELCOME, LOGIN, MAIN }

sealed class Overlay {
    data class Reader(val bookId: String) : Overlay()
    data object Settings : Overlay()
    data object Quotes : Overlay()
    data object Stats : Overlay()
    data object Bookmarks : Overlay()
}

val LocalActivity = staticCompositionLocalOf<FragmentActivity> {
    error("No Activity provided")
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var accountPrefs: AccountPrefs
    @Inject lateinit var uiPrefs: UiPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeId by uiPrefs.themeId.collectAsState(initial = "blue")
            val darkMode by uiPrefs.darkMode.collectAsState(initial = false)
            val uiFontScale by uiPrefs.uiFontScale.collectAsState(initial = 1.0f)
            val uiFontFamily by uiPrefs.uiFontFamily.collectAsState(initial = "sans-serif")
            val uiLineSpacing by uiPrefs.uiLineSpacing.collectAsState(initial = 1.5f)
            val blueLight by uiPrefs.blueLight.collectAsState(initial = false)
            val blueLightAmount by uiPrefs.blueLightAmount.collectAsState(initial = 0.3f)
            val serverUrl by accountPrefs.serverUrl.collectAsState(initial = "")
            val apiKey by accountPrefs.apiKey.collectAsState(initial = "")
            val isLoggedIn = remember(serverUrl, apiKey) { serverUrl.isNotBlank() && apiKey.isNotBlank() }
            val scope = rememberCoroutineScope()

            var appState by remember { mutableStateOf(AppState.WELCOME) }
            LaunchedEffect(Unit) {
                // 直接从 DataStore 读，等待实际值加载完成
                val savedUrl = accountPrefs.serverUrl.first()
                val savedKey = accountPrefs.apiKey.first()
                val hasSeenWelcome = accountPrefs.hasSeenWelcome.first()
                appState = when {
                    savedUrl.isNotBlank() && savedKey.isNotBlank() -> AppState.MAIN
                    hasSeenWelcome -> AppState.LOGIN
                    else -> AppState.WELCOME
                }
            }

            BookNextTheme(
                themeId = themeId,
                darkTheme = darkMode,
                uiFontScale = uiFontScale,
                uiFontFamily = uiFontFamily,
                uiLineSpacing = uiLineSpacing,
            ) {
                CompositionLocalProvider(LocalActivity provides this@MainActivity) {
                    when (appState) {
                        AppState.WELCOME -> com.booknext.app.ui.welcome.WelcomeScreen(
                            onEnterLocal = {
                                scope.launch { accountPrefs.setHasSeenWelcome(true) }
                                appState = AppState.MAIN
                            },
                            onLogin = {
                                scope.launch { accountPrefs.setHasSeenWelcome(true) }
                                appState = AppState.LOGIN
                            },
                        )
                        AppState.LOGIN -> com.booknext.app.ui.login.LoginScreen(
                            onLoginSuccess = { appState = AppState.MAIN },
                            onBack = { appState = AppState.WELCOME },
                        )
                        AppState.MAIN -> MainDrawerScaffold(
                            isLoggedIn = isLoggedIn,
                            onLoginRequest = { appState = AppState.LOGIN },
                            serverUrl = serverUrl,
                            isDarkMode = darkMode,
                            onDarkModeToggle = {
                                scope.launch { uiPrefs.saveDarkMode(!darkMode) }
                            },
                            onLogout = {
                                scope.launch {
                                    accountPrefs.saveApiKey("")
                                    recreate()
                                }
                            },
                            blueLight = blueLight,
                            blueLightAmount = blueLightAmount,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawerScaffold(
    isLoggedIn: Boolean,
    onLoginRequest: () -> Unit,
    serverUrl: String,
    isDarkMode: Boolean,
    onDarkModeToggle: () -> Unit,
    onLogout: () -> Unit,
    blueLight: Boolean = false,
    blueLightAmount: Float = 0.3f,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentPage by rememberSaveable { mutableStateOf(DrawerPage.BOOKSHELF.name) }
    var overlayStack by remember { mutableStateOf(listOf<Overlay>()) }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var pendingCloudUpload by remember { mutableStateOf<android.net.Uri?>(null) }
    val currentOverlay = overlayStack.lastOrNull()

    fun pushOverlay(o: Overlay) { overlayStack = overlayStack + o }
    fun popOverlay() { overlayStack = overlayStack.dropLast(1) }

    BackHandler(enabled = overlayStack.isNotEmpty() || drawerState.isOpen) {
        when {
            overlayStack.isNotEmpty() -> popOverlay()
            drawerState.isOpen -> scope.launch { drawerState.close() }
        }
    }

    when (val overlay = currentOverlay) {
        is Overlay.Reader -> {
            com.booknext.app.ui.reader.ReaderScreen(
                bookId = overlay.bookId,
                onBack = { popOverlay() },
            )
            return
        }
        is Overlay.Settings -> {
            com.booknext.app.ui.settings.SettingsScreen(
                onBack = { popOverlay() },
                onLogout = {
                    overlayStack = emptyList()
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
            return
        }
        is Overlay.Quotes -> {
            com.booknext.app.ui.quotes.QuotesScreen(onBack = { popOverlay() })
            return
        }
        is Overlay.Stats -> {
            com.booknext.app.ui.stats.StatsScreen(onBack = { popOverlay() })
            return
        }
        is Overlay.Bookmarks -> {
            com.booknext.app.ui.bookmarks.BookmarksScreen(
                onBack = { popOverlay() },
                onBookClick = { pushOverlay(Overlay.Reader(it)) },
            )
            return
        }
        null -> { /* continue to main content */ }
    }

    val currentThemeId = LocalAppTheme.current.id

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    currentPage = DrawerPage.valueOf(currentPage),
                    serverUrl = serverUrl,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = {
                        scope.launch { drawerState.close() }
                        onLoginRequest()
                    },
                    onPageSelect = { page ->
                        currentPage = page.name
                        scope.launch { drawerState.close() }
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        pushOverlay(Overlay.Settings)
                    },
                    onDarkModeToggle = onDarkModeToggle,
                    isDarkMode = isDarkMode,
                    onLogout = onLogout,
                )
            },
        ) {
            AnimatedContent(
                targetState = "$currentPage-$currentThemeId",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page_switch",
            ) { key ->
                val page = DrawerPage.valueOf(key.substringBefore("-"))
                when (page) {
                    DrawerPage.RECENT -> RecentScreen(
                        onBookClick = { pushOverlay(Overlay.Reader(it)) },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateToNotes = { pushOverlay(Overlay.Quotes) },
                        onNavigateToStats = { pushOverlay(Overlay.Stats) },
                        onNavigateToFavorites = {
                            currentPage = DrawerPage.BOOKSHELF.name
                            showFavoritesOnly = true
                        },
                        onNavigateToBookmarks = { pushOverlay(Overlay.Bookmarks) },
                    )
                    DrawerPage.BOOKSHELF -> BookshelfScreen(
                        onBookClick = { pushOverlay(Overlay.Reader(it.bookId)) },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onUploadClick = { currentPage = DrawerPage.CLOUD.name },
                        showFavoritesOnly = showFavoritesOnly,
                        onFavoritesFilterCleared = { showFavoritesOnly = false },
                        isLoggedIn = isLoggedIn,
                    )
                    DrawerPage.CATEGORY -> CategoryScreen(
                        onBookClick = { pushOverlay(Overlay.Reader(it)) },
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    DrawerPage.LOCAL -> LocalScreen(
                        onBookClick = { pushOverlay(Overlay.Reader(it)) },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        isLoggedIn = isLoggedIn,
                        onImportToCloud = { uri ->
                            pendingCloudUpload = uri
                            currentPage = DrawerPage.CLOUD.name
                        },
                    )
                    DrawerPage.CLOUD -> CloudScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBookClick = { pushOverlay(Overlay.Reader(it)) },
                        isLoggedIn = isLoggedIn,
                        pendingUploadUri = pendingCloudUpload.also { pendingCloudUpload = null },
                    )
                    DrawerPage.ONLINE_LIBRARY -> OnlineLibraryScreen(
                        onBack = { currentPage = DrawerPage.RECENT.name },
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                }
            }
        }

        if (blueLight) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(1f, 0.7f, 0.4f, (blueLightAmount * 0.2f).coerceIn(0f, 0.4f)))
            )
        }
    }
}
