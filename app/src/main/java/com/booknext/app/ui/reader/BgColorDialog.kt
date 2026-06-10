package com.booknext.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class BgColorOption(val label: String, val colorHex: String)
private val BG_COLORS = listOf(
    BgColorOption("默认白", "#F9F7F4"),
    BgColorOption("护眼绿", "#C7EDCC"),
    BgColorOption("羊皮纸", "#F5F0E8"),
    BgColorOption("浅灰", "#E8E8E8"),
    BgColorOption("豆沙绿", "#B7C9B7"),
    BgColorOption("杏仁", "#F7EED3"),
    BgColorOption("深蓝灰", "#1E2428"),
    BgColorOption("暗灰", "#3A3A3A"),
    BgColorOption("墨绿黑", "#1E2A1E"),
)
private const val BG_COLUMNS = 3

@Composable
fun BgColorDialog(
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读背景", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (BG_COLORS.isEmpty()) {
                    Text("无可用背景", modifier = Modifier.padding(16.dp))
                } else {
                    val rows = BG_COLORS.chunked(BG_COLUMNS)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { opt ->
                                BgColorCircle(opt, onSelect, Modifier.weight(1f))
                            }
                            repeat(BG_COLUMNS - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("跟随系统", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BgColorCircle(opt: BgColorOption, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(try { Color(android.graphics.Color.parseColor(opt.colorHex)) } catch (_: Exception) { Color.Gray })
                .clickable { onSelect(opt.colorHex) }
        )
        Spacer(Modifier.height(4.dp))
        Text(opt.label, style = MaterialTheme.typography.labelSmall)
    }
}
