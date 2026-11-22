package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val EXT_TAG = "ExternalSignClient"

data class ExternalKnowledgeHit(
    val source: String,
    val title: String,
    val snippet: String,
    val url: String
)

/**
 * Lightweight HTTPS client for querying real open linguistic and encyclopedic APIs
 * (Wikipedia ES, Wiktionary ES, Wikidata, Wikimedia Commons) to enrich sign-language
 * lookups without requiring additional API keys.
 */
object ExternalSignKnowledgeClient {

    suspend fun lookupSignConcept(
        query: String,
        languageCode: String = "LSA"
    ): List<ExternalKnowledgeHit> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val languageLabel = when (languageCode.uppercase()) {
            "LSA" -> "Lengua de señas argentina"
            "LSE" -> "Lengua de signos española"
            "LSCh" -> "Lengua de señas chilena"
            "LIBRAS" -> "Língua Brasileira de Sinais"
            "ASL" -> "American Sign Language"
            else -> "Lengua de señas"
        }

        val hits = mutableListOf<ExternalKnowledgeHit>()
        hits += searchWikipediaEs("$languageLabel $cleanQuery")
        if (hits.isEmpty()) {
            hits += searchWikipediaEs("Lengua de señas $cleanQuery")
        }
        hits += searchWiktionaryEs(cleanQuery)
        hits += searchWikidataEs(cleanQuery)
        hits += searchWikimediaCommons("$cleanQuery sign language")

        hits.distinctBy { it.url }.take(8)
    }

    private fun searchWikipediaEs(searchQuery: String): List<ExternalKnowledgeHit> {
        val encoded = URLEncoder.encode(searchQuery, "UTF-8")
        val endpoint = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=3"
        val body = httpGet(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val searchArray = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            (0 until searchArray.length()).mapNotNull { i ->
                val item = searchArray.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val pageId = item.optLong("pageid", 0L)
                val rawSnippet = item.optString("snippet")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (title.isBlank() || pageId == 0L) return@mapNotNull null
                ExternalKnowledgeHit(
                    source = "Wikipedia ES",
                    title = title,
                    snippet = rawSnippet.ifBlank { "Artículo relacionado en Wikipedia en español." },
                    url = "https://es.wikipedia.org/?curid=$pageId"
                )
            }
        }.getOrElse {
            Log.w(EXT_TAG, "Wikipedia parse error: ${it.message}")
            emptyList()
        }
    }

    private fun searchWiktionaryEs(term: String): List<ExternalKnowledgeHit> {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val endpoint = "https://es.wiktionary.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=2"
        val body = httpGet(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val searchArray = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            (0 until searchArray.length()).mapNotNull { i ->
                val item = searchArray.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val pageId = item.optLong("pageid", 0L)
                val rawSnippet = item.optString("snippet")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (title.isBlank() || pageId == 0L) return@mapNotNull null
                ExternalKnowledgeHit(
                    source = "Wikcionario ES",
                    title = title,
                    snippet = rawSnippet.ifBlank { "Definición léxica en Wikcionario en español." },
                    url = "https://es.wiktionary.org/?curid=$pageId"
                )
            }
        }.getOrElse {
            Log.w(EXT_TAG, "Wiktionary parse error: ${it.message}")
            emptyList()
        }
    }

    private fun searchWikidataEs(term: String): List<ExternalKnowledgeHit> {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val endpoint = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=es&format=json&limit=2"
        val body = httpGet(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val results = root.optJSONArray("search") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id").trim()
                val label = item.optString("label").trim()
                val desc = item.optString("description").trim()
                if (id.isBlank() || label.isBlank()) return@mapNotNull null
                ExternalKnowledgeHit(
                    source = "Wikidata",
                    title = "$label ($id)",
                    snippet = desc.ifBlank { "Entidad semántica en Wikidata." },
                    url = "https://www.wikidata.org/wiki/$id"
                )
            }
        }.getOrElse {
            Log.w(EXT_TAG, "Wikidata parse error: ${it.message}")
            emptyList()
        }
    }

    private fun searchWikimediaCommons(searchQuery: String): List<ExternalKnowledgeHit> {
        val encoded = URLEncoder.encode(searchQuery, "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srnamespace=6&utf8=1&format=json&srlimit=2"
        val body = httpGet(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val results = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val pageId = item.optLong("pageid", 0L)
                if (title.isBlank() || pageId == 0L) return@mapNotNull null
                ExternalKnowledgeHit(
                    source = "Wikimedia Commons",
                    title = title.removePrefix("File:"),
                    snippet = "Archivo multimedia público relacionado en Wikimedia Commons.",
                    url = "https://commons.wikimedia.org/?curid=$pageId"
                )
            }
        }.getOrElse {
            Log.w(EXT_TAG, "Wikimedia Commons parse error: ${it.message}")
            emptyList()
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "PuenteLSA-Android/1.0 (https://github.com/ezpesce/Puente-LSA)")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(EXT_TAG, "HTTP ${connection.responseCode} from $urlString")
                null
            }
        } catch (e: Exception) {
            Log.w(EXT_TAG, "External lookup failed for $urlString: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
