package jvm.daily

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AppReplayOptionsTest {

    @Test
    fun `parseReplayOptions should parse defaults`() {
        val options = parseReplayOptions(emptyList())
        assertEquals(24 * 7, options.sinceHours)
        assertEquals(50, options.limit)
        assertTrue(options.ids.isEmpty())
    }

    @Test
    fun `parseReplayOptions should parse id selector and dry-run`() {
        val options = parseReplayOptions(listOf("--ids", "a1,a2", "--dry-run"))
        assertEquals(listOf("a1", "a2"), options.ids)
        assertTrue(options.dryRun)
    }

    @Test
    fun `parseReplayOptions should reject mixed ids and range selectors`() {
        assertFailsWith<IllegalArgumentException> {
            parseReplayOptions(listOf("--ids", "a1", "--limit", "10"))
        }
    }

    @Test
    fun `parseReplayOptions should reject unknown option`() {
        assertFailsWith<IllegalStateException> {
            parseReplayOptions(listOf("--wat"))
        }
    }

    @Test
    fun `parseQualityReportOptions should parse defaults`() {
        val options = parseQualityReportOptions(emptyList())
        assertEquals(24, options.sinceHours)
        assertEquals("output", options.outputDir)
    }

    @Test
    fun `parseQualityReportOptions should parse custom values`() {
        val options = parseQualityReportOptions(
            listOf(
                "--since-hours", "48",
                "--output", "reports",
                "--max-duplicates", "10",
                "--max-feed-failures", "2",
                "--max-summarization-failures", "5",
                "--fail-on-threshold",
            )
        )
        assertEquals(48, options.sinceHours)
        assertEquals("reports", options.outputDir)
        assertEquals(10, options.maxDuplicates)
        assertEquals(2, options.maxFeedFailures)
        assertEquals(5, options.maxSummarizationFailures)
        assertTrue(options.failOnThreshold)
    }

    @Test
    fun `parseQualityReportOptions should reject unknown option`() {
        assertFailsWith<IllegalStateException> {
            parseQualityReportOptions(listOf("--wat"))
        }
    }
}
