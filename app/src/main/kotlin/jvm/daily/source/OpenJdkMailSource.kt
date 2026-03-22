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
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.time.Duration.Companion.days

/**
 * OpenJDK mailing list source — fetches monthly archives and aggregates threads.
 *
 * Uses Mailman plain-text mbox archives:
 *   https://mail.openjdk.org/pipermail/{list}/YYYY-Month.txt
 *
 * Each thread (grouped by normalized Subject) becomes one Article with all messages.
 * Threads with fewer than minReplies are filtered out (noise reduction).
 *
 * Key lists: jdk-dev, amber-dev, loom-dev, valhalla-dev, panama-dev, leyden-dev
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

            // Fetch current + previous month (early in the month there may be no archive yet)
            val currentMbox = fetchMbox(listName, currentMonth) ?: ""
            val prevMbox = fetchMbox(listName, prevMonth) ?: ""
            val mbox = (currentMbox + "\n" + prevMbox).trim()
            if (mbox.isBlank()) {
                return SourceFetchOutcome(
                    feed = FeedIngestResult(sourceType = sourceType, sourceId = listName,
                        status = FeedIngestStatus.SUCCESS, fetchedCount = 0,
                        errors = listOf("No archives found for current/previous month")),
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
                if (skippedLowActivity > 0) add("Skipped $skippedLowActivity threads with < ${config.minReplies} replies in last ${config.sinceDays}d")
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

    private data class MailMessage(
        val subject: String,
        val from: String,
        val date: String,
        val parsedDate: Instant?,
        val body: String,
    )

    private fun parseMbox(mbox: String): List<MailMessage> {
        val messages = mutableListOf<MailMessage>()
        // Split on mbox "From " delimiter at start of line
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
                            // Continuation of previous header
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

    private fun normalizeSubject(subject: String): String {
        // Decode MIME encoded words (=?UTF-8?Q?...?= or =?UTF-8?B?...?=)
        val decoded = decodeMimeSubject(subject)
        // Strip Re:, Fwd:, [External], etc.
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
        // "John Smith <john at openjdk.org>" -> "John Smith"
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
        val archiveUrl = "https://mail.openjdk.org/pipermail/$listName/"

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

        // ID includes lastActiveDay so re-active threads generate a fresh article each day.
        // archiveUrl is NOT passed — it's the same for all threads in a list and would collapse IDs.
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

    private fun fetchMbox(listName: String, yearMonth: YearMonth): String? {
        if (mboxFetcher != null) return mboxFetcher.invoke(listName, yearMonth)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.US)
        val url = "https://mail.openjdk.org/pipermail/$listName/${yearMonth.year}-$monthName.txt"
        return try { httpGet(url) } catch (_: Exception) { null }
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.use { it.bufferedReader().readText() }
    }
}
