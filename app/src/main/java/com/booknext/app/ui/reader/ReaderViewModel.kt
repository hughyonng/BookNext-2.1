package com.booknext.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booknext.app.data.local.db.AnnotationEntity
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.local.db.ReadingSessionEntity
import com.booknext.app.data.local.prefs.ReaderPrefs
import com.booknext.app.data.local.prefs.UiPrefs
import com.booknext.app.data.remote.TranslatorService
import com.booknext.app.data.repository.AnnotationRepository
import com.booknext.app.data.repository.BookRepository
import com.booknext.app.data.repository.PreparedBook
import com.booknext.app.data.repository.ReadingSessionRepository
import com.booknext.app.ui.reader.options.ControlOptions
import com.booknext.app.ui.reader.options.OtherOptions
import com.booknext.app.ui.reader.options.VisualOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class ReaderState {
    data object Idle : ReaderState()
    data object Downloading : ReaderState()
    data class Ready(val file: File, val format: String) : ReaderState()
    data class Error(val msg: String) : ReaderState()
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val sessionRepository: ReadingSessionRepository,
    private val ttsController: TtsController,
    val autoScrollController: AutoScrollController,
    private val readerPrefs: ReaderPrefs,
    private val uiPrefs: UiPrefs,
    val translator: TranslatorService,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
    val state: StateFlow<ReaderState> = _state

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    val bookmarks: StateFlow<List<Int>> = _bookmarks

    private val _annotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    val annotations: StateFlow<List<AnnotationEntity>> = _annotations

    private val _sessions = MutableStateFlow<List<ReadingSessionEntity>>(emptyList())
    val sessions: StateFlow<List<ReadingSessionEntity>> = _sessions

    private var annotationsJob: Job? = null
    private var sessionsJob: Job? = null
    private var sessionStartMs: Long = 0L
    private var sessionStartProgress: Float = 0f
    private var sessionStartChars: Long = 0L
    private var loadedBook: PreparedBook? = null

    // ── 偏好 StateFlows ───────────────────────────────
    val darkMode: StateFlow<Boolean> = uiPrefs.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontSize: StateFlow<Int> = readerPrefs.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 17)
    val fontFamily: StateFlow<String> = readerPrefs.fontFamily.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "serif")
    val lineSpacing: StateFlow<Float> = readerPrefs.lineSpacing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.8f)
    val screenOrientation: StateFlow<String> = readerPrefs.screenOrientation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val brightness: StateFlow<Float> = readerPrefs.brightness.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1f)
    val textColor: StateFlow<String> = readerPrefs.textColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val bgColor: StateFlow<String> = readerPrefs.bgColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val fontBold: StateFlow<Boolean> = readerPrefs.fontBold.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontItalic: StateFlow<Boolean> = readerPrefs.fontItalic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontUnderline: StateFlow<Boolean> = readerPrefs.fontUnderline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontShadow: StateFlow<Boolean> = readerPrefs.fontShadow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val textJustify: StateFlow<Boolean> = readerPrefs.textJustify.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val chineseLayout: StateFlow<Boolean> = readerPrefs.chineseLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val verticalMode: StateFlow<Boolean> = readerPrefs.verticalMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val simplifiedTrad: StateFlow<String> = readerPrefs.simplifiedTrad.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val paraSpacing: StateFlow<Float> = readerPrefs.paraSpacing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val charSpacing: StateFlow<Float> = readerPrefs.charSpacing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    val fontScale: StateFlow<Float> = readerPrefs.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val paddingH: StateFlow<Int> = readerPrefs.paddingH.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
    val paddingTop: StateFlow<Int> = readerPrefs.paddingTop.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)
    val paddingBottom: StateFlow<Int> = readerPrefs.paddingBottom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)
    val pageAnimation: StateFlow<String> = readerPrefs.pageAnimation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val customFontPath: StateFlow<String> = readerPrefs.customFontPath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val tapLeftAction: StateFlow<String> = readerPrefs.tapLeftAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "prev_page")
    val tapRightAction: StateFlow<String> = readerPrefs.tapRightAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "next_page")
    val tapCenterAction: StateFlow<String> = readerPrefs.tapCenterAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "toggle_ui")
    val tapZone: StateFlow<String> = readerPrefs.tapZone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "three_zone")
    val nineZoneConfig: StateFlow<String> = readerPrefs.nineZoneConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val longPressAction: StateFlow<String> = readerPrefs.longPressAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "select_text")
    val volumeUpAction: StateFlow<String> = readerPrefs.volumeUpAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "prev_page")
    val volumeDownAction: StateFlow<String> = readerPrefs.volumeDownAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "next_page")
    val swipeLeftAction: StateFlow<String> = readerPrefs.swipeLeftAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val swipeRightAction: StateFlow<String> = readerPrefs.swipeRightAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val swipeUpAction: StateFlow<String> = readerPrefs.swipeUpAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val swipeDownAction: StateFlow<String> = readerPrefs.swipeDownAction.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val keepScreenOn: StateFlow<Boolean> = readerPrefs.keepScreenOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val edgeSwipeBrightness: StateFlow<Boolean> = readerPrefs.edgeSwipeBrightness.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val edgeSwipeFontSize: StateFlow<Boolean> = readerPrefs.edgeSwipeFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val smartIndent: StateFlow<Boolean> = readerPrefs.smartIndent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val removeExtraBlank: StateFlow<Boolean> = readerPrefs.removeExtraBlank.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val blueLight: StateFlow<Boolean> = uiPrefs.blueLight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val blueLightAmount: StateFlow<Float> = uiPrefs.blueLightAmount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.3f)
    val showStatusBar: StateFlow<Boolean> = readerPrefs.showStatusBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showNavBar: StateFlow<Boolean> = readerPrefs.showNavBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showProgressBar: StateFlow<Boolean> = readerPrefs.showProgressBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val keepLastLine: StateFlow<Boolean> = readerPrefs.keepLastLine.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val readingReminder: StateFlow<Boolean> = readerPrefs.readingReminder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val readingReminderMins: StateFlow<Int> = readerPrefs.readingReminderMins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    val ttsSplitMode: StateFlow<String> = readerPrefs.ttsSplitMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "sentence")
    val allowTiltFlip: StateFlow<Boolean> = readerPrefs.allowTiltFlip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val allowPinchFont: StateFlow<Boolean> = readerPrefs.allowPinchFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val allowSwipeFlip: StateFlow<Boolean> = readerPrefs.allowSwipeFlip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val epubUseBookFont: StateFlow<Boolean> = readerPrefs.epubUseBookFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val epubDisableCss: StateFlow<Boolean> = readerPrefs.epubDisableCss.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val epubShowAnnotations: StateFlow<Boolean> = readerPrefs.epubShowAnnotations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val translateEngine: StateFlow<String> = readerPrefs.translateEngine.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "google")
    val translateTargetLang: StateFlow<String> = readerPrefs.translateTargetLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh-CN")
    val nameReplacements: StateFlow<String> = readerPrefs.nameReplacements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ── 合并选项 ──────────────────────────────────────
    val visualOptions: StateFlow<VisualOptions> = combine(
        listOf<Flow<*>>(fontSize, lineSpacing, fontFamily, customFontPath)
    ) { arr ->
        VisualOptions(fontSize = arr[0] as Int, lineSpacing = arr[1] as Float, fontFamily = arr[2] as String, customFontPath = arr[3] as String)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VisualOptions())
    val controlOptions: StateFlow<ControlOptions> = combine(
        listOf<Flow<*>>(tapLeftAction, tapRightAction, tapCenterAction, longPressAction, volumeUpAction, volumeDownAction, tapZone, nineZoneConfig, swipeLeftAction, swipeRightAction, swipeUpAction, swipeDownAction)
    ) { arr ->
        ControlOptions(tapLeftAction = arr[0] as String, tapRightAction = arr[1] as String, tapCenterAction = arr[2] as String, longPressAction = arr[3] as String, volumeUpAction = arr[4] as String, volumeDownAction = arr[5] as String, tapZone = arr[6] as String, nineZoneConfig = arr[7] as String, swipeLeftAction = arr[8] as String, swipeRightAction = arr[9] as String, swipeUpAction = arr[10] as String, swipeDownAction = arr[11] as String)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ControlOptions())
    val otherOptions: StateFlow<OtherOptions> = combine(
        listOf<Flow<*>>(showStatusBar, showNavBar, showProgressBar, keepScreenOn, edgeSwipeBrightness, edgeSwipeFontSize, keepLastLine, smartIndent, removeExtraBlank, blueLight, blueLightAmount, readingReminder, readingReminderMins, ttsSplitMode, allowTiltFlip, allowPinchFont, allowSwipeFlip, epubUseBookFont, epubDisableCss, epubShowAnnotations)
    ) { arr ->
        OtherOptions(showStatusBar = arr[0] as Boolean, showNavBar = arr[1] as Boolean, showProgressBar = arr[2] as Boolean, keepScreenOn = arr[3] as Boolean, edgeSwipeBrightness = arr[4] as Boolean, edgeSwipeFontSize = arr[5] as Boolean, keepLastLine = arr[6] as Boolean, smartIndent = arr[7] as Boolean, removeExtraBlank = arr[8] as Boolean, blueLight = arr[9] as Boolean, blueLightAmount = arr[10] as Float, readingReminder = arr[11] as Boolean, readingReminderMins = arr[12] as Int, ttsSplitMode = arr[13] as String, allowTiltFlip = arr[14] as Boolean, allowPinchFont = arr[15] as Boolean, allowSwipeFlip = arr[16] as Boolean, epubUseBookFont = arr[17] as Boolean, epubDisableCss = arr[18] as Boolean, epubShowAnnotations = arr[19] as Boolean)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OtherOptions())

    // ── TTS 委托 ──────────────────────────────────────
    val ttsPlaying: StateFlow<Boolean> get() = ttsController.playing
    val ttsLoading: StateFlow<Boolean> get() = ttsController.loading
    val useLocalTts: StateFlow<Boolean> get() = ttsController.useLocalTts
    val ttsCloudVoice: StateFlow<String> get() = ttsController.cloudVoice
    val ttsCloudRate: StateFlow<String> get() = ttsController.cloudRate
    val ttsCloudPitch: StateFlow<String> get() = ttsController.cloudPitch
    fun setTtsCloudVoice(v: String) = ttsController.setCloudVoice(v)
    fun setTtsCloudRate(v: String) = ttsController.setCloudRate(v)
    fun setTtsCloudPitch(v: String) = ttsController.setCloudPitch(v)
    fun setUseLocalTts(v: Boolean) = ttsController.setUseLocalTts(v)
    fun openTtsSettings() = ttsController.openTtsSettings()
    fun startTts(text: String) = ttsController.start(text)
    fun stopTts() = ttsController.stop()
    fun setActivity(activity: android.app.Activity) = ttsController.setActivity(activity)

    val coverUrl: String?
        get() {
            val b = _book.value ?: return null
            return if (b.coverPath?.startsWith("http") == true) b.coverPath else null
        }

    init {
        ttsController.initPrefs()
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = ReaderState.Downloading
            try {
                val prepared = bookRepository.loadBook(bookId)
                loadedBook = prepared
                _book.value = prepared.book
                _progress.value = prepared.book.progress
                _bookmarks.value = uiPrefs.observeBookmarks(bookId).first()
                sessionStartMs = System.currentTimeMillis()
                sessionStartProgress = prepared.book.readingPercent
                _state.value = ReaderState.Ready(prepared.file, prepared.format)
            } catch (e: Exception) {
                _state.value = ReaderState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun toggleBookmark(page: Int) {
        val bookId = _book.value?.bookId ?: return
        val current = _bookmarks.value
        viewModelScope.launch {
            if (page in current) {
                _bookmarks.value = current - page
                uiPrefs.saveBookmarks(bookId, current - page)
            } else {
                _bookmarks.value = (current + page).distinct()
                uiPrefs.saveBookmarks(bookId, (current + page).distinct())
            }
        }
    }

    fun saveProgress(locatorJson: String) {
        val bookId = _book.value?.bookId ?: return
        _progress.value = locatorJson
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateProgress(bookId, locatorJson, 0f, System.currentTimeMillis())
        }
    }

    fun savePageProgress(page: Int) {
        val bookId = _book.value?.bookId ?: return
        _progress.value = page.toString()
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateProgressNumeric(bookId, page.toString(), System.currentTimeMillis())
        }
    }

    fun toggleFavorite() {
        val bookId = _book.value?.bookId ?: return
        viewModelScope.launch { bookRepository.toggleFavorite(bookId) }
    }

    // ── 标注 ───────────────────────────────────────────
    fun loadAnnotations(bookId: String) {
        annotationsJob?.cancel()
        annotationsJob = viewModelScope.launch(Dispatchers.IO) {
            annotationRepository.observeByBook(bookId).collect { _annotations.value = it }
        }
    }
    fun saveAnnotation(annotation: AnnotationEntity) {
        viewModelScope.launch(Dispatchers.IO) { annotationRepository.upsert(annotation) }
    }
    fun deleteAnnotation(id: String) {
        viewModelScope.launch(Dispatchers.IO) { annotationRepository.deleteById(id) }
    }

    // ── 阅读会话 ───────────────────────────────────────
    fun loadSessions(bookId: String) {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch(Dispatchers.IO) {
            sessionRepository.observeSessions(bookId).collect { _sessions.value = it }
        }
    }

    // ── 偏好写入 ───────────────────────────────────────
    fun setDarkMode(enabled: Boolean) { viewModelScope.launch { uiPrefs.saveDarkMode(enabled) } }
    fun setFontSize(size: Int) { viewModelScope.launch { readerPrefs.saveFontSize(size) } }
    fun setScreenOrientation(orientation: String) { viewModelScope.launch { readerPrefs.saveScreenOrientation(orientation) } }
    fun setBrightness(value: Float) { viewModelScope.launch { readerPrefs.saveBrightness(value) } }
    fun setBgColor(hex: String) { viewModelScope.launch { readerPrefs.saveBgColor(hex) } }
    fun setNameReplacements(json: String) { viewModelScope.launch { readerPrefs.saveNameReplacements(json) } }
    fun setTranslateEngine(engine: String) { viewModelScope.launch { readerPrefs.saveTranslateEngine(engine) } }
    fun setTranslateTargetLang(lang: String) { viewModelScope.launch { readerPrefs.saveTranslateTargetLang(lang) } }
    fun getPageTextForCopy(): String = ""
    fun saveVisualOptions(opt: VisualOptions) {
        viewModelScope.launch {
            readerPrefs.saveVisualOptions(fontSize = opt.fontSize, lineSpacing = opt.lineSpacing, fontFamily = opt.fontFamily)
            readerPrefs.saveCustomFontPath(opt.customFontPath)
        }
    }
    fun saveControlOptions(opt: ControlOptions) {
        viewModelScope.launch { readerPrefs.saveControlOptions(tapLeftAction = opt.tapLeftAction, tapRightAction = opt.tapRightAction, tapCenterAction = opt.tapCenterAction, longPressAction = opt.longPressAction, volumeUpAction = opt.volumeUpAction, volumeDownAction = opt.volumeDownAction, tapZone = opt.tapZone, nineZoneConfig = opt.nineZoneConfig, swipeLeftAction = opt.swipeLeftAction, swipeRightAction = opt.swipeRightAction, swipeUpAction = opt.swipeUpAction, swipeDownAction = opt.swipeDownAction) }
    }
    fun saveOtherOptions(opt: OtherOptions) {
        viewModelScope.launch { readerPrefs.saveOtherOptions(showStatusBar = opt.showStatusBar, showNavBar = opt.showNavBar, showProgressBar = opt.showProgressBar, keepScreenOn = opt.keepScreenOn, edgeSwipeBrightness = opt.edgeSwipeBrightness, edgeSwipeFontSize = opt.edgeSwipeFontSize, keepLastLine = opt.keepLastLine, smartIndent = opt.smartIndent, removeExtraBlank = opt.removeExtraBlank, blueLight = opt.blueLight, blueLightAmount = opt.blueLightAmount, readingReminder = opt.readingReminder, readingReminderMins = opt.readingReminderMins, ttsSplitMode = opt.ttsSplitMode, allowTiltFlip = opt.allowTiltFlip, allowPinchFont = opt.allowPinchFont, allowSwipeFlip = opt.allowSwipeFlip, epubUseBookFont = opt.epubUseBookFont, epubDisableCss = opt.epubDisableCss, epubShowAnnotations = opt.epubShowAnnotations) }
    }
    fun setCoverFromScreenshot(bookId: String, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateCoverPath(bookId, filePath)
        }
    }

    fun clearError() { _state.value = ReaderState.Idle }

    override fun onCleared() {
        ttsController.release()
        super.onCleared()
        val bookId = _book.value?.bookId ?: return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000
        val endProgress = _book.value?.readingPercent ?: 0f
        if (elapsed > 5 && loadedBook != null) {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                bookRepository.addReadingTime(bookId, elapsed)
                val session = ReadingSessionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    bookId = bookId,
                    startTime = sessionStartMs,
                    durationSeconds = elapsed,
                    progressPercent = endProgress,
                )
                sessionRepository.insert(session)
            }
        }
    }
}