package jvm.daily

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppBackfillTest {

    @Test
    fun `backfillDates returns single date when from equals to`() {
        assertEquals(listOf("2026-06-03"), backfillDates("2026-06-03", "2026-06-03"))
    }

    @Test
    fun `backfillDates returns inclusive range`() {
        assertEquals(
            listOf("2026-06-03", "2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07", "2026-06-08"),
            backfillDates("2026-06-03", "2026-06-08"),
        )
    }

    @Test
    fun `backfillDates spans month boundary`() {
        assertEquals(
            listOf("2026-05-31", "2026-06-01", "2026-06-02"),
            backfillDates("2026-05-31", "2026-06-02"),
        )
    }

    @Test
    fun `backfillDates rejects reversed range`() {
        assertFailsWith<IllegalArgumentException> { backfillDates("2026-06-08", "2026-06-03") }
    }

    @Test
    fun `argValue reads value after flag`() {
        assertEquals("2026-06-03", listOf("--from", "2026-06-03", "--to", "2026-06-08").argValue("--from"))
        assertEquals("2026-06-08", listOf("--from", "2026-06-03", "--to", "2026-06-08").argValue("--to"))
    }

    @Test
    fun `argValue returns null when flag missing or has no value`() {
        assertEquals(null, listOf("--from", "2026-06-03").argValue("--to"))
        assertEquals(null, listOf("--from").argValue("--from"))
    }
}
