package com.booknext.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class BookMetadata(
    val authors: List<String>,
    val publisher: String,
    val description: String,
    val coverUrl: String?,
)

@Singleton
class MetadataService @Inject constructor(
    private val client: OkHttpClient,
) {

    suspend fun lookup(title: String, apiKey: String): BookMetadata? = withContext(Dispatchers.IO) {
        try {
            val queries = buildQueries(title)
            for (q in queries) {
                val result = tryQuery(q, apiKey)
                if (result != null) return@withContext result
            }
            null
        } catch (_: Exception) { null }
    }

    private suspend fun tryQuery(query: String, apiKey: String): BookMetadata? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=intitle:$encoded&maxResults=5&key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return null
            if (items.length() == 0) return null

            var best: JSONObject? = null
            var bestScore = -1
            for (i in 0 until items.length()) {
                val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                val hasAuthor = info.optJSONArray("authors")?.length() ?: 0 > 0
                val t = info.optString("title", "")
                val match = t.contains(query, ignoreCase = true)
                val score = when {
                    hasAuthor && match -> 3
                    hasAuthor -> 2
                    match -> 1
                    else -> 0
                }
                if (score >= 3) { best = info; break }
                if (score > bestScore) { bestScore = score; best = info }
            }
            if (best == null) best = items.getJSONObject(0).optJSONObject("volumeInfo")
            val info = best ?: return null

            val authors = mutableListOf<String>()
            val authorsArr = info.optJSONArray("authors")
            if (authorsArr != null) {
                for (i in 0 until authorsArr.length()) authors.add(authorsArr.getString(i))
            }
            val coverUrl = info.optJSONObject("imageLinks")?.optString("thumbnail")
                ?.replace("http://", "https://")
            BookMetadata(authors = authors, publisher = info.optString("publisher", ""),
                description = info.optString("description", ""), coverUrl = coverUrl)
        } catch (_: Exception) { null }
    }

    private fun buildQueries(raw: String): List<String> {
        val results = mutableListOf<String>()
        var t = raw.trim()
        t = t.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
        results.add(t)
        val cleaned = t
            .replace(Regex("[【\\[][^】\\]]*[】\\]]"), "")
            .replace(Regex("[（(][^）)]*[）)]"), "")
            .replace(Regex("[-_—].*$"), "")
            .trim()
        if (cleaned != t && cleaned.length >= 2) results.add(cleaned)
        if (cleaned.length > 4) results.add(cleaned.take(4))
        val cn = cleaned.replace(Regex("[^\\u4e00-\\u9fff]"), "")
        if (cn.length >= 2 && cn.length < 6) results.add(cn)
        return results.distinct()
    }

    suspend fun downloadCover(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: return@withContext null
            val bytes = body.bytes()
            if (bytes.isEmpty()) null else bytes
        } catch (_: Exception) { null }
    }
}
