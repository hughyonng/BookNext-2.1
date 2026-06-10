package com.booknext.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val cloudVoice: String = "zh-CN-XiaoxiaoNeural",
    val cloudRate: String = "+0%",
    val cloudPitch: String = "+0Hz",
    val useLocal: Boolean = false,
)

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
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("朗读设置", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            onTtsStateChange(ttsState.copy(useLocal = true))
                            onOpenTtsSettings()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                        Text("手机朗读引擎", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (ttsState.useLocal) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (ttsState.useLocal) {
                    Text("当前引擎：系统首选 · 点击跳转设置更换", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 40.dp, bottom = 4.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onTtsStateChange(ttsState.copy(useLocal = false)) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cloud, null, tint = if (!ttsState.useLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text("云端引擎（微软）", style = MaterialTheme.typography.bodyLarge)
                            if (!ttsState.useLocal) {
                                Text("当前引擎", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!ttsState.useLocal) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!ttsState.useLocal) {
                    Spacer(Modifier.height(8.dp))

                val voices = listOf(
                    "zh-CN-XiaoxiaoNeural" to "晓晓（女声）",
                    "zh-CN-YunyangNeural" to "云扬（男声）",
                    "zh-CN-XiaoyiNeural" to "晓伊（女童）",
                    "zh-CN-YunxiNeural" to "云希（男童）",
                    "zh-CN-XiaohanNeural" to "晓涵（女声）",
                    "en-US-JennyNeural" to "Jenny（英语）",
                )
                Text("发音人", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                voices.forEach { (id, label) ->
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
                TtsSliderRow("语速", ttsState.cloudRate.removePrefix("+").removeSuffix("%").toFloatOrNull() ?: 0f, -50f, 50f) { v ->
                    onTtsStateChange(ttsState.copy(cloudRate = "${if (v >= 0) "+" else ""}${v.toInt()}%"))
                }
                TtsSliderRow("音调", ttsState.cloudPitch.removePrefix("+").removeSuffix("Hz").toFloatOrNull() ?: 0f, -20f, 20f) { v ->
                    onTtsStateChange(ttsState.copy(cloudPitch = "${if (v >= 0) "+" else ""}${v.toInt()}Hz"))
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
