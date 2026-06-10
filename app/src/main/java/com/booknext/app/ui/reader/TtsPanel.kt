package com.booknext.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class TtsState(
    val engine: String = "edge",          // local / edge / azure / baidu / ali
    val cloudVoice: String = "zh-CN-XiaoxiaoNeural",
    val cloudRate: String = "+0%",
    val cloudPitch: String = "+0Hz",
    val azureApiKey: String = "",
    val baiduApiKey: String = "",
    val baiduSecretKey: String = "",
    val aliApiKey: String = "",
    val aliSecretKey: String = "",
)

val EDGE_VOICES = listOf(
    "zh-CN-XiaoxiaoNeural" to "晓晓（女声）",
    "zh-CN-YunyangNeural" to "云扬（男声）",
    "zh-CN-XiaoyiNeural" to "晓伊（女童）",
    "zh-CN-YunxiNeural" to "云希（男童）",
    "zh-CN-XiaohanNeural" to "晓涵（女声）",
    "en-US-JennyNeural" to "Jenny（英语）",
)

val AZURE_VOICES = EDGE_VOICES

val BAIDU_VOICES = listOf(
    "0" to "度小鹿（女声）",
    "1" to "度小宇（男声）",
    "3" to "度逍遥（男声）",
    "4" to "度丫丫（童声）",
    "5003" to "度博文（男声）",
)

val ALI_VOICES = listOf(
    "zhixiaobai" to "晓晓（女声）",
    "zhixiaokun" to "晓坤（男声）",
    "zhixiaotong" to "晓彤（女童）",
    "zhixiaofeng" to "晓峰（男童）",
    "zhixiaolu" to "晓璐（甜美女声）",
)

fun voiceListForEngine(engine: String): List<Pair<String, String>> = when (engine) {
    "azure" -> AZURE_VOICES
    "baidu" -> BAIDU_VOICES
    "ali" -> ALI_VOICES
    else -> EDGE_VOICES
}

@Composable
fun TtsControlPanel(
    isTtsPlaying: Boolean,
    onStop: () -> Unit,
    onTogglePlay: () -> Unit,
    onSettings: () -> Unit = {},
    isLoading: Boolean = false,
) {
    var volume by remember { mutableFloatStateOf(50f) }
    var pitch by remember { mutableFloatStateOf(10f) }
    var speed by remember { mutableFloatStateOf(10f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TtsSliderRow("音量", volume, 0f, 100f) { volume = it }
        TtsSliderRow("音调", pitch, 0f, 20f) { pitch = it }
        TtsSliderRow("语速", speed, 0f, 20f) { speed = it }
        if (isLoading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在生成语音…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "停止") }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isTtsPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
        }
    }
}

@Composable
fun TtsSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(36.dp))
        Text(value.toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(28.dp))
        Slider(
            value = value, onValueChange = onValueChange,
            valueRange = min..max, modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) },
            modifier = Modifier.size(32.dp),
        ) { Text("-") }
        IconButton(
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
            modifier = Modifier.size(32.dp),
        ) { Text("+") }
    }
}

