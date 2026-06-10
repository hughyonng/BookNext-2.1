package com.booknext.app.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReadingSettingsDialog(
    showStatusBar: Boolean,
    showNavBar: Boolean,
    showProgressBar: Boolean,
    onToggleStatusBar: () -> Unit,
    onToggleNavBar: () -> Unit,
    onToggleProgressBar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReadingSettingToggle("显示顶部状态栏", showStatusBar, onToggleStatusBar)
                ReadingSettingToggle("显示底部导航栏", showNavBar, onToggleNavBar)
                ReadingSettingToggle("阅读进度条", showProgressBar, onToggleProgressBar)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ReadingSettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}
