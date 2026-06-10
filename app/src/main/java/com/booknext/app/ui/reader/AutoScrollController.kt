package com.booknext.app.ui.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoScrollController @Inject constructor() {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private var scrollJob: Job? = null
    private var speedMs: Long = 100L
    private var mode: ScrollMode = ScrollMode.SMOOTH
    private var onScrollTickRef: java.lang.ref.WeakReference<() -> Unit>? = null

    enum class ScrollMode { SMOOTH, LINE_BY_LINE }

    fun start(speedMs: Long = 100L, mode: ScrollMode = ScrollMode.SMOOTH, scope: CoroutineScope, tick: () -> Unit) {
        stop()
        this.speedMs = speedMs
        this.mode = mode
        this.onScrollTickRef = java.lang.ref.WeakReference(tick)
        _active.value = true
        scrollJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(speedMs)
                onScrollTickRef?.get()?.invoke()
            }
        }
    }

    fun stop() {
        scrollJob?.cancel()
        scrollJob = null
        _active.value = false
        onScrollTickRef = null
    }

    fun setSpeed(speedMs: Long) { this.speedMs = speedMs }
    fun setMode(mode: ScrollMode) { this.mode = mode }
}