@Composable
fun TtsSettingsDialog(
    ttsState: TtsState,
    onTtsStateChange: (TtsState) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isCloud = ttsState.engine != "local"
    val currentVoices = voiceListForEngine(ttsState.engine)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 380.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("朗读设置", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                // ── Engine options ──
                EngineOption(
                    icon = Icons.Default.PhoneAndroid,
                    label = "本地引擎",
                    selected = ttsState.engine == "local",
                    subText = "跳转系统文字转语音设置",
                    onClick = { onTtsStateChange(ttsState.copy(engine = "local")); onOpenTtsSettings() },
                    showChevron = ttsState.engine != "local",
                )
                if (ttsState.engine == "local") {
                    Text("当前引擎：系统首选 · 点击跳转设置更换", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, bottom = 4.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                EngineOption(
                    icon = Icons.Default.Cloud,
                    label = "Edge TTS（免费）",
                    selected = ttsState.engine == "edge",
                    subText = "无需密钥",
                    onClick = { onTtsStateChange(ttsState.copy(engine = "edge", cloudVoice = "zh-CN-XiaoxiaoNeural")) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CloudEngineSection(
                    title = "微软 Azure",
                    engineKey = "azure",
                    apiKeyLabel = "API Key",
                    ttsState = ttsState,
                    onTtsStateChange = onTtsStateChange,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CloudEngineSection(
                    title = "百度",
                    engineKey = "baidu",
                    apiKeyLabel = "API Key",
                    secretKeyLabel = "Secret Key",
                    ttsState = ttsState,
                    onTtsStateChange = onTtsStateChange,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CloudEngineSection(
                    title = "阿里云",
                    engineKey = "ali",
                    apiKeyLabel = "AppKey",
                    secretKeyLabel = "Token",
                    ttsState = ttsState,
                    onTtsStateChange = onTtsStateChange,
                )

                // ── Voice selection + speed/pitch ──
                if (isCloud) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    val hasKey = when (ttsState.engine) {
                        "edge" -> true
                        "azure" -> ttsState.azureApiKey.isNotBlank()
                        "baidu" -> ttsState.baiduApiKey.isNotBlank() && ttsState.baiduSecretKey.isNotBlank()
                        "ali" -> ttsState.aliApiKey.isNotBlank() && ttsState.aliSecretKey.isNotBlank()
                        else -> false
                    }

                    if (hasKey || ttsState.engine == "edge") {
                        Text("发音人", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        currentVoices.forEach { (id, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onTtsStateChange(ttsState.copy(cloudVoice = id)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                if (id == ttsState.cloudVoice) {
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TtsSliderRow("语速",
                            ttsState.cloudRate.removePrefix("+").removeSuffix("%").toFloatOrNull() ?: 0f,
                            -50f, 50f) { v ->
                            onTtsStateChange(ttsState.copy(cloudRate = "${if (v >= 0) "+" else ""}${v.toInt()}%"))
                        }
                        TtsSliderRow("音调",
                            ttsState.cloudPitch.removePrefix("+").removeSuffix("Hz").toFloatOrNull() ?: 0f,
                            -20f, 20f) { v ->
                            onTtsStateChange(ttsState.copy(cloudPitch = "${if (v >= 0) "+" else ""}${v.toInt()}Hz"))
                        }
                    } else {
                        Text("请先配置密钥以选择发音人", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun EngineOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    subText: String? = null,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (selected && subText != null) {
                    Text(subText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        } else if (showChevron) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CloudEngineSection(
    title: String,
    engineKey: String,
    apiKeyLabel: String,
    secretKeyLabel: String? = null,
    ttsState: TtsState,
    onTtsStateChange: (TtsState) -> Unit,
) {
    var showKeys by remember { mutableStateOf(false) }
    val isSelected = ttsState.engine == engineKey

    EngineOption(
        icon = Icons.Default.Cloud,
        label = title,
        selected = isSelected,
        onClick = { onTtsStateChange(ttsState.copy(engine = engineKey, cloudVoice = voiceListForEngine(engineKey).firstOrNull()?.first ?: ttsState.cloudVoice)) },
    )

    if (isSelected) {
        Spacer(Modifier.height(8.dp))

        val currentApiKey = when (engineKey) {
            "azure" -> ttsState.azureApiKey
            "baidu" -> ttsState.baiduApiKey
            "ali" -> ttsState.aliApiKey
            else -> ""
        }
        val currentSecretKey = when (engineKey) {
            "baidu" -> ttsState.baiduSecretKey
            "ali" -> ttsState.aliSecretKey
            else -> ""
        }

        OutlinedTextField(
            value = if (showKeys) currentApiKey else "••••••••",
            onValueChange = { v ->
                val new = ttsState.let { when (engineKey) {
                    "azure" -> it.copy(azureApiKey = v)
                    "baidu" -> it.copy(baiduApiKey = v)
                    "ali" -> it.copy(aliApiKey = v)
                    else -> it
                }}; onTtsStateChange(new)
            },
            label = { Text(apiKeyLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKeys) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKeys = !showKeys }) {
                    Icon(if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
        )
        if (secretKeyLabel != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = if (showKeys) currentSecretKey else "••••••••",
                onValueChange = { v ->
                    val new = ttsState.let { when (engineKey) {
                        "baidu" -> it.copy(baiduSecretKey = v)
                        "ali" -> it.copy(aliSecretKey = v)
                        else -> it
                    }}; onTtsStateChange(new)
                },
                label = { Text(secretKeyLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKeys) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
        }
        Text("密钥仅保存在本地，不会上传", fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))
    }
}
