package jvm.daily.source

import jvm.daily.config.OpenJdkMailConfig
import dev.vived.engine.model.FeedIngestStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpenJdkMailSourceTest {

    private val fixedNow = Instant.parse("2026-03-19T12:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    // Use "d MMM yyyy HH:mm:ss Z" format (no day-of-week, avoids calendar validation errors)
    private fun mboxMessage(subject: String, from: String, date: String, body: String) = """
From sender@example.com Wed Mar 18 10:00:00 2026
Subject: $subject
From: $from
Date: $date

$body

""".trimIndent()

    private fun buildMbox(vararg messages: String) = messages.joinToString("\n")

    @Test
    fun `sourceType is openjdk_mail`() {
        val source = OpenJdkMailSource(emptyList(), clock)
        assertEquals("openjdk_mail", source.sourceType)
    }

    @Test
    fun `thread with enough messages in window is emitted`() = runTest {
        // 3 messages within 2-day window → should pass minReplies=3
        val mbox = buildMbox(
            mboxMessage("Project Amber Progress", "Alice <alice@example.com>",
                "18 Mar 2026 08:00:00 +0000", "Initial message"),
            mboxMessage("Re: Project Amber Progress", "Bob <bob@example.com>",
                "18 Mar 2026 09:00:00 +0000", "Reply 1"),
            mboxMessage("Re: Project Amber Progress", "Carol <carol@example.com>",
                "18 Mar 2026 10:00:00 +0000", "Reply 2"),
        )

        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("amber-dev", minReplies = 3, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> mbox },
        )

        val outcomes = source.fetchOutcomes()
        assertEquals(1, outcomes.size)
        assertEquals(FeedIngestStatus.SUCCESS, outcomes[0].feed.status)
        val articles = outcomes[0].articles
        assertEquals(1, articles.size)
        assertTrue(articles[0].title.contains("amber-dev"))
        assertTrue(articles[0].title.contains("Project Amber Progress"))
    }

    @Test
    fun `thread without activity in window is skipped`() = runTest {
        // Messages are 10 days old — outside the 2-day window
        val mbox = buildMbox(
            mboxMessage("Old Thread", "Alice <alice@example.com>",
                "09 Mar 2026 08:00:00 +0000", "Stale message"),
            mboxMessage("Re: Old Thread", "Bob <bob@example.com>",
                "09 Mar 2026 09:00:00 +0000", "Stale reply"),
            mboxMessage("Re: Old Thread", "Carol <carol@example.com>",
                "09 Mar 2026 10:00:00 +0000", "Stale reply 2"),
        )

        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("amber-dev", minReplies = 2, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> mbox },
        )

        val outcomes = source.fetchOutcomes()
        assertEquals(FeedIngestStatus.SUCCESS, outcomes[0].feed.status)
        assertTrue(outcomes[0].articles.isEmpty(), "Stale thread should be skipped")
    }

    @Test
    fun `thread below minReplies threshold is skipped`() = runTest {
        // 1 message per month × 2 months = 2 total; minReplies=3 → skipped
        val mbox = buildMbox(
            mboxMessage("Low Activity", "Alice <alice@example.com>",
                "18 Mar 2026 08:00:00 +0000", "Single post"),
        )

        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("jdk-dev", minReplies = 3, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> mbox },
        )

        val outcomes = source.fetchOutcomes()
        assertTrue(outcomes[0].articles.isEmpty(), "Thread with only 2 messages should be skipped (minReplies=3)")
    }

    @Test
    fun `same thread active on different days generates different article IDs`() = runTest {
        val mboxDay1 = buildMbox(
            mboxMessage("Ongoing Thread", "Alice <alice@example.com>",
                "17 Mar 2026 08:00:00 +0000", "Day 1"),
            mboxMessage("Re: Ongoing Thread", "Bob <bob@example.com>",
                "17 Mar 2026 09:00:00 +0000", "Reply day 1"),
        )
        val mboxDay2 = buildMbox(
            mboxMessage("Ongoing Thread", "Alice <alice@example.com>",
                "17 Mar 2026 08:00:00 +0000", "Original"),
            mboxMessage("Re: Ongoing Thread", "Carol <carol@example.com>",
                "18 Mar 2026 09:00:00 +0000", "Reply day 2"),
        )

        val clockDay1 = object : Clock { override fun now() = Instant.parse("2026-03-17T12:00:00Z") }
        val clockDay2 = object : Clock { override fun now() = Instant.parse("2026-03-18T12:00:00Z") }

        val sourceDay1 = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("jdk-dev", minReplies = 2, sinceDays = 2)),
            clock = clockDay1,
            mboxFetcher = { _, _ -> mboxDay1 },
        )
        val sourceDay2 = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("jdk-dev", minReplies = 2, sinceDays = 2)),
            clock = clockDay2,
            mboxFetcher = { _, _ -> mboxDay2 },
        )

        val articlesDay1 = sourceDay1.fetchOutcomes().flatMap { it.articles }
        val articlesDay2 = sourceDay2.fetchOutcomes().flatMap { it.articles }

        assertEquals(1, articlesDay1.size)
        assertEquals(1, articlesDay2.size)
        assertNotEquals(articlesDay1[0].id, articlesDay2[0].id,
            "Article IDs must differ across days (lastActiveDay embedded in ID)")
    }

    @Test
    fun `Re-prefix variants are normalized when grouping threads`() = runTest {
        val mbox = buildMbox(
            mboxMessage("Pattern Matching JEP", "Alice <alice@example.com>",
                "18 Mar 2026 08:00:00 +0000", "Original post"),
            mboxMessage("Re: Pattern Matching JEP", "Bob <bob@example.com>",
                "18 Mar 2026 09:00:00 +0000", "Reply"),
            mboxMessage("Re: Re: Pattern Matching JEP", "Carol <carol@example.com>",
                "18 Mar 2026 10:00:00 +0000", "Reply 2"),
        )

        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("amber-dev", minReplies = 2, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> mbox },
        )

        val articles = source.fetchOutcomes().flatMap { it.articles }
        assertEquals(1, articles.size, "Re: variants should be grouped into one thread")
    }

    @Test
    fun `announce list with minReplies 0 and stale messages does not crash`() = runTest {
        // Reproduces NPE: announce has minReplies=0, so threads with 0 messages
        // in the window are NOT skipped (0 < 0 is false). Then maxOrNull()!! on
        // empty windowMsgs caused NPE. Fix: fallback to today's date.
        val mbox = buildMbox(
            mboxMessage("JDK 25 GA Released", "Iris <iris@oracle.com>",
                "25 Feb 2026 15:00:00 +0000", "Announcing JDK 25 GA"),
        )

        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("announce", minReplies = 0, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> mbox },
        )

        // Should not throw NPE — all messages outside window, but minReplies=0
        val outcomes = source.fetchOutcomes()
        assertEquals(FeedIngestStatus.SUCCESS, outcomes[0].feed.status)
        assertEquals(1, outcomes[0].articles.size)
        assertTrue(outcomes[0].articles[0].title.contains("JDK 25 GA"))
    }

    @Test
    fun `empty mbox results in success with 0 articles and error note`() = runTest {
        val source = OpenJdkMailSource(
            configs = listOf(OpenJdkMailConfig("loom-dev", minReplies = 2, sinceDays = 2)),
            clock = clock,
            mboxFetcher = { _, _ -> null },
        )

        val outcomes = source.fetchOutcomes()
        assertEquals(1, outcomes.size)
        assertEquals(FeedIngestStatus.SUCCESS, outcomes[0].feed.status)
        assertEquals(0, outcomes[0].articles.size)
        assertFalse(outcomes[0].feed.errors.isEmpty(), "Should have an error note about missing archives")
    }
}
