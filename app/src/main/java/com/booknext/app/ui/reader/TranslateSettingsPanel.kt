package com.booknext.app.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun TranslateSettingsPanel(
    currentEngine: String,
    currentTargetLang: String,
    onSetEngine: (String) -> Unit,
    onSetTargetLang: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var selectedLang by remember { mutableStateOf(currentTargetLang) }
    val engines = listOf(
        "google" to "谷歌翻译（免费）",
        "baidu" to "百度翻译",
        "youdao" to "有道翻译",
    )
    val languages = listOf(
        "zh-CN" to "中文（简体）",
        "zh-TW" to "中文（繁体）",
        "en" to "英文",
        "ja" to "日文",
        "ko" to "韩文",
        "fr" to "法文",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("翻译设置", style = MaterialTheme.typography.titleMedium)
            Text("在线翻译", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            engines.forEach { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedEngine == key,
                        onClick = { selectedEngine = key },
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text("目标语言", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            languages.forEach { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedLang == key,
                        onClick = { selectedLang = key },
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    onSetEngine(selectedEngine)
                    onSetTargetLang(selectedLang)
                    onDismiss()
                }) { Text("确定") }
            }
        }
    }
}
