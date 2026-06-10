package com.booknext.app.ui.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.local.db.ReadingSessionEntity
import com.booknext.app.ui.reader.options.VisualOptions
import com.booknext.app.ui.reader.options.VisualOptionsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class TocEntry(val title: String, val index: Int)

data class ReaderToolbarState(
    val title: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val darkMode: Boolean = false,
    val fontSize: Int = 17,
    val brightness: Float = -1f,
    val screenOrientation: String = "auto",
    val isTtsPlaying: Boolean = false,
    val isTtsLoading: Boolean = false,
    val tocEntries: List<TocEntry> = emptyList(),
    val bookmarks: List<Int> = emptyList(),
    val showStatusBar: Boolean = false,
    val showNavBar: Boolean = false,
    val showProgressBar: Boolean = false,
)

@Composable
fun ReaderToolbarOverlay(
    state: ReaderToolbarState,
    visible: Boolean,
    onBack: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onOrientationChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onTtsStart: () -> Unit,
    onTtsStop: () -> Unit,
    onTocJump: (Int) -> Unit,
    onAddBookmark: () -> Unit,
    onAutoScroll: (mode: String, speed: Float) -> Unit = { _, _ -> },
    onSearch: (query: String, nthMatch: Int) -> Unit = { _, _ -> },
    onSearchRequest: () -> Unit = {},
    onReplaceAll: (from: String, to: String) -> Unit = { _, _ -> },
    book: BookEntity? = null,
    sessions: List<ReadingSessionEntity> = emptyList(),
    coverUrl: String? = null,
    onToggleFavorite: () -> Unit = {},
    onPrevChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onPageTextCopy: () -> Unit = {},
    onBookmarkManage: () -> Unit = {},
    currentVisualOptions: VisualOptions = VisualOptions(),
    currentControlOptions: com.booknext.app.ui.reader.options.ControlOptions = com.booknext.app.ui.reader.options.ControlOptions(),
    currentOtherOptions: com.booknext.app.ui.reader.options.OtherOptions = com.booknext.app.ui.reader.options.OtherOptions(),
    onSaveVisualOptions: (VisualOptions) -> Unit = {},
    onSaveControlOptions: (com.booknext.app.ui.reader.options.ControlOptions) -> Unit = {},
    onSaveOtherOptions: (com.booknext.app.ui.reader.options.OtherOptions) -> Unit = {},
    nameReplacements: String = "",
    onNameReplaceChange: (String) -> Unit = {},
    onSaveSetting: (key: String, value: Any) -> Unit = { _, _ -> },
    onSetTranslateEngine: (String) -> Unit = {},
    onSetTranslateTargetLang: (String) -> Unit = {},
    onTranslateText: () -> Unit = {},
    onDictionaryLookup: () -> Unit = {},
    onSetReaderBgColor: (String) -> Unit = {},
    readerBgColor: String = "",
    ttsState: TtsState = TtsState(),
    onTtsStateChange: (TtsState) -> Unit = {},
    onOpenTtsSettings: () -> Unit = {},
    onSetCoverFromScreenshot: (bookId: String, filePath: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val captureScope = rememberCoroutineScope()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val defaultDarkToolbar = Color(android.graphics.Color.parseColor("#1E2428")).copy(alpha = 0.92f)
    val toolbarBg = remember(readerBgColor, state.darkMode, surfaceColor) {
        if (readerBgColor.isNotBlank()) {
            try {
                Color(android.graphics.Color.parseColor(readerBgColor)).copy(alpha = 0.92f)
            } catch (_: Exception) {
                if (state.darkMode) defaultDarkToolbar else surfaceColor
            }
        } else if (state.darkMode) {
            defaultDarkToolbar
        } else {
            surfaceColor
        }
    }

    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showBrightnessPanel by remember { mutableStateOf(false) }
    var showFontPanel by remember { mutableStateOf(false) }
    var showTtsPanel by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var showVisualOptions by remember { mutableStateOf(false) }
    var showControlOptions by remember { mutableStateOf(false) }
    var showOtherOptions by remember { mutableStateOf(false) }
    var showNameReplace by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showTranslateSettings by remember { mutableStateOf(false) }
    var showBgColor by remember { mutableStateOf(false) }
    var showReadingSettings by remember { mutableStateOf(false) }
    var showTtsSettings by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                tonalElevation = 4.dp,
                color = toolbarBg,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = if (state.showStatusBar) 0.dp else 8.dp)
                        .height(72.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "${state.currentPage + 1} / ${state.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Box {
                        IconButton(onClick = { showMoreActions = !showMoreActions }) {
                            Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showMoreActions,
                            onDismissRequest = { showMoreActions = false },
                        ) {
                            listOf(
                                "字体设置" to "Font",
                                "目录" to "Toc",
                                "书签" to "Bookmarks",
                                "搜索" to "Search",
                                "背景" to "BgColor",
                                "阅读设置" to "ReadingSettings",
                                "词典" to "Dictionary",
                                "翻译设置" to "TranslateSettings",
                                "设为封面" to "SetCover",
                            ).forEach { (label, action) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showMoreActions = false
                                        when (action) {
                                            "Font" -> showVisualOptions = true
                                            "Toc" -> showToc = true
                                            "Bookmarks" -> showBookmarks = true
                                            "Search" -> { showSearchBar = true; onSearchRequest() }
                                            "BgColor" -> showBgColor = true
                                            "ReadingSettings" -> showReadingSettings = true
                                            "Dictionary" -> onDictionaryLookup()
                                            "TranslateSettings" -> showTranslateSettings = true
                                            "SetCover" -> {
                                                val b = book ?: return@DropdownMenuItem
                                                val ctx = context
                                                captureScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val act = ctx as? Activity ?: return@launch
                                                        val decor = act.window.decorView
                                                        val w = decor.width
                                                        val h = decor.height
                                                        if (w <= 0 || h <= 0) return@launch
                                                        val fullBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                        val canvas = android.graphics.Canvas(fullBitmap)
                                                        decor.draw(canvas)
                                                        val rect = android.graphics.Rect()
                                                        decor.getWindowVisibleDisplayFrame(rect)
                                                        val top = rect.top
                                                        val cropHeight = rect.height()
                                                        val visibleBitmap = if (top > 0 || cropHeight < h) {
                                                            Bitmap.createBitmap(fullBitmap, 0, top, w, cropHeight)
                                                        } else { fullBitmap }
                                                        if (visibleBitmap !== fullBitmap) fullBitmap.recycle()
                                                        val coverRatio = 0.72f
                                                        val targetH = (w / coverRatio).toInt().coerceAtMost(visibleBitmap.height)
                                                        val finalBitmap = if (targetH < visibleBitmap.height) {
                                                            Bitmap.createBitmap(visibleBitmap, 0, 0, w, targetH)
                                                        } else { visibleBitmap }
                                                        if (finalBitmap !== visibleBitmap) visibleBitmap.recycle()
                                                        val dir = File(ctx.filesDir, "covers")
                                                        dir.mkdirs()
                                                        val coverFile = File(dir, "${b.bookId}.jpg")
                                                        FileOutputStream(coverFile).use { out ->
                                                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                                        }
                                                        finalBitmap.recycle()
                                                        onSetCoverFromScreenshot(b.bookId, coverFile.absolutePath)
                                                        (ctx as? Activity)?.runOnUiThread {
                                                            android.widget.Toast.makeText(ctx, "已设为封面", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.w("ReaderToolbar", "截图设为封面失败: ${e.message}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                tonalElevation = 4.dp,
                color = toolbarBg,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 8.dp),
                ) {
                    if (state.showProgressBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.currentPage + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Slider(
                            value = state.currentPage.toFloat(),
                            onValueChange = { onPageChange(it.toInt()) },
                            valueRange = 0f..(state.totalPages - 1).toFloat().coerceAtLeast(1f),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(
                            "${state.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ToolbarIconButton(
                            icon = if (state.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (state.darkMode) "日间" else "夜间",
                            onClick = { onDarkModeChange(!state.darkMode) },
                            onLongClick = {},
                        )
                        ToolbarIconButton(
                            icon = Icons.Default.Brightness6,
                            label = "亮度",
                            onClick = {
                                showBrightnessPanel = !showBrightnessPanel
                                showFontPanel = false
                                showTtsPanel = false
                            },
                            onLongClick = {},
                        )
                        ToolbarIconButton(
                            icon = Icons.Default.Translate,
                            label = "翻译",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { onTranslateText() },
                            onLongClick = {},
                        )
                        ToolbarIconButton(
                            icon = Icons.Default.FormatSize,
                            label = "字号",
                            onClick = {
                                showFontPanel = !showFontPanel
                                showBrightnessPanel = false
                                showTtsPanel = false
                            },
                            onLongClick = {},
                        )
                        ToolbarIconButton(
                            icon = Icons.Default.RecordVoiceOver,
                            label = "朗读",
                            tint = if (state.isTtsPlaying) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                if (state.isTtsPlaying) {
                                    showTtsPanel = !showTtsPanel
                                } else {
                                    onTtsStart()
                                    showTtsPanel = true
                                    showFontPanel = false
                                    showBrightnessPanel = false
                                }
                            },
                            onLongClick = {},
                        )
                    }

                    AnimatedVisibility(visible = showBrightnessPanel) {
                        BrightnessPanel(
                            brightness = state.brightness,
                            onBrightnessChange = { v ->
                                onBrightnessChange(v)
                                applyBrightness(activity, v)
                            },
                        )
                    }

                    AnimatedVisibility(visible = showFontPanel) {
                        FontPanel(
                            fontSize = state.fontSize,
                            onFontSizeChange = onFontSizeChange,
                        )
                    }

                    AnimatedVisibility(visible = showTtsPanel) {
                        TtsControlPanel(
                            isTtsPlaying = state.isTtsPlaying,
                            onStop = { onTtsStop(); showTtsPanel = false },
                            onTogglePlay = { if (state.isTtsPlaying) onTtsStop() else onTtsStart() },
                            onSettings = { showTtsSettings = true },
                            isLoading = state.isTtsLoading,
                        )
                    }
                }
            }
        }

        if (showTtsSettings) {
            TtsSettingsDialog(
                ttsState = ttsState,
                onTtsStateChange = onTtsStateChange,
                onOpenTtsSettings = onOpenTtsSettings,
                onDismiss = { showTtsSettings = false },
            )
        }

        if (showToc) {
            TocPanel(
                entries = state.tocEntries,
                currentPage = state.currentPage,
                onJump = { idx -> onTocJump(idx); showToc = false },
                onDismiss = { showToc = false },
            )
        }

        if (showBookmarks) {
            BookmarkPanel(
                bookmarks = state.bookmarks,
                onJump = { pg -> onPageChange(pg); showBookmarks = false },
                onAdd = { onAddBookmark(); showBookmarks = false },
                onDismiss = { showBookmarks = false },
            )
        }

        if (showVisualOptions) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                VisualOptionsSheet(
                    options = currentVisualOptions,
                    onOptionsChange = { onSaveVisualOptions(it); showVisualOptions = false },
                    onDismiss = { showVisualOptions = false },
                )
            }
        }

        if (showTranslateSettings) {
            Dialog(
                onDismissRequest = { showTranslateSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                TranslateSettingsPanel(
                    currentEngine = "google",
                    currentTargetLang = "zh-CN",
                    onSetEngine = onSetTranslateEngine,
                    onSetTargetLang = onSetTranslateTargetLang,
                    onDismiss = { showTranslateSettings = false },
                )
            }
        }

        if (showBgColor) {
            BgColorDialog(
                onSelect = { hex -> onSetReaderBgColor(hex); showBgColor = false },
                onReset = { onSetReaderBgColor(""); showBgColor = false },
                onDismiss = { showBgColor = false },
            )
        }

        if (showReadingSettings) {
            ReadingSettingsDialog(
                showStatusBar = state.showStatusBar,
                showNavBar = state.showNavBar,
                showProgressBar = state.showProgressBar,
                onToggleStatusBar = { onSaveSetting("showStatusBar", !state.showStatusBar) },
                onToggleNavBar = { onSaveSetting("showNavBar", !state.showNavBar) },
                onToggleProgressBar = { onSaveSetting("showProgressBar", !state.showProgressBar) },
                onDismiss = { showReadingSettings = false },
            )
        }

        if (showSearchBar) {
            FindReplaceDialog(
                onSearch = { query, nth -> onSearch(query, nth) },
                onReplaceAll = { from, to -> onReplaceAll(from, to) },
                onDismiss = { showSearchBar = false },
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BrightnessPanel(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
) {
    var followSystem by remember { mutableStateOf(brightness < 0f) }
    var eyeProtect by remember { mutableStateOf(false) }
    var localBrightness by remember(brightness) {
        mutableStateOf(if (brightness < 0f) 0.5f else brightness.coerceIn(0.01f, 1f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrightnessLow, null, modifier = Modifier.size(18.dp))
            Slider(
                value = if (followSystem) 0.5f else localBrightness,
                onValueChange = { if (!followSystem) { localBrightness = it; onBrightnessChange(it) } },
                valueRange = 0.01f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                enabled = !followSystem,
            )
            Icon(Icons.Default.BrightnessHigh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("随系统", style = MaterialTheme.typography.labelSmall)
            Switch(
                checked = followSystem,
                onCheckedChange = {
                    followSystem = it
                    if (it) onBrightnessChange(-1f)
                },
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("护眼", style = MaterialTheme.typography.labelSmall)
            Switch(
                checked = eyeProtect,
                onCheckedChange = { eyeProtect = it },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun FontPanel(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledTonalIconButton(
            onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(11)) },
            modifier = Modifier.size(40.dp),
        ) {
            Text("-", fontSize = 20.sp)
        }
        Text(
            text = "${fontSize}sp",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(28)) },
            modifier = Modifier.size(40.dp),
        ) {
            Text("+", fontSize = 20.sp)
        }
    }
}

fun applyOrientation(activity: Activity?, key: String) {
    activity?.requestedOrientation = when (key) {
        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        "portrait_reverse" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        "landscape_reverse" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = if (value < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else value
    window.attributes = attrs
}
