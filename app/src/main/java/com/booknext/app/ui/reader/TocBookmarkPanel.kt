package com.booknext.app.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun TocPanel(
    entries: List<TocEntry>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("目录", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
                if (entries.isEmpty()) {
                    Text("暂无目录", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(entries.size) { i ->
                            val entry = entries[i]
                            val isCurrent = entry.index == currentPage
                            TextButton(
                                onClick = { onJump(entry.index) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    entry.title,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    style = if (isCurrent) MaterialTheme.typography.bodyMedium
                                            else MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun BookmarkPanel(
    bookmarks: List<Int>,
    onJump: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("书签", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
                if (bookmarks.isEmpty()) {
                    Text("暂无书签", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(bookmarks.size) { i ->
                            TextButton(onClick = { onJump(bookmarks[i]) }, modifier = Modifier.fillMaxWidth()) {
                                Text("第 ${bookmarks[i] + 1} 页")
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onAdd) { Text("添加书签") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
