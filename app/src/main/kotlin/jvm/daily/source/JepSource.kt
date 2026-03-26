package jvm.daily.source

import jvm.daily.config.JepConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.JepSnapshot
import jvm.daily.model.SourceFetchOutcome
import jvm.daily.storage.JepSnapshotRepository
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URI

class JepSource(
    private val repository: JepSnapshotRepository,
    private val config: JepConfig = JepConfig(),
    private val clock: Clock = Clock.System,
    private val fetcher: (String) -> String = ::httpGet,
) : Source {

    override val sourceType: String = "jep"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        val listHtml = try { fetcher(LIST_URL) } catch (e: Exception) {
            println("[jep] Failed to fetch list page: ${e.message}")
            return listOf(SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType, sourceId = SOURCE_ID,
                    status = FeedIngestStatus.FAILED, fetchedCount = 0,
                    errors = listOf(e.message ?: "Failed to fetch $LIST_URL"),
                ),
                articles = emptyList(),
            ))
        }

        val current = parseListPage(listHtml)
        val snapshots = repository.findAll().associateBy { it.jepNumber }

        // initialSeed with empty table: populate without emitting
        if (config.initialSeed && snapshots.isEmpty()) {
            println("[jep] initialSeed=true — populating snapshot table, emitting no articles")
            current.forEach { repository.upsert(toSnapshot(it, snapshots[it.jepNumber])) }
            return listOf(SourceFetchOutcome(
                feed = FeedIngestResult(sourceType, SOURCE_ID, FeedIngestStatus.SUCCESS, 0),
                articles = emptyList(),
            ))
        }

        if (config.initialSeed && snapshots.isNotEmpty()) {
            println("[jep] initialSeed=true ignored — snapshot table already has ${snapshots.size} rows, running change detection normally")
        }

        // Fetch individual pages for active JEPs to get updatedDate + summary
        val individualData = mutableMapOf<Int, Pair<String?, String?>>() // number -> (updatedDate, summary)
        current.filter { it.status in config.activeStatuses }.forEach { jep ->
            try {
                Thread.sleep(200)
                val html = fetcher("$JEP_BASE_URL/${jep.jepNumber}")
                val updatedDate = parseUpdatedDate(html)
                val summary = parseSummary(html)
                individualData[jep.jepNumber] = updatedDate to summary
            } catch (e: Exception) {
                println("[jep] Warning: failed to fetch JEP ${jep.jepNumber}: ${e.message}")
            }
        }

        val articles = mutableListOf<Article>()
        val now = clock.now().toString()

        for (fetched in current) {
            val old = snapshots[fetched.jepNumber]
            val (updatedDate, summary) = individualData[fetched.jepNumber] ?: (null to null)

            val changes = detectChanges(old, fetched, updatedDate)
            if (changes.isEmpty()) continue

            val changeType = if (changes.size > 1) "multi" else changes.keys.first()
            val effectiveUpdatedDate = updatedDate
                ?: old?.updatedDate
                ?: "unknown"

            val article = Article(
                id = CanonicalArticleId.from(
                    namespace = "jep",
                    sourceId = SOURCE_ID,
                    title = fetched.title,
                    url = null,
                    sourceNativeId = "jep-${fetched.jepNumber}-$effectiveUpdatedDate-$changeType",
                ),
                title = buildTitle(fetched.jepNumber, fetched.title, changes),
                content = buildContent(changes, summary),
                sourceType = sourceType,
                sourceId = SOURCE_ID,
                url = "$JEP_BASE_URL/${fetched.jepNumber}",
                ingestedAt = clock.now(),
            )
            articles.add(article)

            // Update snapshot after emitting (update-after-emit)
            repository.upsert(JepSnapshot(
                jepNumber = fetched.jepNumber,
                title = fetched.title,
                status = fetched.status,
                targetRelease = fetched.targetRelease,
                updatedDate = updatedDate ?: old?.updatedDate,
                summary = summary ?: old?.summary,
                lastSeenAt = now,
            ))
        }

        println("[jep] Detected ${articles.size} JEP change(s)")
        return listOf(SourceFetchOutcome(
            feed = FeedIngestResult(sourceType, SOURCE_ID, FeedIngestStatus.SUCCESS, articles.size),
            articles = articles,
        ))
    }

    private data class FetchedJep(
        val jepNumber: Int,
        val title: String,
        val status: String,
        val targetRelease: String?,
    )

    private fun parseListPage(html: String): List<FetchedJep> {
        val results = mutableListOf<FetchedJep>()
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        // New format: href="123" (relative) instead of href="/jeps/123"
        val hrefRegex = Regex("""href="(\d+)"""")
        val statusRegex = Regex("""title="Status:\s*([^"]+)"""")
        val releaseRegex = Regex("""title="Release:\s*([^"]+)"""")
        val titleRegex = Regex("""<a\s[^>]*href="\d+"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")

        for (row in rowRegex.findAll(html)) {
            val rowContent = row.groupValues[1]
            val jepNumber = hrefRegex.find(rowContent)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val title = titleRegex.find(rowContent)?.groupValues?.get(1)
                ?.replace(tagRegex, "")?.replace("&amp;", "&")?.trim()
                ?.ifBlank { null } ?: continue
            val status = statusRegex.find(rowContent)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: continue
            val release = releaseRegex.find(rowContent)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            results.add(FetchedJep(jepNumber, title, status, release))
        }
        return results
    }

    private fun parseUpdatedDate(html: String): String? {
        val regex = Regex("""Updated:\s*(\d{4}/\d{2}/\d{2})""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun parseSummary(html: String): String? {
        val regex = Regex("""<h2[^>]*>Summary</h2>\s*<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")
        return regex.find(html)?.groupValues?.get(1)?.replace(tagRegex, "")?.trim()?.take(500)
    }

    private fun detectChanges(old: JepSnapshot?, new: FetchedJep, updatedDate: String?): Map<String, String> {
        val changes = mutableMapOf<String, String>()
        if (old == null) {
            changes["new"] = "status: ${new.status}"
            return changes
        }
        if (old.status != new.status) changes["status"] = "${old.status} → ${new.status}"
        if (old.title != new.title) changes["title"] = "${old.title} → ${new.title}"
        if (old.targetRelease != new.targetRelease) changes["release"] = "${old.targetRelease} → ${new.targetRelease}"
        if (updatedDate != null && updatedDate != old.updatedDate) changes["content"] = "updated $updatedDate"
        return changes
    }

    private fun buildTitle(number: Int, title: String, changes: Map<String, String>): String {
        val desc = when {
            "new" in changes -> "new JEP"
            changes.size > 1 -> changes.values.joinToString(", ")
            "status" in changes -> "status: ${changes["status"]}"
            "title" in changes -> "title updated"
            "release" in changes -> "release: ${changes["release"]}"
            "content" in changes -> "content ${changes["content"]}"
            else -> changes.values.first()
        }
        return "JEP $number: $title — $desc"
    }

    private fun buildContent(changes: Map<String, String>, summary: String?): String = buildString {
        appendLine("[JEP TRACKING]")
        appendLine("topics: jep")
        for ((key, value) in changes) appendLine("$key: $value")
        if (summary != null) { appendLine(); appendLine("summary: $summary") }
    }

    private fun toSnapshot(jep: FetchedJep, existing: JepSnapshot?) = JepSnapshot(
        jepNumber = jep.jepNumber, title = jep.title, status = jep.status,
        targetRelease = jep.targetRelease, updatedDate = existing?.updatedDate,
        summary = existing?.summary, lastSeenAt = clock.now().toString(),
    )

    companion object {
        const val LIST_URL = "https://openjdk.org/jeps/0"
        const val JEP_BASE_URL = "https://openjdk.org/jeps"
        const val SOURCE_ID = "openjdk.org/jeps"

        fun httpGet(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JVM-Daily/1.0")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            return conn.inputStream.bufferedReader().readText()
        }
    }
}
