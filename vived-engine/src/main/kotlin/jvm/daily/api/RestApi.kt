package jvm.daily.api

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import jvm.daily.storage.FeedRunSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Headless REST API for JVM Daily.
 *
 * Endpoints:
 *   GET  /api/dates        → available digest dates (sorted desc)
 *   GET  /api/daily/{date} → digest JSON for a specific date
 *   GET  /api/pipeline     → pipeline status (from JobRunr)
 *   POST /api/ingest       → receive articles from external sources
 *   GET  /                 → serve viewer SPA (index.html)
 */
fun startRestApi(
    port: Int,
    outputDir: Path,
    dbPath: String,
    ingestApiKey: String?,
    jobRunrUrl: String = "http://localhost:8000",
    viewerDir: Path? = null,
) {
    embeddedServer(CIO, port = port) {
        routing {
            // ── API: available dates ────────────────────────────────────────
            get("/api/dates") {
                val dates = outputDir.toFile().listFiles()
                    ?.filter { it.name.startsWith("daily-") && it.name.endsWith(".json") }
                    ?.map { it.name.removePrefix("daily-").removeSuffix(".json") }
                    ?.sorted()
                    ?.reversed()
                    ?: emptyList()
                call.respondText(
                    Json.encodeToString(dates),
                    ContentType.Application.Json,
                )
            }

            // ── API: daily digest ───────────────────────────────────────────
            get("/api/daily/{date}") {
                val date = call.parameters["date"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    return@get call.respond(HttpStatusCode.BadRequest, """{"error":"invalid date"}""")
                }
                val file = outputDir.resolve("daily-$date.json")
                if (!file.exists()) {
                    return@get call.respond(HttpStatusCode.NotFound, """{"error":"not found"}""")
                }
                call.respondBytes(file.readBytes(), ContentType.Application.Json)
            }

            // ── API: backward compat for /api/files ─────────────────────────
            get("/api/files") {
                val files = outputDir.toFile().listFiles()
                    ?.filter { it.name.startsWith("jvm-daily-") && it.name.endsWith(".md") }
                    ?.map { it.name }
                    ?.sorted()
                    ?.reversed()
                    ?: emptyList()
                call.respondText(
                    Json.encodeToString(files),
                    ContentType.Application.Json,
                )
            }

            // ── API: feed run summaries ───────────────────────────────────────
            get("/api/feed-runs") {
                try {
                    DuckDbConnectionFactory.persistent(dbPath).use { conn ->
                        val repo = DuckDbArticleRepository(conn)
                        val summaries = repo.queryFeedRunSummaries()
                        call.respondText(
                            feedRunSummariesToJson(summaries),
                            ContentType.Application.Json,
                        )
                    }
                } catch (e: Exception) {
                    call.respondText(
                        """{"error":"${e.message?.replace("\"", "'")}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }

            // ── API: pipeline status ────────────────────────────────────────
            get("/api/pipeline") {
                try {
                    val stats = jobRunrGet("$jobRunrUrl/api/jobs/default/stats")
                    val succeeded = jobRunrGet("$jobRunrUrl/api/jobs?state=SUCCEEDED&limit=15")
                    val failed = jobRunrGet("$jobRunrUrl/api/jobs?state=FAILED&limit=5")

                    val succItems = extractItems(succeeded)
                    val failItems = extractItems(failed)
                    val allJobs = (failItems + succItems)
                        .sortedByDescending { it.optString("createdAt") }
                        .take(20)

                    val statsObj = stats?.let { Json.parseToJsonElement(it).jsonObject }
                    val response = buildString {
                        append("""{"stats":{""")
                        append(""""succeeded":${statsObj?.get("allTimeSucceeded") ?: statsObj?.get("succeeded") ?: "null"},""")
                        append(""""failed":${statsObj?.get("failed") ?: 0},""")
                        append(""""scheduled":${statsObj?.get("scheduled") ?: 0}""")
                        append("""},"recentJobs":[""")
                        append(allJobs.joinToString(",") { it.raw })
                        append("]}")
                    }
                    call.respondText(response, ContentType.Application.Json)
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"pipeline offline"}""")
                }
            }

            // ── API: ingest (receive articles from external sources) ────────
            post("/api/ingest") {
                if (ingestApiKey == null) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }
                val auth = call.request.header("Authorization") ?: ""
                if (auth != "Bearer $ingestApiKey") {
                    return@post call.respond(HttpStatusCode.Unauthorized, """{"error":"unauthorized"}""")
                }
                try {
                    val body = call.receiveText()
                    val articles = parseIngestPayload(body)
                    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
                        DuckDbArticleRepository(connection).saveAll(articles)
                    }
                    call.respondText("""{"saved":${articles.size}}""", ContentType.Application.Json)
                    println("[ingest-api] Received ${articles.size} article(s)")
                } catch (e: Exception) {
                    call.respondText(
                        """{"error":"${e.message?.replace("\"", "'")}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }

            // ── Serve viewer SPA (static files from SvelteKit build) ────────
            get("/{path...}") {
                val requestPath = call.request.uri.removePrefix("/").split("?")[0]
                val baseDir = viewerDir ?: findViewerDir()

                if (baseDir == null) {
                    call.respondText("JVM Daily API — viewer not found", ContentType.Text.Plain)
                    return@get
                }

                // Try to serve the exact file
                val file = baseDir.resolve(requestPath)
                if (requestPath.isNotBlank() && file.exists() && !file.toFile().isDirectory) {
                    val contentType = when {
                        requestPath.endsWith(".js") -> ContentType.Application.JavaScript
                        requestPath.endsWith(".css") -> ContentType.Text.CSS
                        requestPath.endsWith(".html") -> ContentType.Text.Html
                        requestPath.endsWith(".json") -> ContentType.Application.Json
                        requestPath.endsWith(".svg") -> ContentType("image", "svg+xml")
                        requestPath.endsWith(".png") -> ContentType.Image.PNG
                        requestPath.endsWith(".ico") -> ContentType("image", "x-icon")
                        requestPath.endsWith(".woff2") -> ContentType("font", "woff2")
                        else -> ContentType.Application.OctetStream
                    }
                    call.respondBytes(file.readBytes(), contentType)
                    return@get
                }

                // SPA fallback: serve index.html for all non-file routes
                val indexFile = baseDir.resolve("index.html")
                if (indexFile.exists()) {
                    call.respondText(indexFile.readText(), ContentType.Text.Html)
                } else {
                    call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
        }
    }.start(wait = false)
    println("REST API listening on :$port")
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun findViewerDir(): Path? {
    val candidates = listOf(
        Path.of("viewer-svelte/build"),             // SvelteKit build (local dev)
        Path.of("viewer/build"),                    // alternative local
        Path.of("/app/viewer"),                     // Docker container
    )
    return candidates.firstOrNull { it.exists() && it.resolve("index.html").exists() }
}

private fun jobRunrGet(url: String): String? {
    return try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.use { it.bufferedReader().readText() }
    } catch (_: Exception) { null }
}

private data class JobItem(val raw: String) {
    fun optString(key: String): String {
        val match = Regex(""""$key"\s*:\s*"([^"]+)"""").find(raw)
        return match?.groupValues?.get(1) ?: ""
    }
}

private fun feedRunSummariesToJson(summaries: List<FeedRunSummary>): String {
    return buildString {
        append("[")
        summaries.forEachIndexed { i, s ->
            if (i > 0) append(",")
            append("{")
            append(""""sourceType":"${s.sourceType}",""")
            append(""""sourceId":"${s.sourceId}",""")
            append(""""lastRunAt":"${s.lastRunAt}",""")
            append(""""lastRunStatus":"${s.lastRunStatus}",""")
            append(""""lastSuccessAt":${if (s.lastSuccessAt != null) "\"${s.lastSuccessAt}\"" else "null"},""")
            append(""""last24hRuns":${s.last24hRuns},""")
            append(""""last24hSuccesses":${s.last24hSuccesses},""")
            append(""""last24hFailures":${s.last24hFailures},""")
            append(""""last24hNewCount":${s.last24hNewCount}""")
            append("}")
        }
        append("]")
    }
}

private fun extractItems(json: String?): List<JobItem> {
    if (json == null) return emptyList()
    val itemsMatch = Regex(""""items"\s*:\s*\[(.*)]\s*\}""", RegexOption.DOT_MATCHES_ALL).find(json)
        ?: return emptyList()
    val itemsArray = itemsMatch.groupValues[1]
    // Split top-level JSON objects (simple brace counting)
    val items = mutableListOf<String>()
    var depth = 0
    var start = -1
    for ((i, c) in itemsArray.withIndex()) {
        if (c == '{') { if (depth == 0) start = i; depth++ }
        if (c == '}') { depth--; if (depth == 0 && start >= 0) items.add(itemsArray.substring(start, i + 1)) }
    }
    return items.map { JobItem(it) }
}

