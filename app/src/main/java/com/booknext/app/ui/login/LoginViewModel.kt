package com.booknext.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.remote.api.BookNextApi
import com.booknext.app.data.remote.dto.AuthRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

data class LoginState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val loading: Boolean = false,
    val error: String = "",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountPrefs: AccountPrefs,
) : ViewModel() {

    private val client = OkHttpClient()

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state

    val loginHistory: StateFlow<List<String>> = accountPrefs.loginHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val savedUrl = accountPrefs.serverUrl.first()
            if (savedUrl.isNotBlank()) {
                _state.value = _state.value.copy(serverUrl = savedUrl)
            }
        }
    }

    fun onUrlChange(v: String) {
        _state.value = _state.value.copy(serverUrl = v)
    }
    fun onKeyChange(v: String) { _state.value = _state.value.copy(apiKey = v) }

    fun login(onSuccess: () -> Unit) {
        val raw = _state.value.serverUrl.trim()
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        val key = _state.value.apiKey.trim()
        android.util.Log.d("LoginVM", "login: raw='$raw' url='$url' key='${key.take(4)}...'")
        if (url.isEmpty() || key.isEmpty()) {
            _state.value = _state.value.copy(error = "请填写服务器地址和密钥")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            try {
                val api = Retrofit.Builder()
                    .baseUrl("${url}/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(BookNextApi::class.java)
                val resp = api.auth(AuthRequest(key))
                if (resp.valid) {
                    accountPrefs.saveCredentials(url, key)
                    accountPrefs.addLoginUrl(url)
                    // 发现真实后端地址（用于上传，绕过 Worker 大文件请求体限制）
                    val directUrl = try {
                        api.getMeta("Bearer $key").backend.trimEnd('/')
                    } catch (e: Exception) {
                        android.util.Log.w("LoginVM", "getMeta 失败，回退直连: ${e.message}")
                        url
                    }
                    android.util.Log.d("LoginVM", "serverUrl=$url directUploadUrl=$directUrl")
                    accountPrefs.saveDirectUploadUrl(directUrl)
                    onSuccess()
                } else {
                    _state.value = _state.value.copy(error = "密钥无效，请检查后重试")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "登录失败：${e.message}")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
