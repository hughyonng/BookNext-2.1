package com.booknext.app.ui.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.service.DownloadStatus
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: BookEntity,
    baseUrl: String,
    apiKey: String,
    isSelected: Boolean = false,
    downloadProgress: Int = -1,          // -1 = no download, 0-99 = in progress
    downloadStatus: DownloadStatus? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    compact: Boolean = false,
) {
    Column(
        modifier = (if (compact) Modifier.width(72.dp) else Modifier.fillMaxWidth())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.Start,
    ) {
        Box {
            val cardSizeMod = if (compact) Modifier.size(width = 72.dp, height = 98.dp)
            else Modifier.fillMaxWidth().aspectRatio(0.68f)

            Card(
                modifier = cardSizeMod,
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 10.dp else 5.dp),
                shape = RoundedCornerShape(10.dp),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                if (book.coverPath != null) {
                    AsyncImage(model = File(book.coverPath), contentDescription = book.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (book.hasCover) {
                    AsyncImage(model = "$baseUrl/api/cover/${book.bookId}?k=$apiKey",
                        contentDescription = book.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    BookSpinePlaceholder(book = book, compact = compact)
                }
            }

            // ── 下载进度遮罩 ──────────────────────────────
            if (downloadStatus == DownloadStatus.DOWNLOADING && downloadProgress in 0..99) {
                Box(
                    modifier = cardSizeMod
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = if (compact) Modifier.size(28.dp) else Modifier.size(44.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeWidth = if (compact) 3.dp else 4.dp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$downloadProgress%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── 选中角标 ──────────────────────────────────
            if (isSelected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            }

            // ── 书籍来源标记 ──────────────────────────────
            if (book.bookId.startsWith("local_")) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp)
                        .background(Color(0xFF5C6BC0), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Description, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
            } else if (book.filePath == null && downloadStatus != DownloadStatus.DOWNLOADING) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp)
                        .background(Color(0xFF78909C), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Cloud, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}
