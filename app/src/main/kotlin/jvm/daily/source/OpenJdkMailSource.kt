package jvm.daily.source

import jvm.daily.config.OpenJdkMailConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.net.HttpURLConnection
import java.net.URI
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.time.Duration.Companion.days

/**
 * OpenJDK mailing list source — fetches from Mailman 3 (HyperKitty) archives.
 *
 * Primary: scrapes HyperKitty monthly thread listing + individual thread pages
 *   https://mail.openjdk.org/archives/list/{list}@openjdk.org/{year}/{month}/
 * Fallback: legacy pipermail mbox for older pre-migration archives
 *   https://mail.openjdk.org/pipermail/{list}/YYYY-Month.txt
 *
 * Each thread becomes one Article. Threads with fewer than minReplies are filtered out.
 */
class OpenJdkMailSource(
    private val configs: List<OpenJdkMailConfig>,
    private val clock: Clock = Clock.System,
    private val mboxFetcher: ((listName: String, yearMonth: java.time.YearMonth) -> String?)? = null,
) : Source {

    override val sourceType: String = "openjdk_mail"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return configs.map { config -> fetchList(config) }
    }

    private fun fetchList(config: OpenJdkMailConfig): SourceFetchOutcome {
        val listName = config.list
        return try {
            val now = clock.now().toLocalDateTime(TimeZone.UTC)
            val currentMonth = YearMonth.of(now.year, now.monthNumber)
            val prevMonth = currentMonth.minusMonths(1)

            // If mboxFetcher is injected (tests), skip HyperKitty and use legacy path directly
            if (mboxFetcher != null) {
                return fetchListLegacy(config)
            }

            // Fetch threads from current + previous month via HyperKitty
            val currentThreads = fetchThreadsFromHyperKitty(listName, currentMonth)
            val prevThreads = fetchThreadsFromHyperKitty(listName, prevMonth)
            val allThreads = (currentThreads + prevThreads)
                .distinctBy { it.subject } // deduplicate threads spanning months

            if (allThreads.isEmpty()) {
                // Fallback to legacy pipermail
                return fetchListLegacy(config)
            }

            val windowStart = clock.now().minus(config.sinceDays.days)
            var skippedLowActivity = 0

            var skippedCodeReviews = 0
            val articles = allThreads.mapNotNull { thread ->
                // Skip RFR (Request For Review) threads — these are routine code reviews, not discussions
                if (isCodeReviewThread(thread.subject)) {
                    skippedCodeReviews++
                    return@mapNotNull null
                }
                // Filter by date and reply count
                if (thread.lastActive != null && thread.lastActive < windowStart) {
                    skippedLowActivity++
                    return@mapNotNull null
                }
                if (thread.replyCount < config.minReplies) {
                    skippedLowActivity++
                    return@mapNotNull null
                }

                val lastActiveDay = thread.lastActive
                    ?.toLocalDateTime(TimeZone.UTC)?.date?.toString()
                    ?: clock.now().toLocalDateTime(TimeZone.UTC).date.toString()

                // Fetch thread content for the article body
                val content = if (thread.threadUrl != null) {
                    fetchThreadContent(thread.threadUrl, listName, thread.subject)
                } else {
                    "Thread: ${thread.subject}\nList: $listName@openjdk.org | ${thread.replyCount + 1} messages"
                }

                val canonicalId = CanonicalArticleId.from(
                    sourceType, listName, "${thread.subject}::$lastActiveDay"
                )

                Article(
                    id = canonicalId,
                    title = "[$listName] ${thread.subject}",
                    content = content,
                    sourceType = sourceType,
                    sourceId = "$listName/${normalizeSubject(thread.subject)}",
                    url = thread.threadUrl ?: "https://mail.openjdk.org/archives/list/$listName@openjdk.org/",
                    author = thread.author,
                    ingestedAt = clock.now(),
                )
            }

            val errors = buildList {
                if (skippedCodeReviews > 0) add("Skipped $skippedCodeReviews RFR code review threads")
                if (skippedLowActivity > 0) add("Skipped $skippedLowActivity threads with < ${config.minReplies} replies or outside ${config.sinceDays}d window")
            }

            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = listName,
                    status = FeedIngestStatus.SUCCESS,
                    fetchedCount = articles.size,
                    errors = errors,
                ),
                articles = articles,
            )
        } catch (e: Exception) {
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = listName,
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf("${e.javaClass.simpleName}: ${e.message}"),
                ),
                articles = emptyList(),
            )
        }
    }

    // ── HyperKitty (Mailman 3) scraping ─────────────────────────────────────────

    private data class ThreadInfo(
        val subject: String,
        val author: String,
        val replyCount: Int,
        val lastActive: Instant?,
        val threadUrl: String?,
    )

    /**
     * Scrapes the HyperKitty monthly thread listing page to get thread metadata.
     * URL: /archives/list/{list}@openjdk.org/{year}/{month}/
     *
     * HTML structure per thread:
     *   <div class="thread-email ...">
     *     <a href="/archives/list/{list}@openjdk.org/thread/{hash}/"
     *        class="thread-title"> ... subject text ... </a>
     *     <span class="thread-date pull-right" title="Wednesday, 25 March 2026 12:11:54">
     *     <span class="badge bg-secondary">
     *       <i class="fa fa-comment" aria-label="replies"></i> 18
     */
    private fun fetchThreadsFromHyperKitty(listName: String, yearMonth: YearMonth): List<ThreadInfo> {
        val url = "https://mail.openjdk.org/archives/list/$listName@openjdk.org/${yearMonth.year}/${yearMonth.monthValue}/"
        val html = try { httpGet(url) } catch (_: Exception) { return emptyList() }

        val threads = mutableListOf<ThreadInfo>()

        // Split into thread blocks
        val threadBlocks = html.split("""<div class="thread-email""")

        for (block in threadBlocks.drop(1)) {
            // Extract thread URL and subject
            val linkMatch = Regex(
                """href="(/archives/list/[^"]+/thread/[^"]+/)"\s*\n\s*class="thread-title">""",
            ).find(block) ?: continue

            val threadPath = linkMatch.groupValues[1]
            val threadUrl = "https://mail.openjdk.org$threadPath"

            // Subject is after thread-title"> ... </a>, may span multiple lines with <i> tags
            val subjectMatch = Regex(
                """class="thread-title">\s*(?:<[^>]*>\s*)*([^<]+?)\s*</a>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(block)
            val subject = (subjectMatch?.groupValues?.get(1) ?: "").trim()
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#x27;", "'").replace("&#39;", "'").replace("&quot;", "\"")

            if (subject.isBlank()) continue

            // Extract date from: <span class="thread-date pull-right" title="Wednesday, 25 March 2026 12:11:54">
            val dateMatch = Regex("""thread-date[^"]*"[^>]*title="([^"]+)"""").find(block)
            val lastActive = dateMatch?.let { parseHyperKittyDate(it.groupValues[1]) }

            // Extract reply count from: <i ... aria-label="replies"></i>\n 18
            val replyMatch = Regex("""aria-label="replies"></i>\s*(\d+)""").find(block)
            val replyCount = replyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            threads.add(ThreadInfo(
                subject = normalizeSubject(subject),
                author = "",
                replyCount = replyCount,
                lastActive = lastActive,
                threadUrl = threadUrl,
            ))
        }

        return threads
    }

    /** Parses "Wednesday, 25 March 2026 12:11:54" */
    private fun parseHyperKittyDate(title: String): Instant? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy HH:mm:ss", Locale.US),
        )
        for (fmt in formatters) {
            try {
                val ldt = java.time.LocalDateTime.parse(title.trim(), fmt)
                val zdt = ldt.atZone(java.time.ZoneOffset.UTC)
                return Instant.fromEpochMilliseconds(zdt.toInstant().toEpochMilli())
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Fetches thread page and extracts message content for the article body.
     */
    private fun fetchThreadContent(threadUrl: String, listName: String, subject: String): String {
        val html = try { httpGet(threadUrl) } catch (_: Exception) {
            return "Thread: $subject\nList: $listName@openjdk.org"
        }

        // Extract message bodies from the thread page
        // HyperKitty wraps each message in <div class="email-body">
        val bodyPattern = Regex("""<div[^>]*class="[^"]*email-body[^"]*"[^>]*>(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL))
        val fromPattern = Regex("""class="[^"]*from[^"]*"[^>]*>([^<]+)""")
        val bodies = bodyPattern.findAll(html).take(10).toList() // limit to 10 messages

        return buildString {
            appendLine("Thread: $subject")
            appendLine("List: $listName@openjdk.org | Messages: ${bodies.size}+")
            appendLine()
            for ((i, match) in bodies.withIndex()) {
                val body = match.groupValues[1]
                    .replace(Regex("<[^>]+>"), "") // strip HTML tags
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#39;", "'").replace("&quot;", "\"")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(1500)
                val prefix = if (i == 0) "[OP]" else "[Reply $i]"
                appendLine("$prefix $body")
                appendLine()
            }
        }
    }

    private fun parseIsoDate(dateStr: String): Instant? {
        return try {
            Instant.parse(dateStr)
        } catch (_: Exception) {
            try {
                // Try parsing as ISO datetime without timezone
                val zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
                Instant.fromEpochMilliseconds(zdt.toInstant().toEpochMilli())
            } catch (_: Exception) { null }
        }
    }

    // ── Legacy pipermail fallback ────────────────────────────────────────────────

    private fun fetchListLegacy(config: OpenJdkMailConfig): SourceFetchOutcome {
        val listName = config.list
        val now = clock.now().toLocalDateTime(TimeZone.UTC)
        val currentMonth = YearMonth.of(now.year, now.monthNumber)
        val prevMonth = currentMonth.minusMonths(1)

        val currentMbox = fetchLegacyMbox(listName, currentMonth) ?: ""
        val prevMbox = fetchLegacyMbox(listName, prevMonth) ?: ""
        val mbox = (currentMbox + "\n" + prevMbox).trim()
        if (mbox.isBlank()) {
            return SourceFetchOutcome(
                feed = FeedIngestResult(sourceType = sourceType, sourceId = listName,
                    status = FeedIngestStatus.SUCCESS, fetchedCount = 0,
                    errors = listOf("No archives found (HyperKitty + pipermail both empty)")),
                articles = emptyList(),
            )
        }

        val messages = parseMbox(mbox)
        val threads = groupIntoThreads(messages)
        val windowStart = clock.now().minus(config.sinceDays.days)

        var skippedLowActivity = 0
        val articles = threads.mapNotNull { (subject, msgs) ->
            val windowMsgs = msgs.filter { it.parsedDate != null && it.parsedDate >= windowStart }
            if (windowMsgs.size < config.minReplies) {
                skippedLowActivity++
                return@mapNotNull null
            }
            val lastActiveDay = windowMsgs.mapNotNull { it.parsedDate }
                .maxOrNull()
                ?.toLocalDateTime(TimeZone.UTC)?.date?.toString()
                ?: clock.now().toLocalDateTime(TimeZone.UTC).date.toString()
            threadToArticle(listName, subject, msgs, windowMsgs, lastActiveDay)
        }

        val errors = buildList {
            add("Using legacy pipermail fallback")
            if (skippedLowActivity > 0) add("Skipped $skippedLowActivity threads with < ${config.minReplies} replies in last ${config.sinceDays}d")
        }

        return SourceFetchOutcome(
            feed = FeedIngestResult(sourceType = sourceType, sourceId = listName,
                status = FeedIngestStatus.SUCCESS, fetchedCount = articles.size, errors = errors),
            articles = articles,
        )
    }

    // ── Legacy mbox parsing (unchanged) ─────────────────────────────────────────

    private data class MailMessage(
        val subject: String,
        val from: String,
        val date: String,
        val parsedDate: Instant?,
        val body: String,
    )

    private fun parseMbox(mbox: String): List<MailMessage> {
        val messages = mutableListOf<MailMessage>()
        val rawMessages = mbox.split(Regex("(?m)^From (?=\\S+@\\S+|\\S+ at \\S+)"))
            .filter { it.isNotBlank() }

        for (raw in rawMessages) {
            val lines = raw.lines()
            val headers = mutableMapOf<String, String>()
            var inHeaders = true
            var lastHeaderKey = ""
            val bodyLines = mutableListOf<String>()

            for (line in lines) {
                if (inHeaders) {
                    when {
                        line.isBlank() -> inHeaders = false
                        line.startsWith(" ") || line.startsWith("\t") -> {
                            if (lastHeaderKey.isNotEmpty()) {
                                headers[lastHeaderKey] = headers[lastHeaderKey] + " " + line.trim()
                            }
                        }
                        line.contains(": ") -> {
                            val key = line.substringBefore(": ").lowercase()
                            val value = line.substringAfter(": ").trim()
                            headers[key] = value
                            lastHeaderKey = key
                        }
                    }
                } else {
                    bodyLines.add(line)
                }
            }

            val subject = headers["subject"] ?: ""
            val from = headers["from"] ?: ""
            val date = headers["date"] ?: ""

            if (subject.isNotBlank()) {
                messages.add(MailMessage(
                    subject = decodeMimeSubject(subject),
                    from = cleanFrom(decodeMimeSubject(from)),
                    date = date,
                    parsedDate = parseMailDate(date),
                    body = bodyLines.joinToString("\n").trim().take(3000),
                ))
            }
        }

        return messages
    }

    private fun groupIntoThreads(messages: List<MailMessage>): Map<String, List<MailMessage>> {
        return messages.groupBy { normalizeSubject(it.subject) }
    }

    /**
     * Detects routine code review threads that are not interesting for a daily digest.
     * RFR = "Request For Review" — automated code review notifications from OpenJDK.
     */
    private fun isCodeReviewThread(subject: String): Boolean {
        val normalized = subject.trim()
        return normalized.startsWith("RFR:", ignoreCase = true) ||
            normalized.contains("] RFR:", ignoreCase = true) ||
            normalized.startsWith("Integrated:", ignoreCase = true) ||
            normalized.contains("] Integrated:", ignoreCase = true) ||
            normalized.startsWith("Withdrawn:", ignoreCase = true) ||
            normalized.contains("] Withdrawn:", ignoreCase = true)
    }

    private fun normalizeSubject(subject: String): String {
        val decoded = decodeMimeSubject(subject)
        return decoded
            .replace(Regex("^(\\s*(Re|Fwd|\\[External\\])\\s*:?\\s*)+", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun decodeMimeSubject(subject: String): String {
        return subject.replace(Regex("=\\?([^?]+)\\?([BbQq])\\?([^?]+)\\?=")) { match ->
            val encoding = match.groupValues[2].uppercase()
            val payload = match.groupValues[3]
            try {
                when (encoding) {
                    "B" -> java.util.Base64.getDecoder().decode(payload).decodeToString()
                    "Q" -> payload.replace('_', ' ')
                        .replace(Regex("=([0-9A-Fa-f]{2})")) { hex ->
                            hex.groupValues[1].toInt(16).toChar().toString()
                        }
                    else -> match.value
                }
            } catch (_: Exception) { match.value }
        }.trim()
    }

    private fun cleanFrom(from: String): String {
        return from.substringBefore("<").substringBefore("(").trim()
            .ifBlank { from.substringBefore("@").substringBefore(" at ") }
    }

    private fun threadToArticle(
        listName: String,
        subject: String,
        allMessages: List<MailMessage>,
        windowMessages: List<MailMessage>,
        lastActiveDay: String,
    ): Article {
        val starter = allMessages.first()
        val participants = allMessages.map { it.from }.distinct()
        val archiveUrl = "https://mail.openjdk.org/archives/list/$listName@openjdk.org/"

        val content = buildString {
            appendLine("Thread: $subject")
            appendLine("List: $listName@openjdk.org | Total messages: ${allMessages.size} | New in window: ${windowMessages.size} | Participants: ${participants.size}")
            appendLine("Participants: ${participants.joinToString(", ")}")
            appendLine()
            appendLine("=== Recent messages (last ${windowMessages.size}) ===")
            for ((i, msg) in windowMessages.withIndex()) {
                val prefix = if (i == 0) "[OP]" else "[Reply $i]"
                appendLine("$prefix ${msg.from} (${msg.date})")
                appendLine(msg.body.take(1500))
                appendLine()
            }
        }

        val canonicalId = CanonicalArticleId.from(
            sourceType, listName, "$subject::$lastActiveDay"
        )

        return Article(
            id = canonicalId,
            title = "[$listName] $subject",
            content = content,
            sourceType = sourceType,
            sourceId = "$listName/${normalizeSubject(subject)}",
            url = archiveUrl,
            author = starter.from,
            ingestedAt = clock.now(),
        )
    }

    private fun parseMailDate(date: String): Instant? {
        if (date.isBlank()) return null
        val formatters = listOf(
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
        )
        for (fmt in formatters) {
            try {
                val zdt = ZonedDateTime.parse(date.trim(), fmt)
                return Instant.fromEpochMilliseconds(zdt.toInstant().toEpochMilli())
            } catch (_: Exception) {}
        }
        return null
    }

    private fun fetchLegacyMbox(listName: String, yearMonth: YearMonth): String? {
        if (mboxFetcher != null) return mboxFetcher.invoke(listName, yearMonth)
        val monthName = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        val url = "https://mail.openjdk.org/pipermail/$listName/${yearMonth.year}-$monthName.txt"
        return try { httpGet(url) } catch (_: Exception) { null }
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.use { it.bufferedReader().readText() }
    }
}
