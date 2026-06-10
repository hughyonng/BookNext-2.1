package com.booknext.app.ui.reader

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import com.booknext.app.data.local.prefs.ReaderPrefs
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.data.remote.dto.TtsRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsController @Inject constructor(
    private val apiClient: ApiClient,
    private val readerPrefs: ReaderPrefs,
    private val accountPrefs: AccountPrefs,
    @ApplicationContext private val context: Context,
) {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _useLocalTts = MutableStateFlow(false)
    val useLocalTts: StateFlow<Boolean> = _useLocalTts

    private val _cloudVoice = MutableStateFlow("zh-CN-XiaoxiaoNeural")
    val cloudVoice: StateFlow<String> = _cloudVoice
    private val _cloudRate = MutableStateFlow("+0%")
    val cloudRate: StateFlow<String> = _cloudRate
    private val _cloudPitch = MutableStateFlow("+0Hz")
    val cloudPitch: StateFlow<String> = _cloudPitch
    private val _ttsEngine = MutableStateFlow("edge")
    val ttsEngine: StateFlow<String> = _ttsEngine
    private val _azureKey = MutableStateFlow("")
    val azureApiKey: StateFlow<String> = _azureKey
    private val _baiduKey = MutableStateFlow("")
    val baiduApiKey: StateFlow<String> = _baiduKey
    private val _baiduSecret = MutableStateFlow("")
    val baiduSecretKey: StateFlow<String> = _baiduSecret
    private val _aliKey = MutableStateFlow("")
    val aliApiKey: StateFlow<String> = _aliKey
    private val _aliSecret = MutableStateFlow("")
    val aliSecretKey: StateFlow<String> = _aliSecret

    private var cloudPlayer: MediaPlayer? = null
    private var localTts: TextToSpeech? = null
    private var pendingTtsText: String? = null
    private var ttsGeneration = 0L
    private var localTtsEngines: List<String>? = null
    private var activityRef: WeakReference<android.app.Activity>? = null
    private val internalScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Main
    )

    fun initPrefs() {
        internalScope.launch(Dispatchers.IO) {
            _cloudVoice.value = readerPrefs.ttsCloudVoice.first()
            _cloudRate.value = readerPrefs.ttsCloudRate.first()
            _cloudPitch.value = readerPrefs.ttsCloudPitch.first()
            _ttsEngine.value = readerPrefs.ttsEngine.first()
            _azureKey.value = readerPrefs.azureApiKey.first()
            _baiduKey.value = readerPrefs.baiduApiKey.first()
            _baiduSecret.value = readerPrefs.baiduSecretKey.first()
            _aliKey.value = readerPrefs.aliApiKey.first()
            _aliSecret.value = readerPrefs.aliSecretKey.first()
        }
    }

    fun setTtsEngine(v: String) { _ttsEngine.value = v; internalScope.launch { readerPrefs.saveTtsEngine(v) } }
    fun setCloudVoice(voice: String) {
        _cloudVoice.value = voice
        internalScope.launch { readerPrefs.saveTtsCloudVoice(voice) }
    }
    fun setCloudRate(rate: String) {
        _cloudRate.value = rate
        internalScope.launch { readerPrefs.saveTtsCloudRate(rate) }
    }
    fun setCloudPitch(pitch: String) {
        _cloudPitch.value = pitch
        internalScope.launch { readerPrefs.saveTtsCloudPitch(pitch) }
    }
    fun setUseLocalTts(v: Boolean) {
        _useLocalTts.value = v
        if (v) _ttsEngine.value = "local"
    }
    fun setAzureApiKey(v: String) { _azureKey.value = v; internalScope.launch { readerPrefs.saveAzureApiKey(v) } }
    fun setBaiduApiKey(v: String) { _baiduKey.value = v; internalScope.launch { readerPrefs.saveBaiduApiKey(v) } }
    fun setBaiduSecretKey(v: String) { _baiduSecret.value = v; internalScope.launch { readerPrefs.saveBaiduSecretKey(v) } }
    fun setAliApiKey(v: String) { _aliKey.value = v; internalScope.launch { readerPrefs.saveAliApiKey(v) } }
    fun setAliSecretKey(v: String) { _aliSecret.value = v; internalScope.launch { readerPrefs.saveAliSecretKey(v) } }

    fun setActivity(activity: android.app.Activity) {
        activityRef = WeakReference(activity)
    }

    fun openTtsSettings() {
        val tried = listOf(
            "com.android.settings.TTS_SETTINGS",
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "android.settings.TTS_SETTINGS",
        )
        for (action in tried) {
            try {
                val i = android.content.Intent(action)
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (e: Exception) {
                android.util.Log.w("TtsController", "跳转TTS设置失败 action=$action: ${e.message}")
            }
        }
        try {
            val i = android.content.Intent(Settings.ACTION_SETTINGS)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            android.util.Log.w("TtsController", "跳转系统设置失败: ${e.message}")
        }
    }

    fun start(text: String) {
        if (_ttsEngine.value == "local" || _useLocalTts.value) {
            startLocal(text)
        } else {
            startCloud(text, _ttsEngine.value)
        }
    }

    fun stop() {
        ttsGeneration++
        cloudPlayer?.apply { if (isPlaying) stop(); release() }
        cloudPlayer = null
        localTts?.stop()
        pendingTtsText = null
        _loading.value = false
        _playing.value = false
        try {
            context.cacheDir.listFiles { f -> f.name.startsWith("tts_") && f.name.endsWith(".mp3") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("TtsController", "清理缓存文件失败: ${e.message}")
        }
    }

    fun release() {
        localTts?.shutdown()
        localTts = null
        cloudPlayer?.release()
        cloudPlayer = null
    }

    private fun startCloud(text: String, engine: String = "edge") {
        _playing.value = true
        _loading.value = true
        val gen = ++ttsGeneration
        val safe = text.take(3800)
        internalScope.launch(Dispatchers.IO) {
            try {
                val voice = _cloudVoice.value
                val rate = _cloudRate.value
                val pitch = _cloudPitch.value
                val (apiKey, secretKey) = when (engine) {
                    "azure" -> _azureKey.value to ""
                    "baidu" -> _baiduKey.value to _baiduSecret.value
                    "ali" -> _aliKey.value to _aliSecret.value
                    else -> "" to ""
                }
                val req = TtsRequest(text = safe, voice = voice, rate = rate, pitch = pitch,
                    engine = engine, apiKey = apiKey, secretKey = secretKey)
                val body = apiClient.api().tts(req)
                val bytes = body.bytes()

                withContext(Dispatchers.Main) {
                    if (gen != ttsGeneration) return@withContext
                    try {
                        cloudPlayer?.release()
                        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                        tempFile.writeBytes(bytes)
                        cloudPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            setOnPreparedListener {
                                _loading.value = false
                                start()
                            }
                            setOnCompletionListener { _playing.value = false }
                            setOnErrorListener { _, what, extra ->
                                _loading.value = false
                                _playing.value = false
                                android.util.Log.e("BookNext", "MediaPlayer error what=$what extra=$extra")
                                true
                            }
                            prepare()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BookNext", "MediaPlayer setup: ${e.message}", e)
                        _loading.value = false
                        _playing.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BookNext", "Cloud TTS error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _loading.value = false
                    _playing.value = false
                }
            }
        }
    }

    private fun startLocal(text: String) {
        val gen = ++ttsGeneration
        _playing.value = true
        localTts?.stop()
        localTts?.shutdown()
        localTts = null

        val engines = localTtsEngines ?: run {
            val list = mutableListOf<String>()
            try {
                val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                context.packageManager.queryIntentServices(intent, 0).forEach {
                    list.add(it.serviceInfo.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.w("TtsController", "枚举TTS引擎失败: ${e.message}")
            }
            listOfNotNull(
                Settings.Secure.getString(context.contentResolver, "tts_default_synth"),
                "com.iflytek.vflynote", "com.iflytek.tts",
                "com.iflytek.speechcloud", "com.baidu.tts",
            ).filter { it !in list }.let { list.addAll(it) }
            if (list.isEmpty()) list.add("")
            localTtsEngines = list.distinct()
            list.distinct()
        }

        val actCtx = activityRef?.get()
        val ctxList = if (actCtx != null) listOf(context, actCtx) else listOf(context)
        tryEnginesMultiCtx(text, safeTtsText(text), engines, ctxList, 0, 0, gen)
    }

    private fun tryEnginesMultiCtx(text: String, safe: String, engines: List<String>, ctxs: List<Context>, eIdx: Int, cIdx: Int, gen: Long) {
        if (gen != ttsGeneration) { _playing.value = false; return }
        if (cIdx >= ctxs.size) { _playing.value = false; return }
        if (eIdx >= engines.size) {
            tryEnginesMultiCtx(text, safe, engines, ctxs, 0, cIdx + 1, gen)
            return
        }
        val pkg = engines[eIdx]
        val ctx = ctxs[cIdx]
        pendingTtsText = safe
        localTts = TextToSpeech(ctx, { status ->
            android.util.Log.d("BookNext", "TTS engine [$pkg] ctx[$cIdx] init=$status")
            if (status != TextToSpeech.SUCCESS) {
                localTts?.shutdown()
                localTts = null
                tryEnginesMultiCtx(text, safe, engines, ctxs, eIdx + 1, cIdx, gen)
                return@TextToSpeech
            }
            android.util.Log.d("BookNext", "TTS engine [$pkg] ready! speaking ${safe.length} chars")
            pendingTtsText = null
            localTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { _playing.value = false }
                override fun onError(utteranceId: String?) { _playing.value = false }
            })
            try {
                val result = localTts?.speak(safe, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                android.util.Log.d("BookNext", "TTS speak result=$result")
                if (result == null || result == TextToSpeech.ERROR) {
                    _playing.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("BookNext", "TTS speak error: ${e.message}")
                _playing.value = false
            }
        }, pkg.ifEmpty { null })
    }

    private fun safeTtsText(text: String): String {
        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3800)
            .coerceAtMost(text.length)
        return text.substring(0, maxLen)
    }
}
