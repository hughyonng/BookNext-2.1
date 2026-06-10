package com.booknext.app.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FindReplaceDialog(
    onSearch: (String, Int) -> Unit,
    onReplaceAll: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var nthMatch by remember { mutableIntStateOf(0) }
    var searched by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp).statusBarsPadding()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text("搜索 / 替换", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = findText,
                onValueChange = { findText = it; searched = false },
                label = { Text("搜索内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (findText.isNotBlank()) {
                        IconButton(onClick = { findText = ""; searched = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = replaceText,
                onValueChange = { replaceText = it },
                label = { Text("替换为") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { nthMatch = 0; searched = true; onSearch(findText, 0); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = findText.isNotBlank(),
            ) { Text("搜索") }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { nthMatch++; onSearch(findText, nthMatch) },
                    modifier = Modifier.weight(1f),
                    enabled = findText.isNotBlank() && searched,
                ) { Text("下一个") }

                OutlinedButton(
                    onClick = { onReplaceAll(findText, replaceText); searched = false },
                    modifier = Modifier.weight(1f),
                    enabled = findText.isNotBlank(),
                ) { Text("替换全部") }
            }
        }
    }
}
