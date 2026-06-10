package com.booknext.app.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.booknext.app.data.local.db.AnnotationEntity
import java.util.UUID

@Composable
fun NoteDialog(
    bookId: String,
    selectedText: String,
    selectedLocator: String,
    onSave: (AnnotationEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteContent by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("笔记") },
        text = {
            Column {
                Text(selectedText.take(100), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text("笔记内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ann = AnnotationEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    locatorJson = selectedLocator,
                    type = "note",
                    selectedText = selectedText,
                    note = noteContent,
                )
                onSave(ann)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("取消") }
        },
    )
}
